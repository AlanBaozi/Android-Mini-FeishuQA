plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    // json工具
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.example.feishuqa"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.feishuqa"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.runtime.livedata)
    // XML 布局相关依赖
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    // GridLayout
    implementation("androidx.gridlayout:gridlayout:1.0.0")
    // Compose ConstraintLayout
    implementation(libs.androidx.constraintlayout.compose)
    // Activity KTX
    implementation(libs.androidx.activity.ktx)
    // 图片加载 (Glide) - 用于列表和预览显示
    implementation(libs.glide)
    // 百度语音SDK
    implementation(files("libs/bdasr_V3_20250507_b610f20.jar"))
    // json工具
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    // Markwon - Markdown解析库（只保留核心功能）
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:linkify:4.6.2")
    // Markwon表格支持
    implementation("io.noties.markwon:ext-tables:4.6.2")
    // 1. OkHttp: 用于发送网络请求
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // 2. Gson: 用于解析 JSON 数据
    implementation("com.google.code.gson:gson:2.10.1")
    // 3. Kotlin Coroutines: 协程核心库 + Android 支持
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    // 4. Lifecycle KTX: 提供了 lifecycleScope，能在 Activity/Fragment 中方便地启动协程
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    
    testImplementation(libs.junit)
    // AndroidX Test Core 用于 ApplicationProvider
    testImplementation("androidx.test:core:1.5.0")
    // Robolectric 用于在 JVM 上运行 Android 测试（可选）
    testImplementation("org.robolectric:robolectric:4.11.1")
    // Mockito 用于模拟对象（可选）
    testImplementation("org.mockito:mockito-core:5.5.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}