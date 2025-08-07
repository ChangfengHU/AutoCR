package com.vyibc.autocr.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import java.time.Instant

/**
 * PSI解析引擎
 * 使用IntelliJ IDEA的PSI API解析Java代码结构
 */
class PSIAnalyzer(private val project: Project) {
    private val logger = LoggerFactory.getLogger(PSIAnalyzer::class.java)
    
    /**
     * 分析指定文件的代码结构
     */
    fun analyzeFile(psiFile: PsiFile): FileAnalysisResult {
        logger.debug("Analyzing file: {}", psiFile.name)
        
        val classes = mutableListOf<ClassNode>()
        val methods = mutableListOf<MethodNode>()
        val edges = mutableListOf<Edge>()
        
        psiFile.accept(object : JavaRecursiveElementVisitor() {
            override fun visitClass(aClass: PsiClass) {
                super.visitClass(aClass)
                
                val classNode = extractClassNode(aClass, psiFile.virtualFile?.path ?: "")
                classes.add(classNode)
                
                // 分析类中的方法
                aClass.methods.forEach { method ->
                    val methodNode = extractMethodNode(method, classNode.id, psiFile.virtualFile?.path ?: "")
                    methods.add(methodNode)
                    
                    // 创建类-方法关系边
                    val containsEdge = ContainsEdge(
                        id = "${classNode.id}->contains->${methodNode.id}",
                        sourceId = classNode.id,
                        targetId = methodNode.id,
                        edgeType = EdgeType.CONTAINS
                    )
                    edges.add(containsEdge)
                    
                    // 分析方法内的调用关系
                    val callEdges = extractMethodCalls(method, methodNode.id)
                    edges.addAll(callEdges)
                }
            }
        })
        
        return FileAnalysisResult(
            filePath = psiFile.virtualFile?.path ?: "",
            classes = classes,
            methods = methods,
            edges = edges,
            analysisTime = Instant.now()
        )
    }
    
    /**
     * 分析整个项目的代码结构
     */
    fun analyzeProject(): ProjectAnalysisResult {
        logger.info("Starting project analysis for: {}", project.name)
        
        val allClasses = mutableListOf<ClassNode>()
        val allMethods = mutableListOf<MethodNode>()
        val allEdges = mutableListOf<Edge>()
        
        // 获取项目中的所有Java文件
        val javaFiles = getJavaFiles()
        
        javaFiles.forEach { file ->
            try {
                val result = analyzeFile(file)
                allClasses.addAll(result.classes)
                allMethods.addAll(result.methods)
                allEdges.addAll(result.edges)
            } catch (e: Exception) {
                logger.error("Error analyzing file: {}", file.name, e)
            }
        }
        
        // 分析跨文件的继承和实现关系
        val inheritanceEdges = analyzeInheritanceRelations(allClasses)
        allEdges.addAll(inheritanceEdges)
        
        logger.info("Project analysis completed: {} classes, {} methods, {} edges", 
            allClasses.size, allMethods.size, allEdges.size)
        
        return ProjectAnalysisResult(
            projectName = project.name,
            classes = allClasses,
            methods = allMethods,
            edges = allEdges,
            analysisTime = Instant.now()
        )
    }
    
    /**
     * 提取类节点信息
     */
    private fun extractClassNode(psiClass: PsiClass, filePath: String): ClassNode {
        val annotations = psiClass.annotations.map { it.text }
        
        // 确定块类型
        val blockType = determineBlockType(psiClass, annotations)
        
        return ClassNode(
            id = generateClassId(psiClass),
            className = psiClass.name ?: "Unknown",
            packageName = getPackageName(psiClass),
            blockType = blockType,
            isInterface = psiClass.isInterface,
            isAbstract = psiClass.hasModifierProperty(PsiModifier.ABSTRACT),
            filePath = filePath,
            implementedInterfaces = psiClass.implementsList?.referencedTypes?.mapNotNull { it.resolve()?.qualifiedName } ?: emptyList(),
            superClass = psiClass.superClass?.qualifiedName,
            annotations = annotations
        )
    }
    
    /**
     * 提取方法节点信息
     */
    private fun extractMethodNode(psiMethod: PsiMethod, classId: String, filePath: String): MethodNode {
        val annotations = psiMethod.annotations.map { it.text }
        val paramTypes = psiMethod.parameterList.parameters.map { it.type.canonicalText }
        val returnType = psiMethod.returnType?.canonicalText ?: "void"
        
        // 计算方法复杂度和行数
        val complexity = calculateCyclomaticComplexity(psiMethod)
        val linesOfCode = calculateLinesOfCode(psiMethod)
        
        // 确定块类型
        val blockType = determineMethodBlockType(psiMethod, annotations)
        
        return MethodNode(
            id = generateMethodId(psiMethod, classId),
            methodName = psiMethod.name,
            signature = generateMethodSignature(psiMethod),
            returnType = returnType,
            paramTypes = paramTypes,
            blockType = blockType,
            isInterface = psiMethod.containingClass?.isInterface ?: false,
            annotations = annotations,
            filePath = filePath,
            lineNumber = getLineNumber(psiMethod),
            startLineNumber = getStartLineNumber(psiMethod),
            endLineNumber = getEndLineNumber(psiMethod),
            cyclomaticComplexity = complexity,
            linesOfCode = linesOfCode,
            lastModified = Instant.now()
        )
    }
    
    /**
     * 提取方法调用关系
     */
    private fun extractMethodCalls(psiMethod: PsiMethod, methodId: String): List<CallsEdge> {
        val callEdges = mutableListOf<CallsEdge>()
        
        psiMethod.accept(object : JavaRecursiveElementVisitor() {
            override fun visitMethodCallExpression(expression: PsiMethodCallExpression) {
                super.visitMethodCallExpression(expression)
                
                val resolvedMethod = expression.resolveMethod()
                if (resolvedMethod != null) {
                    val targetMethodId = generateMethodId(resolvedMethod, 
                        generateClassId(resolvedMethod.containingClass!!))
                    
                    val callEdge = CallsEdge(
                        id = "${methodId}->calls->${targetMethodId}",
                        sourceId = methodId,
                        targetId = targetMethodId,
                        edgeType = EdgeType.CALLS,
                        callType = determineCallType(expression),
                        lineNumber = getLineNumber(expression)
                    )
                    callEdges.add(callEdge)
                }
            }
        })
        
        return callEdges
    }
    
    /**
     * 分析继承和实现关系
     */
    private fun analyzeInheritanceRelations(classes: List<ClassNode>): List<Edge> {
        val edges = mutableListOf<Edge>()
        val classMap = classes.associateBy { it.getFullyQualifiedName() }
        
        classes.forEach { classNode ->
            // 继承关系
            classNode.superClass?.let { superClassName ->
                classMap[superClassName]?.let { superClass ->
                    val inheritEdge = InheritsEdge(
                        id = "${classNode.id}->inherits->${superClass.id}",
                        sourceId = classNode.id,
                        targetId = superClass.id,
                        edgeType = EdgeType.INHERITS
                    )
                    edges.add(inheritEdge)
                }
            }
            
            // 实现关系
            classNode.implementedInterfaces.forEach { interfaceName ->
                classMap[interfaceName]?.let { interfaceClass ->
                    val implementsEdge = ImplementsEdge(
                        id = "${classNode.id}->implements->${interfaceClass.id}",
                        sourceId = classNode.id,
                        targetId = interfaceClass.id,
                        edgeType = EdgeType.IMPLEMENTS
                    )
                    edges.add(implementsEdge)
                }
            }
        }
        
        return edges
    }
    
    /**
     * 获取项目中的所有Java文件
     */
    private fun getJavaFiles(): List<PsiFile> {
        val javaFiles = mutableListOf<PsiFile>()
        val psiManager = PsiManager.getInstance(project)
        
        // 使用FilenameIndex搜索所有.java文件
        val virtualFiles = FilenameIndex.getAllFilesByExt(project, "java", GlobalSearchScope.projectScope(project))
        
        virtualFiles.forEach { virtualFile ->
            val psiFile = psiManager.findFile(virtualFile)
            if (psiFile is PsiJavaFile) {
                javaFiles.add(psiFile)
            }
        }
        
        return javaFiles
    }
    
    /**
     * 确定类的块类型
     */
    private fun determineBlockType(psiClass: PsiClass, annotations: List<String>): BlockType {
        return when {
            annotations.any { it.contains("@Entity") } -> BlockType.ENTITY
            annotations.any { it.contains("@Repository") } -> BlockType.REPOSITORY
            annotations.any { it.contains("@Service") } -> BlockType.SERVICE
            annotations.any { it.contains("@Controller") || it.contains("@RestController") } -> BlockType.CONTROLLER
            annotations.any { it.contains("@Component") } -> BlockType.SERVICE
            annotations.any { it.contains("@Configuration") } -> BlockType.CONFIG
            psiClass.name?.endsWith("DTO") == true || psiClass.name?.endsWith("Dto") == true -> BlockType.DTO
            psiClass.name?.endsWith("Util") == true || psiClass.name?.endsWith("Utils") == true -> BlockType.UTIL
            psiClass.name?.endsWith("Test") == true -> BlockType.UNKNOWN
            else -> BlockType.UNKNOWN
        }
    }
    
    /**
     * 确定方法的块类型
     */
    private fun determineMethodBlockType(psiMethod: PsiMethod, annotations: List<String>): BlockType {
        return when {
            annotations.any { it.contains("@Test") } -> BlockType.UNKNOWN
            annotations.any { it.contains("@RequestMapping") || it.contains("@GetMapping") || 
                            it.contains("@PostMapping") || it.contains("@PutMapping") || 
                            it.contains("@DeleteMapping") } -> BlockType.CONTROLLER
            psiMethod.name.startsWith("test") -> BlockType.UNKNOWN
            else -> psiMethod.containingClass?.let { determineBlockType(it, emptyList()) } ?: BlockType.UNKNOWN
        }
    }
    
    /**
     * 计算圈复杂度
     */
    private fun calculateCyclomaticComplexity(psiMethod: PsiMethod): Int {
        var complexity = 1 // 基础复杂度
        
        psiMethod.accept(object : JavaRecursiveElementVisitor() {
            override fun visitIfStatement(statement: PsiIfStatement) {
                super.visitIfStatement(statement)
                complexity++
            }
            
            override fun visitWhileStatement(statement: PsiWhileStatement) {
                super.visitWhileStatement(statement)
                complexity++
            }
            
            override fun visitForStatement(statement: PsiForStatement) {
                super.visitForStatement(statement)
                complexity++
            }
            
            override fun visitForeachStatement(statement: PsiForeachStatement) {
                super.visitForeachStatement(statement)
                complexity++
            }
            
            override fun visitSwitchStatement(statement: PsiSwitchStatement) {
                super.visitSwitchStatement(statement)
                statement.body?.statements?.filterIsInstance<PsiSwitchLabelStatement>()?.let {
                    complexity += it.size
                }
            }
            
            override fun visitTryStatement(statement: PsiTryStatement) {
                super.visitTryStatement(statement)
                complexity += statement.catchBlocks.size
            }
        })
        
        return complexity
    }
    
    /**
     * 计算代码行数
     */
    private fun calculateLinesOfCode(psiMethod: PsiMethod): Int {
        val body = psiMethod.body ?: return 0
        val text = body.text
        return text.lines().count { it.trim().isNotEmpty() }
    }
    
    /**
     * 生成类ID
     */
    private fun generateClassId(psiClass: PsiClass): String {
        return psiClass.qualifiedName ?: "${getPackageName(psiClass)}.${psiClass.name}"
    }
    
    /**
     * 生成方法ID
     */
    private fun generateMethodId(psiMethod: PsiMethod, classId: String): String {
        return "$classId#${psiMethod.name}(${psiMethod.parameterList.parameters.joinToString(",") { it.type.canonicalText }})"
    }
    
    /**
     * 生成方法签名
     */
    private fun generateMethodSignature(psiMethod: PsiMethod): String {
        val params = psiMethod.parameterList.parameters.joinToString(", ") { 
            "${it.type.canonicalText} ${it.name}" 
        }
        return "${psiMethod.name}($params)"
    }
    
    /**
     * 获取包名
     */
    private fun getPackageName(psiClass: PsiClass): String {
        return (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""
    }
    
    /**
     * 获取行号
     */
    private fun getLineNumber(element: PsiElement): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
        return document?.getLineNumber(element.textOffset)?.plus(1) ?: 0
    }
    
    /**
     * 获取开始行号
     */
    private fun getStartLineNumber(element: PsiElement): Int {
        return getLineNumber(element)
    }
    
    /**
     * 获取结束行号
     */
    private fun getEndLineNumber(element: PsiElement): Int {
        val document = PsiDocumentManager.getInstance(project).getDocument(element.containingFile)
        return document?.getLineNumber(element.textOffset + element.textLength)?.plus(1) ?: 0
    }
    
    /**
     * 确定调用类型
     */
    private fun determineCallType(expression: PsiMethodCallExpression): String {
        val methodExpression = expression.methodExpression
        return when {
            methodExpression.qualifierExpression == null -> "local"
            methodExpression.qualifierExpression is PsiThisExpression -> "this"
            methodExpression.qualifierExpression is PsiSuperExpression -> "super"
            else -> "external"
        }
    }
}

/**
 * 文件分析结果
 */
data class FileAnalysisResult(
    val filePath: String,
    val classes: List<ClassNode>,
    val methods: List<MethodNode>,
    val edges: List<Edge>,
    val analysisTime: Instant
)

/**
 * 项目分析结果
 */
data class ProjectAnalysisResult(
    val projectName: String,
    val classes: List<ClassNode>,
    val methods: List<MethodNode>,
    val edges: List<Edge>,
    val analysisTime: Instant
)