package com.vyibc.autocr.psi

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.vyibc.autocr.model.*
import com.vyibc.autocr.graph.CodeGraph
import com.vyibc.autocr.graph.SimpleCodeGraph
import org.slf4j.LoggerFactory

/**
 * PSI分析服务
 * 提供代码结构分析的统一接口
 */
@Service(Service.Level.PROJECT)
class PSIService(private val project: Project) {
    private val logger = LoggerFactory.getLogger(PSIService::class.java)
    private val analyzer = PSIAnalyzer(project)
    private val codeGraph: CodeGraph = SimpleCodeGraph()
    
    /**
     * 分析单个文件并更新代码图谱
     */
    fun analyzeFile(psiFile: PsiFile): FileAnalysisResult {
        logger.info("Analyzing file: {}", psiFile.name)
        
        val result = analyzer.analyzeFile(psiFile)
        
        // 更新代码图谱
        updateCodeGraph(result)
        
        return result
    }
    
    /**
     * 分析整个项目并构建代码图谱
     */
    fun analyzeProject(): ProjectAnalysisResult {
        logger.info("Starting full project analysis")
        
        // 清空现有图谱
        codeGraph.clear()
        
        val result = analyzer.analyzeProject()
        
        // 批量更新代码图谱
        buildCodeGraph(result)
        
        logger.info("Project analysis completed: {} classes, {} methods", 
            result.classes.size, result.methods.size)
        
        return result
    }
    
    /**
     * 获取代码图谱
     */
    fun getCodeGraph(): CodeGraph {
        return codeGraph
    }
    
    /**
     * 获取项目统计信息
     */
    fun getProjectStats(): ProjectStats {
        val allNodes = codeGraph.getAllNodes()
        val allEdges = codeGraph.getAllEdges()
        
        val classes = allNodes.filterIsInstance<ClassNode>()
        val methods = allNodes.filterIsInstance<MethodNode>()
        
        // 按BlockType统计
        val classTypeStats = classes.groupBy { it.blockType }.mapValues { it.value.size }
        val methodTypeStats = methods.groupBy { it.blockType }.mapValues { it.value.size }
        
        // 复杂度统计
        val complexityStats = methods.map { it.cyclomaticComplexity }
        val avgComplexity = if (complexityStats.isNotEmpty()) complexityStats.average() else 0.0
        val maxComplexity = complexityStats.maxOrNull() ?: 0
        
        // 代码行数统计
        val locStats = methods.map { it.linesOfCode }
        val totalLoc = locStats.sum()
        val avgLoc = if (locStats.isNotEmpty()) locStats.average() else 0.0
        
        return ProjectStats(
            totalClasses = classes.size,
            totalMethods = methods.size,
            totalEdges = allEdges.size,
            classTypeDistribution = classTypeStats,
            methodTypeDistribution = methodTypeStats,
            averageComplexity = avgComplexity,
            maxComplexity = maxComplexity,
            totalLinesOfCode = totalLoc,
            averageLinesOfCode = avgLoc
        )
    }
    
    /**
     * 查找方法调用路径
     */
    fun findCallPaths(startMethodId: String, endMethodId: String, maxDepth: Int = 5): List<CallPath> {
        return codeGraph.findPaths(startMethodId, endMethodId, maxDepth)
    }
    
    /**
     * 获取类的所有方法
     */
    fun getMethodsInClass(classId: String): List<MethodNode> {
        return codeGraph.getMethodsByClass(classId)
    }
    
    /**
     * 获取包下的所有类
     */
    fun getClassesInPackage(packageName: String): List<ClassNode> {
        return codeGraph.getClassesByPackage(packageName)
    }
    
    /**
     * 分析方法的依赖关系
     */
    fun analyzeMethodDependencies(methodId: String, depth: Int = 2): MethodDependencyAnalysis {
        val methodNode = codeGraph.getNode(methodId) as? MethodNode
            ?: return MethodDependencyAnalysis(methodId, emptyList(), emptyList(), emptyList())
        
        val outgoingEdges = codeGraph.getOutgoingEdges(methodId)
        val incomingEdges = codeGraph.getIncomingEdges(methodId)
        
        val calledMethods = outgoingEdges
            .filterIsInstance<CallsEdge>()
            .mapNotNull { edge -> codeGraph.getNode(edge.targetId) as? MethodNode }
        
        val callingMethods = incomingEdges
            .filterIsInstance<CallsEdge>()
            .mapNotNull { edge -> codeGraph.getNode(edge.sourceId) as? MethodNode }
        
        val connectedNodes = codeGraph.getConnectedNodes(methodId, depth)
        val relatedMethods = connectedNodes.filterIsInstance<MethodNode>()
        
        return MethodDependencyAnalysis(
            methodId = methodId,
            calledMethods = calledMethods,
            callingMethods = callingMethods,
            relatedMethods = relatedMethods
        )
    }
    
    /**
     * 检测潜在的代码异味
     */
    fun detectCodeSmells(): List<CodeSmell> {
        val codeSmells = mutableListOf<CodeSmell>()
        val allMethods = codeGraph.getAllNodes().filterIsInstance<MethodNode>()
        val allClasses = codeGraph.getAllNodes().filterIsInstance<ClassNode>()
        
        // 检测长方法
        allMethods.filter { it.linesOfCode > 50 }.forEach { method ->
            codeSmells.add(CodeSmell(
                type = "LONG_METHOD",
                description = "Method '${method.methodName}' is too long (${method.linesOfCode} lines)",
                severity = "MEDIUM",
                filePath = method.filePath,
                lineNumber = method.lineNumber,
                methodId = method.id
            ))
        }
        
        // 检测高复杂度方法
        allMethods.filter { it.cyclomaticComplexity > 10 }.forEach { method ->
            codeSmells.add(CodeSmell(
                type = "HIGH_COMPLEXITY",
                description = "Method '${method.methodName}' has high cyclomatic complexity (${method.cyclomaticComplexity})",
                severity = "HIGH",
                filePath = method.filePath,
                lineNumber = method.lineNumber,
                methodId = method.id
            ))
        }
        
        // 检测大类
        allClasses.forEach { classNode ->
            val methodCount = codeGraph.getMethodsByClass(classNode.id).size
            if (methodCount > 20) {
                codeSmells.add(CodeSmell(
                    type = "LARGE_CLASS",
                    description = "Class '${classNode.className}' has too many methods ($methodCount)",
                    severity = "MEDIUM",
                    filePath = classNode.filePath,
                    lineNumber = 1,
                    classId = classNode.id
                ))
            }
        }
        
        return codeSmells
    }
    
    /**
     * 更新代码图谱（单个文件）
     */
    private fun updateCodeGraph(result: FileAnalysisResult) {
        // 先移除该文件的旧数据
        // 这里简化处理，实际应该根据文件路径查找并移除
        
        // 添加新的节点
        result.classes.forEach { classNode ->
            codeGraph.addNode(classNode)
        }
        
        result.methods.forEach { methodNode ->
            codeGraph.addNode(methodNode)
        }
        
        // 添加新的边
        result.edges.forEach { edge ->
            codeGraph.addEdge(edge)
        }
    }
    
    /**
     * 构建完整的代码图谱（项目级别）
     */
    private fun buildCodeGraph(result: ProjectAnalysisResult) {
        logger.info("Building code graph with {} classes, {} methods, {} edges",
            result.classes.size, result.methods.size, result.edges.size)
        
        // 添加所有节点
        result.classes.forEach { classNode ->
            codeGraph.addNode(classNode)
        }
        
        result.methods.forEach { methodNode ->
            codeGraph.addNode(methodNode)
        }
        
        // 添加所有边
        result.edges.forEach { edge ->
            codeGraph.addEdge(edge)
        }
        
        logger.info("Code graph built successfully")
    }
    
    companion object {
        fun getInstance(project: Project): PSIService {
            return project.service()
        }
    }
}

/**
 * 项目统计信息
 */
data class ProjectStats(
    val totalClasses: Int,
    val totalMethods: Int,
    val totalEdges: Int,
    val classTypeDistribution: Map<BlockType, Int>,
    val methodTypeDistribution: Map<BlockType, Int>,
    val averageComplexity: Double,
    val maxComplexity: Int,
    val totalLinesOfCode: Int,
    val averageLinesOfCode: Double
)

/**
 * 方法依赖分析结果
 */
data class MethodDependencyAnalysis(
    val methodId: String,
    val calledMethods: List<MethodNode>,
    val callingMethods: List<MethodNode>,
    val relatedMethods: List<MethodNode>
)

/**
 * 代码异味
 */
data class CodeSmell(
    val type: String,
    val description: String,
    val severity: String,
    val filePath: String,
    val lineNumber: Int,
    val methodId: String? = null,
    val classId: String? = null
)