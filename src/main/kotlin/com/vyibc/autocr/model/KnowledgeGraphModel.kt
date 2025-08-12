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
 * 业务域枚举
 */
enum class BusinessDomain(val displayName: String, val emoji: String) {
    USER("用户业务", "👤"),
    ORDER("订单业务", "📦"),
    PRODUCT("产品业务", "🛍️"),
    PAYMENT("支付业务", "💳"),
    AUTH("认证业务", "🔐"),
    SYSTEM("系统业务", "⚙️"),
    COMMON("通用业务", "🔧"),
    UNKNOWN("未识别", "❓")
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
    val businessDomain: BusinessDomain = BusinessDomain.UNKNOWN,
    val annotations: List<String>,
    val superClass: String?,
    val interfaces: List<String>,
    val isAbstract: Boolean,
    val isInterface: Boolean,
    val methodCount: Int,
    val filePath: String,
    val crossCount: Int = 0, // 交叉数：被多少个Tree包含
    val weight: Double = 0.0 // 权重：基于交叉数等因素计算
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
    val isAbstract: Boolean,
    val crossCount: Int = 0, // 交叉数：被多少个Tree包含
    val weight: Double = 0.0, // 权重：基于交叉数等因素计算
    val isRootNode: Boolean = false, // 是否为Tree的根节点
    val depth: Int = -1, // 在Tree中的深度，根节点为0
    val treeIds: Set<String> = setOf() // 所属的Tree ID集合
) {
    fun toNeo4jId(): String = "method_${id.replace(".", "_").replace("$", "_").replace("(", "_").replace(")", "_").replace(",", "_").replace(" ", "_")}"
    
    fun getVisibility(): String = when {
        isPublic -> "public"
        isPrivate -> "private"
        modifiers.contains("protected") -> "protected"
        else -> "package"
    }
    
    fun isBusinessMethod(): Boolean {
        // 判断是否为业务方法：public且非构造函数，或者带有特定注解
        return isPublic && !isConstructor
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
    val confidence: Float = 1.0f,
    val treeIds: Set<String> = setOf(), // 所属的Tree ID集合
    val pathNumber: String = "" // 链路编号
) {
    fun toNeo4jId(): String = "call_${id.replace(".", "_").replace("$", "_")}"
}

/**
 * 调用树 - 以Controller方法为根节点的调用链路树
 */
data class CallTree(
    val id: String, // Tree的唯一ID
    val treeNumber: String, // Tree编号，如T001, T002等
    val rootMethodId: String, // 根节点方法ID
    val rootClassId: String, // 根节点类ID
    val businessDomain: BusinessDomain, // 业务域
    val depth: Int, // 树的最大深度
    val nodeCount: Int, // 节点总数
    val crossNodeCount: Int, // 交叉节点数量
    val pathCount: Int, // 路径总数
    val buildTime: Long = System.currentTimeMillis(),
    val description: String = "" // 描述信息
) {
    fun toNeo4jId(): String = "tree_${id.replace(".", "_").replace("$", "_")}"
}

/**
 * 树节点关系 - 表示方法在Tree中的位置关系
 */
data class TreeNodeRelation(
    val id: String,
    val treeId: String, // 所属Tree ID
    val parentMethodId: String?, // 父节点方法ID，根节点为null
    val childMethodId: String, // 子节点方法ID
    val depth: Int, // 在树中的深度
    val pathIndex: Int, // 路径索引
    val relationshipType: String = "TREE_CALLS" // 关系类型
) {
    fun toNeo4jId(): String = "tree_rel_${id.replace(".", "_").replace("$", "_")}"
}

/**
 * 核心链路 - 从任意节点到Tree根节点的路径
 */
data class CorePath(
    val id: String,
    val pathNumber: String, // 核心链路编号，如CP001-001
    val fromMethodId: String, // 起始方法ID
    val toTreeRootId: String, // 目标Tree根节点ID
    val treeId: String, // 所属Tree ID
    val pathNodes: List<String>, // 路径上的方法ID列表
    val pathLength: Int, // 路径长度
    val layerCrossCount: Int, // 跨越的层级数
    val weight: Double = 0.0 // 路径权重
) {
    fun toNeo4jId(): String = "core_path_${id.replace(".", "_").replace("$", "_")}"
}

/**
 * 接口到实现类的方法映射关系
 */
data class InterfaceImplementationMapping(
    val id: String,
    val interfaceMethodId: String, // 接口方法ID
    val implementationMethodId: String, // 实现方法ID
    val interfaceClassId: String, // 接口类ID
    val implementationClassId: String, // 实现类ID
    val mappingType: String = "IMPLEMENTS_METHOD" // 映射类型
) {
    fun toNeo4jId(): String = "impl_mapping_${id.replace(".", "_").replace("$", "_")}"
}

/**
 * 知识图谱
 */
data class KnowledgeGraph(
    val classes: MutableList<ClassBlock> = mutableListOf(),
    val methods: MutableList<MethodNode> = mutableListOf(),
    val edges: MutableList<CallEdge> = mutableListOf(),
    val trees: MutableList<CallTree> = mutableListOf(), // 调用树集合
    val treeRelations: MutableList<TreeNodeRelation> = mutableListOf(), // 树节点关系集合
    val corePaths: MutableList<CorePath> = mutableListOf(), // 核心链路集合
    val interfaceMappings: MutableList<InterfaceImplementationMapping> = mutableListOf(), // 接口实现映射集合
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
    
    fun addTree(tree: CallTree) {
        trees.removeIf { it.id == tree.id }
        trees.add(tree)
    }
    
    fun addTreeRelation(relation: TreeNodeRelation) {
        treeRelations.removeIf { it.id == relation.id }
        treeRelations.add(relation)
    }
    
    fun addCorePath(corePath: CorePath) {
        corePaths.removeIf { it.id == corePath.id }
        corePaths.add(corePath)
    }
    
    fun addInterfaceMapping(mapping: InterfaceImplementationMapping) {
        interfaceMappings.removeIf { it.id == mapping.id }
        interfaceMappings.add(mapping)
    }
    
    fun getClassById(id: String): ClassBlock? = classes.find { it.id == id }
    fun getMethodById(id: String): MethodNode? = methods.find { it.id == id }
    fun getTreeById(id: String): CallTree? = trees.find { it.id == id }
    
    fun getMethodsByClass(classId: String): List<MethodNode> = 
        methods.filter { it.classId == classId }
    
    fun getOutgoingEdges(methodId: String): List<CallEdge> = 
        edges.filter { it.fromMethodId == methodId }
    
    fun getIncomingEdges(methodId: String): List<CallEdge> = 
        edges.filter { it.toMethodId == methodId }
        
    fun getClassesByLayer(layer: LayerType): List<ClassBlock> =
        classes.filter { it.layer == layer }
    
    fun getMethodsByBusinessDomain(domain: BusinessDomain): List<MethodNode> =
        methods.filter { method ->
            val classBlock = getClassById(method.classId)
            classBlock?.businessDomain == domain
        }
    
    fun getTreesByBusinessDomain(domain: BusinessDomain): List<CallTree> =
        trees.filter { it.businessDomain == domain }
    
    fun getRootNodes(): List<MethodNode> =
        methods.filter { it.isRootNode }
    
    fun getTreeRelationsByTree(treeId: String): List<TreeNodeRelation> =
        treeRelations.filter { it.treeId == treeId }
    
    fun getCorePathsByMethod(methodId: String): List<CorePath> =
        corePaths.filter { it.fromMethodId == methodId }
    
    fun getInterfaceMappingsByInterface(interfaceClassId: String): List<InterfaceImplementationMapping> =
        interfaceMappings.filter { it.interfaceClassId == interfaceClassId }
        
    fun getStatistics(): GraphStatistics {
        val layerStats = LayerType.values().associateWith { layer ->
            classes.count { it.layer == layer }
        }
        
        val callTypeStats = CallType.values().associateWith { type ->
            edges.count { it.callType == type }
        }
        
        val businessDomainStats = BusinessDomain.values().associateWith { domain ->
            classes.count { it.businessDomain == domain }
        }
        
        return GraphStatistics(
            totalClasses = classes.size,
            totalMethods = methods.size,
            totalEdges = edges.size,
            totalTrees = trees.size,
            totalTreeRelations = treeRelations.size,
            totalCorePaths = corePaths.size,
            totalInterfaceMappings = interfaceMappings.size,
            layerDistribution = layerStats,
            callTypeDistribution = callTypeStats,
            businessDomainDistribution = businessDomainStats,
            avgMethodsPerClass = if (classes.isNotEmpty()) methods.size.toDouble() / classes.size else 0.0,
            avgTreeDepth = if (trees.isNotEmpty()) trees.map { it.depth }.average() else 0.0,
            totalCrossNodes = methods.count { it.crossCount > 1 }
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
    val totalTrees: Int = 0,
    val totalTreeRelations: Int = 0,
    val totalCorePaths: Int = 0,
    val totalInterfaceMappings: Int = 0,
    val layerDistribution: Map<LayerType, Int>,
    val callTypeDistribution: Map<CallType, Int>,
    val businessDomainDistribution: Map<BusinessDomain, Int> = emptyMap(),
    val avgMethodsPerClass: Double,
    val avgTreeDepth: Double = 0.0,
    val totalCrossNodes: Int = 0
)