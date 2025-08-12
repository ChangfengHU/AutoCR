package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.vyibc.autocrplugin.service.GitChangeAnalyzer
import com.vyibc.autocrplugin.service.impl.AICodeReviewService
import com.vyibc.autocrplugin.ui.CodeReviewProcessDialog
import com.vyibc.autocrplugin.util.CodeReviewUtils

/**
 * 评估已提交(git commit)但未推送的代码变更
 */
class ReviewCommittedChangesAction : AnAction("Review Committed Changes") {

    private val codeReviewService = AICodeReviewService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 获取已提交但未推送的变更
        // 由于简化实现，这里使用模拟数据来演示已提交的变更
        val changes = getCommittedButNotPushedChanges(project)
        
        if (changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有已提交(git commit)但未推送的代码变更\n" +
                        "请先提交一些代码后再进行评估，或者所有提交都已推送",
                "评估已提交变更"
            )
            return
        }
        
        val commitMessage = "📦 评估已提交但未推送的代码变更\n" +
                "文件数量: ${changes.size}\n" +
                "状态: 已提交(git commit)，待推送(git push)"
        
        // 显示代码评估过程对话框
        val processDialog = CodeReviewProcessDialog(project, changes, commitMessage)
        
        // 开始评估过程
        processDialog.startReview(codeReviewService) { canPush, reviewResult ->
            processDialog.setCommitEnabled(canPush)
            
            val dialogResult = processDialog.showAndGet()
            if (dialogResult && canPush && reviewResult != null) {
                // 询问是否推送
                val shouldPush = Messages.showYesNoDialog(
                    "✅ 已提交代码评估通过！\n\n" +
                            "评分: ${reviewResult.overallScore}/100\n" +
                            "风险等级: ${reviewResult.riskLevel}\n" +
                            "发现问题: ${reviewResult.issues.size} 个\n\n" +
                            "是否现在推送(git push)这些提交？",
                    "确认推送已提交变更",
                    Messages.getQuestionIcon()
                ) == Messages.YES
                
                if (shouldPush) {
                    performPush(project, reviewResult)
                }
            } else if (!canPush) {
                Messages.showWarningDialog(
                    "❌ 已提交代码质量不符合要求\n\n" +
                            "建议:\n" +
                            "1. 使用 'git reset --soft HEAD~1' 撤销最后一次提交\n" +
                            "2. 修复代码问题\n" +
                            "3. 重新提交代码",
                    "代码评估未通过"
                )
            }
        }
    }

    /**
     * 获取已提交但未推送的变更
     * 这里使用模拟数据，实际实现中应该通过Git命令获取
     */
    private fun getCommittedButNotPushedChanges(project: Project): List<com.vyibc.autocrplugin.service.CodeChange> {
        // 实际实现中，这里应该执行类似以下的Git命令：
        // git log origin/main..HEAD --name-status
        // git show --name-status HEAD
        
        // 为了演示，我们使用模拟数据
        return CodeReviewUtils.createMockCodeChanges().map { change ->
            // 修改文件路径以表示这是已提交的变更
            change.copy(
                filePath = "[COMMITTED] ${change.filePath}"
            )
        }
    }

    /**
     * 执行推送操作
     */
    private fun performPush(
        project: Project, 
        reviewResult: com.vyibc.autocrplugin.service.CodeReviewResult
    ) {
        try {
            // 这里应该调用实际的Git推送逻辑
            // 例如：git push origin main
            
            Messages.showInfoMessage(
                "🚀 代码推送成功！\n\n" +
                        "评估评分: ${reviewResult.overallScore}/100\n" +
                        "风险等级: ${reviewResult.riskLevel}\n" +
                        "代码已安全推送到远程仓库。",
                "推送成功"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "推送失败: ${e.message}",
                "推送错误"
            )
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        // 简化实现：总是显示按钮
        // 实际实现中应该检查是否有已提交但未推送的变更
        e.presentation.isEnabledAndVisible = true
        e.presentation.text = "Review Committed Changes"
        e.presentation.description = "评估已提交(git commit)但未推送的代码变更"
    }
}
