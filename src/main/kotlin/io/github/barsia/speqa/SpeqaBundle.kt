package io.github.barsia.speqa

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.PropertyKey

private const val BUNDLE = "messages.SpeqaBundle"

object SpeqaBundle {
    private val instance = DynamicBundle(SpeqaBundle::class.java, BUNDLE)

    @JvmStatic
    fun message(@PropertyKey(resourceBundle = BUNDLE) key: String, vararg params: Any?): @Nls String {
        return instance.getMessage(key, *params)
    }
}
