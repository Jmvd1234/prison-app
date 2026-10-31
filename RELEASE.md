# Building a Laya release

Release builds enable R8 code shrinking, optimization and obfuscation, resource shrinking, and private-key signing. Debug builds remain easy to debug. Obfuscation increases the effort of reverse engineering; it cannot prevent APK copying. Signing lets Android authenticate updates from the same publisher.

## First-time setup on another computer

Use the existing Laya release key. Do not generate a replacement for an already distributed release.

1. Securely transfer the private signing folder to the new computer.
2. Copy `keystore.properties.example` to `keystore.properties` in the project root.
3. Fill in the key path, alias and passwords locally. Use forward slashes for Windows paths (for example `C:/private/laya-release.p12`). Never commit the filled-in file or the key.
4. Use Android Studio's bundled JDK for Gradle.

On the original Mac, the private key and a backup of its password configuration are stored in:
`/Users/julianvdizon/Documents/Codex/private/laya-signing`

The alias is `laya-release`; the store type is `PKCS12`. Keep an encrypted backup of this folder and retain its password in a password manager. Anyone with both key and password can sign as Laya. Losing the key prevents updates to directly distributed installations.

## Build

From the project root on macOS/Linux:

```sh
./gradlew :app:assembleRelease :app:bundleRelease
```

If the wrapper is not executable, use `bash ./gradlew` instead. On Windows use `gradlew.bat`.

Alternatively, use Android Studio's **Build > Generate Signed App Bundle / APK**, select the existing key, its passwords, and the **release** variant.

Artifacts:
- Installable APK: `app/build/outputs/apk/release/app-release.apk`
- Google Play bundle: `app/build/outputs/bundle/release/app-release.aab`
- Private crash-decoding map: `app/build/outputs/mapping/release/mapping.txt`

Before each published update, increase `versionCode` in `app/build.gradle.kts`. Keep the application ID and signing identity consistent. Save the APK/AAB and matching mapping file for every release; later builds overwrite the build output folders. Keep mapping files private, or upload them only to your trusted crash-reporting/Play Console account.

The current application ID remains `com.example.myapplication`. Decide on a final application ID before the first public distribution; changing it later creates a separate app.

## Testing and distribution

The release signature differs from the development/debug signature. Android will not install this release over a debug installation of the same package. Use a separate emulator or test device; uninstalling an existing app deletes its local profiles and entries.

Test the optimized release itself before distribution. The first configured release passed the existing unit tests and an isolated smoke test covering Guest/profile database startup, the Home new-entry button, saving an entry, the calculation breakdown and timeline zoom. This is not an exhaustive acceptance test.

Distribute the APK for direct installs. The AAB is for Play Console; it cannot be installed directly. Configure Play App Signing when publishing there. If the same app must be updated across direct and Play distribution, plan a consistent app-signing identity before enrollment.

Official references:
- https://developer.android.com/topic/performance/app-optimization/enable-app-optimization
- https://developer.android.com/studio/publish/app-signing
