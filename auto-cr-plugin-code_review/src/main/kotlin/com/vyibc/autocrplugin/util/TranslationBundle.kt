package com.vyibc.autocrplugin.util

import com.intellij.DynamicBundle
import org.jetbrains.annotations.PropertyKey

/**
 * 翻译插件国际化消息工具类
 */
object TranslationBundle : DynamicBundle("messages.TranslationBundle") {

    fun message(@PropertyKey(resourceBundle = "messages.TranslationBundle") key: String, vararg params: Any): String {
        return getMessage(key, *params)
    }
}
