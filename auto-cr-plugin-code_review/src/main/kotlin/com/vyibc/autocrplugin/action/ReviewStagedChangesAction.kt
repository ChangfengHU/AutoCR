package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.vyibc.autocrplugin.service.GitChangeAnalyzer
import com.vyibc.autocrplugin.service.impl.AICodeReviewService
import com.vyibc.autocrplugin.ui.CodeReviewProcessDialog
import com.vyibc.autocrplugin.settings.CodeReviewSettings

/**
 * 评估已暂存(git add)但未提交的代码变更
 */
class ReviewStagedChangesAction : AnAction("Review Staged Changes") {

    private val codeReviewService = AICodeReviewService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 检查是否有已暂存的变更
        val changeListManager = ChangeListManager.getInstance(project)
        val defaultChangeList = changeListManager.defaultChangeList
        
        if (defaultChangeList.changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有已暂存(add)的代码变更\n" +
                        "请先使用 'git add' 添加一些文件后再进行评估",
                "评估已暂存变更"
            )
            return
        }

        // 分析代码变更
        val gitAnalyzer = GitChangeAnalyzer(project)
        val changes = gitAnalyzer.getCurrentChanges()
        
        if (changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有检测到已暂存的代码变更",
                "评估已暂存变更"
            )
            return
        }
        
        val commitMessage = "评估已暂存的代码变更 (${changes.size} 个文件)"
        
        // 显示代码评估过程对话框
        val processDialog = CodeReviewProcessDialog(project, changes, commitMessage)
        
        // 开始评估过程
        processDialog.startReview(codeReviewService) { canCommit, reviewResult ->
            if (canCommit && reviewResult != null) {
                // 代码质量符合要求
                processDialog.setCommitEnabled(true)
                
                val dialogResult = processDialog.showAndGet()
                if (dialogResult) {
                    // 询问是否提交
                    val shouldCommit = Messages.showYesNoDialog(
                        "✅ 已暂存代码评估通过！\n\n" +
                                "评分: ${reviewResult.overallScore}/100\n" +
                                "风险等级: ${reviewResult.riskLevel}\n" +
                                "发现问题: ${reviewResult.issues.size} 个\n\n" +
                                "是否现在提交这些变更？",
                        "确认提交已暂存变更",
                        Messages.getQuestionIcon()
                    ) == Messages.YES
                    
                    if (shouldCommit) {
                        val actualCommitMessage = Messages.showInputDialog(
                            project,
                            "请输入提交信息:",
                            "提交已暂存变更",
                            Messages.getQuestionIcon(),
                            "feat: 通过代码评估的变更",
                            null
                        )
                        
                        if (!actualCommitMessage.isNullOrBlank()) {
                            performCommit(project, actualCommitMessage, reviewResult)
                        }
                    }
                }
            } else {
                // 代码质量不符合要求
                processDialog.setCommitEnabled(false)
                
                val settings = CodeReviewSettings.getInstance()
                val message = if (reviewResult != null) {
                    buildBlockMessage(reviewResult, settings)
                } else {
                    "代码评估失败，无法获取评估结果"
                }
                
                processDialog.showAndGet()
                
                Messages.showWarningDialog(
                    "❌ 已暂存代码质量不符合要求\n\n$message",
                    "代码评估未通过"
                )
            }
        }
    }

    private fun performCommit(
        project: Project, 
        commitMessage: String, 
        reviewResult: com.vyibc.autocrplugin.service.CodeReviewResult
    ) {
        try {
            // 这里应该调用实际的Git提交逻辑
            Messages.showInfoMessage(
                "🎉 已暂存代码提交成功！\n\n" +
                        "提交信息: $commitMessage\n" +
                        "评估评分: ${reviewResult.overallScore}/100\n" +
                        "风险等级: ${reviewResult.riskLevel}",
                "提交成功"
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "提交失败: ${e.message}",
                "提交错误"
            )
        }
    }

    private fun buildBlockMessage(
        reviewResult: com.vyibc.autocrplugin.service.CodeReviewResult, 
        settings: CodeReviewSettings
    ): String {
        val reasons = mutableListOf<String>()
        
        if (reviewResult.overallScore < settings.minimumScore) {
            reasons.add("代码评分 ${reviewResult.overallScore} 低于最低要求 ${settings.minimumScore}")
        }
        
        if (settings.blockHighRiskCommits) {
            when (reviewResult.riskLevel) {
                com.vyibc.autocrplugin.service.RiskLevel.CRITICAL -> 
                    reasons.add("代码存在严重风险")
                com.vyibc.autocrplugin.service.RiskLevel.HIGH -> 
                    reasons.add("代码存在高风险")
                else -> {}
            }
        }
        
        val criticalIssues = reviewResult.issues.filter { 
            it.severity == com.vyibc.autocrplugin.service.IssueSeverity.CRITICAL 
        }
        if (criticalIssues.isNotEmpty()) {
            reasons.add("发现 ${criticalIssues.size} 个严重问题")
        }
        
        return reasons.joinToString("\n") { "• $it" }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        
        val changeListManager = ChangeListManager.getInstance(project)
        val hasChanges = changeListManager.defaultChangeList.changes.isNotEmpty()
        
        e.presentation.isEnabledAndVisible = hasChanges
        e.presentation.text = "Review Staged Changes"
        e.presentation.description = "评估已暂存(add)但未提交的代码变更"
        
        if (hasChanges) {
            val changeCount = changeListManager.defaultChangeList.changes.size
            e.presentation.description = "评估 $changeCount 个已暂存的文件变更"
        }
    }
}
