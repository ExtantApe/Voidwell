plugins {
    java
}

group = "com.extantape"
version = "0.1.2"

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

allprojects {
    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        // OneBlock is published on Modrinth, and Modrinth serves its files as a Maven repository.
        exclusiveContent {
            forRepository { maven("https://api.modrinth.com/maven") }
            filter { includeGroup("maven.modrinth") }
        }
    }
}

// Built against the oldest supported Paper, so nothing newer than 26.1 sneaks in.
val paperApi = "io.papermc.paper:paper-api:26.1.2.build.74-stable"
extra["paperApi"] = paperApi

dependencies {
    compileOnly(paperApi)
    // MrMarL's OneBlock 1.6.4 (MIT). Only its public static getters are read; islands are made with its own command.
    compileOnly("maven.modrinth:oneblock_bukkit:1.6.4") { isTransitive = false }
    // The hand-over event and landing service. Switches to the Waystation 0.4.0 jar once it's released.
    compileOnly(project(":waystation-standin"))

    testImplementation(paperApi)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(25)
    options.compilerArgs.addAll(listOf("-Xlint:deprecation", "-Xlint:removal"))
}

tasks.processResources {
    val props = mapOf("version" to project.version)
    inputs.properties(props)
    filesMatching("paper-plugin.yml") { expand(props) }
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveBaseName.set("Voidwell")
    from("LICENSE")
}
