plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.3.0"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.3.0"
    id("org.jetbrains.intellij.platform") version "2.12.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

group = "io.github.barsia"
version = providers.gradleProperty("pluginVersion").getOrElse("0.0.0-dev")

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
    compileOnly("net.java.dev.jna:jna:5.14.0")
    intellijPlatform {
        if (localIdePath.isNotBlank()) {
            local(localIdePath)
        } else {
            // CI and any other build without a local IDE: download IntelliJ IDEA
            // matching `sinceBuild` (build 253.* = 2025.3). Since 2025.3, JetBrains
            // publishes a single unified IDEA artifact (no separate IC); the platform
            // plugin recommends `intellijIdea(...)` over `create("IC", ...)`.
            intellijIdea("2026.1")
        }
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
            <h3>0.1.3</h3>
            <ul>
                <li>Preview rewritten from Swing to a WebView: crisper rendering, native typography, theme-aware UI throughout</li>
                <li>Rich text formatting in text fields: selection toolbar (Bold / Italic / Strike / Code / Link), inline link rendering with hover popup, smart URL paste</li>
                <li>Code blocks: themed background, syntax highlighting, copy-to-clipboard button on hover with success feedback</li>
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

    val winBridgeDir = layout.projectDirectory.dir("native/WinWebView2Bridge")
    val winBridgeDll = winBridgeDir.dir("target/release").file("win_webview2_bridge.dll")
    val isWindowsHost = org.gradle.internal.os.OperatingSystem.current().isWindows
    val skipNativeBuild = providers.gradleProperty("skipNativeBuild")
        .map { it.toBoolean() }
        .orElse(false)

    fun cargoOnPath(): Boolean {
        val exe = if (isWindowsHost) "cargo.exe" else "cargo"
        return (System.getenv("PATH") ?: "").split(File.pathSeparator).any { dir ->
            dir.isNotBlank() && File(dir, exe).canExecute()
        }
    }

    val buildWinWebView2Bridge by registering(Exec::class) {
        group = "speqa native"
        description = "Compile the Windows WebView2 bridge DLL via cargo (Windows only)."
        onlyIf {
            if (skipNativeBuild.get()) {
                logger.lifecycle("Skipping buildWinWebView2Bridge: -PskipNativeBuild=true")
                return@onlyIf false
            }
            if (!isWindowsHost) return@onlyIf false
            if (!winBridgeDir.file("Cargo.toml").asFile.isFile) {
                logger.lifecycle("Skipping buildWinWebView2Bridge: native/WinWebView2Bridge/Cargo.toml is missing")
                return@onlyIf false
            }
            if (!cargoOnPath()) {
                logger.lifecycle("Skipping buildWinWebView2Bridge: 'cargo' not on PATH (install Rust via https://rustup.rs/ to enable the native bridge build)")
                return@onlyIf false
            }
            true
        }
        workingDir = winBridgeDir.asFile
        commandLine("cargo", "build", "--release")
        inputs.file(winBridgeDir.file("Cargo.toml"))
        inputs.file(winBridgeDir.file("Cargo.lock"))
        inputs.dir(winBridgeDir.dir("src"))
        outputs.file(winBridgeDll)
    }

    processResources {
        if (isWindowsHost) dependsOn(buildWinWebView2Bridge)
        from(winBridgeDll.asFile) {
            into("native/windows")
            rename { "WinWebView2Bridge.dll" }
        }
    }

    // intellij-platform-gradle-plugin 2.12.0 always attaches the Compose Hot Reload
    // javaagent to runIde, even when composeHotReload=false (default). The alpha agent
    // (hot-reload-agent-1.1.0-alpha03) crashes JBR 261 with ClassCircularityError on
    // jdk/internal/vm/ContinuationSupport during premain. Strip the provider.
    named<JavaExec>("runIde") {
        jvmArgumentProviders.removeAll {
            it::class.java.name == "org.jetbrains.intellij.platform.gradle.argumentProviders.ComposeHotReloadArgumentProvider"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

kover {
    reports {
        filters {
            excludes {
                // Native bridge wrappers need OS-level smoke tests; unit coverage here would be artificial.
                classes(
                    "io.github.barsia.speqa.webview.internal.mac.WKWebViewBridge*",
                    "io.github.barsia.speqa.webview.internal.windows.WinWebView2Bridge*",
                    "io.github.barsia.speqa.webview.internal.windows.WindowsHwndUtil*",
                    "io.github.barsia.speqa.webview.internal.MacMainThreadDispatcher*",
                    "io.github.barsia.speqa.webview.internal.NativeLibraryLoader*",
                )
            }
        }
    }
}
