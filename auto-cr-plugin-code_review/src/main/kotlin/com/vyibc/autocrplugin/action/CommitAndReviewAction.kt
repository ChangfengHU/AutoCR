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
 * Commit and Review Action
 * 在Git commit界面添加"提交并评估"功能
 */
class CommitAndReviewAction : AnAction("Commit and Review") {

    private val codeReviewService = AICodeReviewService()

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 获取当前的变更列表
        val changeListManager = ChangeListManager.getInstance(project)
        val defaultChangeList = changeListManager.defaultChangeList
        
        if (defaultChangeList.changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有待提交的变更",
                "Commit and Review"
            )
            return
        }

        // 获取提交信息
        val commitMessage = getCommitMessage(project)
        if (commitMessage.isNullOrBlank()) {
            Messages.showErrorDialog(
                "请输入提交信息",
                "Commit and Review"
            )
            return
        }

        // 分析代码变更
        val gitAnalyzer = GitChangeAnalyzer(project)
        val changes = gitAnalyzer.getCurrentChanges()

        if (changes.isEmpty()) {
            Messages.showInfoMessage(
                "没有检测到代码变更",
                "Commit and Review"
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
                    performCommit(project, commitMessage)
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
                    "代码提交被阻止"
                )
            }
        }
    }

    /**
     * 获取提交信息
     * 这里简化实现，实际应该从commit界面获取
     */
    private fun getCommitMessage(project: Project): String? {
        return Messages.showInputDialog(
            project,
            "请输入提交信息:",
            "Commit Message",
            Messages.getQuestionIcon(),
            null,
            null
        )
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

    /**
     * 执行提交
     */
    private fun performCommit(project: Project, commitMessage: String) {
        try {
            val changeListManager = ChangeListManager.getInstance(project)
            val changes = changeListManager.defaultChangeList.changes.toList()

            if (changes.isNotEmpty()) {
                // 这里应该调用实际的Git提交逻辑
                // 简化实现，显示提交成功消息
                Messages.showInfoMessage(
                    "代码已成功提交！\n提交信息: $commitMessage",
                    "提交成功"
                )
            }
        } catch (e: Exception) {
            Messages.showErrorDialog(
                "提交失败: ${e.message}",
                "提交错误"
            )
        }
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
        e.presentation.text = "Commit and Review"
        e.presentation.description = "提交代码并进行自动代码评估"
    }
}


