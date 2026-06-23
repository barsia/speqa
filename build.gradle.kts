plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.barsia"
version = "0.1.7"

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

        bundledPlugin("com.intellij.modules.json")
        bundledPlugin("org.jetbrains.plugins.yaml")
        bundledPlugin("org.intellij.plugins.markdown")
        bundledPlugin("Git4Idea")
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
            <h3>0.1.7</h3>
            <ul>
                <li>Soft wrap is now enabled by default in the Markdown editor for test cases and test runs</li>
                <li>Fixed keyboard handling when editing expected results in the Markdown editor</li>
                <li>Removing a tag, link, attachment, or environment value from the preview now takes effect immediately</li>
                <li>Deleting the last tag or environment value now removes the field from the file cleanly</li>
                <li>Fixed selecting text inside a code block</li>
                <li>Fixed backspace on empty lines inside code blocks</li>
                <li>Hover over a code block to reveal a copy button</li>
                <li>Removing a link no longer asks for confirmation</li>
                <li>Fixed scroll sync losing alignment on documents with code blocks or long expected results</li>
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

    // Bake the plugin version into a resource so runtime code reads it without
    // depending on internal IntelliJ Platform plugin-lookup APIs.
    processResources {
        val pluginVersion = project.version.toString()
        inputs.property("pluginVersion", pluginVersion)
        filesMatching("speqa-plugin.properties") {
            expand(mapOf("version" to pluginVersion))
        }
    }

    // The sandbox IDE defaults to -Xmx2048m, which is not enough to index a large
    // project opened in the sandbox (heap exhaustion during indexing). Double it.
    runIde {
        maxHeapSize = "4g"
    }

    // buildSearchableOptions launches a headless IDE that also defaults to -Xmx2048m;
    // on a memory-pressured machine that OOMs. Give it the same 4g as runIde.
    buildSearchableOptions {
        maxHeapSize = "4g"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}
