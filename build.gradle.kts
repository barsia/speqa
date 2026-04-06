plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.12.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.0"
}

group = "io.github.barsia"
version = "0.1.0"

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
            <h3>0.1.0 — Initial Release</h3>
            <ul>
                <li>Split editor for <code>.tc.md</code> files — native text editor + interactive preview</li>
                <li>YAML frontmatter: id, title, priority, status, environment, tags</li>
                <li>Body blocks: description, preconditions (ordered, round-trip safe)</li>
                <li>Step-by-step editing with action, expected result, and attachments</li>
                <li>Test run execution with <code>.tr.md</code> files and pass/fail/blocked verdicts</li>
                <li>External links support with add/edit dialog and URL validation</li>
                <li>File attachments with drag-drop, missing file detection, and relink</li>
                <li>Targeted document patching — preserves user formatting</li>
                <li>Tag/environment autocomplete from project-wide registry</li>
                <li>Status-colored file icons in project view (draft/ready/deprecated)</li>
                <li>Resilient YAML parsing — broken fields don't crash the preview</li>
                <li>JSON Schema for frontmatter validation</li>
                <li>Soft validation warnings for incomplete test cases</li>
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
