import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    kotlin("plugin.jpa") version "2.2.21"
    id("org.springframework.boot") version "4.1.0"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
}

group = "cat.subi"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        // Kotlin 2.2.x cannot emit JVM 25 bytecode yet: Java 25 toolchain/runtime, target 24
        jvmTarget = JvmTarget.JVM_24
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<JavaCompile> {
    options.release = 24
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.0"))
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Auth phase: Google OAuth2 login for the browser/PWA (machines still use the static bearer)
    implementation("org.springframework.boot:spring-boot-starter-oauth2-client")
    // Persistent HTTP sessions in Postgres: survive redeploys and back a 1-week login
    // (schema created by Liquibase; spring.session.jdbc.initialize-schema=never)
    implementation("org.springframework.session:spring-session-jdbc")

    // Modulith: verified bounded contexts + event publication registry (outbox)
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("org.springframework.modulith:spring-modulith-starter-jdbc")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

    // Persistence
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    runtimeOnly("org.postgresql:postgresql")

    // Async jobs (same Postgres, no broker)
    implementation("org.jobrunr:jobrunr-spring-boot-4-starter:8.7.0")

    // Claude via Spring AI (GA not out yet; RC2 is the latest on Central)
    implementation("org.springframework.ai:spring-ai-starter-model-anthropic")

    // OpenAPI as the contract (the frontend TS client is generated from it)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")

    // PDF text extraction (finpension performance reports — fixed machine-generated layout)
    implementation("org.apache.pdfbox:pdfbox:3.0.7")

    // Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation(platform("org.testcontainers:testcontainers-bom:2.0.5"))
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.wiremock:wiremock-standalone:3.13.2")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("config/detekt/detekt.yml")
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt> {
    // detekt's embedded analyzer does not know JVM target 25 yet
    jvmTarget = "21"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

// Dev convenience: `./gradlew bootRun` loads env vars from the repo-root .env / .env.local
// (KEY=value, # comments, blanks and empty values skipped) so you don't re-export secrets each
// restart. Same files docker-compose reads. Only affects bootRun — never tests nor the jar; real
// environment variables still win. Resolve paths at configuration time (configuration-cache safe),
// read them at execution time.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    val envFiles = listOf(file("../.env"), file("../.env.local"))
    doFirst {
        envFiles.filter { it.exists() }.forEach { envFile ->
            envFile.readLines().forEach { raw ->
                val line = raw.trim()
                if (line.isNotEmpty() && !line.startsWith("#") && line.contains("=")) {
                    val key = line.substringBefore("=").trim()
                    val value = line.substringAfter("=").trim()
                    if (key.isNotEmpty() && value.isNotEmpty()) environment(key, value)
                }
            }
        }
    }
}
