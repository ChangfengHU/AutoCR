package com.vyibc.autocr

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages
import com.vyibc.autocr.git.GitDiffService
import com.vyibc.autocr.cache.CacheService
import com.vyibc.autocr.psi.PSIService

class AutoCRAction : AnAction("AutoCR Code Review") {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            Messages.showErrorDialog("No project found", "AutoCR Error")
            return
        }
        
        try {
            val gitDiffService = GitDiffService.getInstance(project)
            val cacheService = CacheService.getInstance(project)
            val psiService = PSIService.getInstance(project)
            
            val summary = gitDiffService.getChangeSummary()
            val branch = gitDiffService.getCurrentBranch()
            val cacheSummary = cacheService.getCacheSummary()
            val projectStats = psiService.getProjectStats()
            val codeSmells = psiService.detectCodeSmells()
            
            val message = buildString {
                appendLine("AutoCR - AI Code Review System")
                appendLine("=====================================")
                appendLine()
                appendLine("📂 Git Analysis:")
                appendLine("   Current Branch: $branch")
                appendLine("   Total Files Changed: ${summary.totalFiles}")
                appendLine("   • Added: ${summary.addedFiles}")
                appendLine("   • Modified: ${summary.modifiedFiles}")
                appendLine("   • Deleted: ${summary.deletedFiles}")
                appendLine("   • Added Lines: ${summary.totalAddedLines}")
                appendLine("   • Deleted Lines: ${summary.totalDeletedLines}")
                appendLine()
                appendLine("🏗️ Code Structure Analysis:")
                appendLine("   Total Classes: ${projectStats.totalClasses}")
                appendLine("   Total Methods: ${projectStats.totalMethods}")
                appendLine("   Total Relationships: ${projectStats.totalEdges}")
                appendLine("   Average Complexity: ${String.format("%.1f", projectStats.averageComplexity)}")
                appendLine("   Max Complexity: ${projectStats.maxComplexity}")
                appendLine("   Total Lines of Code: ${projectStats.totalLinesOfCode}")
                appendLine()
                appendLine("⚠️ Code Quality Issues:")
                appendLine("   Total Code Smells: ${codeSmells.size}")
                val smellCounts = codeSmells.groupBy { it.type }.mapValues { it.value.size }
                smellCounts.forEach { (type, count) ->
                    appendLine("   • $type: $count")
                }
                appendLine()
                appendLine("🗄️ Cache System:")
                appendLine("   Cache Types: ${cacheSummary.totalCacheTypes}")
                appendLine("   Total Entries: ${cacheSummary.totalSize}")
                appendLine("   Hit Rate: ${String.format("%.1f%%", cacheSummary.overallHitRate * 100)}")
                appendLine()
                appendLine("✅ System Components:")
                appendLine("   • Git Diff Analyzer (LCS Algorithm)")
                appendLine("   • PSI Code Structure Analyzer")
                appendLine("   • Three-Level Cache System")
                appendLine("   • Code Quality Detection")
            }
            
            Messages.showMessageDialog(
                project,
                message,
                "AutoCR - System Analysis",
                Messages.getInformationIcon()
            )
        } catch (e: Exception) {
            Messages.showErrorDialog(
                project,
                "Failed to analyze system: ${e.message}",
                "AutoCR Error"
            )
        }
    }
}