plugins {
    `kotlin-dsl`
}

group = "com.xiaomi.xmsf.buildlogic"

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.kotlin.compose.gradlePlugin)
    implementation(libs.ksp.gradlePlugin)
    implementation(libs.hilt.gradlePlugin)
}

// 启用类型安全的项目访问器
gradlePlugin {
    plugins {
        create("androidApplication") {
            id = "mipush.android.application"
            implementationClass = "MipushAndroidApplicationPlugin"
        }
        create("androidLibrary") {
            id = "mipush.android.library"
            implementationClass = "MipushAndroidLibraryPlugin"
        }
        create("androidCompose") {
            id = "mipush.android.compose"
            implementationClass = "MipushAndroidComposePlugin"
        }
        create("androidHilt") {
            id = "mipush.android.hilt"
            implementationClass = "MipushAndroidHiltPlugin"
        }
        create("androidRoom") {
            id = "mipush.android.room"
            implementationClass = "MipushAndroidRoomPlugin"
        }
        create("androidAop") {
            id = "mipush.android.aop"
            implementationClass = "MipushAndroidAopPlugin"
        }
        create("appPackaging") {
            id = "mipush.app.packaging"
            implementationClass = "MipushAppPackagingPlugin"
        }
    }
}
