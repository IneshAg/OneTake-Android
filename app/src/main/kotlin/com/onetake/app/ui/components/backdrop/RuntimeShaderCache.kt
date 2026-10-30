/*
 * Vendored from Kyant0/backdrop v2.0.0 (io.github.kyant0:backdrop)
 * https://github.com/Kyant0/backdrop — Copyright 2025 Kyant0, Apache License 2.0
 *
 * Vendored so the library ships as source with this app (binary AARs compiled
 * against older Compose broke at runtime) and to add a backdrop resolution
 * scale for cheaper effect rendering. KMP expect/actual declarations were
 * merged into this single Android source set. Package renamed accordingly.
 */
package com.onetake.app.ui.components.backdrop

import android.os.Build
import androidx.compose.ui.graphics.Color
import org.intellij.lang.annotations.Language

sealed interface RuntimeShaderCache {

    fun obtainRuntimeShader(key: String, @Language("AGSL") string: String): RuntimeShader
}

private object NoOpRuntimeShader : RuntimeShader {
    override fun setFloatUniform(name: String, value: Float) {}
    override fun setFloatUniform(name: String, value1: Float, value2: Float) {}
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float) {}
    override fun setFloatUniform(name: String, value1: Float, value2: Float, value3: Float, value4: Float) {}
    override fun setFloatUniform(name: String, values: FloatArray) {}
    override fun setIntUniform(name: String, value: Int) {}
    override fun setIntUniform(name: String, value1: Int, value2: Int) {}
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int) {}
    override fun setIntUniform(name: String, value1: Int, value2: Int, value3: Int, value4: Int) {}
    override fun setIntUniform(name: String, values: IntArray) {}
    override fun setColorUniform(name: String, color: Color) {}
}

internal class RuntimeShaderCacheImpl : RuntimeShaderCache {

    private val runtimeShaders = mutableMapOf<String, RuntimeShader>()

    override fun obtainRuntimeShader(key: String, string: String): RuntimeShader {
        return runtimeShaders.getOrPut(key) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                RuntimeShader(string)
            } else {
                NoOpRuntimeShader
            }
        }
    }

    fun clear() {
        runtimeShaders.clear()
    }
}
