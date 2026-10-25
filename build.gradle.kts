import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.openapitools.generator.gradle.plugin.tasks.GenerateTask
import org.springframework.boot.gradle.plugin.SpringBootPlugin
import java.util.Properties

plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.spring") version "2.4.21"
    id("org.springframework.boot") version "4.2.0-RC1"
    id("org.openapi.generator") version "7.26.0"
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

// Kotlin models + @HttpExchange interfaces generated from each downstream service's OpenAPI spec (see specs/README.md).
// Every specs/<service>/spec.json gets a generate<Service>Client task, output in build/generated/openapi/<service>
// and package ...clients.<service>. Specs of private services are gitignored, so a service without one is skipped.
val specsDir = layout.projectDirectory.dir("specs")
val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")
val clientsBasePackage = "io.github.mirilumi.mcp.booking.clients"

val serviceSpecDirs = specsDir.asFile
    .listFiles { dir -> dir.isDirectory && dir.resolve("spec.json").isFile }
    .orEmpty()
    .sortedBy { it.name }

val generateServiceClients = serviceSpecDirs.map { dir ->
    val service = dir.name
    val servicePackage = "$clientsBasePackage.${service.replace("-", "").lowercase()}"
    val taskSuffix = service.split('-').joinToString("") { it.replaceFirstChar(Char::uppercase) }
    // Optional per-service type overrides, e.g. DateTime=java.time.LocalDateTime for a service whose
    // timestamps have no offset (default for OpenAPI date-time is OffsetDateTime)
    val typeMappingsFile = dir.resolve("type-mappings.properties")
    val serviceTypeMappings = if (typeMappingsFile.isFile) {
        Properties().apply { typeMappingsFile.reader().use { load(it) } }
            .entries.associate { (key, value) -> key.toString() to value.toString() }
    } else emptyMap()

    tasks.register<GenerateTask>("generate${taskSuffix}Client") {
        group = "openapi"
        description = "Generates Kotlin models and @HttpExchange clients from specs/$service/spec.json"
        generatorName = "kotlin-spring"
        library = "spring-declarative-http-interface"
        inputSpec = specsDir.file("$service/spec.json")
        outputDir = generatedOpenApiDir.map { it.dir(service) }
        packageName = servicePackage
        apiPackage = "$servicePackage.api"
        modelPackage = "$servicePackage.model"
        typeMappings = serviceTypeMappings
        configOptions = mapOf(
            "useSpringBoot3" to "true",
            // one interface per controller tag (e.g. ValidDatesControllerApi) instead of one per path prefix
            "useTags" to "true",
            "generateJsonIncludeAnnotations" to "false",
            "generateJsonSetterNullsAnnotations" to "false",
            // plain data classes: no Swagger or Bean Validation annotations, so no extra dependencies
            "annotationLibrary" to "none",
            "documentationProvider" to "none",
            "useBeanValidation" to "false"
        )
    }
}

serviceSpecDirs.forEach { dir ->
    kotlin.sourceSets["main"].kotlin.srcDir(generatedOpenApiDir.map { it.dir("${dir.name}/src/main/kotlin") })
}

// A specs/<service>/ folder without spec.json (e.g. a fresh clone of this public repo) means the generated classes
// are missing, so leave out the code that uses them: clients/<service>/ and tools/<service>/ in main and test.
val servicesWithoutSpec = specsDir.asFile
    .listFiles { dir -> dir.isDirectory && !dir.resolve("spec.json").isFile }
    .orEmpty()
    .map { it.name.replace("-", "").lowercase() }

if (servicesWithoutSpec.isNotEmpty()) {
    val excluded = servicesWithoutSpec.flatMap { listOf("**/clients/$it/**", "**/tools/$it/**") }
    kotlin.sourceSets["main"].kotlin.exclude(excluded)
    kotlin.sourceSets["test"].kotlin.exclude(excluded)
    logger.lifecycle("No spec for ${servicesWithoutSpec.joinToString()}: skipping their clients and tools (see specs/README.md)")
}

tasks.named("compileKotlin") {
    dependsOn(generateServiceClients)
}
