import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.springframework.boot.gradle.plugin.SpringBootPlugin

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.2.0-RC1"
}

group = "io.github.mirilumi"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

kotlin {
    compilerOptions {
        // Treat Spring's null-safety annotations as Kotlin nullability
        freeCompilerArgs.add("-Xjsr305=strict")
        jvmTarget = JvmTarget.JVM_25
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Versions come from these BOMs (Gradle's built-in platform support, no dependency-management plugin)
    implementation(platform(SpringBootPlugin.BOM_COORDINATES))
    implementation(platform("org.springframework.ai:spring-ai-bom:2.1.0-RC1"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // RestClient + @HttpExchange clients configured from spring.http.serviceclient.<group>.*
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webmvc")
    // Client credentials token for calls to downstream services (docs/design/service-auth.md)
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
