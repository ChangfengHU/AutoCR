package com.vyibc.autocr.util

import com.intellij.psi.*
import com.intellij.psi.util.PsiTreeUtil
import org.slf4j.LoggerFactory

/**
 * PSI元素解析工具，用于从选中的代码元素中提取类和方法信息
 */
object PsiElementAnalyzer {
    
    private val logger = LoggerFactory.getLogger(PsiElementAnalyzer::class.java)
    
    /**
     * 解析的元素信息
     */
    data class ElementInfo(
        val type: ElementType,
        val className: String? = null,
        val qualifiedClassName: String? = null,
        val methodName: String? = null,
        val methodSignature: String? = null,
        val fullMethodId: String? = null
    )
    
    enum class ElementType {
        CLASS,
        METHOD,
        UNKNOWN
    }
    
    /**
     * 从PSI元素中解析出类和方法信息
     */
    fun analyzeElement(element: PsiElement): ElementInfo {
        logger.debug("分析PSI元素: ${element::class.simpleName}")
        
        return when (element) {
            is PsiClass -> analyzeClass(element)
            is PsiMethod -> analyzeMethod(element)
            is PsiIdentifier -> analyzeIdentifier(element)
            else -> {
                // 尝试向上查找类或方法
                val parentClass = PsiTreeUtil.getParentOfType(element, PsiClass::class.java)
                val parentMethod = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java)
                
                when {
                    parentMethod != null -> analyzeMethod(parentMethod)
                    parentClass != null -> analyzeClass(parentClass)
                    else -> ElementInfo(ElementType.UNKNOWN)
                }
            }
        }
    }
    
    private fun analyzeClass(psiClass: PsiClass): ElementInfo {
        return ElementInfo(
            type = ElementType.CLASS,
            className = psiClass.name,
            qualifiedClassName = psiClass.qualifiedName
        )
    }
    
    private fun analyzeMethod(psiMethod: PsiMethod): ElementInfo {
        val containingClass = psiMethod.containingClass
        val methodSignature = buildMethodSignature(psiMethod)
        val fullMethodId = if (containingClass?.qualifiedName != null) {
            "${containingClass.qualifiedName}.$methodSignature"
        } else null
        
        return ElementInfo(
            type = ElementType.METHOD,
            className = containingClass?.name,
            qualifiedClassName = containingClass?.qualifiedName,
            methodName = psiMethod.name,
            methodSignature = methodSignature,
            fullMethodId = fullMethodId
        )
    }
    
    private fun analyzeIdentifier(identifier: PsiIdentifier): ElementInfo {
        val parent = identifier.parent
        logger.debug("标识符父元素: ${parent::class.simpleName}")
        
        return when (parent) {
            is PsiClass -> analyzeClass(parent)
            is PsiMethod -> analyzeMethod(parent)
            is PsiMethodCallExpression -> {
                // 方法调用，尝试解析被调用的方法
                val resolvedMethod = parent.resolveMethod()
                if (resolvedMethod != null) {
                    analyzeMethod(resolvedMethod)
                } else {
                    // 回退到当前所在的方法或类
                    val currentMethod = PsiTreeUtil.getParentOfType(parent, PsiMethod::class.java)
                    val currentClass = PsiTreeUtil.getParentOfType(parent, PsiClass::class.java)
                    when {
                        currentMethod != null -> analyzeMethod(currentMethod)
                        currentClass != null -> analyzeClass(currentClass)
                        else -> ElementInfo(ElementType.UNKNOWN)
                    }
                }
            }
            else -> {
                // 其他情况，查找上下文中的类或方法
                val contextMethod = PsiTreeUtil.getParentOfType(parent, PsiMethod::class.java)
                val contextClass = PsiTreeUtil.getParentOfType(parent, PsiClass::class.java)
                when {
                    contextMethod != null -> analyzeMethod(contextMethod)
                    contextClass != null -> analyzeClass(contextClass)
                    else -> ElementInfo(ElementType.UNKNOWN)
                }
            }
        }
    }
    
    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(",") { 
            it.type.presentableText 
        }
        return "${method.name}($params)"
    }
    
    /**
     * 检查是否可以为此元素生成Neo4j查询
     */
    fun canGenerateQueries(element: PsiElement): Boolean {
        val info = analyzeElement(element)
        return info.type != ElementType.UNKNOWN && 
               (info.qualifiedClassName != null || info.fullMethodId != null)
    }
}