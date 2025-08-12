package com.vyibc.autocrplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.vyibc.autocrplugin.service.TranslationResult
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

/**
 * 翻译结果对话框
 */
class TranslationDialog(
    project: Project?,
    private val translationResult: TranslationResult
) : DialogWrapper(project) {

    private lateinit var originalTextArea: JBTextArea
    private lateinit var translatedTextArea: JBTextArea
    private lateinit var infoLabel: JLabel

    init {
        title = "翻译结果"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)

        // 创建信息标签
        infoLabel = JLabel(
            "服务: ${translationResult.service} | " +
                    "源语言: ${translationResult.sourceLanguage} → " +
                    "目标语言: ${translationResult.targetLanguage}"
        )
        panel.add(infoLabel, BorderLayout.NORTH)

        // 创建文本区域
        val textPanel = JPanel(BorderLayout())
        
        // 原文区域
        originalTextArea = JBTextArea(translationResult.originalText)
        originalTextArea.isEditable = false
        originalTextArea.lineWrap = true
        originalTextArea.wrapStyleWord = true
        val originalScrollPane = JBScrollPane(originalTextArea)
        originalScrollPane.preferredSize = Dimension(580, 150)
        
        // 译文区域
        translatedTextArea = JBTextArea(translationResult.translatedText)
        translatedTextArea.isEditable = false
        translatedTextArea.lineWrap = true
        translatedTextArea.wrapStyleWord = true
        val translatedScrollPane = JBScrollPane(translatedTextArea)
        translatedScrollPane.preferredSize = Dimension(580, 150)

        // 添加标签和文本区域
        val originalPanel = JPanel(BorderLayout())
        originalPanel.add(JLabel("原文:"), BorderLayout.NORTH)
        originalPanel.add(originalScrollPane, BorderLayout.CENTER)
        
        val translatedPanel = JPanel(BorderLayout())
        translatedPanel.add(JLabel("译文:"), BorderLayout.NORTH)
        translatedPanel.add(translatedScrollPane, BorderLayout.CENTER)

        textPanel.add(originalPanel, BorderLayout.NORTH)
        textPanel.add(translatedPanel, BorderLayout.SOUTH)
        
        panel.add(textPanel, BorderLayout.CENTER)

        return panel
    }

    override fun createActions(): Array<Action> {
        val copyAction = object : AbstractAction("复制译文") {
            override fun actionPerformed(e: java.awt.event.ActionEvent?) {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(translationResult.translatedText), null)
            }
        }
        
        return arrayOf(copyAction, okAction)
    }
}
