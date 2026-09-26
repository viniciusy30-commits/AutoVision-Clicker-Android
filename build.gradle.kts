plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.compose.compiler) apply false
}

allprojects {
    val GROUP: String by project
    val VERSION: String by project
    group = GROUP
    version = VERSION
}
