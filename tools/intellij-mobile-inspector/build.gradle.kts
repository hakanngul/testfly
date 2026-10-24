
plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = "io.testfly"
version = "0.1.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.1.7")
    }
    testImplementation("junit:junit:4.13.2")
}

// IntelliJ 2024.1 runs on Java 17; any newer JDK can build it.
tasks.withType<JavaCompile> {
    options.release = 17
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }
        }
    }
    buildSearchableOptions = false
}

// The webview UI is shared with the VS Code extension: one design, one source.
tasks.processResources {
    from("../vscode-mobile-inspector/media") {
        include("body.html", "inspector.css", "inspector.js", "selectors.js")
        into("inspector")
    }
}
