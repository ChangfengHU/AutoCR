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
 * 快速代码评估Action
 * 专门用于在Git提交界面快速触发代码评估
 */
class QuickCodeReviewAction : AnAction() {

    private val codeReviewService = AICodeReviewService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 检查是否有待提交的变更
        val changeListManager = ChangeListManager.getInstance(project)
        val defaultChangeList = changeListManager.defaultChangeList
        
        if (defaultChangeList.changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有待提交的变更，无法进行代码评估",
                "代码评估"
            )
            return
        }

        // 获取提交信息（简化版本，使用默认信息）
        val commitMessage = "代码评估测试提交"
        
        // 分析代码变更
        val gitAnalyzer = GitChangeAnalyzer(project)
        val changes = gitAnalyzer.getCurrentChanges()
        
        if (changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有检测到代码变更",
                "代码评估"
            )
            return
        }
        
        // 显示代码评估过程对话框
        val processDialog = CodeReviewProcessDialog(project, changes, commitMessage)
        
        // 开始评估过程
        processDialog.startReview(codeReviewService) { canCommit, reviewResult ->
            if (canCommit && reviewResult != null) {
                // 代码质量符合要求，可以提交
                processDialog.setCommitEnabled(true)
                
                // 如果用户点击提交按钮
                val dialogResult = processDialog.showAndGet()
                if (dialogResult) {
                    Messages.showInfoMessage(
                        "代码评估通过！\n" +
                                "评分: ${reviewResult.overallScore}/100\n" +
                                "风险等级: ${reviewResult.riskLevel}\n" +
                                "现在可以安全地提交代码了。",
                        "评估通过"
                    )
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
                
                // 显示对话框但禁用提交按钮
                processDialog.showAndGet()
                
                // 显示阻止提交的原因
                Messages.showWarningDialog(
                    message,
                    "代码质量不符合要求"
                )
            }
        }
    }

    /**
     * 构建阻止提交的消息
     */
    private fun buildBlockMessage(reviewResult: com.vyibc.autocrplugin.service.CodeReviewResult, settings: CodeReviewSettings): String {
        val reasons = mutableListOf<String>()
        
        // 检查评分
        if (reviewResult.overallScore < settings.minimumScore) {
            reasons.add("代码评分 ${reviewResult.overallScore} 低于最低要求 ${settings.minimumScore}")
        }
        
        // 检查风险等级
        if (settings.blockHighRiskCommits) {
            when (reviewResult.riskLevel) {
                com.vyibc.autocrplugin.service.RiskLevel.CRITICAL -> 
                    reasons.add("代码存在严重风险")
                com.vyibc.autocrplugin.service.RiskLevel.HIGH -> 
                    reasons.add("代码存在高风险")
                else -> {}
            }
        }
        
        // 检查严重问题
        val criticalIssues = reviewResult.issues.filter { 
            it.severity == com.vyibc.autocrplugin.service.IssueSeverity.CRITICAL 
        }
        if (criticalIssues.isNotEmpty()) {
            reasons.add("发现 ${criticalIssues.size} 个严重问题")
        }
        
        val message = StringBuilder()
        message.append("代码提交被阻止，原因如下：\n\n")
        reasons.forEachIndexed { index, reason ->
            message.append("${index + 1}. $reason\n")
        }
        message.append("\n请修复这些问题后再尝试提交。")
        
        return message.toString()
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
        e.presentation.text = "Code Review"
        e.presentation.description = "对当前变更进行代码评估"
    }
}
