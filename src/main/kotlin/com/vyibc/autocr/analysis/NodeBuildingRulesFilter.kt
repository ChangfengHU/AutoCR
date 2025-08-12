package com.vyibc.autocr.analysis

import com.intellij.psi.*
import com.vyibc.autocr.model.LayerType
import org.slf4j.LoggerFactory

/**
 * 节点构建规则过滤器
 * 根据业务需求过滤应该参与知识图谱构建的类和方法
 */
class NodeBuildingRulesFilter {
    
    private val logger = LoggerFactory.getLogger(NodeBuildingRulesFilter::class.java)
    
    /**
     * 判断类是否应该被包含在知识图谱中
     */
    fun shouldIncludeClass(psiClass: PsiClass, layer: LayerType): Boolean {
        // 1. 排除DO/VO/DTO等数据传输对象
        if (isDataTransferObject(psiClass)) {
            logger.debug("排除DTO类: ${psiClass.qualifiedName}")
            return false
        }
        
        // 2. 排除测试类
        if (isTestClass(psiClass)) {
            logger.debug("排除测试类: ${psiClass.qualifiedName}")
            return false
        }
        
        // 3. 根据层级进行特殊检查
        when (layer) {
            LayerType.CONTROLLER -> {
                return isValidControllerClass(psiClass)
            }
            LayerType.SERVICE -> {
                return isValidServiceClass(psiClass)
            }
            LayerType.REPOSITORY, LayerType.MAPPER -> {
                return isValidRepositoryClass(psiClass)
            }
            LayerType.UTIL -> {
                return isValidUtilClass(psiClass)
            }
            LayerType.CONFIG -> {
                return isValidConfigClass(psiClass)
            }
            LayerType.COMPONENT -> {
                return isValidComponentClass(psiClass)
            }
            LayerType.ENTITY -> {
                // 实体类可以包含，但方法会被严格过滤
                return true
            }
            LayerType.UNKNOWN -> {
                // 未知层级需要更严格的检查
                return hasBusinessMethods(psiClass)
            }
        }
    }
    
    /**
     * 判断方法是否应该被包含在知识图谱中
     */
    fun shouldIncludeMethod(method: PsiMethod, ownerClass: PsiClass, layer: LayerType): Boolean {
        // 1. 基本规则：方法必须是public（除非是特殊情况）
        if (!method.hasModifierProperty(PsiModifier.PUBLIC) && !isSpecialMethod(method, layer)) {
            return false
        }
        
        // 2. 排除getter/setter方法（除非在特殊类中）
        if (isGetterOrSetter(method) && !shouldIncludeGetterSetter(ownerClass, layer)) {
            return false
        }
        
        // 3. 排除toString, equals, hashCode等Object方法
        if (isObjectMethod(method)) {
            return false
        }
        
        // 4. 根据层级进行特殊检查
        return when (layer) {
            LayerType.CONTROLLER -> isValidControllerMethod(method, ownerClass)
            LayerType.SERVICE -> isValidServiceMethod(method, ownerClass)
            LayerType.REPOSITORY, LayerType.MAPPER -> isValidRepositoryMethod(method, ownerClass)
            LayerType.UTIL -> isValidUtilMethod(method, ownerClass)
            LayerType.CONFIG -> isValidConfigMethod(method, ownerClass)
            LayerType.COMPONENT -> isValidComponentMethod(method, ownerClass)
            LayerType.ENTITY -> isValidEntityMethod(method, ownerClass)
            LayerType.UNKNOWN -> isValidBusinessMethod(method, ownerClass)
        }
    }
    
    // ====== 类级别验证方法 ======
    
    /**
     * 判断是否为数据传输对象
     */
    private fun isDataTransferObject(psiClass: PsiClass): Boolean {
        val className = psiClass.name?.lowercase() ?: ""
        val packageName = psiClass.qualifiedName?.lowercase() ?: ""
        
        return className.endsWith("dto") ||
                className.endsWith("vo") ||
                className.endsWith("do") ||
                className.endsWith("po") ||
                className.endsWith("entity") ||
                className.endsWith("model") ||
                packageName.contains(".dto") ||
                packageName.contains(".vo") ||
                packageName.contains(".entity") ||
                packageName.contains(".model") ||
                packageName.contains(".domain") ||
                isOnlyDataClass(psiClass)
    }
    
    /**
     * 判断是否为测试类
     */
    private fun isTestClass(psiClass: PsiClass): Boolean {
        val className = psiClass.name?.lowercase() ?: ""
        val packageName = psiClass.qualifiedName?.lowercase() ?: ""
        
        return className.endsWith("test") ||
                className.endsWith("tests") ||
                className.contains("test") ||
                packageName.contains("test") ||
                hasTestAnnotations(psiClass)
    }
    
    /**
     * 判断是否只包含数据字段的类
     */
    private fun isOnlyDataClass(psiClass: PsiClass): Boolean {
        val methods = psiClass.methods.filter { !it.isConstructor }
        if (methods.isEmpty()) return true
        
        // 如果只有getter/setter方法，认为是数据类
        val businessMethods = methods.filter { method ->
            !isGetterOrSetter(method) && !isObjectMethod(method)
        }
        
        return businessMethods.isEmpty()
    }
    
    /**
     * 验证Controller类
     */
    private fun isValidControllerClass(psiClass: PsiClass): Boolean {
        // Controller类必须有映射注解或者有映射方法
        return hasControllerAnnotations(psiClass) || hasControllerMappingMethods(psiClass)
    }
    
    /**
     * 验证Service类
     */
    private fun isValidServiceClass(psiClass: PsiClass): Boolean {
        // Service类应该有@Service注解或者包含业务逻辑方法
        return hasServiceAnnotations(psiClass) || hasBusinessLogicMethods(psiClass)
    }
    
    /**
     * 验证Repository类
     */
    private fun isValidRepositoryClass(psiClass: PsiClass): Boolean {
        return hasRepositoryAnnotations(psiClass) || hasDataAccessMethods(psiClass)
    }
    
    /**
     * 验证工具类
     */
    private fun isValidUtilClass(psiClass: PsiClass): Boolean {
        // 工具类应该有public static方法
        return hasPublicStaticMethods(psiClass)
    }
    
    /**
     * 验证配置类
     */
    private fun isValidConfigClass(psiClass: PsiClass): Boolean {
        return hasConfigAnnotations(psiClass) || hasBeanMethods(psiClass)
    }
    
    /**
     * 验证组件类
     */
    private fun isValidComponentClass(psiClass: PsiClass): Boolean {
        return hasComponentAnnotations(psiClass) || hasComponentMethods(psiClass)
    }
    
    /**
     * 判断是否有业务方法
     */
    private fun hasBusinessMethods(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            method.hasModifierProperty(PsiModifier.PUBLIC) &&
            !isGetterOrSetter(method) &&
            !isObjectMethod(method) &&
            !method.isConstructor
        }
    }
    
    // ====== 方法级别验证方法 ======
    
    /**
     * 验证Controller方法
     */
    private fun isValidControllerMethod(method: PsiMethod, ownerClass: PsiClass): Boolean {
        // Controller方法应该有映射注解或者是public业务方法
        return hasMappingAnnotations(method) || 
               (method.hasModifierProperty(PsiModifier.PUBLIC) && !isGetterOrSetter(method))
    }
    
    /**
     * 验证Service方法
     */
    private fun isValidServiceMethod(method: PsiMethod, ownerClass: PsiClass): Boolean {
        // Service方法应该是public且非getter/setter
        return method.hasModifierProperty(PsiModifier.PUBLIC) &&
               !isGetterOrSetter(method) &&
               !isObjectMethod(method) &&
               !method.isConstructor
    }
    
    /**
     * 验证Repository方法
     */
    private fun isValidRepositoryMethod(method: PsiMethod, ownerClass: PsiClass): Boolean {
        return method.hasModifierProperty(PsiModifier.PUBLIC) &&
               (hasDataAccessMethodName(method) || hasRepositoryAnnotations(method))
    }
    
    /**
     * 验证工具方法
     */
    private fun isValidUtilMethod(method: PsiMethod, ownerClass: PsiClass): Boolean {
        // 工具方法应该是public static
        return method.hasModifierProperty(PsiModifier.PUBLIC) &&
               method.hasModifierProperty(PsiModifier.STATIC) &&
               !isObjectMethod(method)
    }
    
    /**
     * 验证配置方法
     */
    private fun isValidConfigMethod(method: PsiMethod, ownerClass: PsiClass): Boolean {
        return hasBeanAnnotation(method) || 
               (method.hasModifierProperty(PsiModifier.PUBLIC) && !isGetterOrSetter(method))
    }
    
    /**
     * 验证组件方法
     */
    private fun isValidComponentMethod(method: PsiMethod, ownerClass: PsiClass): Boolean {
        return method.hasModifierProperty(PsiModifier.PUBLIC) &&
               !isGetterOrSetter(method) &&
               !isObjectMethod(method)
    }
    
    /**
     * 验证实体方法
     */
    private fun isValidEntityMethod(method: PsiMethod, ownerClass: PsiClass): Boolean {
        // 实体类中只包含有业务意义的方法，通常排除getter/setter
        return method.hasModifierProperty(PsiModifier.PUBLIC) &&
               !isGetterOrSetter(method) &&
               !isObjectMethod(method) &&
               hasBusinessLogic(method)
    }
    
    /**
     * 验证业务方法
     */
    private fun isValidBusinessMethod(method: PsiMethod, ownerClass: PsiClass): Boolean {
        return method.hasModifierProperty(PsiModifier.PUBLIC) &&
               !isGetterOrSetter(method) &&
               !isObjectMethod(method) &&
               !method.isConstructor
    }
    
    // ====== 辅助判断方法 ======
    
    /**
     * 判断是否为特殊方法（允许非public）
     */
    private fun isSpecialMethod(method: PsiMethod, layer: LayerType): Boolean {
        return when (layer) {
            LayerType.CONFIG -> hasBeanAnnotation(method)  // @Bean方法可以非public
            else -> false
        }
    }
    
    /**
     * 判断是否为getter/setter方法
     */
    private fun isGetterOrSetter(method: PsiMethod): Boolean {
        val name = method.name
        return (name.startsWith("get") || name.startsWith("set") || name.startsWith("is")) &&
               isSimpleGetterSetter(method)
    }
    
    /**
     * 判断是否为简单的getter/setter
     */
    private fun isSimpleGetterSetter(method: PsiMethod): Boolean {
        val body = method.body
        if (body == null) return true  // 接口方法或抽象方法
        
        val statements = body.statements
        return statements.size <= 1  // 只有一个return语句或一个赋值语句
    }
    
    /**
     * 判断是否应该包含getter/setter
     */
    private fun shouldIncludeGetterSetter(ownerClass: PsiClass, layer: LayerType): Boolean {
        return when (layer) {
            LayerType.CONTROLLER -> true  // Controller中的getter/setter可能有业务意义
            LayerType.SERVICE -> false    // Service中的getter/setter通常不重要
            LayerType.ENTITY -> false     // 实体类的getter/setter不重要
            else -> false
        }
    }
    
    /**
     * 判断是否为Object基础方法
     */
    private fun isObjectMethod(method: PsiMethod): Boolean {
        val name = method.name
        return name in setOf("toString", "equals", "hashCode", "clone", "finalize")
    }
    
    /**
     * 判断方法是否有业务逻辑
     */
    private fun hasBusinessLogic(method: PsiMethod): Boolean {
        val body = method.body ?: return false
        return body.statements.size > 1 || hasBusinessAnnotations(method)
    }
    
    // ====== 注解检查方法 ======
    
    private fun hasControllerAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName in setOf(
                "org.springframework.stereotype.Controller",
                "org.springframework.web.bind.annotation.RestController",
                "org.springframework.web.bind.annotation.ControllerAdvice"
            )
        }
    }
    
    private fun hasServiceAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName in setOf(
                "org.springframework.stereotype.Service",
                "javax.ejb.Stateless",
                "javax.ejb.Stateful"
            )
        }
    }
    
    private fun hasRepositoryAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName in setOf(
                "org.springframework.stereotype.Repository",
                "org.apache.ibatis.annotations.Mapper"
            )
        }
    }
    
    private fun hasConfigAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName in setOf(
                "org.springframework.context.annotation.Configuration",
                "org.springframework.boot.autoconfigure.SpringBootApplication"
            )
        }
    }
    
    private fun hasComponentAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName in setOf(
                "org.springframework.stereotype.Component"
            )
        }
    }
    
    private fun hasTestAnnotations(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any { annotation ->
            annotation.qualifiedName?.contains("Test") == true ||
            annotation.qualifiedName?.contains("junit") == true
        }
    }
    
    private fun hasMappingAnnotations(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            annotation.qualifiedName in setOf(
                "org.springframework.web.bind.annotation.RequestMapping",
                "org.springframework.web.bind.annotation.GetMapping",
                "org.springframework.web.bind.annotation.PostMapping",
                "org.springframework.web.bind.annotation.PutMapping",
                "org.springframework.web.bind.annotation.DeleteMapping",
                "org.springframework.web.bind.annotation.PatchMapping"
            )
        }
    }
    
    private fun hasBeanAnnotation(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            annotation.qualifiedName == "org.springframework.context.annotation.Bean"
        }
    }
    
    private fun hasBusinessAnnotations(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            annotation.qualifiedName in setOf(
                "org.springframework.transaction.annotation.Transactional",
                "org.springframework.cache.annotation.Cacheable",
                "org.springframework.security.access.prepost.PreAuthorize"
            )
        }
    }
    
    private fun hasRepositoryAnnotations(method: PsiMethod): Boolean {
        return method.annotations.any { annotation ->
            annotation.qualifiedName?.contains("Query") == true ||
            annotation.qualifiedName?.contains("Select") == true ||
            annotation.qualifiedName?.contains("Insert") == true ||
            annotation.qualifiedName?.contains("Update") == true ||
            annotation.qualifiedName?.contains("Delete") == true
        }
    }
    
    // ====== 方法名检查 ======
    
    private fun hasControllerMappingMethods(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            hasMappingAnnotations(method) ||
            method.name.let { name ->
                name.startsWith("handle") || name.startsWith("get") || 
                name.startsWith("post") || name.startsWith("put") || 
                name.startsWith("delete")
            }
        }
    }
    
    private fun hasBusinessLogicMethods(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            method.hasModifierProperty(PsiModifier.PUBLIC) &&
            method.name.let { name ->
                name.contains("process") || name.contains("handle") || 
                name.contains("execute") || name.contains("calculate") ||
                name.contains("validate") || name.contains("generate")
            }
        }
    }
    
    private fun hasDataAccessMethods(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            hasDataAccessMethodName(method)
        }
    }
    
    private fun hasDataAccessMethodName(method: PsiMethod): Boolean {
        val name = method.name
        return name.startsWith("find") || name.startsWith("get") ||
               name.startsWith("save") || name.startsWith("update") ||
               name.startsWith("delete") || name.startsWith("insert") ||
               name.startsWith("select") || name.startsWith("query")
    }
    
    private fun hasPublicStaticMethods(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            method.hasModifierProperty(PsiModifier.PUBLIC) &&
            method.hasModifierProperty(PsiModifier.STATIC) &&
            !isObjectMethod(method)
        }
    }
    
    private fun hasBeanMethods(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            hasBeanAnnotation(method)
        }
    }
    
    private fun hasComponentMethods(psiClass: PsiClass): Boolean {
        return psiClass.methods.any { method ->
            method.hasModifierProperty(PsiModifier.PUBLIC) &&
            !isGetterOrSetter(method) &&
            !isObjectMethod(method) &&
            !method.isConstructor
        }
    }
    
    /**
     * 获取过滤统计信息
     */
    fun getFilterStatistics(
        totalClasses: Int,
        includedClasses: Int,
        totalMethods: Int,
        includedMethods: Int
    ): FilterStatistics {
        return FilterStatistics(
            totalClasses = totalClasses,
            includedClasses = includedClasses,
            excludedClasses = totalClasses - includedClasses,
            totalMethods = totalMethods,
            includedMethods = includedMethods,
            excludedMethods = totalMethods - includedMethods,
            classInclusionRate = if (totalClasses > 0) includedClasses.toDouble() / totalClasses else 0.0,
            methodInclusionRate = if (totalMethods > 0) includedMethods.toDouble() / totalMethods else 0.0
        )
    }
}

/**
 * 过滤统计信息
 */
data class FilterStatistics(
    val totalClasses: Int,
    val includedClasses: Int,
    val excludedClasses: Int,
    val totalMethods: Int,
    val includedMethods: Int,
    val excludedMethods: Int,
    val classInclusionRate: Double,
    val methodInclusionRate: Double
)