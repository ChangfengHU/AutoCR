package com.vyibc.autocr.service

import com.intellij.openapi.project.Project
import com.vyibc.autocr.model.*
import com.vyibc.autocr.util.PsiElementAnalyzer
import org.slf4j.LoggerFactory
import kotlinx.coroutines.*

/**
 * 调用路径分析器 - 基于Neo4j图遍历的智能路径发现
 * 从Git变更出发，通过实际的调用关系图发现影响路径和依赖路径
 */
class CallPathAnalyzer(private val project: Project) {
    
    private val logger = LoggerFactory.getLogger(CallPathAnalyzer::class.java)
    private val neo4jQueryService = Neo4jQueryService()
    
    /**
     * 从Git变更上下文中发现相关的调用路径
     * 基于Neo4j图数据库进行智能路径发现
     */
    suspend fun findRelevantPaths(gitContext: GitDiffContext): List<CallPath> = withContext(Dispatchers.IO) {
        logger.info("开始基于Neo4j图遍历分析相关调用路径，变更文件数: ${gitContext.changedFiles.size}")
        
        val allPaths = mutableListOf<CallPath>()
        
        // 1. 从直接变更的方法开始构建路径
        val directPaths = findDirectChangePaths(gitContext)
        allPaths.addAll(directPaths)
        
        // 2. 基于Neo4j图遍历发现上游调用链
        val upstreamPaths = findUpstreamPathsFromGraph(gitContext)
        allPaths.addAll(upstreamPaths)
        
        // 3. 基于Neo4j图遍历发现下游调用链
        val downstreamPaths = findDownstreamPathsFromGraph(gitContext)
        allPaths.addAll(downstreamPaths)
        
        // 4. 发现跨层级调用路径
        val crossLayerPaths = findCrossLayerPathsFromGraph(gitContext)
        allPaths.addAll(crossLayerPaths)
        
        // 5. 去重、过滤和排序
        val uniquePaths = removeDuplicates(allPaths)
        val filteredPaths = filterRelevantPaths(uniquePaths, gitContext)
        val sortedPaths = sortPathsByRelevance(filteredPaths, gitContext)
        
        logger.info("发现 ${sortedPaths.size} 条相关调用路径")
        return@withContext sortedPaths
    }
    
    /**
     * 查找直接变更的方法路径
     */
    private fun findDirectChangePaths(gitContext: GitDiffContext): List<CallPath> {
        val paths = mutableListOf<CallPath>()
        
        gitContext.changedFiles.forEach { file ->
            logger.debug("分析文件: ${file.path}, 变更类型: ${file.changeType}")
            
            if (isJavaOrKotlinFile(file.path)) {
                val className = extractClassName(file.path)
                
                // 分析每个hunk中的变更
                file.hunks.forEach { hunk ->
                    val changedMethods = extractMethodsFromHunk(hunk, className)
                    
                    changedMethods.forEach { method ->
                        // 为每个变更的方法创建一个基础路径
                        val path = CallPath(
                            id = generatePathId("direct", method),
                            description = "直接变更: ${method} in $className",
                            methods = listOf("$className.$method"),
                            relatedChanges = listOf(file),
                            // 添加变更详情
                            changeDetails = extractChangeDetails(hunk, method)
                        )
                        paths.add(path)
                        
                        logger.debug("发现直接变更路径: ${path.id} - ${path.description}")
                    }
                }
                
                // 如果没有找到具体方法，至少创建一个类级别的路径
                if (file.hunks.isNotEmpty() && !paths.any { it.relatedChanges.contains(file) }) {
                    val classPath = CallPath(
                        id = generatePathId("class", className),
                        description = "类级别变更: $className",
                        methods = listOf(className),
                        relatedChanges = listOf(file),
                        changeDetails = "文件变更：+${file.addedLines}/-${file.deletedLines}行"
                    )
                    paths.add(classPath)
                    
                    logger.debug("发现类级别变更路径: ${classPath.id} - ${classPath.description}")
                }
            }
        }
        
        logger.info("发现 ${paths.size} 个直接变更路径")
        return paths
    }
    
    /**
     * 基于Neo4j图遍历发现上游调用路径（向上追踪）
     * 查找所有直接和间接调用变更方法的路径
     */
    private suspend fun findUpstreamPathsFromGraph(gitContext: GitDiffContext): List<CallPath> = withContext(Dispatchers.Default) {
        val paths = mutableListOf<CallPath>()
        
        logger.debug("开始从Neo4j图中发现上游调用路径")
        
        val asyncTasks = gitContext.changedFiles.filter { isJavaOrKotlinFile(it.path) }.map { file ->
            async {
                val className = extractClassName(file.path)
                val filePaths = mutableListOf<CallPath>()
                
                // 获取文件中的所有变更方法
                val changedMethods = extractChangedMethodsFromFile(file, className)
                
                changedMethods.forEach { methodName ->
                    try {
                        // 查询方法的调用者
                        val callersInfo = neo4jQueryService.queryMethodCallers(className, methodName)
                        
                        logger.debug("方法 $className.$methodName 有 ${callersInfo.totalCallers} 个调用者")
                        
                        // 为每个调用者创建路径
                        callersInfo.callerDetails.take(10).forEach { caller -> // 限制数量避免过多
                            val upstreamPath = buildUpstreamPath(caller, className, methodName, file)
                            filePaths.add(upstreamPath)
                        }
                        
                        // 构建多层调用链（2-3层）
                        val deepCallerPaths = buildDeepCallerChains(className, methodName, file, maxDepth = 2)
                        filePaths.addAll(deepCallerPaths)
                        
                    } catch (e: Exception) {
                        logger.debug("查询方法调用者失败: $className.$methodName, 错误: ${e.message}")
                    }
                }
                
                filePaths
            }
        }
        
        val results = asyncTasks.awaitAll()
        results.forEach { paths.addAll(it) }
        
        logger.debug("从Neo4j图中发现了 ${paths.size} 个上游调用路径")
        return@withContext paths
    }
    
    /**
     * 基于Neo4j图遍历发现下游调用路径（向下追踪）
     * 查找变更方法直接和间接调用的所有目标
     */
    private suspend fun findDownstreamPathsFromGraph(gitContext: GitDiffContext): List<CallPath> = withContext(Dispatchers.Default) {
        val paths = mutableListOf<CallPath>()
        
        logger.debug("开始从Neo4j图中发现下游调用路径")
        
        val asyncTasks = gitContext.changedFiles.filter { isJavaOrKotlinFile(it.path) }.map { file ->
            async {
                val className = extractClassName(file.path)
                val filePaths = mutableListOf<CallPath>()
                
                // 获取文件中的所有变更方法
                val changedMethods = extractChangedMethodsFromFile(file, className)
                
                changedMethods.forEach { methodName ->
                    try {
                        // 查询方法的被调用者
                        val calleesInfo = neo4jQueryService.queryMethodCallees(className, methodName)
                        
                        logger.debug("方法 $className.$methodName 调用了 ${calleesInfo.totalCallees} 个目标")
                        
                        // 为每个被调用者创建路径
                        calleesInfo.calleeDetails.take(10).forEach { callee -> // 限制数量避免过多
                            val downstreamPath = buildDownstreamPath(className, methodName, callee, file)
                            filePaths.add(downstreamPath)
                        }
                        
                        // 构建多层调用链（2-3层）
                        val deepCalleePaths = buildDeepCalleeChains(className, methodName, file, maxDepth = 2)
                        filePaths.addAll(deepCalleePaths)
                        
                    } catch (e: Exception) {
                        logger.debug("查询方法被调用者失败: $className.$methodName, 错误: ${e.message}")
                    }
                }
                
                filePaths
            }
        }
        
        val results = asyncTasks.awaitAll()
        results.forEach { paths.addAll(it) }
        
        logger.debug("从Neo4j图中发现了 ${paths.size} 个下游调用路径")
        return@withContext paths
    }
    
    /**
     * 从变更文件中提取变更的方法
     */
    private fun extractChangedMethods(file: ChangedFile): List<MethodSignature> {
        val methods = mutableListOf<MethodSignature>()
        
        file.hunks.forEach { hunk ->
            hunk.lines.filter { it.type == DiffLineType.ADDED }.forEach { line ->
                // 简单的方法识别逻辑
                if (isMethodDeclaration(line.content)) {
                    val methodName = extractMethodName(line.content)
                    val methodSignature = MethodSignature(
                        name = methodName,
                        signature = line.content.trim(),
                        fullSignature = "${extractClassName(file.path)}.$methodName"
                    )
                    methods.add(methodSignature)
                }
            }
        }
        
        return methods
    }
    
    /**
     * 判断是否为方法声明
     */
    private fun isMethodDeclaration(line: String): Boolean {
        val trimmedLine = line.trim()
        return (trimmedLine.contains("public ") || 
                trimmedLine.contains("private ") || 
                trimmedLine.contains("protected ")) &&
               trimmedLine.contains("(") && 
               trimmedLine.contains(")") &&
               !trimmedLine.contains("=") &&
               !trimmedLine.startsWith("//")
    }
    
    /**
     * 从代码行中提取方法名
     */
    private fun extractMethodName(line: String): String {
        val regex = Regex("\\b(\\w+)\\s*\\(")
        val match = regex.find(line)
        return match?.groupValues?.get(1) ?: "unknownMethod"
    }
    
    /**
     * 基于Neo4j图遍历发现跨层级调用路径
     * 识别架构上重要的跨层调用关系
     */
    private suspend fun findCrossLayerPathsFromGraph(gitContext: GitDiffContext): List<CallPath> = withContext(Dispatchers.Default) {
        val paths = mutableListOf<CallPath>()
        
        logger.debug("开始发现跨层级调用路径")
        
        gitContext.changedFiles.filter { isJavaOrKotlinFile(it.path) }.forEach { file ->
            val className = extractClassName(file.path)
            
            try {
                // 查询类的架构信息
                val archInfo = neo4jQueryService.queryClassArchitecture(className)
                
                // 如果类跨越多个层级或有复杂依赖，创建跨层级路径
                if (archInfo.dependencyLayers.size > 2) {
                    val crossLayerPath = CallPath(
                        id = generatePathId("cross-layer", className),
                        description = "跨层级路径: ${archInfo.layer} -> [${archInfo.dependencyLayers.joinToString(", ")}]",
                        methods = listOf("$className.main"),
                        relatedChanges = listOf(file),
                        changeDetails = "跨越 ${archInfo.dependencyLayers.size + 1} 个架构层级"
                    )
                    paths.add(crossLayerPath)
                }
                
            } catch (e: Exception) {
                logger.debug("查询类架构信息失败: $className, 错误: ${e.message}")
            }
        }
        
        logger.debug("发现了 ${paths.size} 个跨层级调用路径")
        return@withContext paths
    }
    
    /**
     * 构建上游调用路径
     */
    private fun buildUpstreamPath(caller: CallerInfo, targetClass: String, targetMethod: String, file: ChangedFile): CallPath {
        return CallPath(
            id = generatePathId("upstream", "${caller.className}.${caller.methodName}->$targetClass.$targetMethod"),
            description = "上游路径: ${caller.className}(${caller.layer}) -> $targetClass.$targetMethod",
            methods = listOf("${caller.className}.${caller.methodName}", "$targetClass.$targetMethod"),
            relatedChanges = listOf(file),
            changeDetails = "调用频次: ${caller.callCount}, 层级: ${caller.layer}"
        )
    }
    
    /**
     * 构建下游调用路径
     */
    private fun buildDownstreamPath(sourceClass: String, sourceMethod: String, callee: CalleeInfo, file: ChangedFile): CallPath {
        return CallPath(
            id = generatePathId("downstream", "$sourceClass.$sourceMethod->${callee.className}.${callee.methodName}"),
            description = "下游路径: $sourceClass.$sourceMethod -> ${callee.className}(${callee.layer})",
            methods = listOf("$sourceClass.$sourceMethod", "${callee.className}.${callee.methodName}"),
            relatedChanges = listOf(file),
            changeDetails = "调用频次: ${callee.callCount}, 目标层级: ${callee.layer}"
        )
    }
    
    /**
     * 构建深层调用者链路（多跳）
     */
    private suspend fun buildDeepCallerChains(targetClass: String, targetMethod: String, file: ChangedFile, maxDepth: Int): List<CallPath> {
        val chains = mutableListOf<CallPath>()
        
        if (maxDepth <= 0) return chains
        
        try {
            val callersInfo = neo4jQueryService.queryMethodCallers(targetClass, targetMethod)
            
            callersInfo.callerDetails.take(3).forEach { caller ->
                // 递归查找调用者的调用者
                val deeperCallers = buildDeepCallerChains(caller.className, caller.methodName, file, maxDepth - 1)
                
                if (deeperCallers.isNotEmpty()) {
                    deeperCallers.forEach { deepChain ->
                        val extendedChain = deepChain.copy(
                            id = generatePathId("deep-chain", deepChain.id + "->$targetClass.$targetMethod"),
                            description = deepChain.description + " -> $targetClass.$targetMethod",
                            methods = deepChain.methods + "$targetClass.$targetMethod",
                            changeDetails = deepChain.changeDetails + "; 深度: ${maxDepth + 1}"
                        )
                        chains.add(extendedChain)
                    }
                } else {
                    // 创建二层路径
                    val twoLayerPath = CallPath(
                        id = generatePathId("two-layer", "${caller.className}.${caller.methodName}->$targetClass.$targetMethod"),
                        description = "二层调用链: ${caller.className}.${caller.methodName} -> $targetClass.$targetMethod",
                        methods = listOf("${caller.className}.${caller.methodName}", "$targetClass.$targetMethod"),
                        relatedChanges = listOf(file),
                        changeDetails = "二层调用链, 频次: ${caller.callCount}"
                    )
                    chains.add(twoLayerPath)
                }
            }
        } catch (e: Exception) {
            logger.debug("构建深层调用者链路失败: $targetClass.$targetMethod, 错误: ${e.message}")
        }
        
        return chains
    }
    
    /**
     * 构建深层被调用者链路（多跳）
     */
    private suspend fun buildDeepCalleeChains(sourceClass: String, sourceMethod: String, file: ChangedFile, maxDepth: Int): List<CallPath> {
        val chains = mutableListOf<CallPath>()
        
        if (maxDepth <= 0) return chains
        
        try {
            val calleesInfo = neo4jQueryService.queryMethodCallees(sourceClass, sourceMethod)
            
            calleesInfo.calleeDetails.take(3).forEach { callee ->
                // 递归查找被调用者的被调用者
                val deeperCallees = buildDeepCalleeChains(callee.className, callee.methodName, file, maxDepth - 1)
                
                if (deeperCallees.isNotEmpty()) {
                    deeperCallees.forEach { deepChain ->
                        val extendedChain = deepChain.copy(
                            id = generatePathId("deep-chain", "$sourceClass.$sourceMethod->" + deepChain.id),
                            description = "$sourceClass.$sourceMethod -> " + deepChain.description,
                            methods = listOf("$sourceClass.$sourceMethod") + deepChain.methods,
                            changeDetails = "深度: ${maxDepth + 1}; " + deepChain.changeDetails
                        )
                        chains.add(extendedChain)
                    }
                } else {
                    // 创建二层路径
                    val twoLayerPath = CallPath(
                        id = generatePathId("two-layer", "$sourceClass.$sourceMethod->${callee.className}.${callee.methodName}"),
                        description = "二层调用链: $sourceClass.$sourceMethod -> ${callee.className}.${callee.methodName}",
                        methods = listOf("$sourceClass.$sourceMethod", "${callee.className}.${callee.methodName}"),
                        relatedChanges = listOf(file),
                        changeDetails = "二层调用链, 频次: ${callee.callCount}"
                    )
                    chains.add(twoLayerPath)
                }
            }
        } catch (e: Exception) {
            logger.debug("构建深层被调用者链路失败: $sourceClass.$sourceMethod, 错误: ${e.message}")
        }
        
        return chains
    }
    
    /**
     * 去除重复路径
     */
    private fun removeDuplicates(paths: List<CallPath>): List<CallPath> {
        val uniquePaths = mutableMapOf<String, CallPath>()
        
        paths.forEach { path ->
            val key = path.methods.sorted().joinToString("->")
            if (!uniquePaths.containsKey(key)) {
                uniquePaths[key] = path
            }
        }
        
        return uniquePaths.values.toList()
    }
    
    /**
     * 过滤相关路径 - 基于业务逻辑和架构重要性
     */
    private fun filterRelevantPaths(paths: List<CallPath>, gitContext: GitDiffContext): List<CallPath> {
        return paths.filter { path ->
            // 基本条件：至少包含一个变更的文件相关的方法
            val hasRelatedChanges = path.relatedChanges.isNotEmpty() ||
                gitContext.changedFiles.any { file -> 
                    path.methods.any { method -> 
                        method.contains(extractClassName(file.path))
                    }
                }
                
            // 过滤条件：排除过于简单或重复的路径
            val isSignificant = path.methods.size > 1 || // 多方法路径更重要
                path.description.contains("跨层级") || // 跨层级路径重要
                path.changeDetails?.contains("频次") == true // 有调用频次信息的重要
                
            // 排除测试文件相关的路径（可配置）
            val isNotTestPath = !path.methods.any { method ->
                method.lowercase().contains("test") || 
                method.lowercase().contains("mock")
            }
            
            hasRelatedChanges && isSignificant && isNotTestPath
        }.take(100) // 增加路径数量限制
    }
    
    /**
     * 按相关性对路径排序
     */
    private fun sortPathsByRelevance(paths: List<CallPath>, gitContext: GitDiffContext): List<CallPath> {
        return paths.sortedWith(compareByDescending<CallPath> { path ->
            var score = 0.0
            
            // 1. 路径长度得分（2-3层最佳）
            score += when (path.methods.size) {
                2 -> 3.0
                3 -> 4.0
                4 -> 2.0
                else -> 1.0
            }
            
            // 2. 跨层级路径得分
            if (path.description.contains("跨层级")) score += 2.0
            
            // 3. 业务关键词得分
            val businessTerms = setOf("controller", "service", "repository", "api", "business")
            val matchingTerms = businessTerms.count { term ->
                path.description.lowercase().contains(term)
            }
            score += matchingTerms * 0.5
            
            // 4. 变更规模得分
            val totalChanges = path.relatedChanges.sumOf { it.addedLines + it.deletedLines }
            score += when {
                totalChanges > 100 -> 2.0
                totalChanges > 50 -> 1.5
                totalChanges > 10 -> 1.0
                else -> 0.5
            }
            
            score
        })
    }
    
    /**
     * 从文件中提取实际的变更方法
     */
    private fun extractChangedMethodsFromFile(file: ChangedFile, className: String): List<String> {
        val methods = mutableSetOf<String>()
        
        file.hunks.forEach { hunk ->
            val hunkMethods = extractMethodsFromHunk(hunk, className)
            methods.addAll(hunkMethods)
        }
        
        // 如果没有找到具体方法，返回一些推测的方法名
        if (methods.isEmpty()) {
            methods.addAll(inferMethodsFromClassName(className))
        }
        
        return methods.toList()
    }
    
    /**
     * 根据类名推测可能的方法
     */
    private fun inferMethodsFromClassName(className: String): List<String> {
        return when {
            className.contains("Controller") -> listOf("handle", "process", "get", "post", "put", "delete")
            className.contains("Service") -> listOf("process", "execute", "handle", "create", "update", "delete")
            className.contains("Repository") -> listOf("save", "find", "delete", "update", "query")
            className.contains("Util") -> listOf("convert", "validate", "format", "process")
            else -> listOf("main", "process", "execute")
        }
    }
    
    // === 工具方法 ===
    
    private fun isJavaOrKotlinFile(path: String): Boolean {
        val lowerPath = path.lowercase()
        return lowerPath.endsWith(".java") || lowerPath.endsWith(".kt")
    }
    
    private fun extractClassName(filePath: String): String {
        val fileName = filePath.substringAfterLast("/")
        return fileName.substringBeforeLast(".")
    }
    
    private fun generatePathId(type: String, signature: String): String {
        return "${type}_${signature.hashCode()}"
    }
    
    /**
     * 从hunk中提取变更的方法名
     */
    private fun extractMethodsFromHunk(hunk: DiffHunk, className: String): List<String> {
        val methods = mutableListOf<String>()
        
        // 分析添加的行
        hunk.lines.filter { it.type == DiffLineType.ADDED }.forEach { line ->
            val methodNames = extractMethodNamesFromLine(line.content)
            methods.addAll(methodNames)
        }
        
        // 分析删除的行
        hunk.lines.filter { it.type == DiffLineType.DELETED }.forEach { line ->
            val methodNames = extractMethodNamesFromLine(line.content)
            methods.addAll(methodNames)
        }
        
        // 如果没有找到方法名，尝试从上下文中推测
        if (methods.isEmpty()) {
            // 查看hunk前后的上下文行来确定在哪个方法内
            val contextMethods = inferMethodFromContext(hunk)
            methods.addAll(contextMethods)
        }
        
        return methods.distinct()
    }
    
    /**
     * 从代码行中提取方法名
     */
    private fun extractMethodNamesFromLine(line: String): List<String> {
        val methods = mutableListOf<String>()
        
        // 1. 检查是否是方法声明
        if (isMethodDeclaration(line)) {
            val methodName = extractMethodName(line)
            methods.add(methodName)
        }
        
        // 2. 检查是否是getter/setter方法
        val getterSetterMethods = extractGetterSetterMethods(line)
        methods.addAll(getterSetterMethods)
        
        // 3. 检查方法调用
        val calledMethods = extractMethodCalls(line)
        methods.addAll(calledMethods)
        
        return methods
    }
    
    /**
     * 提取getter/setter方法
     */
    private fun extractGetterSetterMethods(line: String): List<String> {
        val methods = mutableListOf<String>()
        
        // 匹配 public Type getXxx() 或 public void setXxx(Type param)
        val getterRegex = Regex("""public\s+\w+\s+(get\w+)\s*\(\s*\)""")
        val setterRegex = Regex("""public\s+void\s+(set\w+)\s*\([^)]+\)""")
        
        getterRegex.find(line)?.let { match ->
            methods.add(match.groupValues[1])
        }
        
        setterRegex.find(line)?.let { match ->
            methods.add(match.groupValues[1])
        }
        
        return methods
    }
    
    /**
     * 提取方法调用
     */
    private fun extractMethodCalls(line: String): List<String> {
        val methods = mutableListOf<String>()
        
        // 匹配 object.methodName() 或 methodName() 调用
        val callRegex = Regex("""(\w+)\s*\(""")
        callRegex.findAll(line).forEach { match ->
            val methodName = match.groupValues[1]
            // 排除关键字和类名
            if (!isKeywordOrType(methodName)) {
                methods.add(methodName)
            }
        }
        
        return methods
    }
    
    /**
     * 从上下文推断方法名
     */
    private fun inferMethodFromContext(hunk: DiffHunk): List<String> {
        // 简化实现：如果变更很小，可能是在某个方法内部
        if (hunk.lines.size <= 5) {
            return listOf("inferredMethod")
        }
        return emptyList()
    }
    
    /**
     * 提取变更详情
     */
    private fun extractChangeDetails(hunk: DiffHunk, methodName: String): String {
        val addedLines = hunk.lines.count { it.type == DiffLineType.ADDED }
        val removedLines = hunk.lines.count { it.type == DiffLineType.DELETED }
        
        val details = mutableListOf<String>()
        details.add("方法 $methodName")
        if (addedLines > 0) details.add("新增 $addedLines 行")
        if (removedLines > 0) details.add("删除 $removedLines 行")
        
        // 分析变更类型
        val changeTypes = analyzeChangeType(hunk)
        if (changeTypes.isNotEmpty()) {
            details.add("变更类型: ${changeTypes.joinToString(", ")}")
        }
        
        return details.joinToString(", ")
    }
    
    /**
     * 分析变更类型
     */
    private fun analyzeChangeType(hunk: DiffHunk): List<String> {
        val types = mutableListOf<String>()
        
        hunk.lines.forEach { line ->
            when {
                line.content.contains("if (") -> types.add("条件逻辑")
                line.content.contains("for (") || line.content.contains("while (") -> types.add("循环逻辑")
                line.content.contains("try {") || line.content.contains("catch (") -> types.add("异常处理")
                line.content.contains("return ") -> types.add("返回值")
                line.content.contains("new ") -> types.add("对象创建")
                line.content.contains("log.") || line.content.contains("logger.") -> types.add("日志")
            }
        }
        
        return types.distinct()
    }
    
    /**
     * 判断是否为关键字或类型名
     */
    private fun isKeywordOrType(word: String): Boolean {
        val keywords = setOf("if", "for", "while", "try", "catch", "return", "new", "this", "super", 
                             "String", "Integer", "List", "Map", "Set", "Object", "Class")
        return keywords.contains(word) || word.first().isUpperCase()
    }
}

/**
 * 方法签名数据类
 */
data class MethodSignature(
    val name: String,
    val signature: String,
    val fullSignature: String
)