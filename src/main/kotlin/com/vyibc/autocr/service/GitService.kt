package com.vyibc.autocr.service

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vfs.VirtualFileManager
import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

/**
 * Git操作服务类
 * 负责分支管理、差异分析等Git相关操作
 */
class GitService(private val project: Project) {
    
    private val logger = LoggerFactory.getLogger(GitService::class.java)
    private val projectPath = project.basePath ?: throw IllegalStateException("Project path is null")
    
    /**
     * 获取所有分支列表
     */
    fun getAllBranches(): List<String> {
        return try {
            val branches = mutableListOf<String>()

            // 获取本地分支（注意：不能在参数中加引号，否则输出会包含引号字符）
            val localBranches = executeGitCommand(
                listOf("git", "branch", "--format=%(refname:short)")
            )
            branches.addAll(localBranches.map { it.trim() })

            // 获取远程分支
            val remoteBranches = executeGitCommand(
                listOf("git", "branch", "-r", "--format=%(refname:short)")
            )
            branches.addAll(
                remoteBranches
                    .map { it.trim() }
                    .filter { !it.contains("HEAD") }
                    .map { it.removePrefix("origin/") }
            )

            // 去重并排序
            branches.distinct().sorted()
        } catch (e: Exception) {
            logger.error("Failed to get branches", e)
            emptyList()
        }
    }
    
    /**
     * 获取当前分支
     */
    fun getCurrentBranch(): String? {
        return try {
            val result = executeGitCommand(listOf("git", "rev-parse", "--abbrev-ref", "HEAD"))
            result.firstOrNull()?.trim()
        } catch (e: Exception) {
            logger.error("Failed to get current branch", e)
            null
        }
    }
    
    /**
     * 分析两个分支之间的差异
     */
    fun analyzeBranchDifferences(sourceBranch: String, targetBranch: String): GitDiffContext {
        logger.info("分析分支差异: $sourceBranch -> $targetBranch")
        
        try {
            // 预解析分支引用，支持远程分支（如仅存在 origin/test 的情况）
            val resolvedSource = resolveBranchRef(sourceBranch)
            val resolvedTarget = resolveBranchRef(targetBranch)
            logger.info("使用分支引用: $resolvedSource .. $resolvedTarget")
            // 获取变更文件列表
            val changedFiles = getChangedFiles(resolvedSource, resolvedTarget)
            
            // 获取提交历史
            val commits = getCommitHistory(resolvedSource, resolvedTarget)
            
            // 计算总的增减行数
            val totalAddedLines = changedFiles.sumOf { it.addedLines }
            val totalDeletedLines = changedFiles.sumOf { it.deletedLines }
            
            return GitDiffContext(
                sourceBranch = sourceBranch,
                targetBranch = targetBranch,
                changedFiles = changedFiles,
                addedLines = totalAddedLines,
                deletedLines = totalDeletedLines,
                commits = commits
            )
        } catch (e: Exception) {
            logger.error("Failed to analyze branch differences", e)
            throw RuntimeException("无法分析分支差异: ${e.message}", e)
        }
    }
    
    /**
     * 获取变更文件列表
     */
    private fun getChangedFiles(sourceBranch: String, targetBranch: String): List<ChangedFile> {
        val changedFiles = mutableListOf<ChangedFile>()
        
        // 使用git diff获取文件状态
        val statusLines = executeGitCommand(listOf("git", "diff", "--name-status", "$targetBranch..$sourceBranch"))
        
        for (line in statusLines) {
            val parts = line.trim().split("\t")
            if (parts.size >= 2) {
                val status = parts[0]
                val filePath = parts[1]
                
                // 跳过非Java/Kotlin文件
                if (!isRelevantFile(filePath)) continue
                
                val changeType = when (status) {
                    "A" -> FileChangeType.ADDED
                    "M" -> FileChangeType.MODIFIED
                    "D" -> FileChangeType.DELETED
                    "R", "R100" -> FileChangeType.RENAMED
                    "C", "C100" -> FileChangeType.COPIED
                    else -> FileChangeType.MODIFIED
                }
                
                // 获取具体的差异信息
                val diffHunks = if (changeType != FileChangeType.DELETED) {
                    getDiffHunks(filePath, sourceBranch, targetBranch)
                } else {
                    emptyList()
                }
                
                val addedLines = diffHunks.sumOf { hunk -> hunk.lines.count { it.type == DiffLineType.ADDED } }
                val deletedLines = diffHunks.sumOf { hunk -> hunk.lines.count { it.type == DiffLineType.DELETED } }
                
                changedFiles.add(
                    ChangedFile(
                        path = filePath,
                        changeType = changeType,
                        addedLines = addedLines,
                        deletedLines = deletedLines,
                        hunks = diffHunks
                    )
                )
            }
        }
        
        return changedFiles
    }
    
    /**
     * 获取具体的差异块
     */
    private fun getDiffHunks(filePath: String, sourceBranch: String, targetBranch: String): List<DiffHunk> {
        val hunks = mutableListOf<DiffHunk>()
        
        try {
            val diffLines = executeGitCommand(listOf("git", "diff", "$targetBranch..$sourceBranch", "--", filePath))
            
            var currentHunk: DiffHunk? = null
            val currentLines = mutableListOf<DiffLine>()
            var lineNumber = 0
            
            for (line in diffLines) {
                when {
                    line.startsWith("@@") -> {
                        // 保存之前的hunk
                        if (currentHunk != null) {
                            hunks.add(currentHunk.copy(lines = currentLines.toList()))
                            currentLines.clear()
                        }
                        
                        // 解析新的hunk头
                        val hunkHeader = parseHunkHeader(line)
                        if (hunkHeader != null) {
                            currentHunk = hunkHeader
                            lineNumber = hunkHeader.newStart
                        }
                    }
                    line.startsWith("+") && !line.startsWith("+++") -> {
                        currentLines.add(DiffLine(DiffLineType.ADDED, line.substring(1), lineNumber++))
                    }
                    line.startsWith("-") && !line.startsWith("---") -> {
                        currentLines.add(DiffLine(DiffLineType.DELETED, line.substring(1), null))
                    }
                    line.startsWith(" ") -> {
                        currentLines.add(DiffLine(DiffLineType.CONTEXT, line.substring(1), lineNumber++))
                    }
                }
            }
            
            // 保存最后一个hunk
            if (currentHunk != null) {
                hunks.add(currentHunk.copy(lines = currentLines.toList()))
            }
            
        } catch (e: Exception) {
            logger.warn("Failed to get diff hunks for $filePath", e)
        }
        
        return hunks
    }
    
    /**
     * 解析hunk头部信息
     */
    private fun parseHunkHeader(line: String): DiffHunk? {
        // 格式: @@ -oldStart,oldCount +newStart,newCount @@ header
        val regex = Regex("@@\\s*-?(\\d+)(?:,(\\d+))?\\s*\\+?(\\d+)(?:,(\\d+))?\\s*@@(.*)$")
        val match = regex.find(line)
        
        return if (match != null) {
            val groups = match.groupValues
            DiffHunk(
                oldStart = groups[1].toInt(),
                oldCount = groups[2].takeIf { it.isNotEmpty() }?.toInt() ?: 1,
                newStart = groups[3].toInt(),
                newCount = groups[4].takeIf { it.isNotEmpty() }?.toInt() ?: 1,
                header = groups[5].trim(),
                lines = emptyList() // 会在后面填充
            )
        } else {
            null
        }
    }
    
    /**
     * 获取提交历史
     */
    private fun getCommitHistory(sourceBranch: String, targetBranch: String): List<GitCommit> {
        val commits = mutableListOf<GitCommit>()
        
        try {
            val commitLines = executeGitCommand(
                listOf(
                    "git", "log", "$targetBranch..$sourceBranch",
                    "--pretty=format:%H|%an|%ad|%s", "--date=iso"
                )
            )
            
            for (line in commitLines) {
                val parts = line.split("|")
                if (parts.size >= 4) {
                    val businessKeywords = extractBusinessKeywords(parts[3])
                    commits.add(
                        GitCommit(
                            hash = parts[0],
                            author = parts[1],
                            date = parts[2],
                            message = parts[3],
                            businessKeywords = businessKeywords
                        )
                    )
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to get commit history", e)
        }
        
        return commits
    }

    /**
     * 将用户选择的分支名解析为可用的 git rev（支持仅存在远程分支的情况）
     */
    private fun resolveBranchRef(branch: String): String {
        val trimmed = branch.trim()
        if (trimmed.isEmpty()) return trimmed

        // 先检查本地是否存在该分支
        val localBranches = try {
            executeGitCommand(listOf("git", "branch", "--format=%(refname:short)"))
        } catch (_: Exception) { emptyList() }
        if (localBranches.any { it.trim() == trimmed }) return trimmed

        // 若不存在，尝试使用 origin/ 前缀
        val remoteBranches = try {
            executeGitCommand(listOf("git", "branch", "-r", "--format=%(refname:short)"))
        } catch (_: Exception) { emptyList() }
        if (remoteBranches.any { it.trim() == "origin/$trimmed" }) return "origin/$trimmed"

        // 回退为原始输入
        return trimmed
    }
    
    /**
     * 从提交消息中提取业务关键词
     */
    private fun extractBusinessKeywords(message: String): List<String> {
        val keywords = mutableSetOf<String>()
        val lowerMessage = message.lowercase()
        
        // 预定义的业务关键词
        val businessTerms = setOf(
            "user", "order", "product", "payment", "auth", "login", "register",
            "cart", "checkout", "inventory", "customer", "account", "profile",
            "api", "service", "controller", "repository", "database", "cache",
            "security", "permission", "role", "token", "session"
        )
        
        // 动作词
        val actionTerms = setOf(
            "create", "add", "implement", "update", "modify", "fix", "delete",
            "remove", "refactor", "optimize", "improve", "enhance", "validate"
        )
        
        businessTerms.forEach { term ->
            if (lowerMessage.contains(term)) {
                keywords.add(term)
            }
        }
        
        actionTerms.forEach { term ->
            if (lowerMessage.contains(term)) {
                keywords.add(term)
            }
        }
        
        return keywords.toList()
    }
    
    /**
     * 判断是否为相关文件类型
     */
    private fun isRelevantFile(filePath: String): Boolean {
        val lowerPath = filePath.lowercase()
        return lowerPath.endsWith(".java") || 
               lowerPath.endsWith(".kt") || 
               lowerPath.endsWith(".scala") ||
               lowerPath.endsWith(".xml") ||
               lowerPath.endsWith(".properties") ||
               lowerPath.endsWith(".yml") ||
               lowerPath.endsWith(".yaml")
    }
    
    /**
     * 执行Git命令（安全参数传递）
     */
    private fun executeGitCommand(args: List<String>): List<String> {
        val processBuilder = ProcessBuilder(args)
        processBuilder.directory(File(projectPath))
        processBuilder.redirectErrorStream(true)

        val process = processBuilder.start()
        val output = mutableListOf<String>()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lineSequence().forEach { line ->
                output.add(line)
            }
        }

        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished || process.exitValue() != 0) {
            val joined = args.joinToString(" ")
            throw RuntimeException("Git command failed: $joined")
        }

        return output
    }
}