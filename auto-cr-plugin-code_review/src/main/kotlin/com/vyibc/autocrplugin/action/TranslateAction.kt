package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.ui.Messages
import com.vyibc.autocrplugin.service.impl.GoogleTranslationService
import com.vyibc.autocrplugin.settings.TranslationSettings
import com.vyibc.autocrplugin.ui.TranslationDialog
import com.vyibc.autocrplugin.util.TranslationBundle
import kotlinx.coroutines.runBlocking

/**
 * 翻译Action
 */
class TranslateAction : AnAction() {

    private val translationService = GoogleTranslationService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        val editor = e.getData(CommonDataKeys.EDITOR)
        
        if (editor == null) {
            Messages.showErrorDialog(
                TranslationBundle.message("error.no.editor"),
                TranslationBundle.message("action.translate.text")
            )
            return
        }

        val selectionModel = editor.selectionModel
        val selectedText = selectionModel.selectedText

        if (selectedText.isNullOrBlank()) {
            Messages.showErrorDialog(
                TranslationBundle.message("error.no.selection"),
                TranslationBundle.message("action.translate.text")
            )
            return
        }

        val settings = TranslationSettings.getInstance()
        
        // 在后台线程中执行翻译
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在翻译...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "正在翻译文本..."
                    indicator.isIndeterminate = true
                    
                    val result = runBlocking {
                        translationService.translate(
                            text = selectedText,
                            targetLanguage = settings.defaultTargetLanguage,
                            sourceLanguage = if (settings.autoDetectLanguage) null else settings.defaultSourceLanguage
                        )
                    }
                    
                    // 在EDT线程中显示结果
                    ApplicationManager.getApplication().invokeLater {
                        if (settings.showTranslationDialog) {
                            val dialog = TranslationDialog(project, result)
                            dialog.show()
                        } else {
                            Messages.showInfoMessage(
                                "原文: ${result.originalText}\n\n译文: ${result.translatedText}",
                                "翻译结果"
                            )
                        }
                    }
                    
                } catch (e: Exception) {
                    ApplicationManager.getApplication().invokeLater {
                        Messages.showErrorDialog(
                            "翻译失败: ${e.message}",
                            "翻译错误"
                        )
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val editor = e.getData(CommonDataKeys.EDITOR)
        val hasSelection = editor?.selectionModel?.hasSelection() == true
        e.presentation.isEnabledAndVisible = hasSelection
    }
}
