plugins {
    id("com.gradleup.shadow") version "9.6.1"
    id("application")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.github.jwharm.javagi:gtk:0.12.2")
    implementation("io.github.jwharm.javagi:gsk:0.12.2")
    implementation("io.github.jwharm.javagi:graphene:0.12.2")
}

application {
    mainClass = "org.coffinwood.gtklondike.GTKlondike"
}

// compiles src/main/resources/schemas/*.gschema.xml into the same directory
// processResources already stages, so GSettings can find it on the runtime classpath
tasks.register<Exec>("compileGSettingsSchemas") {
    dependsOn("processResources")
    val schemaDir = layout.buildDirectory.dir("resources/main/schemas")
    inputs.dir("src/main/resources/schemas")
    outputs.file(schemaDir.map { it.file("gschemas.compiled") })
    doFirst { schemaDir.get().asFile.mkdirs() }
    commandLine("glib-compile-schemas", "src/main/resources/schemas",
        "--targetdir", schemaDir.get().asFile.absolutePath)
}

tasks.named("classes") {
    dependsOn("compileGSettingsSchemas")
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "org.coffinwood.gtklondike.GTKlondike")
    }
}