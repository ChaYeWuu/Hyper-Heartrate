pluginManagement {
    repositories {
        // 国内镜像优先（解决 SSL Connection reset 问题）
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.fabricmc.net") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://maven.fabricmc.net") }
        mavenCentral()
    }
}

rootProject.name = "hyper-heartrate-1-21-1-1-21-3"
