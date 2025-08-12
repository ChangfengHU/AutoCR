package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.vyibc.autocrplugin.service.GitChangeAnalyzer
import com.vyibc.autocrplugin.service.impl.AICodeReviewService
import com.vyibc.autocrplugin.ui.CodeReviewProcessDialog

/**
 * 右键菜单快速代码评估Action
 */
class QuickCodeReviewMenuAction : AnAction("Code Review") {

    private val codeReviewService = AICodeReviewService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // 检查是否有代码变更
        val changeListManager = ChangeListManager.getInstance(project)
        val defaultChangeList = changeListManager.defaultChangeList

        if (defaultChangeList.changes.isEmpty()) {
            // 如果没有暂存的变更，使用测试数据
            showTestReview(project)
            return
        }

        // 分析代码变更
        val gitAnalyzer = GitChangeAnalyzer(project)
        val changes = gitAnalyzer.getCurrentChanges()

        if (changes.isEmpty()) {
            showTestReview(project)
            return
        }

        val commitMessage = "🔍 AI代码评估准备\n文件数量: ${changes.size}\n状态: 等待用户确认开始分析..."

        // 显示代码评估过程对话框，但不自动开始分析
        val processDialog = CodeReviewProcessDialog(project, changes, commitMessage)

        // 设置代码评估服务
        processDialog.setCodeReviewService(codeReviewService)

        // 显示对话框，让用户查看要分析的代码，然后手动点击"开始分析"
        processDialog.showAndGet()
    }

    /**
     * 显示测试评估（当没有实际代码变更时）
     */
    private fun showTestReview(project: Project) {
        val choice = Messages.showYesNoDialog(
            project,
            "📝 当前没有代码变更\n\n" +
                    "是否使用模拟数据进行AI代码评估测试？\n\n" +
                    "测试功能将:\n" +
                    "• 使用示例代码数据\n" +
                    "• 演示完整的AI分析过程\n" +
                    "• 展示评估结果格式\n\n" +
                    "⚠️ 测试功能也会调用真实的AI服务",
            "AI代码评估测试",
            "开始测试",
            "取消",
            Messages.getQuestionIcon()
        )

        if (choice == Messages.YES) {
            // 调用测试功能
            val testAction = com.vyibc.autocrplugin.action.TestCodeReviewAction()

            // 创建一个包含project的DataContext
            val dataContext = object : com.intellij.openapi.actionSystem.DataContext {
                override fun getData(dataId: String): Any? {
                    return when (dataId) {
                        com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT.name -> project
                        else -> null
                    }
                }
            }

            val mockEvent = AnActionEvent(
                null,
                dataContext,
                "",
                com.intellij.openapi.actionSystem.Presentation(),
                com.intellij.openapi.actionSystem.ActionManager.getInstance(),
                0
            )
            testAction.actionPerformed(mockEvent)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        // 总是显示，让用户可以随时进行AI代码评估
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = "Code Review"
        e.presentation.description = "使用AI对代码进行智能评估"

        // 根据是否有变更来调整描述
        val changeListManager = ChangeListManager.getInstance(project)
        val hasChanges = changeListManager.defaultChangeList.changes.isNotEmpty()

        if (hasChanges) {
            val changeCount = changeListManager.defaultChangeList.changes.size
            e.presentation.description = "AI评估 $changeCount 个文件的变更（需要网络连接）"
        } else {
            e.presentation.description = "AI代码评估测试（使用模拟数据）"
        }
    }
}
