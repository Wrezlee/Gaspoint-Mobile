plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "pmluts_gaspoint"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "maharani.devie.projectuas"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    sourceSets {
        getByName("main") {
            res.srcDirs(
                "src/main/res",
                "src/main/res/layouts/admin",
                "src/main/res/layouts/kasir",
                "src/main/res/layouts/kurir",
                "src/main/res/layouts/pembeli",
                "src/main/res/layouts/auth",
                "src/main/res/layouts/common"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    //menentukan versi library Firebase yang digunakan oleh aplikasi
    implementation(platform("com.google.firebase:firebase-bom:35.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    //library firebase firestore
    implementation("com.google.firebase:firebase-firestore")
    //library firebase auth
    implementation("com.google.firebase:firebase-auth")
    //library firebase messaging
    implementation("com.google.firebase:firebase-messaging")
    //libarry firebase in-app messaging
    implementation("com.google.firebase:firebase-inappmessaging-display")
    val supabaseVersion = "2.3.1"
    val ktorVersion = "2.3.11"
    //menyesuaikan versi modul Supabase & Ktor agar konsisten
    implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
    implementation(platform("io.ktor:ktor-bom:$ktorVersion"))
    //SDK Supabase Storage & Engine jaringan Ktor
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-android")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    //penampil gambar UI
    implementation("io.coil-kt:coil:2.6.0")

    //peta OpenStreetMap (osmdroid)
    implementation("org.osmdroid:osmdroid-android:6.1.10")
    //izin runtime (PermissionX)
    implementation("com.guolindev.permissionx:permissionx:1.8.1")
    //GPS: FusedLocationProviderClient
    implementation("com.google.android.gms:play-services-location:21.3.0")

    //QR Code: generate (BarcodeEncoder) dan scan (ScanContract) memakai ZXing
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}