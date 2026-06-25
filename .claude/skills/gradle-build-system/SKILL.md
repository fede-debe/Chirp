---
name: gradle-build-system
description: Use when adding a Gradle module, adding or upgrading a dependency, editing build.gradle.kts or settings.gradle.kts, touching the build-logic convention plugins or the version catalog, configuring bootJar, or pinning a transitive dependency for a CVE.
---

# Gradle Build System

## Overview

Gradle multi-module build with three layered **convention plugins** in `build-logic/` and a
**version catalog** in `gradle/libs.versions.toml`. Modules stay tiny because all shared
build config lives in the plugins. Only `app` produces an executable jar; every other module
is a library.

## Version catalog — the only place versions live

All dependency coordinates and versions are declared in `gradle/libs.versions.toml` and
referenced as typed accessors. **Never hardcode a version in a `build.gradle.kts`.**

```toml
[versions]
spring-boot = "4.0.2"
[libraries]
spring-boot-starter-web = { module = "org.springframework.boot:spring-boot-starter-web" }
[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
```

```kotlin
// in a module build file:
implementation(libs.spring.boot.starter.web)   // dot-separated accessor
```

Spring Boot starters omit `version.ref` — their versions come from the Spring Boot BOM,
imported by `chirp.kotlin-common` via `dependencyManagement { imports { mavenBom(...) } }`.

To add a dependency: add it to `[libraries]` (or `[versions]`+`[libraries]`), then reference
`libs.<name>` in the module that needs it.

## The three convention plugins (build-logic/)

Layered — each builds on the previous:

| Plugin (`build-logic/src/main/kotlin/...gradle.kts`) | Applied to            | Adds |
|------------------------------------------------------|-----------------------|------|
| `chirp.kotlin-common`       | every module (incl. `common`) | Kotlin JVM + Spring plugin + dependency-management; JVM toolchain 21; strict compiler args; Spring Boot BOM; **forced transitive versions** for CVEs. |
| `chirp.spring-boot-service` | feature modules + `common`    | extends kotlin-common; web starter, Kotlin stdlib/reflect, Jackson Kotlin module, test deps. |
| `chirp.spring-boot-app`     | `app` **only**                | extends spring-boot-service; adds the Spring Boot plugin (so only `app` builds a fat jar) + JPA all-open. |

`build-logic` is an **included build** (`includeBuild("build-logic")` in
`settings.gradle.kts`). It's a separate Gradle project, so it can't use the version catalog —
its own plugin deps are hardcoded in `build-logic/build.gradle.kts` (that's expected).

The `libraries` accessor helper (`VersionCatalogExt.kt`) lets the convention plugins read the
catalog: `libraries.findLibrary("spring-boot-starter-web").get()`.

## A feature module's build file (the template)

```kotlin
plugins {
    id("java-library")
    id("chirp.spring-boot-service")
    kotlin("plugin.jpa")            // only if the module has JPA entities
}
group = "com.project"
version = "unspecified"
repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
    maven { url = uri("https://repo.spring.io/snapshot") }
}
dependencies {
    implementation(projects.common)          // typesafe project accessor
    implementation(libs.spring.boot.starter.data.jpa)
    runtimeOnly(libs.postgresql)
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
```

`projects.common` is a **typesafe project accessor**, enabled by
`enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")` in `settings.gradle.kts`.

## Registering a new module

1. Create `<module>/build.gradle.kts` from the template above.
2. Add `include("<module>")` to root `settings.gradle.kts`.
3. Add `implementation(projects.<module>)` to `app/build.gradle.kts` so it's wired into the
   runnable app.
4. Put code under `com.project.chirp.*` so it gets component-scanned (see [[chirp-architecture]]).

## bootJar — bundling feature-module resources

Only `app` builds the jar (`./gradlew :app:bootJar` → `app/build/libs/app-<version>.jar`).
Feature-module `src/main/resources` files are **not** automatically on the app's classpath, so
`app/build.gradle.kts` explicitly copies them into the jar root:

```kotlin
tasks {
    named<BootJar>("bootJar") {
        from(project(":notification").projectDir.resolve("src/main/resources")) { into("") }
        from(project(":user").projectDir.resolve("src/main/resources")) { into("") }
    }
}
```

**If a new module ships classpath resources** (templates, credentials, Lua scripts, etc.), add
a matching `from(...)` block here — otherwise the app starts without them and fails at runtime
(e.g. missing Firebase creds, 404 email templates).

## Forcing transitive versions (CVE patches)

Project-wide transitive pins live once in `chirp.kotlin-common`, applied to all configurations:

```kotlin
configurations.all {
    resolutionStrategy.eachDependency {
        if (requested.group == "io.netty") useVersion("4.2.9.Final")      // CVE fix
        if (requested.group == "io.grpc" && requested.name == "grpc-netty-shaded") useVersion("1.75.0")
        if (requested.group == "org.apache.commons" && requested.name == "commons-lang3") useVersion("3.18.0")
    }
}
```

Add new forced versions here (with a comment naming the CVE) rather than per-module.

## Testing stack (configured, barely used)

`chirp.spring-boot-service` wires `spring-boot-starter-test`, `kotlin-test-junit5`, and the
JUnit platform launcher; the catalog also has `spring-rabbit-test`, `spring-security-test`,
`spring-restdocs-mockmvc`. JUnit 5 is the platform (`useJUnitPlatform()`). **Note:** the repo
currently has essentially no tests beyond an empty context-load test — there is no established
testing convention to copy. Treat test-writing as greenfield against this available stack.

## Common mistakes

- Hardcoding a version in a module build file instead of the catalog.
- Adding a classpath resource to a feature module but forgetting the `bootJar` `from(...)` block.
- Applying `chirp.spring-boot-app` (or the Spring Boot plugin) to a feature module — only `app`
  gets it, or you'll build multiple fat jars.
- Forgetting `kotlin("plugin.jpa")` on a module with `@Entity` classes (no-arg constructor /
  all-open won't apply and Hibernate will fail).
- Editing `build-logic` and expecting `libs.*` to work there — it can't; that project has its
  own hardcoded plugin versions.
