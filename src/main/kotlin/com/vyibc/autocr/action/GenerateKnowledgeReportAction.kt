package com.vyibc.autocr.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.*
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AllClassesSearch
import com.intellij.psi.util.PsiTreeUtil
import org.slf4j.LoggerFactory
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JOptionPane

/**
 * 生成知识图谱扫描报告的Action
 */
class GenerateKnowledgeReportAction : AnAction("生成知识图谱报告", "扫描项目并生成知识图谱分析报告", null) {
    
    private val logger = LoggerFactory.getLogger(GenerateKnowledgeReportAction::class.java)
    
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
        UNKNOWN("未分类", "❓")
    }
    
    /**
     * 类信息
     */
    data class ClassInfo(
        val name: String,
        val qualifiedName: String,
        val packageName: String,
        val layer: LayerType,
        val methods: List<MethodInfo>,
        val annotations: List<String>
    )
    
    /**
     * 方法信息
     */
    data class MethodInfo(
        val name: String,
        val signature: String,
        val returnType: String,
        val parameters: List<String>,
        val modifiers: Set<String>
    )
    
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        
        // 使用进度条显示扫描过程
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "正在扫描项目知识图谱...", true) {
            override fun run(indicator: ProgressIndicator) {
                try {
                    indicator.text = "正在初始化扫描..."
                    indicator.fraction = 0.0
                    
                    val report = scanProject(project, indicator)
                    
                    indicator.text = "正在生成报告文件..."
                    indicator.fraction = 0.9
                    
                    val reportFile = generateReportFile(project, report)
                    
                    indicator.fraction = 1.0
                    
                    // 在EDT线程中打开文件
                    ApplicationManager.getApplication().invokeLater {
                        openReportFile(project, reportFile)
                        
                        JOptionPane.showMessageDialog(
                            null,
                            """
                            <html>
                            <h3>✅ 知识图谱报告生成成功！</h3>
                            <p>报告已保存到项目根目录：</p>
                            <p><code>${reportFile.name}</code></p>
                            <br>
                            <p>报告已自动打开，您可以查看扫描结果。</p>
                            </html>
                            """.trimIndent(),
                            "报告生成成功",
                            JOptionPane.INFORMATION_MESSAGE
                        )
                    }
                    
                } catch (e: Exception) {
                    logger.error("Failed to generate knowledge graph report", e)
                    
                    ApplicationManager.getApplication().invokeLater {
                        JOptionPane.showMessageDialog(
                            null,
                            """
                            <html>
                            <h3>❌ 报告生成失败</h3>
                            <p>错误信息：${e.message}</p>
                            </html>
                            """.trimIndent(),
                            "生成失败",
                            JOptionPane.ERROR_MESSAGE
                        )
                    }
                }
            }
        })
    }
    
    /**
     * 扫描项目
     */
    private fun scanProject(project: Project, indicator: ProgressIndicator): String {
        val classes = mutableListOf<ClassInfo>()
        val statistics = mutableMapOf<LayerType, Int>()
        LayerType.values().forEach { statistics[it] = 0 }
        
        indicator.text = "正在收集项目中的所有类..."
        indicator.fraction = 0.1
        
        // 使用 AllClassesSearch 查找所有类
        val allClasses = ApplicationManager.getApplication().runReadAction<List<PsiClass>> {
            val scope = GlobalSearchScope.projectScope(project)
            AllClassesSearch.search(scope, project).findAll().toList()
        }
        
        indicator.text = "正在分析 ${allClasses.size} 个类..."
        indicator.fraction = 0.2
        
        // 分析每个类
        allClasses.forEachIndexed { index, psiClass ->
            if (indicator.isCanceled) return@forEachIndexed
            
            indicator.text = "正在分析: ${psiClass.name}"
            indicator.fraction = 0.2 + (0.7 * index / allClasses.size)
            
            ApplicationManager.getApplication().runReadAction {
                val classInfo = analyzeClass(psiClass)
                classes.add(classInfo)
                statistics[classInfo.layer] = statistics[classInfo.layer]!! + 1
            }
        }
        
        // 生成报告内容
        return buildReport(classes, statistics, project)
    }
    
    /**
     * 分析单个类
     */
    private fun analyzeClass(psiClass: PsiClass): ClassInfo {
        val layer = detectLayer(psiClass)
        val methods = analyzeMethods(psiClass)
        val annotations = psiClass.annotations.map { it.qualifiedName ?: "" }.filter { it.isNotEmpty() }
        
        return ClassInfo(
            name = psiClass.name ?: "Unknown",
            qualifiedName = psiClass.qualifiedName ?: "",
            packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: "",
            layer = layer,
            methods = methods,
            annotations = annotations
        )
    }
    
    /**
     * 检测类所属层级
     */
    private fun detectLayer(psiClass: PsiClass): LayerType {
        val annotations = psiClass.annotations
        val className = psiClass.name ?: ""
        val packageName = (psiClass.containingFile as? PsiJavaFile)?.packageName ?: ""
        
        // 1. 根据注解判断
        for (annotation in annotations) {
            when (annotation.qualifiedName) {
                "org.springframework.stereotype.Controller",
                "org.springframework.web.bind.annotation.RestController" -> return LayerType.CONTROLLER
                
                "org.springframework.stereotype.Service" -> return LayerType.SERVICE
                
                "org.springframework.stereotype.Repository" -> return LayerType.REPOSITORY
                
                "org.apache.ibatis.annotations.Mapper",
                "org.mybatis.spring.annotation.MapperScan" -> return LayerType.MAPPER
                
                "org.springframework.context.annotation.Configuration",
                "org.springframework.boot.context.properties.ConfigurationProperties" -> return LayerType.CONFIG
                
                "javax.persistence.Entity",
                "org.springframework.data.mongodb.core.mapping.Document" -> return LayerType.ENTITY
            }
        }
        
        // 2. 根据包名判断
        when {
            packageName.contains("controller") -> return LayerType.CONTROLLER
            packageName.contains("service") -> return LayerType.SERVICE
            packageName.contains("mapper") || packageName.contains("dao") -> return LayerType.MAPPER
            packageName.contains("repository") -> return LayerType.REPOSITORY
            packageName.contains("util") || packageName.contains("utils") -> return LayerType.UTIL
            packageName.contains("entity") || packageName.contains("model") || packageName.contains("domain") -> return LayerType.ENTITY
            packageName.contains("config") -> return LayerType.CONFIG
        }
        
        // 3. 根据类名后缀判断
        when {
            className.endsWith("Controller") -> return LayerType.CONTROLLER
            className.endsWith("Service") || className.endsWith("ServiceImpl") -> return LayerType.SERVICE
            className.endsWith("Mapper") || className.endsWith("Dao") || className.endsWith("DAO") -> return LayerType.MAPPER
            className.endsWith("Repository") -> return LayerType.REPOSITORY
            className.endsWith("Util") || className.endsWith("Utils") || className.endsWith("Helper") -> return LayerType.UTIL
            className.endsWith("Entity") || className.endsWith("Model") || className.endsWith("DO") || className.endsWith("DTO") || className.endsWith("VO") -> return LayerType.ENTITY
            className.endsWith("Config") || className.endsWith("Configuration") -> return LayerType.CONFIG
        }
        
        // 4. 根据继承关系判断
        val superClass = psiClass.superClass
        if (superClass != null) {
            val superClassName = superClass.name ?: ""
            when {
                superClassName.contains("Controller") -> return LayerType.CONTROLLER
                superClassName.contains("Service") -> return LayerType.SERVICE
            }
        }
        
        return LayerType.UNKNOWN
    }
    
    /**
     * 分析类中的方法
     */
    private fun analyzeMethods(psiClass: PsiClass): List<MethodInfo> {
        return psiClass.methods.map { method ->
            MethodInfo(
                name = method.name,
                signature = buildMethodSignature(method),
                returnType = method.returnType?.presentableText ?: "void",
                parameters = method.parameterList.parameters.map { 
                    "${it.type.presentableText} ${it.name}"
                },
                modifiers = extractModifiers(method)
            )
        }
    }
    
    /**
     * 构建方法签名
     */
    private fun buildMethodSignature(method: PsiMethod): String {
        val params = method.parameterList.parameters.joinToString(", ") { 
            it.type.presentableText
        }
        return "${method.name}($params)"
    }
    
    /**
     * 提取方法修饰符
     */
    private fun extractModifiers(method: PsiMethod): Set<String> {
        val modifiers = mutableSetOf<String>()
        if (method.hasModifierProperty(PsiModifier.PUBLIC)) modifiers.add("public")
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) modifiers.add("private")
        if (method.hasModifierProperty(PsiModifier.PROTECTED)) modifiers.add("protected")
        if (method.hasModifierProperty(PsiModifier.STATIC)) modifiers.add("static")
        if (method.hasModifierProperty(PsiModifier.FINAL)) modifiers.add("final")
        if (method.hasModifierProperty(PsiModifier.ABSTRACT)) modifiers.add("abstract")
        return modifiers
    }
    
    /**
     * 构建报告内容
     */
    private fun buildReport(classes: List<ClassInfo>, statistics: Map<LayerType, Int>, project: Project): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val totalClasses = classes.size
        val totalMethods = classes.sumOf { it.methods.size }
        
        val report = StringBuilder()
        report.appendLine("# 📊 AutoCR 知识图谱扫描报告")
        report.appendLine()
        report.appendLine("**项目名称**: ${project.name}")
        report.appendLine("**扫描时间**: $timestamp")
        report.appendLine("**AutoCR版本**: 1.0.0")
        report.appendLine()
        report.appendLine("---")
        report.appendLine()
        
        // 总体统计
        report.appendLine("## 📈 总体统计")
        report.appendLine()
        report.appendLine("| 指标 | 数量 |")
        report.appendLine("|------|------|")
        report.appendLine("| **总类数** | $totalClasses |")
        report.appendLine("| **总方法数** | $totalMethods |")
        report.appendLine("| **平均方法数/类** | ${if (totalClasses > 0) "%.2f".format(totalMethods.toDouble() / totalClasses) else "0"} |")
        report.appendLine()
        
        // 层级分布
        report.appendLine("## 🏗️ 层级分布")
        report.appendLine()
        report.appendLine("| 层级 | 类数量 | 占比 |")
        report.appendLine("|------|--------|------|")
        statistics.filter { it.value > 0 }.forEach { (layer, count) ->
            val percentage = if (totalClasses > 0) "%.1f%%".format(100.0 * count / totalClasses) else "0%"
            report.appendLine("| ${layer.emoji} **${layer.displayName}** | $count | $percentage |")
        }
        report.appendLine()
        
        // 各层级详细信息
        LayerType.values().forEach { layer ->
            val layerClasses = classes.filter { it.layer == layer }
            if (layerClasses.isNotEmpty()) {
                report.appendLine("## ${layer.emoji} ${layer.displayName}")
                report.appendLine()
                report.appendLine("### 类列表 (${layerClasses.size} 个)")
                report.appendLine()
                
                layerClasses.sortedBy { it.name }.forEach { classInfo ->
                    report.appendLine("#### 📦 `${classInfo.name}`")
                    report.appendLine("- **全限定名**: `${classInfo.qualifiedName}`")
                    report.appendLine("- **包名**: `${classInfo.packageName}`")
                    
                    if (classInfo.annotations.isNotEmpty()) {
                        report.appendLine("- **注解**: ${classInfo.annotations.joinToString(", ") { "`@${it.substringAfterLast(".")}`" }}")
                    }
                    
                    report.appendLine("- **方法数量**: ${classInfo.methods.size}")
                    
                    if (classInfo.methods.isNotEmpty()) {
                        report.appendLine()
                        report.appendLine("  <details>")
                        report.appendLine("  <summary>展开查看方法列表</summary>")
                        report.appendLine()
                        report.appendLine("  | 方法名 | 返回类型 | 参数 | 修饰符 |")
                        report.appendLine("  |--------|----------|------|--------|")
                        
                        classInfo.methods.sortedBy { it.name }.forEach { method ->
                            val params = if (method.parameters.isEmpty()) "无" else method.parameters.joinToString(", ")
                            val modifiers = if (method.modifiers.isEmpty()) "-" else method.modifiers.joinToString(" ")
                            report.appendLine("  | `${method.name}` | `${method.returnType}` | $params | $modifiers |")
                        }
                        
                        report.appendLine()
                        report.appendLine("  </details>")
                    }
                    
                    report.appendLine()
                }
            }
        }
        
        // 分析建议
        report.appendLine("## 💡 分析建议")
        report.appendLine()
        
        val suggestions = mutableListOf<String>()
        
        // 根据统计数据给出建议
        val unknownCount = statistics[LayerType.UNKNOWN] ?: 0
        if (unknownCount > totalClasses * 0.2) {
            suggestions.add("🔍 有 **${unknownCount}** 个类未能识别层级，建议规范命名或添加注解")
        }
        
        val avgMethods = if (totalClasses > 0) totalMethods.toDouble() / totalClasses else 0.0
        if (avgMethods > 20) {
            suggestions.add("⚠️ 平均每个类有 **%.1f** 个方法，某些类可能过于复杂，建议进行拆分".format(avgMethods))
        }
        
        val utilCount = statistics[LayerType.UTIL] ?: 0
        if (utilCount > totalClasses * 0.3) {
            suggestions.add("🔧 工具类占比较高 (**%.1f%%**)，考虑是否存在过度使用静态方法的情况".format(100.0 * utilCount / totalClasses))
        }
        
        if (suggestions.isEmpty()) {
            suggestions.add("✅ 项目结构清晰，层级划分合理")
        }
        
        suggestions.forEach { suggestion ->
            report.appendLine("- $suggestion")
        }
        
        report.appendLine()
        report.appendLine("---")
        report.appendLine()
        report.appendLine("*本报告由 AutoCR 插件自动生成*")
        
        return report.toString()
    }
    
    /**
     * 生成报告文件
     */
    private fun generateReportFile(project: Project, content: String): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val fileName = "AutoCR_Report_$timestamp.md"
        val file = File(project.basePath, fileName)
        
        file.writeText(content)
        
        return file
    }
    
    /**
     * 打开报告文件
     */
    private fun openReportFile(project: Project, file: File) {
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
        virtualFile?.let {
            FileEditorManager.getInstance(project).openFile(it, true)
        }
    }
    
    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null
    }
}