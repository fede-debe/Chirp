plugins {
    id("chirp.spring-boot-service")
    id("org.springframework.boot")
    // not applied to the services but here
    kotlin("plugin.spring")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}