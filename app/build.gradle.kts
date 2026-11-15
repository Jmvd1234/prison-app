import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// This local file and its secrets are intentionally excluded from version control.
val releaseSigningFile = rootProject.file("keystore.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.isFile) releaseSigningFile.inputStream().use { load(it) }
}
fun signingValue(name: String): String = releaseSigning.getProperty(name)
    ?.takeIf { it.isNotBlank() }
    ?: throw GradleException("Missing $name in local keystore.properties. See RELEASE.md.")


android {
    namespace = "com.example.myapplication"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "edition"
    productFlavors {
        create("full") {
            dimension = "edition"
            buildConfigField("boolean", "FREE_EDITION", "false")
        }
        create("free") {
            dimension = "edition"
            applicationIdSuffix = ".free"
            versionNameSuffix = "-free"
            buildConfigField("boolean", "FREE_EDITION", "true")
        }
    }

    signingConfigs {
        if (releaseSigningFile.isFile) {
            create("release") {
                storeFile = rootProject.file(signingValue("storeFile"))
                storePassword = signingValue("storePassword")
                keyAlias = signingValue("keyAlias")
                keyPassword = signingValue("keyPassword")
                storeType = releaseSigning.getProperty("storeType", "PKCS12")
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "ADS_TEST_MODE", "true")
            resValue("string", "admob_app_id", "ca-app-pub-3940256099942544~3347511713")
            resValue("string", "admob_rewarded_id", "ca-app-pub-3940256099942544/5224354917")
            for (placement in listOf("home", "dashboard", "entry", "breakdown")) {
                resValue("string", "admob_banner_" + placement, "ca-app-pub-3940256099942544/9214589741")
            }
        }
        release {
            buildConfigField("boolean", "ADS_TEST_MODE", "false")
            resValue("string", "admob_app_id", providers.gradleProperty("layaAdmobAppId").orElse("").get())
            resValue("string", "admob_rewarded_id", providers.gradleProperty("layaRewardedId").orElse("").get())
            for (placement in listOf("home", "dashboard", "entry", "breakdown")) {
                resValue("string", "admob_banner_" + placement,
                    providers.gradleProperty("layaBanner" + placement.replaceFirstChar { it.uppercase() } + "Id").orElse("").get())
            }
            isDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningFile.isFile) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Keep downloaded Android test runtimes and lock files inside build output.
            val testHome = layout.buildDirectory.dir("test-home").get().asFile
            it.systemProperty("user.home", testHome.absolutePath)
            providers.gradleProperty("layaTestScreenshots").orNull?.let { path -> it.systemProperty("laya.test.screenshots", path) }
            it.doFirst { testHome.mkdirs() }
            it.systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
            it.jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED", "--add-opens=java.base/java.net=ALL-UNNAMED",
                "--add-opens=java.base/java.security=ALL-UNNAMED", "--add-opens=java.base/java.text=ALL-UNNAMED",
                "--add-opens=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
                "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED")
        }
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        resValues = true
    }
}

dependencies {
    "freeImplementation"("com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.6.0")
    "freeImplementation"("com.google.android.ump:user-messaging-platform:4.0.0")
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.cardview)
    implementation(libs.activity)
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
    testImplementation("androidx.test:core:1.7.0")
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)

    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    implementation(libs.recyclerview)

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
}
// Never silently produce an unsigned release when local signing files are absent.
val checkReleaseSigning by tasks.registering {
    val config = releaseSigningFile
    doLast {
        if (!config.isFile) throw GradleException("Release signing is not configured. See RELEASE.md; debug builds still work.")
    }
}
tasks.matching { (it.name.startsWith("package") && it.name.endsWith("Release")) || (it.name.startsWith("sign") && it.name.endsWith("ReleaseBundle")) }.configureEach {
    dependsOn(checkReleaseSigning)
}

// A test APK can always be built. Production free releases require your own AdMob IDs.
val adProperties = listOf("layaAdmobAppId", "layaRewardedId", "layaBannerHomeId",
    "layaBannerDashboardId", "layaBannerEntryId", "layaBannerBreakdownId")
val productionAdIds = adProperties.associateWith { providers.gradleProperty(it).orElse("").get() }
val checkProductionAds by tasks.registering {
    doLast {
        for ((key, value) in productionAdIds) {
            val pattern = if (key == "layaAdmobAppId") "ca-app-pub-[0-9]{16}~[0-9]{10}" else "ca-app-pub-[0-9]{16}/[0-9]{10}"
            if (!value.matches(Regex(pattern)) || value.contains("3940256099942544"))
                throw GradleException("Configure $key with your production AdMob ID. Use freeDebug for test ads; see FREE_VERSION.md.")
        }
    }
}
tasks.matching { it.name == "preFreeReleaseBuild" }.configureEach { dependsOn(checkProductionAds) }
