plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.12.0"
}

group = "io.github.barsia"
version = "0.1.9"

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
            <h3>0.1.9</h3>
            <ul>
                <li>The overall run result is now a dropdown for single and multi-case runs, with a manual override and a per-case result you can set yourself</li>
                <li>Reset all results in a run, and expand or collapse every case, in one action</li>
                <li>The Create Test Run dialog defaults its import options off and toggles a case when you click anywhere in its row</li>
                <li>Test case files now place Links before Preconditions</li>
                <li>Resolve Duplicate Test Case IDs now lists every file that shares an ID and marks the one that keeps it</li>
                <li>The preview is now fully keyboard-navigable: <code>Tab</code> moves between fields, and tag chips and link, attachment, and ticket rows are single <code>Tab</code> stops where <code>Delete</code> removes, <code>F2</code> edits, and <code>Enter</code> activates</li>
                <li>A thin focus ring shows on keyboard focus and after keyboard-driven actions, never on mouse clicks</li>
                <li>The step drag handle is keyboard-operable: <code>Space</code>, <code>Enter</code>, or a left-click opens its Move, Duplicate, and Delete menu</li>
                <li>Keyboard focus returns to where you were after closing a dialog or popup or removing an item</li>
                <li>Fixed the preview and editor getting out of sync after undoing an added step</li>
                <li>Fixed several test case and run title editing issues, including the caret jumping and blank titles being saved</li>
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
        // The test-case-writer skill lives as a real project skill under
        // .claude/skills, and is bundled into the jar as a template so the
        // new-project wizard (SpeqaProjectScaffold.installSkill) can install it
        // into freshly scaffolded user projects.
        from(".claude/skills/speqa-test-cases/SKILL.md") {
            into("templates")
            rename { "speqa-test-cases-skill.md" }
        }
        // The bundled starter (web-login example) is kept as a ".tc.md.template" so
        // SpeQA does not index it in this repo; it is bundled into the jar as a real
        // ".tc.md" that the wizard and the SpeqaProjectScaffoldTest guard read as
        // templates/test-cases/login-happy-path.tc.md.
        from("templates/test-cases/login-happy-path.tc.md.template") {
            into("templates/test-cases")
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
