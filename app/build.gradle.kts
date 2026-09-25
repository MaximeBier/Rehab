import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "rehab.app"
    compileSdk = 37
    defaultConfig {
        applicationId = "rehab.app"
        minSdk = 34
        targetSdk = 37
        versionCode = 9
        versionName = "0.6.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            // Signature debug réutilisée pour le release (revue finale, mineur) : sans signingConfig,
            // assembleRelease produit un APK non signé, installable nulle part. Ce n'est pas une clé de
            // production — juste ce qu'il faut pour qu'assembleRelease produise un artefact installable
            // en V1 (usage personnel, pas de publication sur un store). À remplacer par une vraie
            // signingConfig de release avant toute distribution hors du téléphone de développement.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        // Rendu PNG (rehab.app.render.*) ignoré par défaut : ./gradlew test reste rapide et inchangé.
        // Activation explicite : -Prehab.render=true.
        unitTests.all { it.systemProperty("rehab.render", (project.findProperty("rehab.render") ?: "false").toString()) }
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    implementation(project(":domain"))
    implementation(project(":rules"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.savedstate)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    // FakeClock / InMemory*Repo vivent en java-test-fixtures du module domain (revue finale, mineur) :
    // elles ne doivent pas partir dans l'APK (le build release n'est pas minifié).
    testImplementation(testFixtures(project(":domain")))
}
