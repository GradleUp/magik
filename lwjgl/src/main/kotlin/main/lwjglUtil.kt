package main

import main.Lwjgl.Module.*
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.internal.os.OperatingSystem.*
import org.gradle.kotlin.dsl.accessors.runtime.addExternalModuleDependencyTo

object Lwjgl {

    var group = "org.lwjgl"
    var version = "3.2.3"

    operator fun invoke(block: Lwjgl.() -> Unit) = Lwjgl.block()

    fun DependencyHandler.implementation(vararg modules: Module) = implementation(false, modules)
    fun DependencyHandler.testImplementation(vararg modules: Module) = implementation(true, modules)

    fun DependencyHandler.implementation(preset: Preset) = implementation(false, preset.modules)
    fun DependencyHandler.testImplementation(preset: Preset) = implementation(true, preset.modules)

    private fun DependencyHandler.implementation(test: Boolean, modules: Array<out Module>) {
        // core
        if(core !in modules)
            implementation(test, core)
        for (module in modules)
            implementation(test, module)
    }

    private fun DependencyHandler.implementation(test: Boolean, module: Module) {
        var config = if (test) "testImplementation" else "implementation"
        add(config, "$group:${module.artifact}")
        if (module.hasNative) {
            config = if (test) "testRuntimeOnly" else "runtimeOnly"
            addExternalModuleDependencyTo(this, config, group, module.artifact, version, null, natives, null, null)
        }
    }

    enum class Module(val hasNative: Boolean = true) {

        core,

        assimp,
        bgfx,
        bullet,
        cuda(false),
        driftfx,
        egl(false),
        glfw,
        jawt(false),
        jemalloc,
        libdivide,
        llvm,
        lmdb,
        lz4,
        meow,
        meshoptimizer,
        nanovg,
        nfd,
        nuklear,
        odbc(false),
        openal,
        opencl(false),
        opengl,
        opengles,
        openvr,
        opus,
        par,
        remotery,
        rpmalloc,
        shaderc,
        spvc,
        sse,
        stb,
        tinyexr,
        tinyfd,
        tootle,
        vma,
        vulkan(false),
        xxhash,
        yoga,
        zstd;

        val artifact: String
            get() = when(this) {
                core -> "lwjgl"
                else -> "lwjgl-$name"
            }
    }

    enum class Preset(val modules: Array<Module>) {
        none(emptyArray<Module>()),
        everything(Module.values()),
        gettingStarted(arrayOf(core, assimp, bgfx, glfw, nanovg, nuklear, openal, opengl, par, stb, vulkan)),
        minimalOpenGL(arrayOf(core, assimp, glfw, openal, opengl, stb)),
        minimalOpenGLES(arrayOf(core, assimp, egl, glfw, openal, opengles, stb)),
        minimalVulkan(arrayOf(core, assimp, glfw, openal, stb, vulkan))
    }

    val natives = "natives-" + when (current()) {
        WINDOWS -> "windows"
        LINUX -> "linux"
        else -> "macos"
    }
}