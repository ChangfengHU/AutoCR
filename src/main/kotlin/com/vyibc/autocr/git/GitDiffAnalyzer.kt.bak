package com.vyibc.autocr.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.vyibc.autocr.model.FileChange
import com.vyibc.autocr.model.DiffHunk
import com.vyibc.autocr.model.ChangeType
import org.slf4j.LoggerFactory

/**
 * Git差异分析器
 * 使用基于LCS（最长公共子序列）的精确diff算法
 */
class GitDiffAnalyzer(private val project: Project) {
    private val logger = LoggerFactory.getLogger(GitDiffAnalyzer::class.java)
    
    /**
     * 获取当前项目的所有变更
     */
    fun getCurrentChanges(): List<FileChange> {
        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.defaultChangeList.changes
        
        return changes.mapNotNull { change ->
            try {
                convertToFileChange(change)
            } catch (e: Exception) {
                logger.error("Failed to convert change: ${change.virtualFile?.path}", e)
                null
            }
        }
    }
    
    /**
     * 将IntelliJ的Change对象转换为FileChange
     */
    private fun convertToFileChange(change: Change): FileChange? {
        val beforeRevision = change.beforeRevision
        val afterRevision = change.afterRevision
        
        val filePath = when {
            afterRevision != null -> afterRevision.file.path
            beforeRevision != null -> beforeRevision.file.path
            else -> return null
        }
        
        val changeType = when {
            beforeRevision == null && afterRevision != null -> ChangeType.ADDED
            beforeRevision != null && afterRevision == null -> ChangeType.DELETED
            beforeRevision != null && afterRevision != null -> {
                if (beforeRevision.file.path != afterRevision.file.path) {
                    ChangeType.MODIFIED // RENAMED is not in the enum, treating as MODIFIED
                } else {
                    ChangeType.MODIFIED
                }
            }
            else -> return null
        }
        
        val oldContent = try {
            beforeRevision?.content
        } catch (e: Exception) {
            logger.warn("Failed to get old content for: $filePath", e)
            null
        }
        
        val newContent = try {
            afterRevision?.content
        } catch (e: Exception) {
            logger.warn("Failed to get new content for: $filePath", e)
            null
        }
        
        val hunks = if (oldContent != null || newContent != null) {
            computeDiffHunks(oldContent ?: "", newContent ?: "")
        } else {
            emptyList()
        }
        
        return FileChange(
            filePath = filePath,
            changeType = changeType,
            addedMethods = emptyList(), // TODO: Extract methods from hunks
            modifiedMethods = emptyList(), // TODO: Extract methods from hunks
            deletedMethods = emptyList(), // TODO: Extract methods from hunks
            hunks = hunks
        )
    }
    
    /**
     * 计算文件的差异块
     * 使用基于LCS的精确diff算法
     */
    private fun computeDiffHunks(oldContent: String, newContent: String): List<DiffHunk> {
        if (oldContent == newContent) {
            return emptyList()
        }
        
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()
        
        // 使用LCS算法计算差异
        val diffChanges = computeDiff(oldLines, newLines)
        
        // 将连续的变更组合成hunks
        return groupChangesIntoHunks(diffChanges, oldLines, newLines)
    }
    
    /**
     * 使用动态规划计算最长公共子序列（LCS）
     * 然后使用三指针遍历算法识别具体变更
     */
    private fun computeDiff(oldLines: List<String>, newLines: List<String>): List<DiffChange> {
        val changes = mutableListOf<DiffChange>()
        
        // 计算LCS
        val lcs = computeLCS(oldLines, newLines)
        
        // 三指针遍历
        var oldIndex = 0
        var newIndex = 0
        var lcsIndex = 0
        
        while (oldIndex < oldLines.size || newIndex < newLines.size) {
            if (lcsIndex < lcs.size && 
                oldIndex < oldLines.size && 
                newIndex < newLines.size &&
                oldLines[oldIndex] == lcs[lcsIndex] && 
                newLines[newIndex] == lcs[lcsIndex]) {
                // 这行没有变化
                oldIndex++
                newIndex++
                lcsIndex++
            } else if (oldIndex < oldLines.size && 
                      (lcsIndex >= lcs.size || oldLines[oldIndex] != lcs[lcsIndex])) {
                // 删除的行
                changes.add(DiffChange(
                    type = DiffType.REMOVED,
                    line = oldLines[oldIndex],
                    oldLineNumber = oldIndex,
                    newLineNumber = -1
                ))
                oldIndex++
            } else if (newIndex < newLines.size) {
                // 新增的行
                changes.add(DiffChange(
                    type = DiffType.ADDED,
                    line = newLines[newIndex],
                    oldLineNumber = -1,
                    newLineNumber = newIndex
                ))
                newIndex++
            }
        }
        
        return changes
    }
    
    /**
     * 计算最长公共子序列
     * 使用动态规划算法，时间复杂度O(m*n)
     */
    private fun computeLCS(oldLines: List<String>, newLines: List<String>): List<String> {
        val m = oldLines.size
        val n = newLines.size
        val dp = Array(m + 1) { IntArray(n + 1) }
        
        // 填充DP表
        for (i in 1..m) {
            for (j in 1..n) {
                if (oldLines[i - 1] == newLines[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1
                } else {
                    dp[i][j] = maxOf(dp[i - 1][j], dp[i][j - 1])
                }
            }
        }
        
        // 回溯构建LCS
        val lcs = mutableListOf<String>()
        var i = m
        var j = n
        
        while (i > 0 && j > 0) {
            if (oldLines[i - 1] == newLines[j - 1]) {
                lcs.add(0, oldLines[i - 1])
                i--
                j--
            } else if (dp[i - 1][j] > dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        
        return lcs
    }
    
    /**
     * 将连续的变更组合成hunks
     */
    private fun groupChangesIntoHunks(
        changes: List<DiffChange>,
        oldLines: List<String>,
        newLines: List<String>
    ): List<DiffHunk> {
        if (changes.isEmpty()) {
            return emptyList()
        }
        
        val hunks = mutableListOf<DiffHunk>()
        var currentHunk = mutableListOf<DiffChange>()
        var lastOldLine = -10  // 初始值设为-10，确保第一个变更会开始新的hunk
        var lastNewLine = -10
        
        for (change in changes) {
            val oldLine = if (change.oldLineNumber >= 0) change.oldLineNumber else lastOldLine
            val newLine = if (change.newLineNumber >= 0) change.newLineNumber else lastNewLine
            
            // 如果当前变更与上一个变更不连续（间隔超过3行），则开始新的hunk
            if (currentHunk.isNotEmpty() && 
                (kotlin.math.abs(oldLine - lastOldLine) > 3 || 
                 kotlin.math.abs(newLine - lastNewLine) > 3)) {
                hunks.add(createHunkFromChanges(currentHunk, oldLines, newLines))
                currentHunk = mutableListOf()
            }
            
            currentHunk.add(change)
            lastOldLine = oldLine
            lastNewLine = newLine
        }
        
        // 添加最后一个hunk
        if (currentHunk.isNotEmpty()) {
            hunks.add(createHunkFromChanges(currentHunk, oldLines, newLines))
        }
        
        return hunks
    }
    
    /**
     * 从一组连续的变更创建DiffHunk
     */
    private fun createHunkFromChanges(
        changes: List<DiffChange>,
        oldLines: List<String>,
        newLines: List<String>
    ): DiffHunk {
        val addedLines = mutableListOf<Pair<Int, String>>()
        val removedLines = mutableListOf<Pair<Int, String>>()
        
        for (change in changes) {
            when (change.type) {
                DiffType.ADDED -> {
                    // 过滤策略：只过滤完全空的行
                    if (change.line.isNotBlank() || change.line.isNotEmpty()) {
                        addedLines.add(change.newLineNumber to change.line)
                    }
                }
                DiffType.REMOVED -> {
                    if (change.line.isNotBlank() || change.line.isNotEmpty()) {
                        removedLines.add(change.oldLineNumber to change.line)
                    }
                }
            }
        }
        
        // 计算hunk的起始行号
        val startOldLine = changes
            .filter { it.oldLineNumber >= 0 }
            .minOfOrNull { it.oldLineNumber } ?: 0
        val startNewLine = changes
            .filter { it.newLineNumber >= 0 }
            .minOfOrNull { it.newLineNumber } ?: 0
        
        // 计算内容：包含变更的上下文
        val content = buildString {
            removedLines.forEach { (lineNum, line) ->
                appendLine("- $line")
            }
            addedLines.forEach { (lineNum, line) ->
                appendLine("+ $line")
            }
        }
        
        return DiffHunk(
            startLine = minOf(startOldLine, startNewLine),
            endLine = maxOf(
                removedLines.maxOfOrNull { it.first } ?: startOldLine,
                addedLines.maxOfOrNull { it.first } ?: startNewLine
            ),
            addedLines = addedLines.size,
            deletedLines = removedLines.size,
            content = content
        )
    }
    
    /**
     * 获取指定文件的变更
     */
    fun getFileChanges(filePath: String): FileChange? {
        return getCurrentChanges().find { it.filePath == filePath }
    }
    
    /**
     * 获取当前分支名
     */
    fun getCurrentBranch(): String? {
        // TODO: 实现获取当前Git分支的逻辑
        return "main"
    }
    
    /**
     * 获取最近的commit信息
     */
    fun getLastCommitInfo(): String? {
        // TODO: 实现获取最近commit信息的逻辑
        return "Latest commit"
    }
}

/**
 * 差异类型
 */
private enum class DiffType {
    ADDED,      // 新增行
    REMOVED     // 删除行
}

/**
 * 差异变更
 */
private data class DiffChange(
    val type: DiffType,
    val line: String,
    val oldLineNumber: Int,
    val newLineNumber: Int
)