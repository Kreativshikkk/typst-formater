plugins {
    kotlin("jvm") version "2.1.20"
    alias(libs.plugins.kotlin.serialization)
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven(url = "https://packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
}

dependencies {
    testImplementation(kotlin("test"))
    implementation(libs.grazie.client)
    implementation(libs.koog.agents)
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("org.jsoup:jsoup:1.17.2")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}