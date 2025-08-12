package com.vyibc.autocrplugin.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.JBCheckBox
import com.vyibc.autocrplugin.service.impl.GoogleTranslationService
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * 翻译设置配置页面
 */
class TranslationConfigurable : Configurable {

    private var settingsPanel: JPanel? = null
    private lateinit var targetLanguageCombo: ComboBox<String>
    private lateinit var sourceLanguageCombo: ComboBox<String>
    private lateinit var showDialogCheckBox: JBCheckBox
    private lateinit var autoDetectCheckBox: JBCheckBox

    private val translationService = GoogleTranslationService()
    private val supportedLanguages = translationService.getSupportedLanguages()

    override fun getDisplayName(): String = "翻译设置"

    override fun createComponent(): JComponent? {
        if (settingsPanel == null) {
            settingsPanel = createSettingsPanel()
        }
        return settingsPanel
    }

    private fun createSettingsPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()

        // 目标语言设置
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.anchor = GridBagConstraints.WEST
        gbc.insets = Insets(5, 5, 5, 5)
        panel.add(JLabel("默认目标语言:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        targetLanguageCombo = ComboBox(supportedLanguages.keys.toTypedArray())
        targetLanguageCombo.renderer = LanguageComboBoxRenderer(supportedLanguages)
        panel.add(targetLanguageCombo, gbc)

        // 源语言设置
        gbc.gridx = 0
        gbc.gridy = 1
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        panel.add(JLabel("默认源语言:"), gbc)

        gbc.gridx = 1
        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.weightx = 1.0
        sourceLanguageCombo = ComboBox(supportedLanguages.keys.toTypedArray())
        sourceLanguageCombo.renderer = LanguageComboBoxRenderer(supportedLanguages)
        panel.add(sourceLanguageCombo, gbc)

        // 自动检测语言
        gbc.gridx = 0
        gbc.gridy = 2
        gbc.gridwidth = 2
        gbc.fill = GridBagConstraints.NONE
        gbc.weightx = 0.0
        autoDetectCheckBox = JBCheckBox("自动检测源语言")
        panel.add(autoDetectCheckBox, gbc)

        // 显示对话框设置
        gbc.gridy = 3
        showDialogCheckBox = JBCheckBox("显示翻译结果对话框")
        panel.add(showDialogCheckBox, gbc)

        // 添加监听器
        autoDetectCheckBox.addActionListener {
            sourceLanguageCombo.isEnabled = !autoDetectCheckBox.isSelected
        }

        return panel
    }

    override fun isModified(): Boolean {
        val settings = TranslationSettings.getInstance()
        return targetLanguageCombo.selectedItem != settings.defaultTargetLanguage ||
                sourceLanguageCombo.selectedItem != settings.defaultSourceLanguage ||
                showDialogCheckBox.isSelected != settings.showTranslationDialog ||
                autoDetectCheckBox.isSelected != settings.autoDetectLanguage
    }

    override fun apply() {
        val settings = TranslationSettings.getInstance()
        settings.defaultTargetLanguage = targetLanguageCombo.selectedItem as String
        settings.defaultSourceLanguage = sourceLanguageCombo.selectedItem as String
        settings.showTranslationDialog = showDialogCheckBox.isSelected
        settings.autoDetectLanguage = autoDetectCheckBox.isSelected
    }

    override fun reset() {
        val settings = TranslationSettings.getInstance()
        targetLanguageCombo.selectedItem = settings.defaultTargetLanguage
        sourceLanguageCombo.selectedItem = settings.defaultSourceLanguage
        showDialogCheckBox.isSelected = settings.showTranslationDialog
        autoDetectCheckBox.isSelected = settings.autoDetectLanguage
        sourceLanguageCombo.isEnabled = !settings.autoDetectLanguage
    }

    override fun disposeUIResources() {
        settingsPanel = null
    }

    /**
     * 语言下拉框渲染器
     */
    private class LanguageComboBoxRenderer(
        private val languageMap: Map<String, String>
    ) : javax.swing.DefaultListCellRenderer() {
        
        override fun getListCellRendererComponent(
            list: javax.swing.JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): java.awt.Component {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
            if (value is String) {
                text = "${languageMap[value]} ($value)"
            }
            return this
        }
    }
}
