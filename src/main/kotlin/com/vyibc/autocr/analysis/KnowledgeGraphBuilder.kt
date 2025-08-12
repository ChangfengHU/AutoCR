package com.vyibc.autocr.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 增强的知识图谱构建器 - 集成Tree维度和权重计算
 */
class KnowledgeGraphBuilder(private val project: Project) {
    
    private val logger = LoggerFactory.getLogger(KnowledgeGraphBuilder::class.java)
    private val layerDetector = LayerDetector()
    private val businessDomainDetector = BusinessDomainDetector()
    private val nodeFilter = NodeBuildingRulesFilter()
    private val callAnalyzer = CallRelationAnalyzer()
    private val interfaceAnalyzer = InterfaceImplementationAnalyzer(project)
    private val treeBuilder = CallTreeBuilder()
    private val weightCalculator = CrossCountAndWeightCalculator()
    
    fun buildGraph(indicator: ProgressIndicator): KnowledgeGraph {
        val metadata = GraphMetadata(
            projectName = project.name,
            buildTime = System.currentTimeMillis(),
            version = "2.0.0",
            description = "Enhanced AutoCR Knowledge Graph with Tree dimension"
        )
        val graph = KnowledgeGraph(metadata = metadata)
        
        try {
            // 第1阶段：收集和过滤类
            indicator.text = "正在收集和过滤项目类..."
            indicator.fraction = 0.0
            val allClasses = collectAndFilterClasses(indicator)
            logger.info("收集到${allClasses.size}个有效类")
            
            // 第2阶段：分析类结构和业务域
            indicator.text = "正在分析类结构和业务域..."
            indicator.fraction = 0.15
            analyzeClassStructureWithBusinessDomain(allClasses, graph, indicator)
            logger.info("创建了${graph.classes.size}个类节点，${graph.methods.size}个方法节点")
            
            // 第3阶段：分析调用关系
            indicator.text = "正在分析方法调用关系..."
            indicator.fraction = 0.35
            analyzeCallRelations(graph, indicator)
            logger.info("创建了${graph.edges.size}个调用关系")
            
            // 第4阶段：分析接口实现映射
            indicator.text = "正在分析接口实现映射..."
            indicator.fraction = 0.50
            analyzeInterfaceImplementations(graph, indicator)
            logger.info("创建了${graph.interfaceMappings.size}个接口实现映射")
            
            // 第5阶段：构建调用树
            indicator.text = "正在构建调用树..."
            indicator.fraction = 0.65
            buildCallTrees(graph, indicator)
            logger.info("构建了${graph.trees.size}个调用树，${graph.treeRelations.size}个树关系")
            
            // 第6阶段：计算权重和交叉数
            indicator.text = "正在计算节点权重和交叉数..."
            indicator.fraction = 0.80
            calculateWeightsAndCrossCount(graph, indicator)
            
            // 第7阶段：最终验证和优化
            indicator.text = "正在进行最终验证和优化..."
            indicator.fraction = 0.95
            performFinalValidation(graph, indicator)
            
            indicator.text = "知识图谱构建完成"
            indicator.fraction = 1.0
            
            logFinalStatistics(graph)
            return graph
            
        } catch (e: Exception) {
            logger.error("知识图谱构建失败", e)
            throw e
        }
    }
    
    /**
     * 收集和过滤类
     */
    private fun collectAndFilterClasses(indicator: ProgressIndicator): List<PsiClass> {
        return ApplicationManager.getApplication().runReadAction<List<PsiClass>> {
            val allClasses = mutableListOf<PsiClass>()
            val filteredClasses = mutableListOf<PsiClass>()
            val scope = GlobalSearchScope.projectScope(project)
            
            AllClassesSearch.search(scope, project).forEach { psiClass ->
                if (isProjectClass(psiClass)) {
                    allClasses.add(psiClass)
                    
                    val layer = layerDetector.detectLayer(psiClass)
                    if (nodeFilter.shouldIncludeClass(psiClass, layer)) {
                        filteredClasses.add(psiClass)
                        logger.debug("包含类: ${psiClass.qualifiedName} [${layer}]")
                    } else {
                        logger.debug("排除类: ${psiClass.qualifiedName} [${layer}]")
                    }
                }
            }
            
            val stats = nodeFilter.getFilterStatistics(
                totalClasses = allClasses.size,
                includedClasses = filteredClasses.size,
                totalMethods = 0, // 将在后续计算
                includedMethods = 0
            )
            logger.info("类过滤统计: ${stats}")
            
            filteredClasses
        }
    }
    
    /**
     * 分析类结构和业务域
     */
    private fun analyzeClassStructureWithBusinessDomain(
        allClasses: List<PsiClass>, 
        graph: KnowledgeGraph, 
        indicator: ProgressIndicator
    ) {
        val total = allClasses.size
        var totalMethods = 0
        var includedMethods = 0
        
        allClasses.forEachIndexed { index, psiClass ->
            if (indicator.isCanceled) return
            
            indicator.text = "正在分析类: ${psiClass.name}"
            indicator.fraction = 0.15 + (0.2 * index / total)
            
            ApplicationManager.getApplication().runReadAction {
                try {
                    val layer = layerDetector.detectLayer(psiClass)
                    val businessDomain = businessDomainDetector.detectBusinessDomain(psiClass, layer)
                    
                    // 创建类节点
                    val classBlock = createEnhancedClassBlock(psiClass, layer, businessDomain)
                    graph.addClass(classBlock)
                    
                    // 创建方法节点（使用严格的过滤规则）
                    psiClass.methods.forEach { method ->
                        totalMethods++
                        if (nodeFilter.shouldIncludeMethod(method, psiClass, layer)) {
                            val methodNode = createEnhancedMethodNode(method, classBlock.id)
                            graph.addMethod(methodNode)
                            includedMethods++
                            logger.debug("包含方法: ${classBlock.name}.${method.name}")
                        } else {
                            logger.debug("排除方法: ${classBlock.name}.${method.name}")
                        }
                    }
                    
                    // 处理构造函数
                    psiClass.constructors.forEach { constructor ->
                        totalMethods++
                        if (nodeFilter.shouldIncludeMethod(constructor, psiClass, layer)) {
                            val constructorNode = createEnhancedConstructorNode(constructor, classBlock.id)
                            graph.addMethod(constructorNode)
                            includedMethods++
                        }
                    }
                    
                } catch (e: Exception) {
                    logger.warn("Failed to analyze class: ${psiClass.name}", e)
                }
            }
        }
        
        val methodStats = nodeFilter.getFilterStatistics(
            totalClasses = allClasses.size,
            includedClasses = graph.classes.size,
            totalMethods = totalMethods,
            includedMethods = includedMethods
        )
        logger.info("方法过滤统计: ${methodStats}")
    }
    
    /**
     * 分析调用关系
     */
    private fun analyzeCallRelations(graph: KnowledgeGraph, indicator: ProgressIndicator) {
        val methods = graph.methods
        val total = methods.size
        var totalEdges = 0
        var failedMethods = 0
        
        logger.info("开始分析 ${total} 个方法的调用关系...")
        
        methods.forEachIndexed { index, method ->
            if (indicator.isCanceled) return
            
            indicator.text = "正在分析调用关系: ${method.name}"
            indicator.fraction = 0.35 + (0.15 * index / total)
            
            ApplicationManager.getApplication().runReadAction {
                try {
                    val psiMethod = findPsiMethod(method)
                    if (psiMethod != null) {
                        val edges = callAnalyzer.analyzeMethodCalls(psiMethod, graph)
                        if (edges.isNotEmpty()) {
                            logger.debug("方法 ${method.name} 找到 ${edges.size} 个调用关系")
                            totalEdges += edges.size
                        }
                        edges.forEach { edge -> graph.addEdge(edge) }
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
    
    /**
     * 分析接口实现映射
     */
    private fun analyzeInterfaceImplementations(graph: KnowledgeGraph, indicator: ProgressIndicator) {
        logger.info("开始分析接口实现映射...")
        
        val mappings = interfaceAnalyzer.analyzeInterfaceImplementations(graph)
        val validationResult = interfaceAnalyzer.validateInterfaceImplementationMappings(mappings, graph)
        
        validationResult.validMappings.forEach { mapping ->
            graph.addInterfaceMapping(mapping)
        }
        
        if (validationResult.invalidMappings.isNotEmpty()) {
            logger.warn("发现${validationResult.invalidMappings.size}个无效的接口映射:")
            validationResult.invalidMappings.forEach { error ->
                logger.warn("  ${error.mappingId}: ${error.errorMessage}")
            }
        }
        
        logger.info("接口实现映射完成: 有效${validationResult.validMappings.size}个")
    }
    
    /**
     * 构建调用树
     */
    private fun buildCallTrees(graph: KnowledgeGraph, indicator: ProgressIndicator) {
        logger.info("开始构建调用树...")
        
        val treeResult = treeBuilder.buildCallTrees(graph)
        
        // 添加到图谱
        treeResult.trees.forEach { tree ->
            graph.addTree(tree)
        }
        
        treeResult.treeRelations.forEach { relation ->
            graph.addTreeRelation(relation)
        }
        
        treeResult.corePaths.forEach { corePath ->
            graph.addCorePath(corePath)
        }
        
        logger.info("调用树构建完成: ${treeResult.trees.size}个树, ${treeResult.treeRelations.size}个关系, ${treeResult.corePaths.size}个核心路径")
    }
    
    /**
     * 计算权重和交叉数
     */
    private fun calculateWeightsAndCrossCount(graph: KnowledgeGraph, indicator: ProgressIndicator) {
        logger.info("开始计算权重和交叉数...")
        
        val result = weightCalculator.calculateCrossCountAndWeight(graph)
        logger.info("权重计算完成: ${result}")
        
        // 获取权重统计信息
        val stats = weightCalculator.getWeightStatistics(graph)
        logger.info("权重统计: 方法权重平均${String.format("%.2f", stats.methodWeightStats.avg)}, 类权重平均${String.format("%.2f", stats.classWeightStats.avg)}")
        logger.info("交叉节点: ${stats.crossNodesCount}个方法, 根节点: ${stats.rootNodesCount}个")
    }
    
    /**
     * 最终验证和优化
     */
    private fun performFinalValidation(graph: KnowledgeGraph, indicator: ProgressIndicator) {
        logger.info("执行最终验证和优化...")
        
        // 验证图谱完整性
        var issues = 0
        
        // 检查孤立节点
        val orphanMethods = graph.methods.filter { method ->
            graph.getIncomingEdges(method.id).isEmpty() && 
            graph.getOutgoingEdges(method.id).isEmpty() &&
            !method.isRootNode
        }
        
        if (orphanMethods.isNotEmpty()) {
            logger.warn("发现${orphanMethods.size}个孤立方法节点")
            issues++
        }
        
        // 检查Tree完整性
        graph.trees.forEach { tree ->
            val rootMethod = graph.getMethodById(tree.rootMethodId)
            if (rootMethod == null) {
                logger.error("Tree ${tree.treeNumber} 的根方法不存在: ${tree.rootMethodId}")
                issues++
            }
            
            val relations = graph.getTreeRelationsByTree(tree.id)
            if (relations.size != tree.nodeCount - 1) {
                logger.warn("Tree ${tree.treeNumber} 的关系数量不匹配: 期望${tree.nodeCount - 1}, 实际${relations.size}")
            }
        }
        
        // 检查核心路径完整性
        graph.corePaths.forEach { corePath ->
            val fromMethod = graph.getMethodById(corePath.fromMethodId)
            val toMethod = graph.getMethodById(corePath.toTreeRootId)
            val tree = graph.getTreeById(corePath.treeId)
            
            if (fromMethod == null || toMethod == null || tree == null) {
                logger.error("核心路径${corePath.pathNumber}存在无效引用")
                issues++
            }
        }
        
        if (issues == 0) {
            logger.info("验证通过，图谱结构完整")
        } else {
            logger.warn("验证发现${issues}个问题")
        }
    }
    
    /**
     * 记录最终统计信息
     */
    private fun logFinalStatistics(graph: KnowledgeGraph) {
        val stats = graph.getStatistics()
        
        logger.info("=== 知识图谱构建完成 ===")
        logger.info("类节点: ${stats.totalClasses}个")
        logger.info("方法节点: ${stats.totalMethods}个")
        logger.info("调用关系: ${stats.totalEdges}个")
        logger.info("接口映射: ${stats.totalInterfaceMappings}个")
        logger.info("调用树: ${stats.totalTrees}个")
        logger.info("树关系: ${stats.totalTreeRelations}个")
        logger.info("核心路径: ${stats.totalCorePaths}个")
        logger.info("交叉节点: ${stats.totalCrossNodes}个")
        logger.info("平均方法/类: ${String.format("%.2f", stats.avgMethodsPerClass)}")
        logger.info("平均树深度: ${String.format("%.1f", stats.avgTreeDepth)}")
        
        logger.info("业务域分布:")
        stats.businessDomainDistribution.forEach { (domain, count) ->
            logger.info("  ${domain.displayName}: ${count}个")
        }
        
        logger.info("层级分布:")
        stats.layerDistribution.forEach { (layer, count) ->
            logger.info("  ${layer.displayName}: ${count}个")
        }
        
        logger.info("========================")
    }
    
    // ====== 辅助方法 ======
    
    private fun createEnhancedClassBlock(
        psiClass: PsiClass, 
        layer: LayerType, 
        businessDomain: BusinessDomain
    ): ClassBlock {
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
            businessDomain = businessDomain,
            annotations = annotations,
            superClass = superClass,
            interfaces = interfaces,
            isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT),
            isInterface = psiClass.isInterface,
            methodCount = psiClass.methods.size,
            filePath = filePath
        )
    }
    
    private fun createEnhancedMethodNode(method: PsiMethod, classId: String): MethodNode {
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
    
    private fun createEnhancedConstructorNode(constructor: PsiMethod, classId: String): MethodNode {
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