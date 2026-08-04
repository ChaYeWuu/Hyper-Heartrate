plugins {
    id("net.fabricmc.fabric-loom") version "1.15.5"
}

group = "com.chayewuu.xiaomiheartrate"
version = "1.2.0_fabric-26.1"

base {
    archivesName.set("xiaomi-heartrate")
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net") }
}

dependencies {
    // Minecraft 26.1（非混淆，Loom 自动应用 Mojang 官方映射，无需手动声明 mappings）
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")

    // Fabric Loader
    implementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // JNA（BLE 后端用，Windows Bluetooth LE API 封装）
    implementation("net.java.dev.jna:jna:5.16.0")
    implementation("net.java.dev.jna:jna-platform:5.16.0")
}

tasks {
    processResources {
        // 替换 fabric.mod.json 中的占位符
        val props = mapOf(
            "version" to project.version,
            "minecraft_version" to project.property("minecraft_version"),
            "loader_version" to project.property("loader_version"),
            "fabric_version" to project.property("fabric_version")
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") {
            expand(props)
        }
    }

    withType<JavaCompile>().configureEach {
        options.release = 25
        options.encoding = "UTF-8"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
    withSourcesJar()
}
