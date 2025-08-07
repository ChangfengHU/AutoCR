package com.vyibc.autocr.model

/**
 * 层级类型枚举
 */
enum class LayerType(val displayName: String, val emoji: String) {
    CONTROLLER("Controller层", "🎮"),
    SERVICE("Service层", "⚙️"),
    MAPPER("Mapper/DAO层", "🗄️"),
    REPOSITORY("Repository层", "💾"),
    UTIL("工具类", "🔧"),
    ENTITY("实体类", "📦"),
    CONFIG("配置类", "⚙️"),
    COMPONENT("组件", "🔗"),
    UNKNOWN("未分类", "❓")
}

/**
 * 调用类型
 */
enum class CallType(val displayName: String) {
    DIRECT("直接调用"),
    INTERFACE("接口调用"),
    INHERITANCE("继承调用"),
    STATIC("静态调用"),
    REFLECTION("反射调用"),
    ASYNC("异步调用"),
    LAMBDA("Lambda表达式"),
    METHOD_REFERENCE("方法引用")
}

/**
 * 类区块
 */
data class ClassBlock(
    val id: String,
    val name: String,
    val qualifiedName: String,
    val packageName: String,
    val layer: LayerType,
    val annotations: List<String>,
    val superClass: String?,
    val interfaces: List<String>,
    val isAbstract: Boolean,
    val isInterface: Boolean,
    val methodCount: Int,
    val filePath: String
) {
    fun toNeo4jId(): String = "class_${id.replace(".", "_").replace("$", "_")}"
}

/**
 * 方法节点
 */
data class MethodNode(
    val id: String,
    val name: String,
    val signature: String,
    val classId: String,
    val returnType: String,
    val parameters: List<Parameter>,
    val modifiers: Set<String>,
    val lineNumber: Int,
    val isConstructor: Boolean,
    val isStatic: Boolean,
    val isPrivate: Boolean,
    val isPublic: Boolean,
    val isAbstract: Boolean
) {
    fun toNeo4jId(): String = "method_${id.replace(".", "_").replace("$", "_").replace("(", "_").replace(")", "_").replace(",", "_").replace(" ", "_")}"
    
    fun getVisibility(): String = when {
        isPublic -> "public"
        isPrivate -> "private"
        modifiers.contains("protected") -> "protected"
        else -> "package"
    }
}

/**
 * 方法参数
 */
data class Parameter(
    val name: String,
    val type: String,
    val isVarArgs: Boolean = false
)

/**
 * 调用边
 */
data class CallEdge(
    val id: String,
    val fromMethodId: String,
    val toMethodId: String,
    val fromClassId: String,
    val toClassId: String,
    val callType: CallType,
    val lineNumber: Int,
    val confidence: Float = 1.0f
) {
    fun toNeo4jId(): String = "call_${id.replace(".", "_").replace("$", "_")}"
}

/**
 * 知识图谱
 */
data class KnowledgeGraph(
    val classes: MutableList<ClassBlock> = mutableListOf(),
    val methods: MutableList<MethodNode> = mutableListOf(),
    val edges: MutableList<CallEdge> = mutableListOf(),
    val metadata: GraphMetadata = GraphMetadata()
) {
    fun addClass(classBlock: ClassBlock) {
        classes.removeIf { it.id == classBlock.id }
        classes.add(classBlock)
    }
    
    fun addMethod(method: MethodNode) {
        methods.removeIf { it.id == method.id }
        methods.add(method)
    }
    
    fun addEdge(edge: CallEdge) {
        edges.removeIf { it.id == edge.id }
        edges.add(edge)
    }
    
    fun getClassById(id: String): ClassBlock? = classes.find { it.id == id }
    fun getMethodById(id: String): MethodNode? = methods.find { it.id == id }
    
    fun getMethodsByClass(classId: String): List<MethodNode> = 
        methods.filter { it.classId == classId }
    
    fun getOutgoingEdges(methodId: String): List<CallEdge> = 
        edges.filter { it.fromMethodId == methodId }
    
    fun getIncomingEdges(methodId: String): List<CallEdge> = 
        edges.filter { it.toMethodId == methodId }
        
    fun getClassesByLayer(layer: LayerType): List<ClassBlock> =
        classes.filter { it.layer == layer }
        
    fun getStatistics(): GraphStatistics {
        val layerStats = LayerType.values().associateWith { layer ->
            classes.count { it.layer == layer }
        }
        
        val callTypeStats = CallType.values().associateWith { type ->
            edges.count { it.callType == type }
        }
        
        return GraphStatistics(
            totalClasses = classes.size,
            totalMethods = methods.size,
            totalEdges = edges.size,
            layerDistribution = layerStats,
            callTypeDistribution = callTypeStats,
            avgMethodsPerClass = if (classes.isNotEmpty()) methods.size.toDouble() / classes.size else 0.0
        )
    }
}

/**
 * 图谱元数据
 */
data class GraphMetadata(
    val projectName: String = "",
    val buildTime: Long = System.currentTimeMillis(),
    val version: String = "1.0.0",
    val description: String = "AutoCR Knowledge Graph"
)

/**
 * 图谱统计信息
 */
data class GraphStatistics(
    val totalClasses: Int,
    val totalMethods: Int,
    val totalEdges: Int,
    val layerDistribution: Map<LayerType, Int>,
    val callTypeDistribution: Map<CallType, Int>,
    val avgMethodsPerClass: Double
)