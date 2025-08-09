package com.vyibc.autocr.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 知识图谱构建器
 */
class KnowledgeGraphBuilder(private val project: Project) {
    
    private val logger = LoggerFactory.getLogger(KnowledgeGraphBuilder::class.java)
    private val layerDetector = LayerDetector()
    private val callAnalyzer = CallRelationAnalyzer()
    
    fun buildGraph(indicator: ProgressIndicator): KnowledgeGraph {
        val metadata = GraphMetadata(
            projectName = project.name,
            buildTime = System.currentTimeMillis(),
            version = "1.0.0"
        )
        val graph = KnowledgeGraph(metadata = metadata)
        
        indicator.text = "正在收集项目中的所有类..."
        indicator.fraction = 0.0
        
        // 1. 收集所有项目类
        val allClasses = collectProjectClasses(indicator)
        logger.info("Found ${allClasses.size} classes in project")
        
        indicator.text = "正在分析类结构..."
        indicator.fraction = 0.2
        
        // 2. 分析类结构，创建类节点和方法节点
        analyzeClassStructure(allClasses, graph, indicator)
        
        indicator.text = "正在分析方法调用关系..."
        indicator.fraction = 0.6
        
        // 3. 分析调用关系
        analyzeCallRelations(graph, indicator)
        
        indicator.text = "正在完成图谱构建..."
        indicator.fraction = 0.9
        
        logger.info("Knowledge graph built: ${graph.classes.size} classes, ${graph.methods.size} methods, ${graph.edges.size} edges")
        
        indicator.fraction = 1.0
        return graph
    }
    
    private fun collectProjectClasses(@Suppress("UNUSED_PARAMETER") indicator: ProgressIndicator): List<PsiClass> {
        return ApplicationManager.getApplication().runReadAction<List<PsiClass>> {
            val classes = mutableListOf<PsiClass>()
            val scope = GlobalSearchScope.projectScope(project)
            
            AllClassesSearch.search(scope, project).forEach { psiClass ->
                if (isProjectClass(psiClass)) {
                    classes.add(psiClass)
                }
            }
            
            classes
        }
    }
    
    private fun analyzeClassStructure(
        allClasses: List<PsiClass>, 
        graph: KnowledgeGraph, 
        indicator: ProgressIndicator
    ) {
        val total = allClasses.size
        
        allClasses.forEachIndexed { index, psiClass ->
            if (indicator.isCanceled) return
            
            indicator.text = "正在分析类: ${psiClass.name}"
            indicator.fraction = 0.2 + (0.4 * index / total)
            
            ApplicationManager.getApplication().runReadAction {
                try {
                    // 创建类节点
                    val classBlock = createClassBlock(psiClass)
                    graph.addClass(classBlock)
                    
                    // 创建方法节点
                    psiClass.methods.forEach { method ->
                        val methodNode = createMethodNode(method, classBlock.id)
                        graph.addMethod(methodNode)
                    }
                    
                    // 处理构造函数
                    psiClass.constructors.forEach { constructor ->
                        val constructorNode = createConstructorNode(constructor, classBlock.id)
                        graph.addMethod(constructorNode)
                    }
                    
                } catch (e: Exception) {
                    logger.warn("Failed to analyze class: ${psiClass.name}", e)
                }
            }
        }
    }
    
    private fun analyzeCallRelations(graph: KnowledgeGraph, indicator: ProgressIndicator) {
        val methods = graph.methods
        val total = methods.size
        var totalEdges = 0
        var failedMethods = 0
        
        logger.info("开始分析 ${total} 个方法的调用关系...")
        
        methods.forEachIndexed { index, method ->
            if (indicator.isCanceled) return
            
            indicator.text = "正在分析调用关系: ${method.name}"
            indicator.fraction = 0.6 + (0.3 * index / total)
            
            ApplicationManager.getApplication().runReadAction {
                try {
                    val psiMethod = findPsiMethod(method)
                    if (psiMethod != null) {
                        val edges = callAnalyzer.analyzeMethodCalls(psiMethod, graph)
                        if (edges.isNotEmpty()) {
                            logger.debug("方法 ${method.name} 找到 ${edges.size} 个调用关系")
                            totalEdges += edges.size
                        }
                        edges.forEach { graph.addEdge(it) }
                    } else {
                        failedMethods++
                        logger.debug("未找到PSI方法: ${method.id}")
                    }
                } catch (e: Exception) {
                    logger.warn("Failed to analyze calls for method: ${method.name}", e)
                    failedMethods++
                }
            }
        }
        
        logger.info("调用关系分析完成：总边数 $totalEdges，失败方法数 $failedMethods")
    }
    
    private fun createClassBlock(psiClass: PsiClass): ClassBlock {
        val layer = layerDetector.detectLayer(psiClass)
        val annotations = psiClass.annotations.mapNotNull { it.qualifiedName }
        val superClass = psiClass.superClass?.qualifiedName
        val interfaces = psiClass.interfaces.mapNotNull { it.qualifiedName }
        val filePath = psiClass.containingFile?.virtualFile?.path ?: ""
        
        return ClassBlock(
            id = psiClass.qualifiedName ?: psiClass.name ?: "Unknown",
            name = psiClass.name ?: "Unknown",
            qualifiedName = psiClass.qualifiedName ?: "",
            packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: "",
            layer = layer,
            annotations = annotations,
            superClass = superClass,
            interfaces = interfaces,
            isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT),
            isInterface = psiClass.isInterface,
            methodCount = psiClass.methods.size,
            filePath = filePath
        )
    }
    
    private fun createMethodNode(method: PsiMethod, classId: String): MethodNode {
        val parameters = method.parameterList.parameters.map { param ->
            Parameter(
                name = param.name,
                type = param.type.presentableText,
                isVarArgs = param.isVarArgs
            )
        }
        
        val signature = buildSignature(method)
        val methodId = "${classId}.${signature}"
        
        return MethodNode(
            id = methodId,
            name = method.name,
            signature = signature,
            classId = classId,
            returnType = method.returnType?.presentableText ?: "void",
            parameters = parameters,
            modifiers = extractModifiers(method),
            lineNumber = getLineNumber(method),
            isConstructor = method.isConstructor,
            isStatic = method.hasModifierProperty(PsiModifier.STATIC),
            isPrivate = method.hasModifierProperty(PsiModifier.PRIVATE),
            isPublic = method.hasModifierProperty(PsiModifier.PUBLIC),
            isAbstract = method.hasModifierProperty(PsiModifier.ABSTRACT)
        )
    }
    
    private fun createConstructorNode(constructor: PsiMethod, classId: String): MethodNode {
        val parameters = constructor.parameterList.parameters.map { param ->
            Parameter(
                name = param.name,
                type = param.type.presentableText,
                isVarArgs = param.isVarArgs
            )
        }
        
        val signature = buildSignature(constructor)
        val methodId = "${classId}.<init>${signature}"
        
        return MethodNode(
            id = methodId,
            name = "<init>",
            signature = signature,
            classId = classId,
            returnType = "void",
            parameters = parameters,
            modifiers = extractModifiers(constructor),
            lineNumber = getLineNumber(constructor),
            isConstructor = true,
            isStatic = false,
            isPrivate = constructor.hasModifierProperty(PsiModifier.PRIVATE),
            isPublic = constructor.hasModifierProperty(PsiModifier.PUBLIC),
            isAbstract = false
        )
    }
    
    private fun buildSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(",") { 
            it.type.presentableText 
        }
        return "${method.name}($params)"
    }
    
    private fun extractModifiers(method: PsiMethod): Set<String> {
        val modifiers = mutableSetOf<String>()
        if (method.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public")
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private")
        if (method.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected")
        if (method.hasModifierProperty(PsiModifier.STATIC)) modifiers.add("static")
        if (method.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final")
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract")
        if (method.hasModifierProperty(PsiModifier.SYNCHRONIZED)) modifiers.add("synchronized")
        return modifiers
    }
    
    private fun getLineNumber(method: PsiMethod): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(method.containingFile)
        return if (document != null) {
            document.getLineNumber(method.textOffset) + 1
        } else {
            -1
        }
    }
    
    private fun findPsiMethod(methodNode: MethodNode): PsiMethod? {
        return ApplicationManager.getApplication().runReadAction<PsiMethod?> {
            try {
                val psiClass = JavaPsiFacade.getInstance(project)
                    .findClass(methodNode.classId, GlobalSearchScope.projectScope(project))
                
                if (psiClass != null) {
                    if (methodNode.isConstructor) {
                        psiClass.constructors.find { constructor ->
                            buildSignature(constructor) == methodNode.signature
                        }
                    } else {
                        psiClass.methods.find { method ->
                            buildSignature(method) == methodNode.signature
                        }
                    }
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.warn("Failed to find PSI method for: ${methodNode.id}", e)
                null
            }
        }
    }
    
    private fun isProjectClass(psiClass: PsiClass): Boolean {
        val file = psiClass.containingFile?.virtualFile
        return file != null && 
               !file.path.contains("/.m2/repository/") &&
               !file.path.contains("/build/") &&
               !file.path.contains("/target/") &&
               file.extension == "java"
    }
}

/**
 * 调用关系分析器
 */
class CallRelationAnalyzer {
    
    private val logger = LoggerFactory.getLogger(CallRelationAnalyzer::class.java)
    private val edgeCache = ConcurrentHashMap<String, List<CallEdge>>()
    
    fun analyzeMethodCalls(method: PsiMethod, graph: KnowledgeGraph): List<CallEdge> {
        val methodId = generateMethodId(method)
        
        // 检查缓存
        edgeCache[methodId]?.let { return it }
        
        val edges = mutableListOf<CallEdge>()
        val methodBody = method.body
        
        if (methodBody != null) {
            methodBody.accept(object : JavaRecursiveElementVisitor() {
                override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                    super.visitMethodCallExpression(expression)
                    
                    val resolvedMethod = expression.resolveMethod()
                    if (resolvedMethod != null && isProjectMethod(resolvedMethod)) {
                        val edge = createCallEdge(method, resolvedMethod, expression, graph)
                        if (edge != null) {
                            edges.add(edge)
                        }
                    }
                }
                
                override fun visitNewExpression(expression: PsiNewExpression) {
                    super.visitNewExpression(expression)
                    
                    val resolvedConstructor = expression.resolveConstructor()
                    if (resolvedConstructor != null && isProjectMethod(resolvedConstructor)) {
                        val edge = createCallEdge(method, resolvedConstructor, expression, graph)
                        if (edge != null) {
                            edges.add(edge)
                        }
                    }
                }
                
                override fun visitLambdaExpression(expression: PsiLambdaExpression) {
                    super.visitLambdaExpression(expression)
                    
                    // 分析 Lambda 表达式中的方法调用
                    val body = expression.body
                    if (body != null) {
                        body.accept(this)
                    }
                }
                
                override fun visitMethodReferenceExpression(expression: PsiMethodReferenceExpression) {
                    super.visitMethodReferenceExpression(expression)
                    
                    val resolvedMethod = expression.resolve()
                    if (resolvedMethod is PsiMethod && isProjectMethod(resolvedMethod)) {
                        val edge = createMethodReferenceEdge(method, resolvedMethod, expression, graph)
                        if (edge != null) {
                            edges.add(edge)
                        }
                    }
                }
            })
        }
        
        // 缓存结果
        edgeCache[methodId] = edges
        
        return edges
    }
    
    private fun createCallEdge(
        fromMethod: PsiMethod,
        toMethod: PsiMethod,
        callSite: PsiElement,
        graph: KnowledgeGraph
    ): CallEdge? {
        val fromMethodId = generateMethodId(fromMethod)
        val toMethodId = generateMethodId(toMethod)
        val fromClassId = fromMethod.containingClass?.qualifiedName ?: return null
        val toClassId = toMethod.containingClass?.qualifiedName ?: return null
        
        // 检查方法是否在图谱中
        val fromNode = graph.getMethodById(fromMethodId)
        val toNode = graph.getMethodById(toMethodId)
        
        if (fromNode == null || toNode == null) {
            return null
        }
        
        val callType = determineCallType(fromMethod, toMethod, callSite)
        val lineNumber = getLineNumber(callSite, fromMethod.project)
        
        val edgeId = generateEdgeId(fromMethodId, toMethodId, callType, lineNumber)
        
        return CallEdge(
            id = edgeId,
            fromMethodId = fromMethodId,
            toMethodId = toMethodId,
            fromClassId = fromClassId,
            toClassId = toClassId,
            callType = callType,
            lineNumber = lineNumber,
            confidence = calculateConfidence(callType)
        )
    }
    
    private fun createMethodReferenceEdge(
        fromMethod: PsiMethod,
        toMethod: PsiMethod,
        methodRef: PsiMethodReferenceExpression,
        graph: KnowledgeGraph
    ): CallEdge? {
        val fromMethodId = generateMethodId(fromMethod)
        val toMethodId = generateMethodId(toMethod)
        val fromClassId = fromMethod.containingClass?.qualifiedName ?: return null
        val toClassId = toMethod.containingClass?.qualifiedName ?: return null
        
        val fromNode = graph.getMethodById(fromMethodId)
        val toNode = graph.getMethodById(toMethodId)
        
        if (fromNode == null || toNode == null) {
            return null
        }
        
        val lineNumber = getLineNumber(methodRef, fromMethod.project)
        val edgeId = generateEdgeId(fromMethodId, toMethodId, CallType.METHOD_REFERENCE, lineNumber)
        
        return CallEdge(
            id = edgeId,
            fromMethodId = fromMethodId,
            toMethodId = toMethodId,
            fromClassId = fromClassId,
            toClassId = toClassId,
            callType = CallType.METHOD_REFERENCE,
            lineNumber = lineNumber,
            confidence = 0.9f
        )
    }
    
    private fun determineCallType(
        fromMethod: PsiMethod,
        toMethod: PsiMethod,
        callSite: PsiElement
    ): CallType {
        return when {
            toMethod.hasModifierProperty(PsiModifier.STATIC) -> CallType.STATIC
            toMethod.containingClass?.isInterface == true -> CallType.INTERFACE
            isInheritanceCall(fromMethod, toMethod) -> CallType.INHERITANCE
            callSite is PsiNewExpression -> CallType.DIRECT
            callSite.text.contains("reflect") -> CallType.REFLECTION
            isAsyncCall(callSite) -> CallType.ASYNC
            else -> CallType.DIRECT
        }
    }
    
    private fun isInheritanceCall(fromMethod: PsiMethod, toMethod: PsiMethod): Boolean {
        val fromClass = fromMethod.containingClass ?: return false
        val toClass = toMethod.containingClass ?: return false
        
        return fromClass.isInheritor(toClass, true) || toClass.isInheritor(fromClass, true)
    }
    
    private fun isAsyncCall(callSite: PsiElement): Boolean {
        val text = callSite.text.lowercase()
        return text.contains("async") || 
               text.contains("future") || 
               text.contains("completablefuture") ||
               text.contains("@async")
    }
    
    private fun calculateConfidence(callType: CallType): Float {
        return when (callType) {
            CallType.DIRECT -> 1.0f
            CallType.STATIC -> 1.0f
            CallType.INTERFACE -> 0.9f
            CallType.INHERITANCE -> 0.9f
            CallType.METHOD_REFERENCE -> 0.9f
            CallType.LAMBDA -> 0.8f
            CallType.ASYNC -> 0.8f
            CallType.REFLECTION -> 0.6f
        }
    }
    
    private fun generateMethodId(method: PsiMethod): String {
        val className = method.containingClass?.qualifiedName ?: "Unknown"
        val params = method.parameterList.parameters.joinToString(",") { 
            it.type.presentableText 
        }
        val signature = "${method.name}($params)"
        
        return if (method.isConstructor) {
            "$className.<init>$signature"
        } else {
            "$className.$signature"
        }
    }
    
    private fun generateEdgeId(fromId: String, toId: String, callType: CallType, lineNumber: Int): String {
        val combined = "$fromId->$toId:${callType.name}:$lineNumber"
        return md5Hash(combined)
    }
    
    private fun md5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    private fun getLineNumber(element: PsiElement, project: Project): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
        return if (document != null) {
            document.getLineNumber(element.textOffset) + 1
        } else {
            -1
        }
    }
    
    private fun isProjectMethod(method: PsiMethod): Boolean {
        val file = method.containingFile?.virtualFile
        return file != null && 
               !file.path.contains("/.m2/repository/") &&
               !file.path.contains("/build/") &&
               !file.path.contains("/target/") &&
               file.extension == "java"
    }
}