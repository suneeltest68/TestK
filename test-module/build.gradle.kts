plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("com.example.TradingDaemonMainKt")
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(fileTree("libs").include("*.jar"))
    implementation("org.json:json:20211205")
    implementation(libs.kotlinx.coroutines)

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
}
