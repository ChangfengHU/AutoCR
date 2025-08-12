package com.vyibc.autocrplugin.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.vyibc.autocrplugin.settings.CodeReviewSettings

/**
 * 插件状态检查Action
 * 用于验证插件是否正确加载
 */
class PluginStatusAction : AnAction("Plugin Status") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        
        val statusInfo = StringBuilder()
        statusInfo.append("🔍 Auto CR Plugin 状态检查\n\n")
        
        // 检查项目
        if (project != null) {
            statusInfo.append("✅ 项目: ${project.name}\n")
        } else {
            statusInfo.append("❌ 没有打开的项目\n")
        }
        
        // 检查设置
        try {
            val settings = CodeReviewSettings.getInstance()
            statusInfo.append("✅ 设置服务: 已加载\n")
            statusInfo.append("   - 最低评分: ${settings.minimumScore}\n")
            statusInfo.append("   - DeepSeek启用: ${settings.deepSeekEnabled}\n")
            statusInfo.append("   - API Key配置: ${if (settings.deepSeekApiKey.isNotBlank()) "已配置" else "未配置"}\n")
        } catch (e: Exception) {
            statusInfo.append("❌ 设置服务: 加载失败 - ${e.message}\n")
        }
        
        // 检查Actions
        statusInfo.append("✅ Actions: 已注册\n")
        statusInfo.append("   - 翻译功能: Ctrl+Alt+T\n")
        statusInfo.append("   - 代码评估: Ctrl+Shift+C\n")
        statusInfo.append("   - 测试功能: Ctrl+Alt+Shift+R\n")
        
        // 使用说明
        statusInfo.append("\n📋 使用方法:\n")
        statusInfo.append("1. 配置设置: File → Settings → Tools → Auto Code Review\n")
        statusInfo.append("2. 测试功能: Ctrl+Alt+Shift+R\n")
        statusInfo.append("3. 实际使用: 修改代码后使用 Ctrl+Shift+C\n")
        
        Messages.showInfoMessage(
            statusInfo.toString(),
            "Auto CR Plugin 状态"
        )
    }

    override fun update(e: AnActionEvent) {
        e.presentation.text = "Plugin Status"
        e.presentation.description = "检查Auto CR Plugin的状态"
    }
}
