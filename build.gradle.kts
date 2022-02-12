import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.yaml.snakeyaml.Yaml
import java.io.FileInputStream

buildscript {
    repositories {
        mavenCentral()
        maven(url = "https://papermc.io/repo/repository/maven-public/")
        maven("https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath("org.yaml:snakeyaml:1.26")
        classpath("org.jlleitschuh.gradle:ktlint-gradle:10.1.0")
    }
}

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.6.0"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    id("org.jlleitschuh.gradle.ktlint") version "10.1.0"
}

group = "org.kraftwerk28"

val cfg: Map<String, String> = Yaml()
    .load(FileInputStream("$projectDir/src/main/resources/plugin.yml"))
val pluginVersion = cfg["version"]
val paperApiVersion = cfg["api-version"]
val retrofitVersion = "2.9.0"
version = pluginVersion as Any

repositories {
    mavenCentral()
    maven(url = "https://papermc.io/repo/repository/maven-public/")
    maven(url = "https://oss.sonatype.org/content/groups/public/")
    maven(url = "https://jitpack.io")

}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:4.9.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.0")
    implementation("org.yaml:snakeyaml:1.30")
    compileOnly("io.papermc.paper:paper-api:1.18.1-R0.1-SNAPSHOT")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7")
}

defaultTasks("shadowJar")

java {
    targetCompatibility = JavaVersion.VERSION_17
    sourceCompatibility = JavaVersion.VERSION_17
}

tasks {
    named<ShadowJar>("shadowJar") {
        archiveFileName.set(
            "spigot-tg-bridge-$paperApiVersion-v$pluginVersion.jar"
        )
    }
    register<Copy>("copyArtifacts") {
        val dest = File(
            System.getProperty("user.home"),
            "MinecraftServers/spigot_1.18/plugins/",
        )
        from(shadowJar)
        into(dest)
    }
    register("pack") {
        description = "[For development only!] Build project and copy .jar into servers directory"
        dependsOn("shadowJar")
        finalizedBy("copyArtifacts")
    }
//    withType<KotlinCompile> {
//        kotlinOptions {
//            jvmTarget = "15"
//        }
//    }
}
