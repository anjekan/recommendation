plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val recommendationBaseUrl = providers.gradleProperty("recommendationBaseUrl")
    .orElse(providers.environmentVariable("RECOMMENDATION_BASE_URL"))
    .orElse("http://183.96.45.33:18080")
val kioskKey = providers.gradleProperty("kioskKey")
    .orElse(providers.environmentVariable("KIOSK_KEY"))
    .orElse("LOCAL-DEVELOPMENT")
val distributionStoreFile = providers.environmentVariable("RECOMMENDATION_SIGNING_STORE_FILE").orNull
val distributionStorePassword = providers.environmentVariable("RECOMMENDATION_SIGNING_STORE_PASSWORD").orNull
val distributionKeyAlias = providers.environmentVariable("RECOMMENDATION_SIGNING_KEY_ALIAS").orNull
val distributionKeyPassword = providers.environmentVariable("RECOMMENDATION_SIGNING_KEY_PASSWORD").orNull
val hasDistributionSigning = listOf(
    distributionStoreFile,
    distributionStorePassword,
    distributionKeyAlias,
    distributionKeyPassword,
).all { !it.isNullOrBlank() }
if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) }) {
    require(hasDistributionSigning) {
        "Release builds require RECOMMENDATION_SIGNING_STORE_FILE, " +
            "RECOMMENDATION_SIGNING_STORE_PASSWORD, RECOMMENDATION_SIGNING_KEY_ALIAS, " +
            "and RECOMMENDATION_SIGNING_KEY_PASSWORD."
    }
}

android {
    namespace = "kr.co.ninetyseconds.recommendation"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "kr.co.ninetyseconds.recommendation"
        minSdk = 24
        targetSdk = 35
        versionCode = 3
        versionName = "0.2.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "RECOMMENDATION_BASE_URL", "\"${recommendationBaseUrl.get()}\"")
        buildConfigField("String", "KIOSK_KEY", "\"${kioskKey.get()}\"")
    }

    signingConfigs {
        if (hasDistributionSigning) {
            create("distribution") {
                storeFile = file(distributionStoreFile!!)
                storePassword = distributionStorePassword
                keyAlias = distributionKeyAlias
                keyPassword = distributionKeyPassword
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("distribution")?.let { signingConfig = it }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    sourceSets {
        getByName("main").assets.directories.add("../../../contracts/examples")
        getByName("main").assets.directories.add("../../../projects/taean-flower")
        getByName("main").assets.directories.add("../../../projects/uiryeong-richrich-2026")
    }

    androidResources {
        ignoreAssetsPattern = "!.svn:!.git:!.ds_store:!*.scc:.*:CVS:thumbs.db:picasa.ini:!*~:README.md:MAP_COORDINATES.md"
    }
}

dependencies {
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.kotlinx.coroutines.core)
    implementation(project(":core:analysis"))
    implementation(project(":core:analysis-android"))
    implementation(project(":core:application"))
    implementation(project(":core:data-config"))
    implementation(project(":core:data-local"))
    implementation(project(":core:data-remote"))
    implementation(project(":core:domain"))

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit4)
}
