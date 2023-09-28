val kotlin_version: String by rootProject.extra
val hilt_version: String by rootProject.extra
val dokka_version: String by rootProject.extra
val buildFirebase = project.hasProperty("buildFirebase") || gradle.startParameter.taskRequests.toString().contains("Firebase")

plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("kapt")
    id("dagger.hilt.android.plugin")
    id("com.google.protobuf") version "0.9.3"
}

android {
    namespace = "cx.ring"
    compileSdk = 34
    buildToolsVersion = "34.0.0"
    ndkVersion = "25.2.9519653"
    defaultConfig {
        minSdk = 24
        targetSdk = 34
        versionCode = 383
        versionName = "20230928-01"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        externalNativeBuild {
            cmake {
                version = "3.22.1"
                arguments += listOf(
                    "-DANDROID_STL=c++_shared",
                    "-DBUILD_CONTRIB=ON",
                    "-DBUILD_EXTRA_TOOLS=ON",
                    "-DJAMI_TESTS=OFF",
                    "-DBUILD_TESTING=OFF",
                    "-DJAMI_JNI=ON",
                    "-DJAMI_JNI_PACKAGEDIR="+rootProject.projectDir.resolve("libjamiclient/src/main/java"),
                    "-DJAMI_DATADIR=/data/data/$namespace/files",
                    "-DJAMI_NATPMP=Off"
                )
            }
            ndk {
                debugSymbolLevel = "FULL"
                abiFilters += properties["archs"]?.toString()?.split(",") ?: listOf("arm64-v8a", "x86_64", "armeabi-v7a")
                println ("Building for ABIs $abiFilters")
            }
        }
    }
    buildTypes {
        debug {
            isDebuggable = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    buildFeatures {
        viewBinding = true
        dataBinding = true
    }
    flavorDimensions += "push"
    productFlavors {
        create("noPush") {
            dimension = "push"
        }
        create("withFirebase") {
            dimension = "push"
        }
        create("withUnifiedPush") {
            dimension = "push"
        }
    }
    signingConfigs {
        create("config") {
            keyAlias = "ring"
            storeFile = file("../keystore.bin")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    externalNativeBuild {
        cmake {
            path = file("../../daemon/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

val markwon_version = "4.6.2"

dependencies {
    implementation (project(":libjamiclient"))
    implementation ("org.jetbrains.kotlin:kotlin-stdlib:$kotlin_version")
    implementation ("androidx.core:core-ktx:1.12.0")
    implementation ("androidx.appcompat:appcompat:1.6.1")
    implementation ("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation ("androidx.cardview:cardview:1.0.0")
    implementation ("com.caverock:androidsvg-aar:1.4")
    implementation ("androidx.preference:preference-ktx:1.2.1")
    implementation ("androidx.recyclerview:recyclerview:1.3.1")
    implementation ("androidx.leanback:leanback:1.2.0-alpha03")
    implementation ("androidx.leanback:leanback-preference:1.2.0-alpha03")
    implementation ("androidx.car.app:app:1.4.0-beta01")
    implementation ("androidx.tvprovider:tvprovider:1.1.0-alpha01")
    implementation ("androidx.media:media:1.6.0")
    implementation ("androidx.sharetarget:sharetarget:1.2.0")
    implementation ("androidx.percentlayout:percentlayout:1.0.0")
    implementation ("androidx.emoji2:emoji2:1.4.0")
    implementation ("androidx.emoji2:emoji2-emojipicker:1.4.0")
    implementation ("androidx.lifecycle:lifecycle-viewmodel-ktx:2.6.2")
    implementation ("androidx.slidingpanelayout:slidingpanelayout:1.2.0")
    implementation ("com.google.android.material:material:1.9.0")
    implementation ("com.google.android.flexbox:flexbox:3.0.0")
    implementation ("org.osmdroid:osmdroid-android:6.1.16")
    implementation ("io.noties.markwon:core:$markwon_version")
    implementation ("io.noties.markwon:linkify:$markwon_version")
    implementation ("com.google.protobuf:protobuf-javalite:3.23.2")

    // ORM
    implementation ("com.j256.ormlite:ormlite-android:5.7")

    // Barcode scanning
    implementation("com.journeyapps:zxing-android-embedded:4.3.0") { isTransitive = false }
    implementation ("com.google.zxing:core:3.5.1")

    // Dagger dependency injection
    implementation("com.google.dagger:hilt-android:$hilt_version")
    kapt("com.google.dagger:hilt-android-compiler:$hilt_version")

    // Espresso Unit Tests
    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")

    // Glide
    implementation ("com.github.bumptech.glide:glide:4.15.1")
    kapt ("com.github.bumptech.glide:compiler:4.15.1")

    // RxAndroid
    implementation ("io.reactivex.rxjava3:rxandroid:3.0.2")
    implementation ("io.reactivex.rxjava3:rxjava:3.1.6")

    implementation ("com.jsibbold:zoomage:1.3.1")
    implementation ("com.googlecode.ez-vcard:ez-vcard:0.11.3") {
        exclude(group= "org.freemarker", module= "freemarker")
        exclude(group= "com.fasterxml.jackson.core", module= "jackson-core")
    }

    "withFirebaseImplementation"("com.google.firebase:firebase-messaging:23.1.2") {
        exclude(group= "com.google.firebase", module= "firebase-core")
        exclude(group= "com.google.firebase", module= "firebase-analytics")
        exclude(group= "com.google.firebase", module= "firebase-measurement-connector")
    }
    "withUnifiedPushImplementation"("com.github.UnifiedPush:android-connector:2.2.0")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.23.2"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java") {
                    option("lite")
                }
            }
        }
    }
}

kapt {
    correctErrorTypes = true
}

if (buildFirebase) {
    println ("apply plugin $buildFirebase")
    apply(plugin = "com.google.gms.google-services")
}
