# Website-compatible calculator

Updated 29 September 2026 after the user approved following the existing
[Laya calculator](https://chr.layacalculator.org/) behavior. This supersedes the
previous implementation's separate CPI/service-start interpretation.

## Current behavior

- The sentence calendar is anchored at the entered commitment date.
- The website's arrest-to-commitment interval is subtracted in every trigger mode,
  including CPI disqualification. The observation date supplies the remaining balance.
- CPI disqualification makes GCTA and TASTM zero. The Android switch continues to
  force TA checked, as explicitly requested, without adding any fields.
- TA alone with no conviction gives zero GCTA/TASTM. With a conviction date, the
  website counts complete months from conviction and starts its allowance tiers
  from month one there.
- Not Committed to Jail changes the commitment label, without changing the
  website's baseline arithmetic.
- STAL applies 20% or 40% to the maximum sentence length, rounded up. That same
  day credit is subtracted from both balances and remains available with CPI/TA.
- Deductions and bail/ATI gaps are each added to the balance once, without capping
  either to the calculated allowance or the pre-commitment interval.
- Years and months are added together before adjusting the day, matching the
  website's calendar behavior for leap dates.
- The website's optional LEA date is supported; a blank date uses commitment.
- Old stored CPI percentages, service dates and timeline metadata cannot change
  a calculation from the visible website inputs. Their database columns are retained.
- Dashboard cards contain the normal profile details, balances and short action.
  The repeated disclaimer paragraphs are removed. Confirmation dialogs remain concise.

## Screenshot acceptance case

Observation: 29 September 2026. Arrest: 4 June 2024. Commitment: 6 February 2025.
Minimum: 1 year. Maximum: 3 years. CPI selected. No STAL, deductions or bail gap.

| Component | Days |
|---|---:|
| Arrest to commitment | 247 |
| Commitment to observation | 600 |
| GCTA / TASTM | 0 / 0 |
| Minimum remaining | **-482** |
| Maximum remaining | **248** |

Equivalent dates match the screenshot: 4 June 2025 and 4 June 2027.
The earlier screenshot case also matches: -255 / 476 days.

## Verification

- 19 entirely invented cases were sent to the public website and their returned
  results captured independently. No screenshot/profile dates were transmitted.
- Two additional expected cases come directly from the user-supplied screenshots.
- All 21 reference cases pass, including triggers, conviction timing, STAL,
  adjustments, tier transitions, leap dates and month ends.
- Ten further tests cover saved-profile mapping, legacy data, input validation,
  hidden dates and the requested switch rules.
- Debug app and Android test APKs compile successfully.
- Emulator UI execution was not repeated: its storage-service failure was already
  established in the earlier run. The user will test the installed Android UI.
- Database version and schema remain unchanged; no profile reset is needed.

The reference table is `app/src/test/resources/website-calculator-cases.tsv`.
Expected values are captured website outputs, not generated from the Java implementation.
The old contrary CPI assertions have been replaced.

## Deferred review

Website behavior is the acceptance target for this prototype. Two observed behaviors
are retained for a later review: CPI still uses the arrest-based baseline, and a future
conviction can produce negative GCTA/TASTM because the website uses a signed month count.
The documented golden cases preserve these observations rather than introducing a
different rule silently. No legal interpretation is asserted by this compatibility work.
