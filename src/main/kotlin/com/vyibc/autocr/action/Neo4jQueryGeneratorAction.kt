package com.vyibc.autocr.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.Project
import com.vyibc.autocr.ui.Neo4jQueryGeneratorDialog
import org.slf4j.LoggerFactory

/**
 * Neo4j查询生成器Action
 */
class Neo4jQueryGeneratorAction : AnAction() {
    
    private val logger = LoggerFactory.getLogger(Neo4jQueryGeneratorAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        logger.info("打开Neo4j查询生成器，项目：${project.name}")
        
        // 创建并显示查询生成器对话框
        val dialog = Neo4jQueryGeneratorDialog(project)
        dialog.show()
    }

    override fun update(e: AnActionEvent) {
        val project = e.getData(CommonDataKeys.PROJECT)
        e.presentation.isEnabledAndVisible = project != null
    }
}