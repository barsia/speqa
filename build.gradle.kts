plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "io.github.barsia"
version = "0.1.1"

val localProps = rootProject.file("local.properties")
    .takeIf { it.exists() }
    ?.readLines()
    ?.filter { it.contains('=') && !it.startsWith("#") }
    ?.associate { it.substringBefore('=').trim() to it.substringAfter('=').trim() }
    ?: emptyMap()

fun localProp(key: String): String = localProps[key] ?: ""

val localIdePath: String = localProp("ideaPath")

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    intellijPlatform {
        local(localIdePath)
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        // Add plugin dependencies for compilation here:

        composeUI()

        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.intellij.plugins.markdown")
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "253.32098.37"
            untilBuild = "263.*"
        }

        changeNotes = """
            <h3>0.1.1</h3>
            <ul>
                <li>Ticket linking in test cases and test runs. Attach ticket IDs to expected results, click to open in YouTrack, Linear, or your custom tracker</li>
                <li>Auto-continue numbered and bulleted lists on Enter in text fields in preview</li>
                <li>Rename selects only base name for <code>.tc.md</code> / <code>.tr.md</code></li>
                <li>Better hover feedback on verdict buttons and comment toggle in test runs</li>
                <li>Polished UI text across the board</li>
            </ul>
        """.trimIndent()
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    signing {
        certificateChain = providers.provider { localProp("certificateChain").ifEmpty { null } }
            .orElse(providers.environmentVariable("CERTIFICATE_CHAIN"))
        privateKey = providers.provider { localProp("privateKey").ifEmpty { null } }
            .orElse(providers.environmentVariable("PRIVATE_KEY"))
        password = providers.provider { localProp("privateKeyPassword").ifEmpty { null } }
            .orElse(providers.environmentVariable("PRIVATE_KEY_PASSWORD"))
    }
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }

}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
