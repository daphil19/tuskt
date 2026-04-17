plugins {
    id("tuskt.root-release")
    alias(libs.plugins.ktlint) apply false

    // TODO if we need to override any config we might need a build-logic plugin!
    alias(libs.plugins.detekt) apply false

    // these need to be defined for some of the other plugins to work correctly
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.shadow) apply false

//    alias(libs.plugins.ktor) apply false
}
