plugins {
    // 1.21.x 使用混淆 remap Loom（与 26.x 的非混淆 Loom 不同）
    id("net.fabricmc.fabric-loom-remap") version "1.14.10"
}

group = "com.chayewuu.hyperheartrate"
version = "1.3.0_fabric-1.21.6-1.21.10"

base {
    archivesName.set("hyper-heartrate")
}

repositories {
    // jna 与 asm 优先走阿里云（fabricmc 仓库常因 SSL Connection reset 失败）
    exclusiveContent {
        forRepository {
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        filter {
            includeGroup("net.java.dev.jna")
            includeGroup("org.ow2.asm")
        }
    }
    mavenCentral()
    maven { url = uri("https://maven.fabricmc.net") }
}

dependencies {
    // Minecraft 1.21.6-1.21.10（混淆，需要 Yarn 映射）
    minecraft("com.mojang:minecraft:${property("minecraft_version")}")
    mappings("net.fabricmc:yarn:${property("yarn_mappings")}:v2")

    // Fabric Loader
    modImplementation("net.fabricmc:fabric-loader:${property("loader_version")}")

    // Fabric API
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_version")}")

    // JNA（BLE 后端用，Windows Bluetooth LE API 封装）
    implementation("net.java.dev.jna:jna:5.16.0")
    implementation("net.java.dev.jna:jna-platform:5.16.0")
}

tasks {
    processResources {
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
        options.release = 21
        options.encoding = "UTF-8"
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    withSourcesJar()
}
