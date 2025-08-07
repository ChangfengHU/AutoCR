package com.vyibc.autocr.git

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.vyibc.autocr.model.FileChange
import org.slf4j.LoggerFactory

/**
 * Git差异分析服务
 * 提供统一的Git差异分析功能接口
 */
@Service(Service.Level.PROJECT)
class GitDiffService(private val project: Project) {
    private val logger = LoggerFactory.getLogger(GitDiffService::class.java)
    private val analyzer = GitDiffAnalyzer(project)
    
    /**
     * 获取当前项目的所有变更
     */
    fun getCurrentChanges(): List<FileChange> {
        return try {
            analyzer.getCurrentChanges()
        } catch (e: Exception) {
            logger.error("Failed to get current changes", e)
            emptyList()
        }
    }
    
    /**
     * 获取指定文件的变更详情
     */
    fun getFileChanges(filePath: String): FileChange? {
        return try {
            analyzer.getFileChanges(filePath)
        } catch (e: Exception) {
            logger.error("Failed to get file changes for: $filePath", e)
            null
        }
    }
    
    /**
     * 获取当前分支名
     */
    fun getCurrentBranch(): String {
        return analyzer.getCurrentBranch() ?: "unknown"
    }
    
    /**
     * 检查是否有未提交的变更
     */
    fun hasUncommittedChanges(): Boolean {
        return getCurrentChanges().isNotEmpty()
    }
    
    /**
     * 获取变更统计信息
     */
    fun getChangeSummary(): ChangeSummary {
        val changes = getCurrentChanges()
        return ChangeSummary(
            totalFiles = changes.size,
            addedFiles = changes.count { it.changeType == com.vyibc.autocr.model.ChangeType.ADDED },
            modifiedFiles = changes.count { it.changeType == com.vyibc.autocr.model.ChangeType.MODIFIED },
            deletedFiles = changes.count { it.changeType == com.vyibc.autocr.model.ChangeType.DELETED },
            totalAddedLines = changes.sumOf { change -> 
                change.hunks.sumOf { it.addedLines }
            },
            totalDeletedLines = changes.sumOf { change ->
                change.hunks.sumOf { it.deletedLines }
            }
        )
    }
    
    companion object {
        fun getInstance(project: Project): GitDiffService {
            return project.service()
        }
    }
}

/**
 * 变更摘要信息
 */
data class ChangeSummary(
    val totalFiles: Int,
    val addedFiles: Int,
    val modifiedFiles: Int,
    val deletedFiles: Int,
    val totalAddedLines: Int,
    val totalDeletedLines: Int
)