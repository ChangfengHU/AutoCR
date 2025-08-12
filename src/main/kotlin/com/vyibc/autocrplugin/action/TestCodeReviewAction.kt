package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.vyibc.autocrplugin.service.impl.AICodeReviewService
import com.vyibc.autocrplugin.ui.CodeReviewProcessDialog

/**
 * 测试代码评估功能的Action（使用模拟数据）
 */
class TestCodeReviewAction : AnAction("Test Code Review") {

    private val codeReviewService = AICodeReviewService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 使用简单的模拟数据进行测试
        val mockChanges = listOf(
            com.vyibc.autocrplugin.service.CodeChange(
                filePath = "src/main/kotlin/Example.kt",
                changeType = com.vyibc.autocrplugin.service.ChangeType.MODIFIED,
                oldContent = "class Example { fun old() {} }",
                newContent = "class Example { fun new() {} }",
                addedLines = listOf("fun new() {}"),
                removedLines = listOf("fun old() {}"),
                modifiedLines = emptyList()
            )
        )
        val mockCommitMessage = "feat: 示例提交\n\n- 新增方法 new()\n- 移除方法 old()"
        
        val processDialog = CodeReviewProcessDialog(project, mockChanges, mockCommitMessage)
        processDialog.setCodeReviewService(codeReviewService)
        processDialog.showAndGet()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
        e.presentation.text = "Test Code Review"
        e.presentation.description = "使用模拟数据测试AI代码评估"
    }
}
