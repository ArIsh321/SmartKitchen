# SmartKitchen AI — Requirements & Project Status

AI-powered inventory and food-waste app for restaurant owners (Android, Kotlin, Jetpack Compose).

_Last updated: 20 Nov 2026_

---

## 1. Product goal (Phase 1)

One application where restaurant owners can:

| # | Goal | Status |
|---|------|--------|
| 1 | Monitor ingredients and stock | ✅ Done |
| 2 | Record wasted food | ✅ Done |
| 3 | Track expiry dates | ✅ Done |
| 4 | Predict ingredient requirements | ✅ Done |
| 5 | Understand where money is being lost | ✅ Done |
| 6 | Receive AI-generated recommendations | ✅ Built — needs App Check debug token registered in Firebase to work |

---

## 2. Roles

Three roles, each with its own 4-digit PIN. Hierarchy: a higher role can open lower roles' screens ("View as") without a PIN.

| Role | Purpose | Tabs | Sees prices |
|------|---------|------|-------------|
| **Owner** | Financial reports, analytics, AI recommendations | Overview · Reports · Insights · Profile (+ View as Manager / Staff) | Yes |
| **Manager** | Inventory, purchasing, waste monitoring | Inventory · Purchasing · Sales · Waste · Expiry (+ View as Staff) | Yes |
| **Kitchen staff** | Quick ingredient usage and waste entry | Quick Entry (single screen) | **No — never** |

### Login & security
- First launch: Owner creates three PINs (Owner, Manager, Staff), each entered twice; all must be different.
- Every start: "Who are you?" → choose role → PIN pad.
- PINs stored as salted SHA-256 hashes; constant-time check; PINs never kept in saved UI state.
- Wrong PIN: after 5 tries the pad locks 30 s → 1 min → 2 min … up to 15 min. Attempts/lock survive app restarts. Correct PIN resets it.
- **Auto-lock:** if the app is in the background for more than **2 minutes**, it returns to the PIN screen.
- Role bar on every screen: who is signed in, **View as**, and **Lock / switch user**.
- Owner can change any PIN in Profile.

---

## 3. Features by role

### 3.1 Owner
**Overview (dashboard — per supplied design)**
- SmartKitchen logo + tagline, owner initials avatar (opens Profile).
- Branch picker (one branch or **All branches**) and period picker (This week / This month / Last month / Last 30 days).
- Today's sales card with Enter/Edit sales (pre-fills only the selected branch's amount, never the all-branches total).
- KPI cards with change vs previous period: **Revenue**, **Food Cost %** ((food used + wasted) ÷ revenue), **Waste Cost**.
- Revenue bar chart (daily for a week, weekly otherwise; tap a bar for its value).
- AI Recommendations: two cards (title, text, type icon) + View all / View details → Insights.

**Reports**
- Summary: revenue, food cost, food cost %, purchases, waste cost, waste % of purchases, stock value now, expired stock not written off (each with change vs previous period).
- Two separate charts (last 8 weeks): weekly revenue, weekly waste cost (separate because the scales differ greatly).
- Where money is lost: top 5 ingredients by waste cost, waste cost by reason.
- Daily sales list: add, edit, move, delete.

**Insights**
- AI recommendations (full list, refresh).
- Where money is lost (last 30 days).
- Reorder forecast per ingredient.

**Profile**
- Restaurant name, owner name.
- Branches: add, rename, choose **this device's branch**.
- Change Owner / Manager / Staff PINs.
- Lock / switch user.

### 3.2 Manager
**Inventory**
- Add / edit / delete ingredients: name, category, unit, quantity, cost per unit, minimum stock, expiry date, barcode.
- Search; low-stock items flagged ⚠ and listed first; summary of items, stock value, low-stock count.
- **Scan barcode:** checks this branch's inventory → saved barcodes → Open Food Facts → otherwise remembers what you type.
- **Record usage** (amount + date) — subtracts from stock.

**Purchasing (per supplied design)**
- Header: SmartKitchen / Restaurant Operations, this device's branch (label), status filter.
- Reorder banner: "N items need reorder" → opens a new order pre-filled from the forecast.
- **Create purchase order:** supplier name + note, items with quantity & price (supplier pre-filled if all items were last bought from the same one).
- Purchase order cards grouped by supplier: status chip, items with emoji tiles, line totals, item count, total.
- Order flow: **Draft → Sent to supplier → Received**. Actions: Edit (draft), Mark as sent & share (WhatsApp/email), Share, Receive delivery, Delete (not received).
- Receive: confirm received quantity (0 = missing), price, expiry → adds stock, updates cost, records purchase.
- Sort: Most recent / Oldest / Highest total / Status.
- "Record a delivery without an order".

**Sales** — enter each day's total sales (from till/POS); 7-day and 30-day totals.

**Waste** — log waste (ingredient, quantity, reason, date, note); cost calculated; stock deducted; 7/30-day loss and top reason.

**Expiry** — groups: Expired / ≤3 days / ≤7 days / Later; value at risk; one-tap **Write off** expired stock as waste.

### 3.3 Kitchen staff — Quick Entry (per supplied design)
- Header: SmartKitchen, current shift (Breakfast < 11:00, Lunch < 16:00, Dinner after), "Kitchen Staff", chef avatar.
- Mode cards: **Log Ingredient Usage** / **Report Waste**.
- Quick Select: this branch's 4 most-used ingredients (last 30 days); See All → searchable list + barcode Scan.
- Quantity display + unit dropdown (kg ↔ g, L ↔ mL — converted before saving), 4-column keypad, Save Entry / Save Waste.
- Waste reasons as chips; warning when entering more than in stock.
- Recent Activity (last 3, See All → up to 30). Shows date (Today/Yesterday), not time.
- **No prices anywhere.**

---

## 4. Demand prediction (rules)
- Average daily use over the last **14 days**; needs at least **3 days** of usage history.
- Days of stock left = quantity ÷ average daily use.
- Suggested order = 7 days of use + safety buffer (max of minimum stock or 2 days of use) − current stock.
- Urgency: Order now (≤ 2 days), Order soon (≤ 7 days), OK, No recent use, Not enough data.

## 5. AI recommendations
- Firebase AI Logic → Gemini (`gemini-3.5-flash`), JSON output: title, detail, type (ordering / waste / expiry / prep / sales / other).
- Sends **numbers only**: stock, usage, waste, expiry, sales totals (no personal data).
- Latest result cached per branch selection on the device.
- Requires: `google-services.json` (done), AI Logic → Gemini Developer API enabled, **App Check debug token registered** (debug builds) / Play Integrity (release builds).

## 6. Barcode scanning
- Google Code Scanner (no camera permission needed).
- Lookup order: inventory (this branch) → saved products → Open Food Facts (barcode only is sent) → manual entry remembered.
- Barcodes don't contain expiry dates; fresh produce usually has no barcode.

## 7. Multi-branch
- Branches table; existing data moved into "Main Kitchen".
- Each device has one **device branch** (set by Owner in Profile); Manager & Staff work only in it.
- Owner sees one branch or All branches.
- ⚠ Until cloud sync is finished, data exists only on the device where it was entered.

## 8. Design
- Brand: herb green + terracotta on warm off-white; chef-hat logo; rounded cards. Applies to the whole app (no wallpaper colours).
- Chart colours validated for contrast/readability in light and dark mode.
- Emoji tiles instead of product photos.

---

## 9. Technical summary
- Kotlin, Jetpack Compose, Material 3, MVVM, Navigation Compose, manual DI (`KitchenApp`).
- Local database: Room **v4** (migrations 1→2→3→4 keep all data).
- minSdk 24, targetSdk/compileSdk 37, AGP 9.5.
- Libraries: Room, Navigation Compose, Lifecycle, Google Code Scanner, Firebase AI, App Check (debug provider in debug builds, Play Integrity in release).
- Firebase project: **wasteless-8714b**.

---

## 10. Cloud sync (in progress)

### Decisions
| Topic | Decision |
|-------|----------|
| Sign-in | **Owner links devices** — Owner signs in with Google once on each device, then PIN roles work as now |
| Approach | **Firestore is the main database** with built-in offline cache (works without internet, syncs later) |
| Database location | **me-central2 (Dammam)**, Standard edition, `(default)` |
| Existing data | **Upload once** from this device on first sign-in |
| Roles | Enforced by the app (not by the server) |
| Stock updates | Atomic add/subtract (`FieldValue.increment`) so simultaneous entries from several devices are never lost; stock may briefly show below 0 → shown as 0 and flagged for recount |
| Reports | Load only the selected + comparison period to stay in the free tier |
| Kept local | Device branch, PIN lockout counters, AI tip cache, auto-lock |
| Synced | Restaurant & branches, PIN hashes (change once → all devices), ingredients, usage, waste, sales, purchases, purchase orders, saved barcodes |

### Cloud data model
```
restaurants/{ownerUid}                  name, ownerName, createdAt
  config/security                       pinOwner, pinManager, pinStaff (hashes)
  branches/{branchId}                   name, createdAt
  ingredients/{id}                      branchId, name, category, unit, quantity, unitCost, minStock,
                                        expiryEpochDay?, barcode?, createdAt, updatedAt
  usage/{id}                            branchId, ingredientId, quantity, unitCost, epochDay, createdAt
  waste/{id}                            branchId, ingredientId?, ingredientName, quantity, unit, reason,
                                        costLoss, epochDay, note, createdAt
  sales/{branchId_epochDay}             branchId, epochDay, amount, updatedAt
  purchases/{id}                        branchId, ingredientId?, ingredientName, quantity, unit, unitCost,
                                        totalCost, epochDay, supplier, createdAt
  purchaseOrders/{id}                   branchId, supplier, supplierNote, status, createdDay, sentDay?, receivedDay?
    items/{id}                          ingredientId, ingredientName, unit, quantity, unitCost
  knownProducts/{barcode}               name, category, unit, unitCost
```

### Security rules (`firestore.rules` — prototype, review before launch)
- Only the signed-in Owner account can read/write `restaurants/{its uid}`; everything else denied.
- Every create and update validates allowed fields, types, sizes and ranges.
- Timestamps must be server time; `createdAt`, branch of an ingredient, and sales branch/day are immutable.
- Usage and purchases are append-only; restaurants and branches can't be deleted.
- Purchase orders: created as Draft; only Draft → Sent → Received; items editable only while Draft; received orders can't be deleted.
- Syntax checked with the local emulator (no errors reported).

### Phases
| # | Phase | Status |
|---|-------|--------|
| 1 | Firebase setup: Google sign-in, Firestore database, security rules | 🟡 Database created, rules written — **rules not yet published** |
| 2 | Sign in with Google, create/join restaurant, pick device branch | ⏳ Next |
| 3 | Cloud repository replacing Room under the screens | ⏳ |
| 4 | One-time upload of this device's data | ⏳ |
| 5 | Test with two devices, offline → online, rules block other accounts | ⏳ |
| 6 | Remove old local database after confirmation | ⏳ |

---

## 11. Pending actions for the Owner / developer
- [ ] **Publish rules:** copy `firestore.rules` → Firebase console → Firestore Database → Rules → Publish.
- [ ] Confirm **Authentication → Google** sign-in is enabled.
- [ ] Confirm debug **SHA-1** added to the Firebase app: `85:E9:33:11:24:7B:D8:4A:DE:99:3F:08:FB:26:7F:FB:47:13:F3:3E` (SHA-256: `C1:E5:A7:44:FD:EE:2E:25:37:AF:BC:24:5B:E9:2B:0E:F9:58:93:B6:15:F3:2B:2D:02:92:67:9F:54:B8:9F:81`).
- [ ] Register the **App Check debug token** (Logcat → `DebugAppCheckProvider`) in Firebase → App Check → Manage debug tokens.
- [ ] Fix Firebase CLI login (`firebase logout` → `firebase login --no-localhost`) so rules can be deployed from the command line.
- [ ] Turn off **Gradle offline mode** and run a release build (release build not yet verified).
- [ ] Add release keystore SHA-256 to Firebase before publishing (Play Integrity).
- [ ] Delete generated `firebase-debug.log` / `firestore-debug.log` and keep them out of git.

---

## 12. Known limitations
- Data is device-local until cloud sync (Phases 2–6) is finished.
- Roles are enforced in the app, not on the server.
- Recent Activity shows date only (no time of day).
- Emoji tiles instead of product photos; supplier typed per order (no saved supplier list); no Owner approval step for purchase orders.
- AI tips can only use stock, waste and sales totals (no dish-level/menu sales).
- No "forgot PIN" recovery; clearing app storage erases local data.

## 13. Open items from code review (not yet fixed)
| # | Severity | Item |
|---|----------|------|
| 4 | Medium | Room migrations have no automated tests (`exportSchema = false`) |
| 5 | Low | Wasting more than in stock records cost for the full amount |
| 6 | Low | Moving a sales entry onto a day that already has sales overwrites it without warning |
| 7 | Low | Rotating the phone during first-time PIN setup restarts setup |
| 8 | Low | Editing a draft order drops lines whose ingredient was deleted, without notice |
| 9 | Low | "Mark as sent & share" marks Sent even if sharing is cancelled |
| 10 | Low | AI error message stays after switching branch |
| 11 | Low | Android cloud backup includes PIN hashes (`allowBackup=true`) |
| 12 | Info | Release build unverified (Gradle offline mode) |

Fixed from the review: Owner sales pre-fill using all-branches total (#1), no auto-lock (#2), PIN lockout reset on restart (#3).

## 14. Ideas for later
- Time of day on entries; Owner approval for purchase orders; saved supplier list with phone/WhatsApp.
- Separate accounts per person with server-enforced roles.
- Daily expiry notifications; real product photos; POS integration for automatic sales.
