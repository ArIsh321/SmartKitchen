package com.arise.smartkitchenai.auth

import android.content.Context
import android.os.SystemClock
import android.util.Base64
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom

/** Roles in order of seniority: a role can view its own screens and those of lower roles. */
enum class Role(val label: String, val description: String) {
    OWNER("Owner", "Reports, analytics and AI recommendations"),
    MANAGER("Manager", "Inventory, purchasing and waste monitoring"),
    STAFF("Kitchen staff", "Quick usage and waste entry");

    /** Roles whose screens this role may open (itself and everything below it). */
    val canView: List<Role> get() = entries.filter { it.ordinal >= ordinal }
}

/**
 * @param loggedIn who entered their PIN.
 * @param viewing whose screens are shown; Owner/Manager can switch to lower roles without a PIN.
 */
data class Session(val loggedIn: Role, val viewing: Role)

sealed interface PinResult {
    data object Success : PinResult
    data class Wrong(val attemptsLeft: Int) : PinResult
    data class LockedOut(val secondsLeft: Int) : PinResult
}

/**
 * Stores salted SHA-256 hashes of each role's PIN on the device and tracks the current session.
 * This is a convenience lock for a shared kitchen device, not strong security.
 */
class RoleManager(context: Context) {

    private val prefs = context.getSharedPreferences("role_pins", Context.MODE_PRIVATE)
    private val random = SecureRandom()

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _isSetUp = MutableStateFlow(Role.entries.all { hasPin(it) })
    /** False until the Owner has created all three PINs on first launch. */
    val isSetUp: StateFlow<Boolean> = _isSetUp.asStateFlow()

    /** When the app last went to the background (elapsedRealtime), or 0 while in the foreground. */
    private var backgroundedAt = 0L

    fun hasPin(role: Role): Boolean = prefs.contains(key(role))

    fun setPin(role: Role, pin: String) {
        require(pin.length == PIN_LENGTH && pin.all(Char::isDigit)) { "PIN must be $PIN_LENGTH digits" }
        val salt = ByteArray(16).also(random::nextBytes)
        val value = encode(salt) + ":" + encode(hash(salt, pin))
        prefs.edit { putString(key(role), value) }
        _isSetUp.value = Role.entries.all { hasPin(it) }
    }

    /**
     * Checks the PIN and, if correct, starts a session for that role.
     * After 5 wrong tries the PIN pad locks: 30 s, then 60 s, 120 s … up to 15 min. Attempts and the
     * lock are stored on disk, so closing or restarting the app doesn't reset them.
     */
    fun login(role: Role, pin: String): PinResult {
        val now = System.currentTimeMillis()
        val lockedUntil = prefs.getLong(KEY_LOCKED_UNTIL, 0L)
        // Ignore a lock further ahead than the maximum (e.g. the device clock was moved back).
        if (now < lockedUntil && lockedUntil - now <= MAX_LOCKOUT_MS) {
            return PinResult.LockedOut(((lockedUntil - now) / 1000 + 1).toInt())
        }

        if (verify(role, pin)) {
            prefs.edit { putInt(KEY_FAILED, 0).putInt(KEY_LOCKOUTS, 0).putLong(KEY_LOCKED_UNTIL, 0L) }
            _session.value = Session(loggedIn = role, viewing = role)
            return PinResult.Success
        }

        val failed = prefs.getInt(KEY_FAILED, 0) + 1
        if (failed >= MAX_ATTEMPTS) {
            val lockouts = prefs.getInt(KEY_LOCKOUTS, 0)
            val duration = (BASE_LOCKOUT_MS shl lockouts.coerceAtMost(5)).coerceAtMost(MAX_LOCKOUT_MS)
            prefs.edit {
                putInt(KEY_FAILED, 0)
                putInt(KEY_LOCKOUTS, lockouts + 1)
                putLong(KEY_LOCKED_UNTIL, now + duration)
            }
            return PinResult.LockedOut((duration / 1000).toInt())
        }
        prefs.edit { putInt(KEY_FAILED, failed) }
        return PinResult.Wrong(MAX_ATTEMPTS - failed)
    }

    /** Call when the app goes to the background. */
    fun onAppBackgrounded() {
        backgroundedAt = SystemClock.elapsedRealtime()
    }

    /**
     * Call when the app returns to the foreground: locks the session if the app was away longer than
     * [AUTO_LOCK_MS]. (A short grace period lets the barcode scanner, date pickers and share sheet
     * — which briefly cover the app — work without logging the user out.)
     */
    fun onAppForegrounded() {
        val since = backgroundedAt
        backgroundedAt = 0L
        if (since != 0L && SystemClock.elapsedRealtime() - since > AUTO_LOCK_MS) logout()
    }

    /** Starts a session directly — used right after first-time setup. */
    fun startSession(role: Role) {
        _session.value = Session(loggedIn = role, viewing = role)
    }

    /** Owner/Manager switch to a lower role's screens without a PIN. */
    fun viewAs(role: Role) {
        val current = _session.value ?: return
        if (role in current.loggedIn.canView) _session.value = current.copy(viewing = role)
    }

    fun logout() {
        _session.value = null
    }

    /** True if [pin] already unlocks a different role (PINs must be unique per role). */
    fun isPinUsedByOtherRole(role: Role, pin: String): Boolean =
        Role.entries.any { it != role && verify(it, pin) }

    private fun verify(role: Role, pin: String): Boolean {
        val stored = prefs.getString(key(role), null) ?: return false
        val parts = stored.split(":")
        if (parts.size != 2) return false
        val salt = decode(parts[0])
        // Constant-time comparison.
        return MessageDigest.isEqual(decode(parts[1]), hash(salt, pin))
    }

    private fun hash(salt: ByteArray, pin: String): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update(salt)
            digest(pin.toByteArray(Charsets.UTF_8))
        }

    private fun key(role: Role) = "pin_${role.name}"
    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    companion object {
        const val PIN_LENGTH = 4
        private const val MAX_ATTEMPTS = 5
        private const val BASE_LOCKOUT_MS = 30_000L
        private const val MAX_LOCKOUT_MS = 15 * 60_000L
        /** Lock the session after the app has been in the background this long. */
        const val AUTO_LOCK_MS = 2 * 60_000L

        private const val KEY_FAILED = "failed_attempts"
        private const val KEY_LOCKOUTS = "lockout_count"
        private const val KEY_LOCKED_UNTIL = "locked_until"
    }
}
