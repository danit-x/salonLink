// =====================================================================================
// SalonLink — root build file
// Plugins are declared here with `apply false` so the version is resolved once, and each
// module then applies only the plugins it needs.
// =====================================================================================

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.appdistribution) apply false
}
