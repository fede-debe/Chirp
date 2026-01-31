plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}

// seen as separate project from gradle, we can't use libs.versions.toml
dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.21")
    implementation("org.jetbrains.kotlin:kotlin-allopen:2.2.21")
    implementation("org.jetbrains.kotlin:kotlin-noarg:2.2.21")

    implementation("org.springframework.boot:spring-boot-gradle-plugin:4.0.2")
    implementation("io.spring.gradle:dependency-management-plugin:1.1.7")
}