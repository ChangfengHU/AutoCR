package com.vyibc.autocr.analysis

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.vyibc.autocr.model.*
import org.slf4j.LoggerFactory
import java.security.MessageDigest

/**
 * 接口实现分析器
 * 负责分析接口和实现类之间的方法映射关系
 */
class InterfaceImplementationAnalyzer(private val project: Project) {
    
    private val logger = LoggerFactory.getLogger(InterfaceImplementationAnalyzer::class.java)
    
    /**
     * 分析项目中的接口实现关系，建立方法级别的映射
     */
    fun analyzeInterfaceImplementations(graph: KnowledgeGraph): List<InterfaceImplementationMapping> {
        val mappings = mutableListOf<InterfaceImplementationMapping>()
        
        logger.info("开始分析接口实现关系...")
        
        return ApplicationManager.getApplication().runReadAction<List<InterfaceImplementationMapping>> {
            try {
                // 1. 找到所有接口类
                val interfaceClasses = graph.classes.filter { it.isInterface }
                logger.info("发现${interfaceClasses.size}个接口")
                
                // 2. 对每个接口，找到其实现类
                interfaceClasses.forEach { interfaceClass ->
                    val interfaceMethods = graph.getMethodsByClass(interfaceClass.id)
                    logger.debug("分析接口: ${interfaceClass.id}, 方法数: ${interfaceMethods.size}")
                    
                    // 找到实现了该接口的类
                    val implementationClasses = findImplementationClasses(graph, interfaceClass)
                    
                    implementationClasses.forEach { implClass ->
                        val implMethods = graph.getMethodsByClass(implClass.id)
                        logger.debug("找到实现类: ${implClass.id}, 方法数: ${implMethods.size}")
                        
                        // 3. 匹配接口方法和实现方法
                        val methodMappings = matchInterfaceAndImplementationMethods(
                            interfaceClass, interfaceMethods, implClass, implMethods
                        )
                        
                        mappings.addAll(methodMappings)
                    }
                }
                
                logger.info("接口实现关系分析完成，共发现${mappings.size}个方法映射")
                mappings
                
            } catch (e: Exception) {
                logger.error("分析接口实现关系时出错", e)
                emptyList()
            }
        }
    }
    
    /**
     * 找到实现了指定接口的所有类
     */
    private fun findImplementationClasses(graph: KnowledgeGraph, interfaceClass: ClassBlock): List<ClassBlock> {
        return graph.classes.filter { classBlock ->
            !classBlock.isInterface && 
            classBlock.interfaces.contains(interfaceClass.qualifiedName)
        }
    }
    
    /**
     * 匹配接口方法和实现方法
     */
    private fun matchInterfaceAndImplementationMethods(
        interfaceClass: ClassBlock,
        interfaceMethods: List<MethodNode>,
        implClass: ClassBlock,
        implMethods: List<MethodNode>
    ): List<InterfaceImplementationMapping> {
        val mappings = mutableListOf<InterfaceImplementationMapping>()
        
        // 对每个接口方法，寻找对应的实现方法
        interfaceMethods.forEach { interfaceMethod ->
            // 跳过静态方法和默认方法的处理
            if (interfaceMethod.isStatic) {
                return@forEach
            }
            
            // 查找匹配的实现方法
            val matchingImplMethods = findMatchingImplementationMethods(interfaceMethod, implMethods)
            
            matchingImplMethods.forEach { implMethod ->
                val mapping = createInterfaceImplementationMapping(
                    interfaceClass, interfaceMethod,
                    implClass, implMethod
                )
                mappings.add(mapping)
                
                logger.debug("发现方法映射: ${interfaceMethod.signature} -> ${implMethod.signature}")
            }
        }
        
        return mappings
    }
    
    /**
     * 查找匹配的实现方法
     */
    private fun findMatchingImplementationMethods(
        interfaceMethod: MethodNode,
        implMethods: List<MethodNode>
    ): List<MethodNode> {
        return implMethods.filter { implMethod ->
            isMethodImplementation(interfaceMethod, implMethod)
        }
    }
    
    /**
     * 判断实现方法是否实现了接口方法
     */
    private fun isMethodImplementation(interfaceMethod: MethodNode, implMethod: MethodNode): Boolean {
        // 1. 方法名必须相同
        if (interfaceMethod.name != implMethod.name) {
            return false
        }
        
        // 2. 参数列表必须匹配
        if (!areParametersMatching(interfaceMethod.parameters, implMethod.parameters)) {
            return false
        }
        
        // 3. 返回类型必须兼容
        if (!areReturnTypesCompatible(interfaceMethod.returnType, implMethod.returnType)) {
            return false
        }
        
        // 4. 实现方法必须是public（接口方法默认public）
        if (!implMethod.isPublic) {
            return false
        }
        
        return true
    }
    
    /**
     * 检查参数列表是否匹配
     */
    private fun areParametersMatching(interfaceParams: List<Parameter>, implParams: List<Parameter>): Boolean {
        if (interfaceParams.size != implParams.size) {
            return false
        }
        
        return interfaceParams.zip(implParams).all { (ifaceParam, implParam) ->
            areTypesCompatible(ifaceParam.type, implParam.type)
        }
    }
    
    /**
     * 检查返回类型是否兼容
     */
    private fun areReturnTypesCompatible(interfaceReturnType: String, implReturnType: String): Boolean {
        return areTypesCompatible(interfaceReturnType, implReturnType)
    }
    
    /**
     * 检查类型是否兼容
     */
    private fun areTypesCompatible(interfaceType: String, implType: String): Boolean {
        // 简化的类型兼容性检查
        val normalizedInterfaceType = normalizeType(interfaceType)
        val normalizedImplType = normalizeType(implType)
        
        return normalizedInterfaceType == normalizedImplType
    }
    
    /**
     * 标准化类型字符串
     */
    private fun normalizeType(type: String): String {
        return type
            .replace("\\s+".toRegex(), "") // 移除所有空白字符
            .replace("java.lang.", "") // 移除java.lang包名
            .replace("java.util.", "") // 移除java.util包名前缀（保留核心部分）
    }
    
    /**
     * 创建接口实现映射对象
     */
    private fun createInterfaceImplementationMapping(
        interfaceClass: ClassBlock,
        interfaceMethod: MethodNode,
        implClass: ClassBlock,
        implMethod: MethodNode
    ): InterfaceImplementationMapping {
        val id = generateMappingId(interfaceMethod.id, implMethod.id)
        
        return InterfaceImplementationMapping(
            id = id,
            interfaceMethodId = interfaceMethod.id,
            implementationMethodId = implMethod.id,
            interfaceClassId = interfaceClass.id,
            implementationClassId = implClass.id,
            mappingType = "IMPLEMENTS_METHOD"
        )
    }
    
    /**
     * 生成映射ID
     */
    private fun generateMappingId(interfaceMethodId: String, implMethodId: String): String {
        val combined = "$interfaceMethodId->$implMethodId"
        return "mapping_" + md5Hash(combined)
    }
    
    /**
     * MD5哈希
     */
    private fun md5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * 验证接口实现映射的准确性
     */
    fun validateInterfaceImplementationMappings(
        mappings: List<InterfaceImplementationMapping>,
        graph: KnowledgeGraph
    ): ValidationResult {
        val validMappings = mutableListOf<InterfaceImplementationMapping>()
        val invalidMappings = mutableListOf<ValidationError>()
        
        mappings.forEach { mapping ->
            val interfaceMethod = graph.getMethodById(mapping.interfaceMethodId)
            val implMethod = graph.getMethodById(mapping.implementationMethodId)
            
            when {
                interfaceMethod == null -> {
                    invalidMappings.add(ValidationError(mapping.id, "接口方法不存在: ${mapping.interfaceMethodId}"))
                }
                implMethod == null -> {
                    invalidMappings.add(ValidationError(mapping.id, "实现方法不存在: ${mapping.implementationMethodId}"))
                }
                !isMethodImplementation(interfaceMethod, implMethod) -> {
                    invalidMappings.add(ValidationError(mapping.id, "方法签名不匹配"))
                }
                else -> {
                    validMappings.add(mapping)
                }
            }
        }
        
        logger.info("映射验证完成: 有效${validMappings.size}个, 无效${invalidMappings.size}个")
        
        return ValidationResult(validMappings, invalidMappings)
    }
}

/**
 * 验证结果
 */
data class ValidationResult(
    val validMappings: List<InterfaceImplementationMapping>,
    val invalidMappings: List<ValidationError>
)

/**
 * 验证错误
 */
data class ValidationError(
    val mappingId: String,
    val errorMessage: String
)