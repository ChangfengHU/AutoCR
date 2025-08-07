# 基于 IDEA 索引构建 Java 项目知识图谱方案

## 一、方案概述

### 1.1 核心思想
**利用 IDEA 现有索引是最优方案！** 

理由：
- ✅ **避免重复解析**：IDEA 已经完成了语法分析和索引构建
- ✅ **实时性保证**：IDEA 索引会自动更新，图谱数据始终最新
- ✅ **性能最优**：直接查询索引比重新解析快 100 倍以上
- ✅ **准确性高**：利用 IDEA 成熟的语义分析能力
- ✅ **维护成本低**：无需自己处理各种语法边界情况

### 1.2 架构设计
```
┌─────────────────────────────────────────┐
│         IDEA Platform Indexes           │
│  (PSI Tree, Stub Index, Reference Index)│
└────────────┬────────────────────────────┘
             │ 查询和提取
             ▼
┌─────────────────────────────────────────┐
│       Knowledge Graph Builder           │
│  ┌──────────────┬──────────────────┐   │
│  │Layer Detector│ Method Analyzer   │   │
│  └──────────────┴──────────────────┘   │
└────────────┬────────────────────────────┘
             │ 构建
             ▼
┌─────────────────────────────────────────┐
│          Knowledge Graph                │
│  Blocks → Nodes → Edges                 │
└─────────────────────────────────────────┘
```

## 二、层级识别策略

### 2.1 基于注解的层级识别
```kotlin
class LayerDetector {
    enum class LayerType {
        CONTROLLER,
        SERVICE,
        MAPPER,
        REPOSITORY,
        UTIL,
        COMPONENT,
        CONFIGURATION
    }
    
    fun detectLayer(psiClass: PsiClass): LayerType? {
        // 1. 注解识别（最准确）
        val annotations = psiClass.annotations
        
        return when {
            annotations.any { it.qualifiedName in CONTROLLER_ANNOTATIONS } -> LayerType.CONTROLLER
            annotations.any { it.qualifiedName in SERVICE_ANNOTATIONS } -> LayerType.SERVICE
            annotations.any { it.qualifiedName in REPOSITORY_ANNOTATIONS } -> LayerType.REPOSITORY
            annotations.any { it.qualifiedName in MAPPER_ANNOTATIONS } -> LayerType.MAPPER
            
            // 2. 包名识别（次选）
            psiClass.containingFile?.let { file ->
                val packageName = (file as? PsiJavaFile)?.packageName ?: ""
                when {
                    packageName.contains("controller") -> LayerType.CONTROLLER
                    packageName.contains("service") -> LayerType.SERVICE
                    packageName.contains("mapper") || packageName.contains("dao") -> LayerType.MAPPER
                    packageName.contains("repository") -> LayerType.REPOSITORY
                    packageName.contains("util") || packageName.contains("utils") -> LayerType.UTIL
                    else -> null
                }
            }
            
            // 3. 类名识别（补充）
            psiClass.name?.let { className ->
                when {
                    className.endsWith("Controller") -> LayerType.CONTROLLER
                    className.endsWith("Service") || className.endsWith("ServiceImpl") -> LayerType.SERVICE
                    className.endsWith("Mapper") || className.endsWith("Dao") -> LayerType.MAPPER
                    className.endsWith("Repository") -> LayerType.REPOSITORY
                    className.endsWith("Util") || className.endsWith("Utils") -> LayerType.UTIL
                    else -> null
                }
            }
            
            else -> null
        }
    }
    
    companion object {
        private val CONTROLLER_ANNOTATIONS = setOf(
            "org.springframework.stereotype.Controller",
            "org.springframework.web.bind.annotation.RestController",
            "javax.ws.rs.Path"
        )
        
        private val SERVICE_ANNOTATIONS = setOf(
            "org.springframework.stereotype.Service",
            "javax.ejb.Stateless",
            "javax.ejb.Stateful"
        )
        
        private val REPOSITORY_ANNOTATIONS = setOf(
            "org.springframework.stereotype.Repository",
            "org.springframework.data.repository.Repository"
        )
        
        private val MAPPER_ANNOTATIONS = setOf(
            "org.apache.ibatis.annotations.Mapper",
            "org.mybatis.spring.annotation.MapperScan"
        )
    }
}
```

### 2.2 智能层级推断
```kotlin
class SmartLayerInference {
    fun inferLayerByDependencies(psiClass: PsiClass): LayerType? {
        val imports = (psiClass.containingFile as? PsiJavaFile)?.importList?.allImportStatements
        val dependencies = analyzeDependencies(psiClass)
        
        return when {
            // Controller 特征：依赖 Service，返回 ResponseEntity
            hasWebMvcImports(imports) && dependsOnService(dependencies) -> LayerType.CONTROLLER
            
            // Service 特征：被 Controller 调用，调用 Repository/Mapper
            isCalledByController(dependencies) && callsRepository(dependencies) -> LayerType.SERVICE
            
            // Repository/Mapper 特征：操作数据库
            hasDataAccessImports(imports) -> LayerType.MAPPER
            
            // Util 特征：静态方法多，无状态
            isUtilityClass(psiClass) -> LayerType.UTIL
            
            else -> null
        }
    }
}
```

## 三、方法调用链路分析

### 3.1 利用 IDEA Reference Index
```kotlin
class CallChainAnalyzer {
    fun analyzeCallChain(project: Project, method: PsiMethod): CallGraph {
        val graph = CallGraph()
        
        // 1. 使用 IDEA 的 ReferencesSearch 查找调用
        val references = ReferencesSearch.search(method, GlobalSearchScope.projectScope(project))
        
        references.forEach { reference ->
            val element = reference.element
            val caller = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
            
            caller?.let {
                graph.addEdge(
                    from = createMethodNode(caller),
                    to = createMethodNode(method),
                    callSite = element
                )
            }
        }
        
        // 2. 分析方法内部调用
        analyzeMethodBody(method, graph)
        
        return graph
    }
    
    private fun analyzeMethodBody(method: PsiMethod, graph: CallGraph) {
        method.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                
                val calledMethod = expression.resolveMethod()
                calledMethod?.let {
                    // 只关注项目内的方法调用
                    if (isProjectMethod(it)) {
                        graph.addEdge(
                            from = createMethodNode(method),
                            to = createMethodNode(it),
                            callSite = expression
                        )
                    }
                }
            }
        })
    }
}
```

### 3.2 高性能批量分析
```kotlin
class BatchCallAnalyzer {
    fun analyzeProject(project: Project): KnowledgeGraph {
        val graph = KnowledgeGraph()
        
        // 1. 使用 ProjectFileIndex 遍历所有 Java 文件
        val fileIndex = ProjectFileIndex.getInstance(project)
        val psiManager = PsiManager.getInstance(project)
        
        // 2. 并行处理提高性能
        val executor = ForkJoinPool.commonPool()
        val futures = mutableListOf<Future<List<GraphElement>>>()
        
        fileIndex.iterateContent { virtualFile ->
            if (virtualFile.extension == "java") {
                futures.add(executor.submit(Callable {
                    analyzeFile(psiManager.findFile(virtualFile))
                }))
            }
            true
        }
        
        // 3. 收集结果
        futures.forEach { future ->
            future.get().forEach { element ->
                graph.addElement(element)
            }
        }
        
        return graph
    }
    
    private fun analyzeFile(psiFile: PsiFile?): List<GraphElement> {
        val elements = mutableListOf<GraphElement>()
        
        psiFile?.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                super.visitClass(aClass)
                
                // 识别层级
                val layer = LayerDetector().detectLayer(aClass)
                layer?.let {
                    elements.add(ClassBlock(aClass, layer))
                    
                    // 分析类中的方法
                    aClass.methods.forEach { method ->
                        elements.add(MethodNode(method, aClass))
                    }
                }
            }
        })
        
        return elements
    }
}
```

## 四、知识图谱数据结构

### 4.1 图谱模型定义
```kotlin
// 区块（类层级）
data class ClassBlock(
    val id: String,
    val name: String,
    val qualifiedName: String,
    val layer: LayerType,
    val packageName: String,
    val annotations: List<String>,
    val methods: List<String>  // 方法ID列表
)

// 节点（方法）
data class MethodNode(
    val id: String,
    val name: String,
    val signature: String,
    val classId: String,       // 所属类
    val returnType: String,
    val parameters: List<Parameter>,
    val modifiers: Set<String>,
    val lineNumber: Int
)

// 边（调用关系）
data class CallEdge(
    val id: String,
    val fromMethodId: String,
    val toMethodId: String,
    val callType: CallType,    // 直接调用、反射调用、异步调用等
    val lineNumber: Int,
    val confidence: Float       // 调用关系的置信度
)

enum class CallType {
    DIRECT,         // 直接调用
    REFLECTION,     // 反射调用
    ASYNC,          // 异步调用
    EVENT,          // 事件驱动
    AOP             // AOP 切面
}
```

### 4.2 图谱存储优化
```kotlin
class GraphStorage {
    // 使用 Neo4j 存储
    fun saveToNeo4j(graph: KnowledgeGraph) {
        val driver = GraphDatabase.driver("bolt://localhost:7687")
        driver.session().use { session ->
            // 批量创建节点
            session.writeTransaction { tx ->
                // 创建类节点
                graph.classes.forEach { classBlock ->
                    tx.run("""
                        MERGE (c:Class {id: ${'$'}id})
                        SET c.name = ${'$'}name,
                            c.layer = ${'$'}layer,
                            c.package = ${'$'}package
                    """, mapOf(
                        "id" to classBlock.id,
                        "name" to classBlock.name,
                        "layer" to classBlock.layer.name,
                        "package" to classBlock.packageName
                    ))
                }
                
                // 创建方法节点
                graph.methods.forEach { method ->
                    tx.run("""
                        MERGE (m:Method {id: ${'$'}id})
                        SET m.name = ${'$'}name,
                            m.signature = ${'$'}signature,
                            m.returnType = ${'$'}returnType
                    """, mapOf(
                        "id" to method.id,
                        "name" to method.name,
                        "signature" to method.signature,
                        "returnType" to method.returnType
                    ))
                }
                
                // 创建调用关系
                graph.edges.forEach { edge ->
                    tx.run("""
                        MATCH (from:Method {id: ${'$'}fromId})
                        MATCH (to:Method {id: ${'$'}toId})
                        MERGE (from)-[r:CALLS]->(to)
                        SET r.type = ${'$'}type,
                            r.line = ${'$'}line
                    """, mapOf(
                        "fromId" to edge.fromMethodId,
                        "toId" to edge.toMethodId,
                        "type" to edge.callType.name,
                        "line" to edge.lineNumber
                    ))
                }
            }
        }
    }
}
```

## 五、实际实现示例

### 5.1 插件主服务
```kotlin
class KnowledgeGraphService(private val project: Project) {
    private val cache = ConcurrentHashMap<String, KnowledgeGraph>()
    
    fun buildGraph(): KnowledgeGraph {
        // 1. 检查缓存
        val cacheKey = project.name + "_" + getProjectVersion()
        cache[cacheKey]?.let { return it }
        
        // 2. 构建新图谱
        val graph = DumbService.getInstance(project).runReadActionInSmartMode {
            val builder = GraphBuilder(project)
            
            // 利用 IDEA 索引构建
            ProgressManager.getInstance().runProcessWithProgressSynchronously({
                builder.build()
            }, "构建知识图谱", true, project)
        }
        
        // 3. 缓存结果
        cache[cacheKey] = graph
        
        return graph
    }
    
    inner class GraphBuilder(private val project: Project) {
        fun build(): KnowledgeGraph {
            val graph = KnowledgeGraph()
            
            // 1. 收集所有类
            val allClasses = collectAllClasses()
            
            // 2. 分层处理
            val layeredClasses = allClasses.groupBy { 
                LayerDetector().detectLayer(it) 
            }
            
            // 3. 构建类区块
            layeredClasses.forEach { (layer, classes) ->
                classes.forEach { psiClass ->
                    val block = createClassBlock(psiClass, layer)
                    graph.addBlock(block)
                    
                    // 4. 构建方法节点
                    psiClass.methods.forEach { method ->
                        val node = createMethodNode(method, block)
                        graph.addNode(node)
                    }
                }
            }
            
            // 5. 分析调用关系
            analyzeCallRelations(graph)
            
            return graph
        }
        
        private fun collectAllClasses(): List<PsiClass> {
            val classes = mutableListOf<PsiClass>()
            
            // 使用 AllClassesGetter 获取所有类
            AllClassesGetter.processJavaClasses(
                PsiSearchHelper.getInstance(project).getUseScope(),
                project,
                { psiClass ->
                    if (isProjectClass(psiClass)) {
                        classes.add(psiClass)
                    }
                    true
                }
            )
            
            return classes
        }
        
        private fun analyzeCallRelations(graph: KnowledgeGraph) {
            graph.getAllMethods().forEach { method ->
                // 使用 Call Hierarchy 功能
                val callees = CallHierarchyUtil.getCallees(method.psiMethod)
                
                callees.forEach { callee ->
                    graph.addEdge(CallEdge(
                        fromMethodId = method.id,
                        toMethodId = createMethodId(callee),
                        callType = determineCallType(method.psiMethod, callee)
                    ))
                }
            }
        }
    }
}
```

### 5.2 实时更新机制
```kotlin
class GraphUpdateListener : BulkFileListener {
    override fun after(events: List<VFileEvent>) {
        events.forEach { event ->
            when (event) {
                is VFileContentChangeEvent -> handleFileChange(event.file)
                is VFileCreateEvent -> handleFileCreate(event.file)
                is VFileDeleteEvent -> handleFileDelete(event.file)
            }
        }
    }
    
    private fun handleFileChange(file: VirtualFile) {
        if (file.extension != "java") return
        
        ApplicationManager.getApplication().invokeLater {
            val project = ProjectUtil.guessProjectForFile(file) ?: return@invokeLater
            val service = project.service<KnowledgeGraphService>()
            
            // 增量更新
            service.updateGraphForFile(file)
        }
    }
}
```

### 5.3 查询和可视化
```kotlin
class GraphQueryService(private val graph: KnowledgeGraph) {
    
    // 查找调用链
    fun findCallChain(fromMethod: String, toMethod: String): List<CallPath> {
        return BreadthFirstSearch.findPaths(
            graph = graph,
            start = fromMethod,
            end = toMethod,
            maxDepth = 10
        )
    }
    
    // 查找循环依赖
    fun findCircularDependencies(): List<Cycle> {
        val cycles = mutableListOf<Cycle>()
        
        graph.getAllClasses().forEach { classNode ->
            val visited = mutableSetOf<String>()
            val stack = mutableListOf<String>()
            
            if (hasCycle(classNode.id, visited, stack)) {
                cycles.add(Cycle(stack.toList()))
            }
        }
        
        return cycles
    }
    
    // 分析影响范围
    fun analyzeImpact(methodId: String): ImpactAnalysis {
        val impacted = mutableSetOf<String>()
        val queue = LinkedList<String>()
        queue.offer(methodId)
        
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            
            graph.getCallers(current).forEach { caller ->
                if (impacted.add(caller)) {
                    queue.offer(caller)
                }
            }
        }
        
        return ImpactAnalysis(
            directImpact = graph.getCallers(methodId),
            indirectImpact = impacted,
            riskLevel = calculateRiskLevel(impacted)
        )
    }
}
```

## 六、性能优化策略

### 6.1 增量更新
```kotlin
class IncrementalGraphUpdater {
    fun updateForChangedFile(file: VirtualFile, graph: KnowledgeGraph) {
        val psiFile = PsiManager.getInstance(project).findFile(file) as? PsiJavaFile
            ?: return
        
        // 1. 找出文件中的所有类
        val classes = PsiTreeUtil.findChildrenOfType(psiFile, PsiClass::class.java)
        
        // 2. 移除旧节点
        classes.forEach { psiClass ->
            graph.removeClass(psiClass.qualifiedName)
        }
        
        // 3. 添加新节点
        classes.forEach { psiClass ->
            val layer = LayerDetector().detectLayer(psiClass)
            val block = createClassBlock(psiClass, layer)
            graph.addBlock(block)
            
            // 更新方法和调用关系
            updateMethodsAndCalls(psiClass, graph)
        }
    }
}
```

### 6.2 缓存策略
```kotlin
class CachedGraphService {
    private val graphCache = Caffeine.newBuilder()
        .maximumSize(100)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .build<String, KnowledgeGraph>()
    
    private val methodCache = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterAccess(5, TimeUnit.MINUTES)
        .build<String, MethodNode>()
    
    fun getGraph(project: Project): KnowledgeGraph {
        return graphCache.get(project.name) {
            buildGraph(project)
        }
    }
}
```

## 七、最佳实践建议

### 7.1 为什么基于 IDEA 索引是最佳选择

| 方面 | 自己解析 | 基于 IDEA 索引 |
|------|----------|----------------|
| **开发成本** | 高（需要实现完整的 Java 解析器） | 低（直接使用现成 API） |
| **准确性** | 难以处理所有语法情况 | IDEA 已处理各种边界情况 |
| **性能** | 需要重复解析文件 | 直接查询已有索引 |
| **实时性** | 需要自己监听文件变化 | IDEA 自动维护索引更新 |
| **维护成本** | 需要持续更新解析逻辑 | 跟随 IDEA 版本自动升级 |

### 7.2 推荐的实现步骤

1. **第一阶段：基础图谱**
   - 实现层级识别
   - 构建类和方法节点
   - 简单的直接调用关系

2. **第二阶段：完善关系**
   - 添加继承关系
   - 实现接口调用分析
   - 处理 Lambda 和方法引用

3. **第三阶段：高级特性**
   - 异步调用链路追踪
   - Spring AOP 切面分析
   - 动态代理识别

4. **第四阶段：智能分析**
   - 循环依赖检测
   - 代码坏味道识别
   - 架构合规性检查

### 7.3 关键 API 使用

```kotlin
// 1. 获取所有类
JavaPsiFacade.getInstance(project).findClasses(
    "com.example",
    GlobalSearchScope.projectScope(project)
)

// 2. 查找方法调用
ReferencesSearch.search(
    method,
    GlobalSearchScope.projectScope(project)
)

// 3. 获取类的层级
PsiUtil.getSuperTypes(psiClass)

// 4. 分析方法体
ControlFlowFactory.getInstance(project)
    .getControlFlow(method.body)
```

## 八、总结

基于 IDEA 索引构建知识图谱的优势：

1. **复用成熟能力**：利用 IDEA 20+ 年的代码分析经验
2. **实时同步**：图谱随代码自动更新
3. **高性能**：毫秒级查询响应
4. **易维护**：代码量减少 80%
5. **可扩展**：轻松添加新的分析维度

**核心建议**：不要重复造轮子，充分利用 IDEA 平台的能力，专注于业务价值的实现！

---

*这个方案让您能够快速实现一个准确、高效、易维护的 Java 项目知识图谱系统。*