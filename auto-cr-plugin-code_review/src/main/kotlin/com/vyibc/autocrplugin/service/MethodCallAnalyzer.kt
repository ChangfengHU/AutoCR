
package com.vyibc.autocrplugin.service

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Computable
import java.io.File

/**
 * 方法调用分析器
 * 用于分析代码变更中涉及的方法调用，并获取被调用方法的实现
 */
class MethodCallAnalyzer(
    private val project: Project,
    private val maxCascadeDepth: Int = 1
) {

    private var inferredProjectPath: String? = null
    var debugCallback: ((String) -> Unit)? = null

    /**
     * 分析代码变更中的方法调用
     */
    fun analyzeMethodCalls(changes: List<CodeChange>): List<MethodCallInfo> {
        val methodCalls = mutableListOf<MethodCallInfo>()

        // 获取项目根路径（基于第一个文件的路径推断）
        if (changes.isNotEmpty()) {
            val firstFilePath = changes.first().filePath
            inferredProjectPath = inferProjectRootPath(firstFilePath)
            debugCallback?.invoke("🔍 推断的项目根路径: $inferredProjectPath")
        }

        changes.forEach { change ->
            if (change.changeType == ChangeType.MODIFIED || change.changeType == ChangeType.ADDED) {
                // 分析新增和修改的代码行
                val addedCalls = analyzeCodeLines(change.addedLines, change.filePath)
                val modifiedCalls = change.modifiedLines.flatMap { (_, newLine) ->
                    analyzeCodeLines(listOf(newLine), change.filePath)
                }

                methodCalls.addAll(addedCalls)
                methodCalls.addAll(modifiedCalls)
            }
        }

        return methodCalls.distinctBy { "${it.className}.${it.methodName}" }
    }

    /**
     * 根据文件路径推断项目根路径
     */
    private fun inferProjectRootPath(filePath: String): String {
        val file = File(filePath)
        var current = file.parentFile

        // 向上查找，直到找到包含src目录的路径
        while (current != null) {
            val srcDir = File(current, "src")
            if (srcDir.exists() && srcDir.isDirectory) {
                return current.absolutePath
            }
            current = current.parentFile
        }

        // 如果找不到src目录，返回文件的上级目录
        return file.parentFile?.absolutePath ?: filePath
    }

    /**
     * 分析代码行中的方法调用
     */
    private fun analyzeCodeLines(lines: List<String>, filePath: String): List<MethodCallInfo> {
        val methodCalls = mutableListOf<MethodCallInfo>()

        lines.forEach { line ->
            // 使用正则表达式匹配方法调用
            val methodCallPattern = Regex("""(\w+)\.(\w+)\s*\(""")
            val matches = methodCallPattern.findAll(line)

            matches.forEach { match ->
                val className = match.groupValues[1]
                val methodName = match.groupValues[2]

                debugCallback?.invoke("🔍 检测到方法调用: $className.$methodName() 在行: ${line.trim()}")

                // 智能过滤：只分析值得关注的方法调用
                if (!shouldAnalyzeMethodCall(className, methodName, line)) {
                    debugCallback?.invoke("⏭️ 跳过不重要的方法调用: $className.$methodName")
                    return@forEach
                }

                // 查找方法实现 - 优先使用 PSI API
                val methodImpl = findMethodImplementationWithPSI(className, methodName)
                if (methodImpl != null) {
                    debugCallback?.invoke("✅ 找到方法实现: ${methodImpl.filePath}")
                    methodCalls.add(
                        MethodCallInfo(
                            className = className,
                            methodName = methodName,
                            callerFile = filePath,
                            callerLine = line.trim(),
                            implementation = methodImpl
                        )
                    )
                } else {
                    debugCallback?.invoke("❌ 未找到方法实现: $className.$methodName")
                }
            }
        }

        return methodCalls
    }

    /**
     * 智能判断是否需要分析该方法调用
     */
    private fun shouldAnalyzeMethodCall(className: String, methodName: String, codeLine: String): Boolean {
        debugCallback?.invoke("🔍 判断是否分析方法: $className.$methodName")
        
        // 1. 排除常见的不重要方法调用
        if (isExcludedMethod(className, methodName)) {
            debugCallback?.invoke("❌ 排除方法: $className.$methodName (属于排除列表)")
            return false
        }

        // 2. 优先分析可能有风险的方法调用
        if (isPotentiallyDangerousCall(className, methodName, codeLine)) {
            debugCallback?.invoke("✅ 包含方法: $className.$methodName (潜在危险调用)")
            return true
        }

        // 3. 检查是否为注入的服务类调用
        if (isInjectedServiceCall(className)) {
            debugCallback?.invoke("✅ 包含方法: $className.$methodName (注入的服务类)")
            return true
        }

        // 4. 默认跳过其他方法调用
        debugCallback?.invoke("❌ 跳过方法: $className.$methodName (不符合任何条件)")
        return false
    }

    /**
     * 检查是否为排除的方法类型
     */
    private fun isExcludedMethod(className: String, methodName: String): Boolean {
        // 排除简单的 getter/setter 方法，但保留可能有业务逻辑的方法
        if (methodName.startsWith("get") && methodName.length <= 15 && !methodName.contains("All") && !methodName.contains("By")) {
            return true
        }
        if (methodName.startsWith("set") && methodName.length <= 15) {
            return true
        }
        if (methodName.startsWith("is") && methodName.length <= 10) {
            return true
        }

        // 排除日志相关方法
        val logClasses = listOf("log", "logger", "Logger", "LOG")
        if (logClasses.any { className.contains(it, ignoreCase = true) }) {
            return true
        }

        // 排除常见工具方法
        val utilityMethods = listOf("toString", "valueOf", "equals", "hashCode", "size", "isEmpty", "length")
        if (utilityMethods.contains(methodName)) {
            return true
        }

        // 排除 DTO/VO/Model 的简单属性调用
        val dataClasses = listOf("Request", "Response", "DTO", "VO", "Model", "Entity", "Bean")
        if (dataClasses.any { className.contains(it, ignoreCase = true) }) {
            return true
        }

        return false
    }

    /**
     * 检查是否为潜在危险的方法调用
     */
    private fun isPotentiallyDangerousCall(className: String, methodName: String, codeLine: String): Boolean {
        // 数据库/缓存相关的危险操作
        val dangerousMethods = listOf(
            "delete", "remove", "clear", "drop", "truncate", "update", "insert",
            "keys", "scan", "flushall", "flushdb", "eval", "exec", "multi",
            "execute", "query", "save", "persist", "merge"
        )

        if (dangerousMethods.any { methodName.contains(it, ignoreCase = true) }) {
            return true
        }

        // 缓存/Redis 相关类（包括变量名）
        val cacheClasses = listOf("cache", "redis", "Cache", "Redis", "redisUtils", "cacheUtils")
        if (cacheClasses.any { className.contains(it, ignoreCase = true) }) {
            return true
        }
        
        // 特殊处理：包含 Value、Key、Cache 等关键词的方法名
        val cacheMethodKeywords = listOf("value", "key", "cache", "redis", "get", "set", "put", "remove")
        if (cacheMethodKeywords.any { methodName.contains(it, ignoreCase = true) }) {
            return true
        }

        // 包含危险关键词的代码行
        val dangerousKeywords = listOf("redis", "cache", "database", "sql", "delete", "remove")
        if (dangerousKeywords.any { codeLine.contains(it, ignoreCase = true) }) {
            return true
        }

        return false
    }

    /**
     * 检查是否为注入的服务类调用
     */
    private fun isInjectedServiceCall(className: String): Boolean {
        return ApplicationManager.getApplication().runReadAction(Computable {
            try {
                // 查找变量声明，检查是否有 @Autowired 等注解
                val psiManager = PsiManager.getInstance(project)
                val scope = GlobalSearchScope.projectScope(project)
                val virtualFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)

                for (virtualFile in virtualFiles) {
                    val psiFile = psiManager.findFile(virtualFile) as? PsiJavaFile ?: continue

                    // 查找变量声明
                    val variables = PsiTreeUtil.findChildrenOfType(psiFile, PsiVariable::class.java)
                    for (variable in variables) {
                        if (variable.name == className) {
                            // 检查是否有依赖注入注解
                            val annotations = variable.annotations
                            for (annotation in annotations) {
                                val annotationName = annotation.qualifiedName
                                if (annotationName != null && (
                                            annotationName.contains("Autowired") ||
                                                    annotationName.contains("Resource") ||
                                                    annotationName.contains("Inject") ||
                                                    annotationName.contains("Component") ||
                                                    annotationName.contains("Service")
                                            )) {
                                    return@Computable true
                                }
                            }

                            // 检查变量类型是否为 Service 类
                            val variableType = variable.type
                            if (variableType is PsiClassType) {
                                val typeName = variableType.className
                                if (typeName != null && (
                                            typeName.contains("Service") ||
                                                    typeName.contains("Repository") ||
                                                    typeName.contains("Component") ||
                                                    typeName.contains("Manager")
                                            )) {
                                    return@Computable true
                                }
                            }
                        }
                    }
                }

                return@Computable false
            } catch (e: Exception) {
                return@Computable false
            }
        })
    }

    /**
     * 使用 PSI API 查找方法实现的新版本
     */
    private fun findMethodImplementationWithPSI(className: String, methodName: String, depth: Int = 0): MethodImplementation? {
        if (depth > maxCascadeDepth) return null

        return ApplicationManager.getApplication().runReadAction(Computable {
            try {
                // 1. 首先尝试使用 PSI API 查找
                val psiMethodImpl = findMethodUsingPSI(className, methodName, depth)
                if (psiMethodImpl != null) {
                    return@Computable psiMethodImpl
                }

                // 2. 如果 PSI API 找不到，回退到文件系统搜索
                return@Computable findMethodUsingFileSystem(className, methodName, depth)

            } catch (e: Exception) {
                debugCallback?.invoke("❌ 查找方法实现时出错: ${e.message}")
                return@Computable null
            }
        })
    }

    /**
     * 使用 PSI API 查找方法实现
     */
    private fun findMethodUsingPSI(className: String, methodName: String, depth: Int): MethodImplementation? {
        try {
            val facade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)

            // 1. 先尝试直接查找类（处理类名的情况）
            var psiClass = facade.findClass(className, scope)

            // 2. 如果找不到，可能 className 是变量名，需要查找变量类型
            if (psiClass == null) {
                debugCallback?.invoke("🔍 未找到类 $className，尝试解析为变量类型...")
                psiClass = findClassByVariableName(className)
            }

            if (psiClass != null) {
                debugCallback?.invoke("✅ 使用 PSI API 找到类: ${psiClass.qualifiedName}")

                // 查找方法
                val methods = psiClass.findMethodsByName(methodName, true)
                val targetMethod = methods.firstOrNull()

                if (targetMethod != null) {
                    debugCallback?.invoke("✅ 使用 PSI API 找到方法: ${targetMethod.name}")

                    // 检查是否为接口方法或抽象方法，如果是则查找实现类
                    val implementationMethod = findConcreteImplementation(targetMethod, psiClass)
                    val actualMethod = implementationMethod ?: targetMethod
                    val actualClass = implementationMethod?.containingClass ?: psiClass

                    val sourceCode = actualMethod.text
                    val filePath = actualMethod.containingFile.virtualFile?.path ?: ""

                    if (implementationMethod != null) {
                        debugCallback?.invoke("✅ 找到具体实现类: ${actualClass.name} (原接口: ${psiClass.name})")
                    }

                    // 分析方法内的级联调用
                    val cascadedMethods = findCascadedMethodsUsingPSI(actualMethod, depth + 1)

                    return MethodImplementation(
                        className = actualClass.name ?: className,
                        methodName = actualMethod.name,
                        filePath = filePath,
                        sourceCode = sourceCode,
                        containsDangerousOperations = checkForDangerousOperations(sourceCode),
                        cascadedMethods = cascadedMethods
                    )
                }
            } else {
                debugCallback?.invoke("❌ PSI API 未找到类或变量类型: $className")
            }
        } catch (e: Exception) {
            debugCallback?.invoke("❌ PSI API 查找失败: ${e.message}")
        }

        return null
    }

    /**
     * 通过变量名查找其类型对应的 PsiClass
     */
    private fun findClassByVariableName(variableName: String): PsiClass? {
        try {
            val facade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)

            // 搜索所有 Java 文件中的变量声明
            val psiManager = PsiManager.getInstance(project)
            val virtualFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)

            for (virtualFile in virtualFiles) {
                val psiFile = psiManager.findFile(virtualFile) as? PsiJavaFile ?: continue

                // 查找变量声明
                val variables = PsiTreeUtil.findChildrenOfType(psiFile, PsiVariable::class.java)
                for (variable in variables) {
                    if (variable.name == variableName) {
                        val variableType = variable.type
                        if (variableType is PsiClassType) {
                            val resolvedClass = variableType.resolve()
                            if (resolvedClass != null) {
                                debugCallback?.invoke("✅ 找到变量 $variableName 的类型: ${resolvedClass.qualifiedName}")
                                return resolvedClass
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            debugCallback?.invoke("❌ 查找变量类型失败: ${e.message}")
        }

        return null
    }

    /**
     * 查找接口方法的具体实现类
     */
    private fun findConcreteImplementation(method: PsiMethod, originalClass: PsiClass): PsiMethod? {
        // 如果方法已经有具体实现（非抽象），直接返回
        if (!method.hasModifierProperty(PsiModifier.ABSTRACT) && method.body != null) {
            debugCallback?.invoke("🔍 方法已有具体实现: ${method.name}")
            return null // 返回 null 表示使用原方法
        }

        debugCallback?.invoke("🔍 查找接口/抽象方法的实现: ${originalClass.name}.${method.name}")

        try {
            val facade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)

            // 查找所有 Java 文件
            val psiManager = PsiManager.getInstance(project)
            val virtualFiles = FilenameIndex.getAllFilesByExt(project, "java", scope)

            for (virtualFile in virtualFiles) {
                val psiFile = psiManager.findFile(virtualFile) as? PsiJavaFile ?: continue

                // 查找文件中的所有类
                val classes = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
                for (psiClass in classes) {
                    // 检查是否实现了目标接口或继承了抽象类
                    if (isImplementationOf(psiClass, originalClass)) {
                        // 查找对应的方法实现
                        val implementedMethods = psiClass.findMethodsBySignature(method, false)
                        val implementedMethod = implementedMethods.firstOrNull()

                        if (implementedMethod != null && implementedMethod.body != null) {
                            debugCallback?.invoke("✅ 找到具体实现: ${psiClass.name}.${implementedMethod.name}")
                            return implementedMethod
                        }
                    }
                }
            }

            debugCallback?.invoke("❌ 未找到具体实现: ${originalClass.name}.${method.name}")
        } catch (e: Exception) {
            debugCallback?.invoke("❌ 查找具体实现失败: ${e.message}")
        }

        return null
    }

    /**
     * 检查类是否实现了指定的接口或继承了抽象类
     */
    private fun isImplementationOf(implementationClass: PsiClass, interfaceOrAbstractClass: PsiClass): Boolean {
        // 检查直接实现的接口
        implementationClass.implementsList?.referenceElements?.forEach { ref ->
            val resolved = ref.resolve()
            if (resolved == interfaceOrAbstractClass) {
                return true
            }
        }

        // 检查继承的类
        implementationClass.extendsList?.referenceElements?.forEach { ref ->
            val resolved = ref.resolve()
            if (resolved == interfaceOrAbstractClass) {
                return true
            }
        }

        // 检查间接继承/实现
        val superTypes = implementationClass.superTypes
        for (superType in superTypes) {
            val resolved = superType.resolve()
            if (resolved == interfaceOrAbstractClass) {
                return true
            }
            // 递归检查父类的父类
            if (resolved != null && isImplementationOf(resolved, interfaceOrAbstractClass)) {
                return true
            }
        }

        return false
    }

    /**
     * 使用 PSI API 查找方法内的级联调用
     */
    private fun findCascadedMethodsUsingPSI(method: PsiMethod, depth: Int): List<MethodImplementation> {
        if (depth > maxCascadeDepth) return emptyList()

        val cascadedMethods = mutableListOf<MethodImplementation>()

        try {
            // 查找方法体中的所有方法调用表达式
            val methodCalls = PsiTreeUtil.findChildrenOfType(method, PsiMethodCallExpression::class.java)

            methodCalls.forEach { call ->
                val resolvedMethod = call.resolveMethod()
                if (resolvedMethod != null && resolvedMethod != method) { // 避免自递归
                    val containingClass = resolvedMethod.containingClass
                    if (containingClass != null) {
                        val cascadedClassName = containingClass.name ?: ""
                        val cascadedMethodName = resolvedMethod.name

                        debugCallback?.invoke("🔍 PSI API 发现级联调用: $cascadedClassName.$cascadedMethodName")

                        // 创建级联方法的实现信息
                        val cascadedImpl = MethodImplementation(
                            className = cascadedClassName,
                            methodName = cascadedMethodName,
                            filePath = resolvedMethod.containingFile.virtualFile?.path ?: "",
                            sourceCode = resolvedMethod.text,
                            containsDangerousOperations = checkForDangerousOperations(resolvedMethod.text),
                            cascadedMethods = if (depth < maxCascadeDepth) {
                                findCascadedMethodsUsingPSI(resolvedMethod, depth + 1)
                            } else emptyList()
                        )

                        cascadedMethods.add(cascadedImpl)
                    }
                }
            }
        } catch (e: Exception) {
            debugCallback?.invoke("❌ PSI API 级联分析失败: ${e.message}")
        }

        return cascadedMethods.distinctBy { "${it.className}.${it.methodName}" }
    }

    /**
     * 文件系统搜索方法（备用方案）
     */
    private fun findMethodUsingFileSystem(className: String, methodName: String, depth: Int): MethodImplementation? {
        val classFile = findClassFile(className)
        if (classFile != null && classFile.exists()) {
            val content = classFile.readText()
            val methodImpl = extractMethodImplementation(content, methodName)
            if (methodImpl != null) {
                // 分析方法内的调用并获取级联方法
                val cascadedMethods = findCascadedMethods(methodImpl, depth + 1)

                return MethodImplementation(
                    className = className,
                    methodName = methodName,
                    filePath = classFile.absolutePath,
                    sourceCode = methodImpl,
                    containsDangerousOperations = checkForDangerousOperations(methodImpl),
                    cascadedMethods = cascadedMethods
                )
            }
        }
        return null
    }

    /**
     * 查找方法实现（包含级联分析）- 旧版本，保留作为备用
     */
    private fun findMethodImplementation(className: String, methodName: String, depth: Int = 0): MethodImplementation? {
        if (depth > maxCascadeDepth) return null // 防止无限递归

        try {

            // 简化实现：通过文件系统搜索
            val classFile = findClassFile(className)
            if (classFile != null && classFile.exists()) {
                val content = classFile.readText()
                val methodImpl = extractMethodImplementation(content, methodName)
                if (methodImpl != null) {
                    // 分析方法内的调用并获取级联方法
                    val cascadedMethods = findCascadedMethods(methodImpl, depth + 1)

                    return MethodImplementation(
                        className = className,
                        methodName = methodName,
                        filePath = classFile.absolutePath,
                        sourceCode = methodImpl,
                        containsDangerousOperations = checkForDangerousOperations(methodImpl),
                        cascadedMethods = cascadedMethods
                    )
                }
            }
        } catch (e: Exception) {
            debugCallback?.invoke("❌ 文件分析出错: ${e.message}")
        }

        return null
    }

    /**
     * 查找方法内部的级联调用
     */
    private fun findCascadedMethods(methodCode: String, depth: Int): List<MethodImplementation> {
        val cascadedMethods = mutableListOf<MethodImplementation>()

        // 使用正则表达式匹配方法调用
        val methodCallPattern = Regex("""(\w+)\.(\w+)\s*\(""")
        val matches = methodCallPattern.findAll(methodCode)

        matches.forEach { match ->
            val cascadedClassName = match.groupValues[1]
            val cascadedMethodName = match.groupValues[2]

            // 递归查找级联方法的实现
            val cascadedImpl = findMethodImplementation(cascadedClassName, cascadedMethodName, depth)
            if (cascadedImpl != null) {
                cascadedMethods.add(cascadedImpl)
            }
        }

        return cascadedMethods.distinctBy { "${it.className}.${it.methodName}" }
    }

    /**
     * 查找类文件
     */
    private fun findClassFile(className: String): File? {
        val projectPath = inferredProjectPath ?: project.basePath ?: return null
        val projectDir = File(projectPath)

        debugCallback?.invoke("🔍 搜索类文件: $className 在项目路径: $projectPath")

        // 在src目录下搜索Java文件
        val result = findFileRecursively(projectDir, "${className}.java")
        if (result != null) {
            debugCallback?.invoke("✅ 找到类文件: ${result.absolutePath}")
        } else {
            debugCallback?.invoke("❌ 未找到类文件: ${className}.java")
        }

        return result
    }

    /**
     * 递归查找文件
     */
    private fun findFileRecursively(dir: File, fileName: String): File? {
        if (!dir.exists() || !dir.isDirectory) return null

        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                val found = findFileRecursively(file, fileName)
                if (found != null) return found
            } else if (file.name == fileName) {
                return file
            }
        }

        return null
    }

    /**
     * 提取方法实现
     */
    private fun extractMethodImplementation(classContent: String, methodName: String): String? {
        debugCallback?.invoke("🔍 开始提取方法: $methodName")

        // 针对您的getValue方法的特定模式: public static Set<String> getValue(String key) {
        val specificPattern = """public\s+static\s+Set<String>\s+$methodName\s*\([^)]*\)\s*\{"""

        // 更通用的模式
        val generalPatterns = listOf(
            // 精确匹配泛型返回类型
            Regex("""(public|private|protected)?\s*(static)?\s*Set<\w+>\s+$methodName\s*\([^)]*\)\s*\{""", RegexOption.MULTILINE),
            // 匹配任何泛型返回类型
            Regex("""(public|private|protected)?\s*(static)?\s*\w+<[^>]+>\s+$methodName\s*\([^)]*\)\s*\{""", RegexOption.MULTILINE),
            // 匹配简单返回类型
            Regex("""(public|private|protected)?\s*(static)?\s*\w+\s+$methodName\s*\([^)]*\)\s*\{""", RegexOption.MULTILINE),
            // 最宽松匹配
            Regex(""".*$methodName\s*\([^)]*\)\s*\{""", RegexOption.MULTILINE)
        )

        var match: MatchResult? = null
        var startIndex = -1

        // 先尝试特定模式
        debugCallback?.invoke("🔍 尝试特定模式: $specificPattern")
        match = Regex(specificPattern, RegexOption.MULTILINE).find(classContent)

        if (match != null) {
            startIndex = match.range.first
            debugCallback?.invoke("✅ 特定模式匹配成功: ${match.value}")
        } else {
            // 尝试通用模式
            for ((index, pattern) in generalPatterns.withIndex()) {
                debugCallback?.invoke("🔍 尝试通用模式 $index: ${pattern.pattern}")
                match = pattern.find(classContent)
                if (match != null) {
                    startIndex = match.range.first
                    debugCallback?.invoke("✅ 通用模式 $index 匹配成功: ${match.value}")
                    break
                }
            }
        }

        if (match == null || startIndex == -1) {
            debugCallback?.invoke("❌ 所有正则模式都无法匹配方法: $methodName")

            // 作为最后手段，尝试简单搜索并显示上下文
            val simpleSearch = classContent.indexOf("$methodName(")
            if (simpleSearch != -1) {
                debugCallback?.invoke("💡 找到方法名位置: $simpleSearch")
                val lineStart = classContent.lastIndexOf('\n', simpleSearch) + 1
                val lineEnd = classContent.indexOf('\n', simpleSearch).let { if (it == -1) classContent.length else it }
                val currentLine = classContent.substring(lineStart, lineEnd)
                debugCallback?.invoke("💡 当前行内容: '$currentLine'")

                // 显示更多上下文
                val contextStart = maxOf(0, simpleSearch - 200)
                val contextEnd = minOf(classContent.length, simpleSearch + 200)
                val context = classContent.substring(contextStart, contextEnd)
                debugCallback?.invoke("💡 上下文代码:\n$context")
            }
            return null
        }

        // 找到方法的结束位置（匹配大括号）
        var braceCount = 0
        var endIndex = startIndex
        var inMethod = false

        for (i in startIndex until classContent.length) {
            val char = classContent[i]
            if (char == '{') {
                braceCount++
                inMethod = true
            } else if (char == '}') {
                braceCount--
                if (inMethod && braceCount == 0) {
                    endIndex = i + 1
                    break
                }
            }
        }

        val result = if (endIndex > startIndex) {
            val methodCode = classContent.substring(startIndex, endIndex)
            debugCallback?.invoke("✅ 成功提取方法实现，长度: ${methodCode.length} 字符")
            debugCallback?.invoke("📝 方法代码预览: ${methodCode.take(100)}...")
            methodCode
        } else {
            debugCallback?.invoke("❌ 无法确定方法结束位置")
            null
        }

        return result
    }

    /**
     * 检查方法实现中是否包含危险操作
     */
    private fun checkForDangerousOperations(methodCode: String): List<String> {
        val dangerousOperations = mutableListOf<String>()

        // 检查Redis危险命令
        val redisDangerousPatterns = listOf(
            Regex("""(stringRedisTemplate|redisTemplate|jedis)\.keys\s*\(""", RegexOption.IGNORE_CASE) to "Redis keys() 模式匹配（可能导致性能问题）",
            Regex("""\.keys\s*\(""", RegexOption.IGNORE_CASE) to "Redis keys() 调用",
            Regex("""\.flushdb\s*\(""", RegexOption.IGNORE_CASE) to "Redis flushdb() 清空数据库",
            Regex("""\.flushall\s*\(""", RegexOption.IGNORE_CASE) to "Redis flushall() 清空所有数据库",
            Regex("""\.config\s*\(""", RegexOption.IGNORE_CASE) to "Redis config() 配置操作"
        )

        redisDangerousPatterns.forEach { (pattern, description) ->
            if (pattern.containsMatchIn(methodCode)) {
                dangerousOperations.add("Redis危险操作: $description")
            }
        }

        // 检查SQL危险操作
        val sqlDangerousPatterns = listOf(
            "select \\* from", "delete from.*where", "update.*set.*where"
        )

        sqlDangerousPatterns.forEach { pattern ->
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(methodCode)) {
                dangerousOperations.add("SQL危险操作: $pattern")
            }
        }

        return dangerousOperations
    }
}

/**
 * 方法调用信息
 */
data class MethodCallInfo(
    val className: String,
    val methodName: String,
    val callerFile: String,
    val callerLine: String,
    val implementation: MethodImplementation
)

/**
 * 方法实现信息
 */
data class MethodImplementation(
    val className: String,
    val methodName: String,
    val filePath: String,
    val sourceCode: String,
    val containsDangerousOperations: List<String>,
    val cascadedMethods: List<MethodImplementation> = emptyList()
)
