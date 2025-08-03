package com.vyibc.autocr.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.vyibc.autocr.ui.BranchSelectionDialog
import com.vyibc.autocr.service.AutoCRService
import org.slf4j.LoggerFactory

/**
 * AutoCR主入口Action
 * 启动代码评审流程
 */
class StartCodeReviewAction : AnAction() {
    private val logger = LoggerFactory.getLogger(StartCodeReviewAction::class.java)
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT) ?: return
        
        logger.info("Starting AutoCR code review for project: {}", project.name)
        
        // 显示分支选择对话框
        val dialog = BranchSelectionDialog(project)
        
        if (dialog.showAndGet()) {
            val sourceBranch = dialog.getSelectedSourceBranch()
            val targetBranch = dialog.getSelectedTargetBranch()
            val fileChanges = dialog.getFileChanges()
            
            if (sourceBranch != null && targetBranch != null) {
                logger.info("Selected branches: {} -> {}, {} files", 
                    sourceBranch, targetBranch, fileChanges.size)
                
                // 启动代码评审服务
                val autoCRService = AutoCRService.getInstance(project)
                autoCRService.startCodeReview(sourceBranch, targetBranch, fileChanges)
            }
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }
}