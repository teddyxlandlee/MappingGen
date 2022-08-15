import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.7.10"
    `maven-publish`
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "xland.ioutils"
version = "0.1-SNAPSHOT"

repositories {
    maven (url = "https://maven.aliyun.com/repository/public") {
        name = "Aliyun"
    }
    maven (url = "https://maven.fabricmc.net/")
    mavenCentral()
}

dependencies {
    shadow(implementation("net.fabricmc", "mapping-io", "0.3.0"))
    testImplementation(kotlin("test"))
    shadow(kotlin("stdlib-jdk8", "1.7.10"))
    //implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.3.3")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.jar {
    manifest.attributes(
        "Main-Class" to "xland.ioutils.mappinggen.MainKt"
    )
}

tasks.shadowJar {
    //configurations = mutableListOf(project.configurations.compileClasspath)
    archiveClassifier.set("fat")
    manifest.inheritFrom(tasks.jar.get().manifest)
    exclude("**/module-info.class")
}

publishing {
    this.publications {
        create<MavenPublication>("mavenJava") {
            from(components["kotlin"])
        }
    }
    repositories {
        mavenLocal()
    }
}
