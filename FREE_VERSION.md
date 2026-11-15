# Laya Free

This work lives on `free-version`. The original `master` branch remains at `357173f87ca66163deaf98ff148f9c5fa1470620`; nothing is merged into it.

## Builds and installation

This branch has two product flavors:

- `freeDebug`: Laya Free, application ID `com.example.myapplication.free`, Google demo ads. This is the build to use for testing on an emulator or phone.
- `freeRelease`: the free app with production AdMob IDs, R8/resource shrinking and existing release signing. Missing or demo production IDs deliberately fail the build.
- `fullDebug` / `fullRelease`: existing multi-user app with no advertising SDK or new internet permission. The full app's application ID is unchanged.

The free and full apps install side by side with separate private databases. Installing Laya Free does not overwrite the full app's records. The free edition opens straight to Home under one automatic local owner, with no login/profile picker.

In Android Studio, sync Gradle and select **freeDebug** in **Build Variants** (or **Build > Select Build Variant**), then Run. From a terminal with the Android Studio JDK:

```sh
bash gradlew :app:assembleFreeDebug
```

The test APK is `app/build/outputs/apk/free/debug/app-free-debug.apk`. It uses debug signing and is intended for testing, not store publication.

## Profile slots

- One saved-case slot is available initially. Multiple charges within each case remain unrestricted.
- **Unlock a slot** on Home or Dashboard offers one extra permanent local slot for completing one rewarded ad.
- New Entry also offers this when all slots are used. The user must explicitly choose **Watch ad**.
- Only Google's earned-reward callback grants capacity. Closing early, cancelling loading, failed ads and unavailable ads do not grant slots.
- Each ad presentation has a unique token. A transactional reward ledger ignores duplicate callbacks.
- Capacity survives closing the app, rebooting, and normal app updates. Deleting a case frees its slot; it does not remove the entitlement.
- Unlocks are stored on this installation. Clearing data or uninstalling removes them. They do not sync between devices or transfer in `.laya` exports. This app has no account/server-based entitlement recovery.
- Profile creation and bulk import enforce capacity inside Room transactions. An oversized import adds nothing. Export a smaller backup or unlock sufficient slots first.
- Existing calculations, editing, exports and saved records work without an ad or an internet connection.

## Banner placement

Adaptive banners are separated from controls and labelled Advertisement:

- Home: after the main actions and slot counter, before About. Action cards are shorter to fit this naturally.
- Dashboard: a reserved footer below the scrolling case list. In landscape, controls move into a separate scrolling column beside the list.
- Add/Edit: below the form and Calculate/Save action, with separation from the button and keyboard.
- Detailed breakdown: before Sentence Breakdown and after the deductions section. The timeline and calculation cards remain intact.

Banners use the available width after padding, load only when their placement enters the viewport, and collapse if loading fails. They are destroyed with their screen. Portrait and landscape use their actual measured widths.

## Testing the ads

1. Install `freeDebug` and open **Laya Free** with an internet connection.
2. Home should show **0 of 1 profiles used** and a banner marked **Test Ad**.
3. Tap **Unlock a slot**, then **Watch ad**. Wait until the ad says the reward is earned, then close it using the ad's close control. No purchase or app installation is needed.
4. The counter should become **0 of 2 profiles used**. Close and reopen Laya Free: it must remain 2.
5. Create two cases, then try New Entry again: the unlock offer should appear. Cancelling must leave both cases and the capacity unchanged.
6. Delete a case and create another without watching an additional ad.
7. Visit Dashboard, Add/Edit and the detailed breakdown; scroll to the banner locations and rotate the device.
8. Disconnect the internet: saved cases and calculations should still work. A new unlock may be unavailable, and must not falsely report success.

Google's demo IDs are compiled into debug builds for both emulators and physical phones, so test ads cannot generate revenue or bill an advertiser. Do not test by clicking your own production ads. To restart from one slot, clear only **Laya Free** data (this also deletes its cases); do not clear the full app.

## Before real ad serving

Create/register the free Android app in AdMob and create one rewarded unit and four banner units. Put these Gradle properties in your private Gradle user properties or pass them through your build environment:

```properties
layaAdmobAppId=ca-app-pub-YOUR_PUBLISHER_ID~YOUR_APP_ID
layaRewardedId=ca-app-pub-YOUR_PUBLISHER_ID/YOUR_REWARDED_UNIT
layaBannerHomeId=ca-app-pub-YOUR_PUBLISHER_ID/YOUR_HOME_UNIT
layaBannerDashboardId=ca-app-pub-YOUR_PUBLISHER_ID/YOUR_DASHBOARD_UNIT
layaBannerEntryId=ca-app-pub-YOUR_PUBLISHER_ID/YOUR_ENTRY_UNIT
layaBannerBreakdownId=ca-app-pub-YOUR_PUBLISHER_ID/YOUR_BREAKDOWN_UNIT
```

Then build `:app:assembleFreeRelease :app:bundleFreeRelease` with the existing private signing configuration described in RELEASE.md. Debug always overrides those values with demo IDs.

Configure the applicable consent messages in AdMob Privacy & messaging. Production uses Google's UMP consent flow before requesting ads, and exposes **Ad privacy choices** on Home when required. Debug demo ads skip the unconfigured publisher consent flow; production consent behavior still needs testing against your AdMob account and relevant regions.

Publish a privacy policy describing the ad SDK, complete Play's Data safety and Contains ads declarations, and finish AdMob's app verification/readiness requirements. The in-app privacy summary is not a substitute for the hosted store privacy policy. Review the final application IDs before first public publication. Ads add device/advertising data processing; the free app no longer claims that no data is collected. Case names, charges, dates, photos and calculations are not passed to the ad SDK. Automatic backup/device transfer is disabled for the free edition; explicit profile export remains available.

## Database and checks

This branch uses schema 14 for the local reward ledger. Per project preference there is no migration from schema 13. The new free application ID starts with separate fresh storage, so the full app does not need to be cleared. If using an older test build with the same free ID and an incompatible schema, export its cases before clearing only the free app. Use `master` for the original full-app release; the `full` flavor on this development branch also uses schema 14, so it is not a migration-compatible update for existing full installations.

Automated checks:

```sh
bash gradlew :app:testFreeDebugUnitTest --tests 'com.example.myapplication.FreeSlotsTest' --tests 'com.example.myapplication.FreeNavigationTest'
bash gradlew :app:testFullDebugUnitTest
```

The original multi-profile tests belong to the full flavor; some intentionally exceed the free slot limit. Free-specific tests cover capacity, duplicate callbacks, persistence, import atomicity, concurrent saves, cancellation/unavailable ads, direct-save protection and automatic-owner navigation.

Official SDK references:
- https://developers.google.com/admob/android/next-gen/quick-start
- https://developers.google.com/admob/android/next-gen/rewarded/single-load
- https://developers.google.com/admob/android/next-gen/banner
- https://developers.google.com/admob/android/next-gen/privacy
- https://developers.google.com/admob/android/next-gen/privacy/play-data-disclosure
