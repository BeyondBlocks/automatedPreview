plugins {
    id("java")
    id("application")
    id("com.gradleup.shadow") version("8.3.0")
}

group = "de.beyondblocks"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.lemaik.de/")
}

dependencies {
    implementation("se.llbit:chunky-core:2.4.6")
    implementation("org.json:json:20240303")
}

application {
    mainClass.set("de.beyondblocks.automatedPreview.AutomatedPreview")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.shadowJar {
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.named<JavaExec>("run") {
    workingDir = projectDir.resolve("run")
    workingDir.mkdir()
}