# LocalSpend

**Track less. Understand more. Keep your financial data under your control.**

A privacy-focused Android app that tracks spending automatically from bank SMS notifications and
keeps everything in a database on the phone. Built for COMP9003 Object-Oriented Programming
(Team 22), following the *Final Project Progress Report*.

Pipeline: **Capture → Extract → Categorise → Store → Analyse**

## What it does

Every feature in the progress report is implemented, including all **Could Have** items.

| Report requirement | Priority | Where it lives |
|---|---|---|
| Transaction storage, local database | Must | SQLite via `DatabaseHelper`, `TransactionRepository`, `CategoryRepository` |
| Manual entry (amount, date, merchant, category) | Must | `AddTransactionActivity` (also edit and delete) |
| Expense categorisation (9 categories) | Must | `logic/CategorisationEngine` |
| Current-month dashboard | Must | `MainActivity` |
| Historical spending | Must | `HistoryActivity` (month navigator) |
| Automatic SMS extraction (amount, merchant, date, type) | Should | `SmsImporter` (inbox scan), `SmsReceiver` (live), `sms/` parsers, `SmsDateExtractor` |
| Automatic categorisation | Should | Runs on every SMS and suggests while typing a manual entry |
| Monthly comparison | Should | Month vs month on both screens, per-category comparison |
| Spending visualisations | Should | bar chart over 3 months, 6 months, 12 months or the tax year, category share bars, budget progress bars |
| **Budget limits** | Could | Overall monthly budget (Settings) and per-category budgets (Categories); `BudgetTracker` |
| **Notifications** | Could | New SMS transaction captured; budget warning (at 80% by default) and over-budget alerts; `notify/` |
| **Category corrections** | Could | Change any transaction's category; choose to apply it to just this one, future ones, or all from that merchant |
| **Additional spending insights** | Could | `InsightGenerator`: pace and month-end projection, budget overruns, biggest category rise/fall, biggest expense, top merchant, savings rate, weekend share, uncategorised count |

### Customisable categories

Categories are stored in the database, not hard-coded, so the user can:

- **rename** any category and **change its colour** (renaming never breaks old transactions, because
  each category has a fixed internal key);
- **add their own** categories (from Categories and budgets, or straight from the category list
  when adding a transaction);
- **delete** categories (their transactions move to Other; built-in ones can be restored);
- **add keywords** to a category ("petbarn", "vet" → Pets). User keywords beat the built-in lists;
- see and **forget remembered merchants** (corrections they made earlier);
- **switch off the built-in keyword lists** in Settings;
- **re-categorise** past transactions after changing the rules. Categories the user picked by hand
  are never overwritten.

Rule order: remembered merchants → user keywords → built-in keywords → Other.

### Only real bank messages are added

Before a message becomes a transaction it passes three checks (`SenderVerifier`,
`SenderDirectory`, `AbstractSmsParser.isPromotion`):

1. **Is it an advert?** Marketing phrases ("chat with us", "we'll refund you", "50% off",
   "apply now") are rejected outright. Words that can also be shop names ("sale", "free") only
   count together with a web link.
2. **Who sent it?** Recognised bank sender IDs (CommBank, NAB, Westpac, ANZ, ING, HDFCBK,
   SBIINB, ...) are trusted and added automatically.
3. **Anyone else** (a phone number, an unknown company) goes to **Review messages** instead of
   the dashboard. The user can *Add* it (once, or "always trust this sender") or *Ignore* it (once,
   or "block this sender"). A dashboard banner and a notification say when something is waiting.

Trusted and blocked senders can be reviewed in Settings. When upgrading, existing SMS
transactions are re-checked once: adverts are removed (they stay under *Review → Show ignored*),
messages from unrecognised senders move to Review, and the rest are re-read with the improved
parser (for example "To: S KARTHIKEYAN (+61...)" now gives the payee "S Karthikeyan").

### Dollars and rupees

Every transaction keeps its own currency, read from the message ("INR 1,999.00", "Rs.500", "₹250"
→ rupees; "$25.50", "AUD 89.99" → dollars) or picked when adding one by hand. Amounts are always
shown with their own symbol, and rupees use Indian grouping (₹1,00,000.00).

Totals are never mixed. The dashboard and history add up one currency at a time: by default the
currency of your most recent transaction, so the app switches between dollars and rupees by
itself. A currency button on both screens (shown once you have more than one currency) and a
setting let you pin one. Budgets are set in a chosen currency and only measured against spending
in that currency.

### Categories when there is no merchant

Messages that do not say who was paid ("Rs.500 debited from your A/c XX1234") go to the built-in
**Bank Transactions** category instead of Other. Indian UPI references are read as merchants
("for UPI-312824365410-HENNES" → "Hennes" → Shopping; "Info: UPI/P2M/…/AMAZON PAY"; "to
zomato@hdfcbank"), and the keyword lists include common Indian merchants (Swiggy, Blinkit, Zepto,
Myntra, IRCTC, Airtel, Apollo, PVR, H&M/Hennes and many more).

### Calendar, recurring payments and the home-screen widget
- **Calendar** (dashboard button or menu): a month grid showing, for each day, what came in (blue), what
  was spent (red) and the difference when both happened, with the month's totals above. Tap a day to see
  and edit its transactions; the **+** button adds one on that day. It follows the dollar/rupee setting.
- **Repeat / Instalment** (on the Add screen): make a payment repeat daily, weekly, fortnightly,
  monthly or yearly, optionally stopping after N payments, or set up an instalment plan with a fixed
  number of payments. The payment you save is the first one; the app adds the rest on their due dates
  (checked when the app opens, once a day in the background and after a restart). Payments missed while
  the phone was off are caught up, and a plan on the 31st falls on the last day of shorter months.
  **Recurring payments** (dashboard button or menu) lists the plans so you can pause, resume or delete them.
- **Home-screen widget** ("LocalSpend" under Widgets): shows today's spending and an **Add expense**
  button that opens a small pop-up (amount, optional note, category or Automatic) without opening the app.

### Categorising from the whole message
The merchant name is tried first. If no rule recognises it (or the message names no merchant), the
**whole SMS** is searched: boilerplate (dispute and balance sentences, bank names, amounts, dates,
reference numbers, fees) is stripped, then your own keywords win, otherwise the most specific built-in
keyword found anywhere in the text decides. Nothing found: "Bank Transactions" (no merchant) or "Other".
Needs the original SMS to be stored (Settings); existing messages are re-checked once after updating.

### Bank senders

`SenderDirectory` lists the SMS sender IDs of Indian public sector, private, small finance,
payments, foreign and major co-operative banks (HDFCBK, ICICIT, SBIINB, AXISBK, KOTAKB, ...), and
Australian banks, mutual banks and credit unions (CommBank, NAB, Westpac, ANZ, ING, Macquarie,
Bendigo, HumeBank, ...). Banks not on the list are still recognised by their shape: an Indian DLT
header ending in BNK/BANK/BK/CRD/SFB, or any sender name containing "BANK". Anything else goes to
Review, where it can be trusted with one tap.

### Original SMS stored

Every transaction captured from an SMS keeps the **complete original message**: sender, full text
and arrival time. It is shown on the transaction's edit screen (with Copy) and in
**menu → SMS messages**, a searchable list of every stored message next to what was extracted from
it. Transactions imported by older versions get their text filled in the next time
**Import from SMS** runs. Storing can be switched off, and stored text deleted, in Settings.

The transaction date is now read from the message itself when it contains one ("on 28/08/2026",
"28-Aug-26", "2026-08-28", "28 Aug"), falling back to the arrival time.

## Build and run

1. Install **Android Studio** (Koala 2024.1 or newer) with JDK 17.
2. **File → Open** and choose this `LocalSpend` folder. Let Gradle sync (it downloads Gradle 8.7
   from `gradle-wrapper.properties`; there is no `gradlew` script in the zip, Android Studio does
   not need one).
3. Run on a device or emulator (Android 7.0 / API 24 or newer).
4. To try SMS capture on an emulator: open **Extended controls → Phone** and send an SMS such as
   `Your card ending 1234 was used for $25.50 at WOOLWORTHS SYDNEY on 28/08/2026. Avail bal $1,200.00`.
   On a real phone, tap **Enable SMS tracking** and grant access.

On Android 13+ a sideloaded app may need **Settings → Apps → LocalSpend → ⋮ → Allow restricted
settings** before the SMS permission can be granted.

## Tests

`app/src/test` has 100 JUnit 4 tests covering SMS parsing and date extraction, categorisation
(including custom categories, user keywords and deleted categories), sender checks and advert
filtering, budgets, insights, month
analysis and money/date helpers. Run them with `./gradlew test` or right-click `test` in Android Studio.
These classes have no Android dependencies, so they run on a normal JVM.

## SMS feasibility (Challenge 1 in the report)

- **Android:** possible with `READ_SMS` and `RECEIVE_SMS`, which is what this app uses.
- **iOS:** not possible. Apps cannot read the SMS inbox.
- **Google Play:** restricts SMS permissions to default SMS/dialer apps and a few exceptions, so
  a public Play release would likely be rejected. Distributing the APK directly (or as a course
  prototype) is fine.
- Manual entry always works without any SMS permission.

## Supported SMS formats (Challenge 2)

The parsers look for patterns rather than one bank's layout:

- **Amounts:** `$25.50`, `A$25.50`, `AUD 25.50`, `25.50 AUD`, `Rs 500`, `Rs.500`, `INR 500`, `₹250`
- **Money out:** words such as *spent, debited, purchase, paid, charged, withdrawn, payment successful*
- **Money in:** *credited, received, deposited, salary, refund*
- **Merchant:** after `at`, `@`, `to` (for debits/payments), `from` (for income) or `Info:`
- **Ignored:** OTPs, bill reminders (*due*), declined or failed transactions, promotions
- **Never the amount:** balance and limit figures (`Avl Bal $1,200.00`)

Messages that do not match are skipped, not guessed at. Adding a bank-specific format means
writing one new `SmsParser` and adding it to `SmsParserChain.defaultChain()`.

## OOP design

| Concept | Example |
|---|---|
| Encapsulation | `Transaction` has private fields and validating setters; money is stored as `long` cents; `Category` is immutable with `with...` copy methods |
| Abstraction | Separate classes for parsing, categorising, storage and analysis; the UI never touches SQL |
| Inheritance | `PurchaseSmsParser` and `IncomeSmsParser` extend `AbstractSmsParser` (template method) |
| Polymorphism | `SmsParser` (parsers and `SmsParserChain`) and `CategorisationRule` (`KeywordRule`, `UserKeywordRule`, `LearnedMerchantRule`) are used through their interfaces |
| Composition | `CategorisationEngine` is a list of rules plus a `CategoryCatalog`; `UserKeywordRule` is made of `KeywordRule`s; `SmsImporter` combines a parser, an engine and a repository; `SmsParserChain` combines parsers |

## Project layout

```
app/src/main/java/com/localspend/
  model/   Transaction, Category, CategoryCatalog, TransactionType, Source
  data/    DatabaseHelper, TransactionRepository, CategoryRepository, AppSettings
  logic/   CategorisationEngine, KeywordRule, UserKeywordRule, LearnedMerchantRule,
           SpendingAnalyzer, MonthSummary, BudgetTracker, BudgetStatus, InsightGenerator ...
  sms/     SmsParser, AbstractSmsParser, PurchaseSmsParser, IncomeSmsParser, SmsParserChain,
           SmsDateExtractor, SmsImporter, SmsReceiver, SmsKeys
  notify/  Notifier, BudgetAlerts
  ui/      MainActivity, AddTransactionActivity, HistoryActivity, CategoriesActivity,
           CategoryEditActivity, MessagesActivity, SettingsActivity, adapters, MonthlyBarChartView
  util/    MoneyFormatter, Dates, Background
```

## Known limitations

- Built-in keywords are tuned for Australian and Indian merchants. Unknown merchants land in
  **Other**; add a keyword or correct one transaction to teach the app.
- Income is tracked but always filed under Other.
- Budgets are monthly. The history screen measures past months against today's budgets.
- The history list is not paginated; a month with thousands of transactions would be slow.
- Payments in other currencies are converted into the main currency with the rate of their day
  (see "Mixed currencies"); a payment is left out of totals until its rate is known.
- Database upgrade: version 1 databases are migrated in place (new columns and tables only), so
  existing transactions and corrections are kept.

## Mixed currencies

The app picks a **main currency**: the one most of your transactions use (or the one you pin with the currency button). Payments in any other currency are converted into it using the rate for the day of the payment, and every total, chart and calendar day is added up in the main currency.

- The original amount and currency are always kept, and each converted payment stores its calculation (for example `$56.28 x 59.418 = ₹3,344.05`, the rate and the day it applies to). It is shown on the Edit screen and in the list.
- Rates are looked up for the day of each payment (European Central Bank reference rates via the free Frankfurter service) and saved on the device, so each day is fetched once. Only the two currency codes and a date are sent, never amounts, merchants or messages. This is the only thing the app uses the internet for, and Settings → Exchange rates turns it off.
- No rate is built in. If a rate can't be fetched (offline), the payment is not counted in the totals until it is, or until you type the rate on the Edit screen (which is remembered for that day).

## Look and feel

Soft off-white page with flat outlined cards and rounded corners, a tinted summary card with a large total, category badges, transaction lists grouped by day (Today, Yesterday, dates), a larger amount field when adding, and a matching dark theme (light status bar in the light theme, brighter greens and reds in the dark one).

## Categories: what "Other" means

"Other" is only for a payment whose shop or message matched no keyword. Money coming in from a bank,
and payments to a person by UPI/IMPS/NEFT/PayID, are filed under "Bank Transactions". Existing
transactions are re-checked once after an update (categories you chose yourself are never changed).

## Live inbox check

While LocalSpend is open it reads the SMS inbox every 30 seconds, so a new bank message appears
without pressing Import. When the app is closed, the SMS receiver still captures messages as they arrive.

## Search, CSV export and PIN lock

- **Search** (menu → Search): finds transactions by words in the merchant, category or original
  message, and can narrow by category, income/expense, a date range and an amount range. Results
  update as you type; tap one to edit it. The amount range is in your dashboard currency: a US$5 or A$5 purchase is converted with its stored rate first (so it is not under a ₹100 maximum), and one with no stored rate is left out when a minimum or maximum is set.
- **Export to CSV** (Settings → Privacy and backup): saves every transaction (date, type, merchant,
  category, amount, currency, stored conversion, source, and the original SMS) to a file you pick
  with the system file picker, so the app needs no storage permission. Text that a spreadsheet could
  run as a formula is neutralised.
- **PIN lock** (Settings → Privacy and backup): a 4 to 8 digit PIN, kept only as a salted PBKDF2 hash.
  The app asks for it when opened and after being out of sight for 15 seconds. Optional fingerprint
  unlock uses AndroidX Biometric. After five wrong PINs, guesses are delayed (30 s, doubling to 5 min).
  A forgotten PIN cannot be recovered; clearing the app's data removes the lock and the transactions.

## Everything follows the dashboard currency

The currency shown on the dashboard decides the rest:
- **Budgets** (overall and per category) are in that currency, so there is no currency to pick: 5,000 is ₹5,000
  on a rupee dashboard and A$5,000 on a dollar one. Budget alerts check spending in that currency.
- **Tax year** (chart option) starts in April for ₹ (shown as "FY 2026–27"), July for A$ and January for US$.

## Budgets that change over time

A budget is a history of "from this month, the budget is X" entries, so earlier months keep the budget they had.
- Setting a budget (Settings, a category, or the dashboard's Manage button) asks: **from this month onwards**
  (earlier months keep theirs) or **this month and all earlier months** (replaces what was set for them).
- **Removing** a budget in the current month only removes it from this month on; earlier months keep theirs.
- To change or remove an earlier month's budget, open **Manage** while viewing that month. A past month changes only itself.
- The dashboard and History show the budgets that applied in the month on screen. Budget alerts use the current month's.

- **Budgets and currency:** a budget keeps the currency it was set in. If the dashboard is in another currency, the budget is converted at the current rate (US$500 becomes its rupee worth, not ₹500). With no rate yet, it is fetched in the background (if online rates are on) and the screen refreshes.
