# Breakdown popup and full screen

Includes STAL, multiple charges, the monthly date preview, LEA timeline and labelled milestones.

## STAL verification and display

The STAL selector already affected the balances. Its existing arithmetic was
verified against nine live results at https://layacalculator.org/calculator/
on October 6, 2026, and retained. The popup and full-screen breakdown now also
show a separate STAL label and day-value TextView below the custody table.
STAL belongs to the whole sentence, so it is not repeated in the LEA/Jail/Prison
rows or added to their Days/GCTA/TASTM totals.

[Article 98, as amended by RA 10592 section 4](https://lawphil.net/statutes/repacts/ra2013/ra_10592_2013.html)
specifies one-fifth for qualifying return within 48 hours after the proclamation
that the calamity has passed, or two-fifths for staying in confinement. The basis
is the sentence period, not the balance after other allowances. The statute does
not spell out this calculator's minimum/maximum handling or rounding convention.

LAYA uses `ceil(maximum sentence days * selected percentage / 100)`, then
subtracts that same credit from each balance along with custody, GCTA and TASTM.
The manual deductions and bail/ATI gap fields continue to add back days under
the existing website rules. These adjustments do not change the STAL basis.
For multiple charges, the app calculates STAL after applying the Article 70 cap
to the combined maximum; the website does not offer multiple-charge inputs.

Live reference inputs: arrest December 1, 2024; commitment January 1, 2025;
minimum 3 years; maximum 5 years (1,826 days). All switches are off unless CPI
is indicated. Remaining balances below include time allowances.

| Observation | STAL | Deductions / gap | CPI | STAL days | Min remaining | Max remaining |
| --- | ---: | --- | --- | ---: | ---: | ---: |
| 2026-01-01 | None | 0 / 0 | Off | 0 | 279 | 1,010 |
| 2026-01-01 | 20% | 0 / 0 | Off | 366 | -87 | 644 |
| 2026-01-01 | 40% | 0 / 0 | Off | 731 | -452 | 279 |
| 2026-01-01 | 40% | 50 / 17 | Off | 731 | -385 | 346 |
| 2026-01-01 | 20% | 50 / 17 | Off | 366 | -20 | 711 |
| 2026-02-01 | 20% | 50 / 17 | Off | 366 | -86 | 645 |
| 2026-02-01 | 20% | 50 / 17 | On | 366 | 369 | 1,100 |
| 2026-02-01 | 40% | 50 / 17 | On | 731 | 4 | 735 |
| 2026-02-01 | None | 50 / 17 | On | 0 | 735 | 1,466 |

The existing selection is the calculator input, not an automatic eligibility or
award determination. In particular, LAYA keeps selected STAL when CPI is ticked;
matching that behavior does not establish legal entitlement for a specific PDL.
The form has no award date, so the selected credit stays fixed throughout the
slider's historical/future previews; this is a scenario with that award applied.
Moving the slider does not earn STAL again or progressively shrink its basis.

Files changed:

- `SentenceCalculator.java`: names and documents the existing loyalty-credit
  calculation; adds the selected percentage to the immutable result.
- `BreakdownViewBinder.java`, `ProfileBreakdownDialogFragment.java`,
  `newEntryActivity.java`, and `BreakdownActivity.java`: pass and display the
  actual STAL result in the saved popup Bundle and each date preview.
- `view_sentence_breakdown.xml` and `profile_breakdown_strings.xml`: add the
  two editable TextViews and their short labels, shared by both breakdown views.
- `StalCalculationTest.java`: live website fixtures (including release dates),
  all trigger combinations, Article 70 caps, fixed preview credit, both completion
  dates and invalid percentages.
- `StalFlowTest.java` and `ProfileBreakdownDialogTest.java`: save/reopen/edit,
  40% to 20% to None, dashboard balance, slider, serialization and rotation checks.

No database/schema changes. The saved `stalPercent` is reused; derived STAL days
are not stored. This adds two TextViews and two small Bundle values, with no
additional database work or per-month loop. No noticeable performance impact
is expected; no device timing benchmark is claimed.

Verification: all 68 unit tests and 18 relevant emulator tests pass, including
the nine newly captured website examples. Both STAL breakdown views were
visually checked. Emulator checks use the isolated `.stalcheck` app ID.

## Multiple-charge storage and calculation

Add Entry and Edit Entry use the same charge-row controller. Charge 1 stays in
the form; subsequent charges can be added or removed. Every charge retains its
own minimum and maximum years, months and days, including after reopening or
rotating Edit Entry. Each charge must have a positive minimum and maximum, with
minimum no greater than maximum.

For two or more successive numeric imprisonment terms, each displayed endpoint
uses the smallest of the sum of those endpoints, three times the longest
endpoint, and 40 years. Minimum and maximum estimates are calculated separately.
The existing single-charge calculation is unchanged. Each term first uses the
existing commitment-anchored calendar conversion to days; sums and caps then
operate on those day counts. Forty years uses the same anchor. For example, four
one-year terms anchored on January 1, 2020 are capped at 3 x 366 = 1,098 days.
This retains the prototype's calendar convention rather than introducing another
time basis or changing the single-charge website fixtures.

[Article 70 as discussed by the Supreme Court](https://lawphil.net/judjuris/juri2022/aug2022/gr_241986_2022.html)
limits service while leaving the individual penalties in place. This app's
numeric minimum/maximum estimates do not determine parole eligibility or model
concurrent service, life imprisonment or reclusion perpetua. Custody days, GCTA,
TASTM and adjustments are applied once to the combined term. STAL keeps its
existing percentage formula, now based on the combined maximum.

- `db/ProfileCharge.java`: an ordered child table with the six entered numbers,
  profile ID and position. Deleting a profile also deletes its charges.
- `db/Profile.java`: holds profile metadata and an in-memory charge list; the
  six first-row fields are temporary form values, not duplicate database columns.
- `db/ProfileWithCharges.java` and `db/ProfileDao.java`: load ordered charges
  with each profile. Parent and child saves are transactional, so a failed child
  write rolls back the whole save. Dashboard loading batches the related rows.
- `db/AppDatabase.java`: version 6, deliberately no migration or automatic
  destructive fallback. Clear app storage before testing this development schema.
- `AdditionalChargesController.java`, `newEntryActivity.java`,
  `editProfileActivity.java` and `activity_edit_profile.xml`: share charge entry,
  restore drafts on rotation, validate all rows, and save them together.
- `SentenceCalculator.java` and `ProfileCalculationMapper.java`: aggregate all
  saved charges before the existing credit calculation. Dashboard, popup and
  timeline therefore use the same result; derived totals are not stored.

Storage and aggregation grow linearly with the number of charges. There is no
separate allowance calculation per charge, and slider movement still performs
no database reads or writes. Ordinary charge counts should have negligible
impact; no device timing benchmark is claimed.

`MultipleChargesCalculationTest` covers the sum, threefold and 40-year caps,
charge ordering, leap-year conversion, single-charge compatibility, invalid
charges, all trigger combinations and both timeline endpoints.
`ChargeStorageTest` checks ordered round trips, updates, cascading deletes and
transaction rollback. `MultipleChargesTest` checks Add/save/popup/dashboard/
breakdown consistency and Edit/remove/rotation/reopen behavior. The earlier
feature descriptions and verification below refer to those earlier updates.

After a new profile is saved, Home displays a dialog containing Days, GCTA and
TASTM columns for LEA, Jail, Prison and Total. The existing minimum and maximum
remaining balances and close button remain below/above the table respectively.
Every numeric cell, row label and column heading is a separate XML TextView.

The dashboard's green View Breakdown button now opens `BreakdownActivity` for
that row's saved profile. The screen adds the profile name and observation date,
uses the same table/balances in a larger card, and has a Back button. A monthly
slider now changes the observation date for this screen only. The existing
calculation rules and single commitment date remain in use.

## LEA timeline and labelled milestones

The slider now starts at LEA arrest when supplied; otherwise it starts at the
recorded commitment date. LEA was already included in later balances. The new
behavior lets the user preview that earlier interval: LEA days accumulate only
as far as the selected date, and jail/prison days plus GCTA/TASTM remain zero
until commitment. Penalty lengths retain the existing commitment-date anchor.
Normal commitment-day and later calculations remain identical to the website
fixtures. Manual STAL, deductions and gaps keep their saved values in all previews.

The track has labelled, tappable stops for LEA arrest, the recorded jail or prison
entry, minimum zero and maximum zero. Zero means the first non-positive balance;
monthly credit jumps can skip exact zero. Each label includes its date. Nearby
labels stack to stay readable, and milestones sharing a date share one stop and
a combined label. Exact milestone dates supplement the monthly steps and Today.

Only the existing inputs are used. The single commitment field supplies either
Jail entry or Prison entry according to the triggers. Legacy jail/prison columns
do not create a second transfer, and no new form fields or database columns were
added. A missing arrest date does not produce an invented LEA arrest marker.

Changes in this update:

- `SentenceCalculator.java`: adds `calculatePreview`, sharing the normal
  calculation implementation while allowing arrest-to-commitment previews.
  Standard input validation still rejects observation dates before commitment.
- `SentenceProjection.java`: adds both completion searches, LEA monthly stops,
  the recorded transfer stop and typed milestones. LEA is checked separately
  from post-commitment because the existing TA future-conviction behavior can
  cause a balance discontinuity at commitment. Each completion is the first
  crossing anywhere in the preview, including LEA when applicable.
- `BreakdownActivity.java`: binds the milestones and selects their dates on tap;
  renders previews through the shared preview calculation.
- `TimelineMilestonesLayout.java` (new): aligns dots with the actual slider
  stop positions, draws connectors and places real clickable TextViews in rows
  without label overlap. Supports coincident dates, narrow widths and RTL.
- `activity_breakdown.xml`: wraps the existing slider in the milestone layout;
  removes the former maximum-only completion caption.
- `view_timeline_milestone.xml` (new): editable label appearance, text size,
  padding, color and minimum touch height.
- `profile_breakdown_strings.xml`: milestone titles and date formatting.
- `SentenceProjectionTest.java`: extends reference-search checks to both
  balances; adds LEA, missing-arrest, transfer, coincident-date and discontinuity cases.
- `CustodyPreviewTest.java` (new): checks partial LEA days, zero future credits,
  unchanged normal validation and identical post-commitment results in all modes.
- `BreakdownActivityTest.java`: tests tapping each milestone, partial LEA
  previews, jail/prison labels, coincident labels, larger text, narrow widths and RTL.
- This document. Source/resource/test directories are listed below.

## Today and the monthly preview

- Dashboard calculations and the new-profile popup use the phone's current local
  date. New/Edit no longer ask for an observation date.
- Each fresh visit to Breakdown opens at Today. Moving the slider recalculates
  days, GCTA, TASTM and the minimum/maximum balances at the selected date. The
  Today button resets the preview. Rotation/recreation retains a manual preview;
  leaving and opening the screen again returns to Today.
- The range starts at arrest, or commitment if no arrest date was supplied.
  It ends at the later of Today or the first day
  the maximum remaining balance is zero or negative. A monthly credit can jump
  past zero, so the endpoint need not have a balance of exactly zero. The first
  non-positive minimum balance is a selectable stop too. The recorded commitment
  stays in range even when an earlier LEA preview already reaches zero.
- LEA monthly stops follow the arrest date. Later monthly stops follow the
  calculator's allowance anniversaries: commitment in
  ordinary mode, or conviction in TA-only mode when supplied. End-of-month dates
  stay anchored, so January 31 can step to February 29 and then March 31. Exact
  Today, commitment and both completion dates are also stops, even between anniversaries. The
  slider spaces these selectable stops equally; it is not a daily time scale.
- STAL, manual deductions and bail/ATI gaps keep their saved values throughout
  the preview. There are no dated adjustment records from which to reconstruct
  a different value at each historical/future date.
- A visible dashboard follows local date changes. Breakdown refreshes its range
  when the local date changes; an active manual preview stays selected when it
  remains in range. Returning to either screen also checks the current date.
- The old `Profile.observationDate` column remains for database compatibility.
  Calculations ignore it, editing preserves an old value, and newly saved
  profiles leave it null. No database migration or date-sync writes are needed.
- Invalid inputs show an error. If Today can be calculated but completion lies
  outside the calculator's supported range, Today remains visible and the
  unavailable projection is explained instead of inventing an endpoint.

## Files changed for the monthly preview

- `BreakdownActivity.java`: loads the profile once, sets up the date controls,
  recalculates previews, restores the selected date and handles Today/reset.
- `SentenceProjection.java` (new): finds the first non-positive maximum balance
  with a bounded binary search using the existing calculator, then creates the
  monthly stops plus exact Today/completion dates.
- `TodayDateObserver.java` (new): watches local date/time-zone changes and
  schedules one midnight callback while a screen is resumed.
- `ProfileCalculationMapper.java`: uses Today by default and provides an explicit
  date overload for previews and reproducible tests; never mutates the profile.
- `DashboardActivity.java`: calculates all cards against one captured current
  date and refreshes on date changes.
- `newEntryActivity.java` and `editProfileActivity.java`: remove observation-date
  input handling and use current-date calculations. The save popup uses the same
  captured date as the save calculation.
- `db/Profile.java`: documents the legacy date column; no schema change.
- `activity_breakdown.xml`: adds the slider, Today button and range/completion
  labels below the observation date. This is the XML to edit for slider styling.
- `activity_new_entry.xml` and `activity_edit_profile.xml`: remove the old
  observation-date row and its extra divider.
- `profile_breakdown_strings.xml`: adds the date-preview labels and messages.
- `SentenceProjectionTest.java` (new): checks endpoint search, all trigger
  combinations, date stops, month ends, restoration, mapping and search bounds.
- `SentenceCalculatorTest.java`: gives the historical mapper regression case an
  explicit observation date so its expected values remain reproducible.
- `BreakdownActivityTest.java`: checks slider values, Today/reset, recreation,
  fresh reopening, unchanged stored dates, new/edit saves and existing navigation.
- This document records the changes. Java files are under
  `app/src/main/java/com/example/myapplication`, layouts under
  `app/src/main/res/layout`, strings under `app/src/main/res/values`, and tests
  under their corresponding `app/src/test` or `app/src/androidTest` package.

The initial monthly-preview update did not change the calculator. The LEA update
adds the preview extension described above; normal calculation formulas, the
shared table XML, trigger rules and database schema are unchanged.

## How the rows are assigned

The current form has only one commitment date. It cannot establish a separate
jail-to-prison transfer, and retained legacy dates must not silently supply one.

- LEA: the existing arrest-to-commitment day count; GCTA and TASTM are zero,
  matching the current calculator's treatment of this interval.
- Jail: commitment-to-observation days and the existing GCTA/TASTM totals when
  the form says Jail Commitment Date.
- Prison: the same post-commitment values when Not Committed to Jail or CPI
  disqualification makes the form say Prison Commitment Date.
- The other commitment row displays an em dash: no separate stage was recorded.
  This does not assert that the stage is legally inapplicable or never occurred.
- Total: column sums. A blank arrest date retains the existing zero-day LEA
  fallback. CPI retains zero GCTA/TASTM; TA-only retains the existing conviction
  month calculation. Conviction is not treated as a physical transfer date.

These are gross calendar days and allowance credits, before STAL, deductions
and bail/ATI gaps. Those adjustments continue to affect the remaining balances
exactly once. They cannot be assigned to individual stages without dated records.
The short caption explains this distinction; no new legal disclaimer is added.

## Earlier table and full-screen changes

- `app/src/main/java/com/example/myapplication/SentenceCalculator.java` adds
  immutable `StageCredits` and `CustodyBreakdown` result objects. Existing
  calculation formulas, eligibility rules and remaining balances are unchanged.
- `app/src/main/java/com/example/myapplication/newEntryActivity.java` passes the
  already calculated table and the two existing balance strings in a Bundle
  after a successful save.
- `app/src/main/java/com/example/myapplication/HomeActivity.java` forwards that
  Bundle to the dialog instead of forwarding only two strings.
- `app/src/main/java/com/example/myapplication/ProfileBreakdownDialogFragment.java`
  stores the Bundle as fragment arguments and delegates rendering to the shared
  `BreakdownViewBinder`. Arguments restore on recreation; X dismisses the dialog.
- `app/src/main/java/com/example/myapplication/BreakdownViewBinder.java` binds
  the twelve separate cells, balances and screen-reader descriptions for both
  views. It contains formatting only, not a second calculation algorithm.
- `app/src/main/res/layout/view_sentence_breakdown.xml` defines the shared table,
  caption, legend and balances. Edit individual TextViews here in Android Studio.
- `app/src/main/res/layout/dialog_profile_breakdown.xml` defines the popup shell,
  title and X button, and includes the shared table layout.
- `app/src/main/res/layout/activity_breakdown.xml` defines the full-screen shell,
  Back button, profile name, observation date and larger breakdown card.
- `app/src/main/java/com/example/myapplication/BreakdownActivity.java` reads the
  selected profile by ID and uses the existing mapper/calculator on a background
  executor. It checks that the profile belongs to the signed-in user and handles
  missing profiles or invalid calculation inputs without showing invented values.
- `app/src/main/AndroidManifest.xml` registers the new non-exported activity.
- `app/src/main/res/layout/recycler_view_row.xml` replaces the placeholder text
  with a green MaterialButton. The row's Export PDF placeholder remains.
- `app/src/main/java/com/example/myapplication/Profile_RecyclerViewAdapter.java`
  resolves the clicked row's current adapter position and passes its profile ID
  to BreakdownActivity, so sorting/recycling cannot select a stale position.
- `app/src/main/res/values/profile_breakdown_styles.xml` defines the cream, gold,
  sage and text colors plus shared cell/balance styles. Change shared spacing,
  fonts and colors here.
- `app/src/main/res/values/profile_breakdown_strings.xml` adds the table labels,
  short caption, legend and accessible cell-description format.
- `app/src/main/res/drawable/breakdown_table_background.xml` and
  `breakdown_table_divider.xml` supply the rounded border and thin row separators.
- `app/src/test/java/com/example/myapplication/CustodyBreakdownTest.java` checks
  stage assignment, trigger behavior, gross totals, adjustments and invalid data.
- `app/src/test/java/com/example/myapplication/WebsiteCalculationTest.java` also
  checks the table totals against all 21 captured website/screenshot cases.
- `app/src/androidTest/java/com/example/myapplication/ProfileBreakdownDialogTest.java`
  checks cell binding, CPI display, Bundle serialization, rotation and dismissal.
- `app/src/androidTest/java/com/example/myapplication/BreakdownActivityTest.java`
  checks navigation after sorting, the selected profile's values, Back navigation,
  recreation and missing-profile handling using isolated synthetic records.
- This file documents the feature.

## Storage and performance

The popup reuses values from the calculation already performed when saving.
It does not make another Room query, perform a second calculation, add columns
to the database or save derived values that could become stale after editing.
Each successful calculation adds five small immutable objects and one addition;
work does not grow with the number of days in custody. Opening the popup adds
twelve numeric TextViews plus the labels, and passes a small Bundle in memory.
The instrumentation test requires its serialized payload to remain below 4 KiB.
No noticeable performance impact is expected; no device timing benchmark is claimed.

The full-screen activity reads one saved profile by primary key on opening or
recreation. It finds both completion dates off the UI thread in logarithmic time
(about 17 search steps each at a 200-year span), then builds roughly one stop per
month. It does not calculate every day in the range. Each slider change runs
the existing small calculation against the cached profile and updates the
TextViews; there are no Room reads or writes while dragging. The date observer
does not poll and runs only while the relevant screen is resumed. No noticeable
performance impact is expected; no frame-time or device timing benchmark is claimed.
Milestone drawing adds at most four labels and dots; identical dates are grouped.
Pre-commitment LEA spans are bounded to 200 years for timeline construction.

## Verification

All 53 calculation checks pass: 21 captured website cases, 10 calculator tests,
seven custody-table tests, 11 projection tests and four custody-preview tests. Endpoint tests compare the
production binary search to a separate day-by-day search across all eight
trigger combinations for both minimum and maximum balances. They also cover
completion during LEA and coincident completion dates.

All 15 emulator tests pass: seven activity/save-flow tests, two popup tests and
six trigger-form tests. They use isolated synthetic profiles in a temporary app
whose ID ends in `.timelinecheck`, leaving the installed production app alone.
Today, LEA, minimum-zero, prison and coincident-marker screenshots were visually checked. The temporary
test app is removed after verification. The ordinary debug build also compiles.
Local-midnight/time-zone refresh is implemented, but the device clock was not
changed to exercise rollover during these tests.
