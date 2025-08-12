package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.vyibc.autocrplugin.service.impl.AICodeReviewService
import com.vyibc.autocrplugin.ui.CodeReviewProcessDialog
import com.vyibc.autocrplugin.util.CodeReviewUtils

/**
 * 测试代码评估功能的Action
 * 用于演示和测试代码评估流程
 */
class TestCodeReviewAction : AnAction("Test Code Review") {

    private val codeReviewService = AICodeReviewService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 使用模拟数据进行测试
        val mockChanges = CodeReviewUtils.createMockCodeChanges()
        val mockCommitMessage = "feat: 添加用户数据处理功能\n\n- 新增用户数据获取方法\n- 优化数据处理逻辑\n- 添加错误处理机制"
        
        // 显示代码评估过程对话框
        val processDialog = CodeReviewProcessDialog(project, mockChanges, mockCommitMessage)
        
        // 开始评估过程
        processDialog.startReview(codeReviewService) { canCommit, reviewResult ->
            if (canCommit && reviewResult != null) {
                // 代码质量符合要求
                processDialog.setCommitEnabled(true)
                
                val dialogResult = processDialog.showAndGet()
                if (dialogResult) {
                    // 模拟提交成功
                    com.intellij.openapi.ui.Messages.showInfoMessage(
                        "测试提交成功！\n评分: ${reviewResult.overallScore}/100\n风险等级: ${reviewResult.riskLevel}",
                        "测试提交完成"
                    )
                }
            } else {
                // 代码质量不符合要求
                processDialog.setCommitEnabled(false)
                processDialog.showAndGet()
                
                val message = if (reviewResult != null) {
                    "测试评估完成，但代码质量不符合要求：\n" +
                            "评分: ${reviewResult.overallScore}/100\n" +
                            "风险等级: ${reviewResult.riskLevel}\n" +
                            "问题数量: ${reviewResult.issues.size}"
                } else {
                    "代码评估失败，无法获取评估结果"
                }
                
                com.intellij.openapi.ui.Messages.showWarningDialog(
                    message,
                    "测试评估结果"
                )
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
        e.presentation.text = "Test Code Review"
        e.presentation.description = "测试代码评估功能（使用模拟数据）"
    }
}
