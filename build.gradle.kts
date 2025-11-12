plugins {
    id("java")
    id("io.github.patrick.remapper") version "1.4.2"
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

group = "anvil.fix"
version = "2.1"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}




//project.layout.buildDirectory.set(File("C:\\Users\\Kubia\\Desktop\\Server\\plugins"))


repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.io/repository/maven-releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.8-R0.1-SNAPSHOT")
    compileOnly("com.github.retrooper:packetevents-spigot:2.4.0")
    //compileOnly("com.github.retrooper.packetevents:spigot:2.3.0")
}