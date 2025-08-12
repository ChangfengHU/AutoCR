package com.vyibc.autocrplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 翻译设置状态类
 */
@State(
    name = "TranslationSettings",
    storages = [Storage("TranslationSettings.xml")]
)
class TranslationSettings : PersistentStateComponent<TranslationSettings> {

    var defaultTargetLanguage: String = "zh"
    var defaultSourceLanguage: String = "auto"
    var translationService: String = "Google Translate"
    var showTranslationDialog: Boolean = true
    var autoDetectLanguage: Boolean = true

    companion object {
        fun getInstance(): TranslationSettings {
            return ApplicationManager.getApplication().getService(TranslationSettings::class.java)
        }
    }

    override fun getState(): TranslationSettings = this

    override fun loadState(state: TranslationSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
