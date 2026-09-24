plugins {
    id("mihon.library")
    kotlin("android")
    kotlin("plugin.serialization")
}

android {
    namespace = "mihon.core.archive"
}

dependencies {
    implementation(projects.core.common)
    implementation(libs.image.decoder)
    implementation(libs.injekt)
    implementation(libs.jsoup)
    implementation(libs.libarchive)
    implementation(libs.unifile)
}
