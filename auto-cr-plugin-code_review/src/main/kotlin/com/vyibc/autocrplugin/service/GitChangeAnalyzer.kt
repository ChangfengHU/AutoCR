package com.vyibc.autocrplugin.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ContentRevision

/**
 * Git变更分析器
 */
class GitChangeAnalyzer(private val project: Project) {

    /**
     * 获取当前待提交的变更
     */
    fun getCurrentChanges(): List<CodeChange> {
        val changeListManager = ChangeListManager.getInstance(project)
        val changes = changeListManager.defaultChangeList.changes
        
        return changes.mapNotNull { change ->
            convertToCodeChange(change)
        }
    }

    /**
     * 获取指定commit的变更
     */
    fun getCommitChanges(commitHash: String): List<CodeChange> {
        // 简化实现，返回当前变更
        // 在实际项目中，这里可以通过Git命令获取指定commit的变更
        return getCurrentChanges()
    }

    /**
     * 将IntelliJ的Change对象转换为CodeChange
     */
    private fun convertToCodeChange(change: Change): CodeChange? {
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
                    ChangeType.RENAMED
                } else {
                    ChangeType.MODIFIED
                }
            }
            else -> return null
        }
        
        val oldContent = try {
            beforeRevision?.content
        } catch (e: Exception) {
            null
        }
        
        val newContent = try {
            afterRevision?.content
        } catch (e: Exception) {
            null
        }
        
        val (addedLines, removedLines, modifiedLines) = analyzeDiff(oldContent, newContent)
        
        return CodeChange(
            filePath = filePath,
            changeType = changeType,
            oldContent = oldContent,
            newContent = newContent,
            addedLines = addedLines,
            removedLines = removedLines,
            modifiedLines = modifiedLines
        )
    }

    /**
     * 分析文件差异
     */
    private fun analyzeDiff(
        oldContent: String?,
        newContent: String?
    ): Triple<List<String>, List<String>, List<Pair<String, String>>> {
        
        if (oldContent == null && newContent == null) {
            return Triple(emptyList(), emptyList(), emptyList())
        }
        
        if (oldContent == null) {
            // 新增文件
            val lines = newContent?.lines() ?: emptyList()
            return Triple(lines, emptyList(), emptyList())
        }
        
        if (newContent == null) {
            // 删除文件
            val lines = oldContent.lines()
            return Triple(emptyList(), lines, emptyList())
        }
        
        // 改进的差异分析算法
        val oldLines = oldContent.lines()
        val newLines = newContent.lines()

        val addedLines = mutableListOf<String>()
        val removedLines = mutableListOf<String>()
        val modifiedLines = mutableListOf<Pair<String, String>>()

        // 使用基于最长公共子序列(LCS)的精确diff算法
        // 这个算法可以准确地识别出哪些行是新增的，哪些行是删除的
        val diff = computeDiff(oldLines, newLines)

        for (change in diff) {
            when (change.type) {
                DiffType.ADDED -> {
                    // 更宽松的过滤策略：只过滤完全空的行，保留有意义的空白字符变更
                    val line = change.line
                    if (line.isNotBlank() || (line.isBlank() && line.isNotEmpty())) {
                        addedLines.add(line)
                    }
                }
                DiffType.REMOVED -> {
                    val line = change.line
                    if (line.isNotBlank() || (line.isBlank() && line.isNotEmpty())) {
                        removedLines.add(line)
                    }
                }
                DiffType.MODIFIED -> {
                    if (change.oldLine != null) {
                        modifiedLines.add(change.oldLine to change.line)
                    }
                }
            }
        }

        return Triple(addedLines, removedLines, modifiedLines)
    }

    /**
     * 计算两个文件的差异
     * 使用改进的逐行比较算法，避免Set去重导致的问题
     */
    private fun computeDiff(oldLines: List<String>, newLines: List<String>): List<DiffChange> {
        val changes = mutableListOf<DiffChange>()
        
        // 使用动态规划算法计算最长公共子序列(LCS)
        val lcs = computeLCS(oldLines, newLines)
        
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
                changes.add(DiffChange(DiffType.REMOVED, oldLines[oldIndex], null, oldIndex))
                oldIndex++
            } else if (newIndex < newLines.size) {
                // 新增的行
                changes.add(DiffChange(DiffType.ADDED, newLines[newIndex], null, newIndex))
                newIndex++
            }
        }
        
        return changes
    }
    
    /**
     * 计算最长公共子序列
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
     * 获取当前分支名
     */
    fun getCurrentBranch(): String? {
        // 简化实现，返回默认分支名
        return "main"
    }

    /**
     * 获取最近的commit信息
     */
    fun getLastCommitInfo(): String? {
        // 简化实现，返回模拟的commit信息
        return "Latest commit info"
    }
}

/**
 * 差异类型
 * 用于标识每一行的变化类型
 */
enum class DiffType {
    ADDED,      // 新增行：在新文件中存在，但在旧文件中不存在
    REMOVED,    // 删除行：在旧文件中存在，但在新文件中不存在
    MODIFIED    // 修改行：在两个文件中都存在但内容不同（当前算法中未使用）
}

/**
 * 差异变更数据结构
 * 记录每一个检测到的变更
 * 
 * @param type 变更类型（新增/删除/修改）
 * @param line 当前行的内容
 * @param oldLine 旧行的内容（仅在MODIFIED类型中使用）
 * @param lineNumber 行号（在原文件中的位置）
 */
data class DiffChange(
    val type: DiffType,
    val line: String,
    val oldLine: String?,
    val lineNumber: Int
)
