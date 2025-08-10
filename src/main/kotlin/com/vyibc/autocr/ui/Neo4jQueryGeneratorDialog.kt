package com.vyibc.autocr.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.*
import com.intellij.util.ui.JBUI
import com.vyibc.autocr.service.Neo4jQueryTemplateService
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.border.TitledBorder

/**
 * Neo4j查询生成器对话框
 */
class Neo4jQueryGeneratorDialog(private val project: Project) : DialogWrapper(project) {
    
    private val queryTemplateService = Neo4jQueryTemplateService()
    
    // UI组件
    private val classNameField = JBTextField().apply { 
        toolTipText = "输入完整类名，如：com.example.UserService" 
    }
    private val methodNameField = JBTextField().apply { 
        toolTipText = "输入方法名，如：getUserById" 
    }
    private val sourceMethodField = JBTextField().apply { 
        toolTipText = "输入源方法完整ID，如：com.example.UserService.getUserById(Long)" 
    }
    private val targetMethodField = JBTextField().apply { 
        toolTipText = "输入目标方法完整ID，如：com.example.UserDAO.findById(Long)" 
    }
    
    private val queryTypeComboBox = JComboBox<QueryType>().apply {
        QueryType.values().forEach { addItem(it) }
    }
    
    private val queryResultArea = JTextArea(15, 60).apply {
        isEditable = false
        font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        lineWrap = true
        wrapStyleWord = true
    }
    
    private val generateButton = JButton("生成查询").apply {
        addActionListener { generateQuery() }
    }
    
    private val copyButton = JButton("复制查询").apply {
        addActionListener { copyQuery() }
    }

    init {
        title = "Neo4j 查询生成器"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val mainPanel = JPanel(BorderLayout())
        mainPanel.preferredSize = Dimension(800, 600)
        
        // 输入区域
        val inputPanel = createInputPanel()
        mainPanel.add(inputPanel, BorderLayout.NORTH)
        
        // 查询结果区域
        val resultPanel = createResultPanel()
        mainPanel.add(resultPanel, BorderLayout.CENTER)
        
        // 按钮区域
        val buttonPanel = createButtonPanel()
        mainPanel.add(buttonPanel, BorderLayout.SOUTH)
        
        return mainPanel
    }
    
    private fun createInputPanel(): JPanel {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)
        panel.border = TitledBorder("查询参数")
        
        // 查询类型选择
        val typePanel = JPanel(FlowLayout(FlowLayout.LEFT))
        typePanel.add(JLabel("查询类型:"))
        typePanel.add(queryTypeComboBox)
        panel.add(typePanel)
        
        // 根据查询类型显示不同的输入字段
        queryTypeComboBox.addActionListener {
            updateInputFields()
        }
        
        // 单个类/方法查询
        val singleQueryPanel = createSingleQueryPanel()
        panel.add(singleQueryPanel)
        
        // 调用链路查询
        val pathQueryPanel = createPathQueryPanel()
        panel.add(pathQueryPanel)
        
        return panel
    }
    
    private fun createSingleQueryPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder("单个查询")
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // 类名输入
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("类名:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(classNameField, gbc)
        
        // 方法名输入
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("方法名:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(methodNameField, gbc)
        
        return panel
    }
    
    private fun createPathQueryPanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        panel.border = TitledBorder("调用链路查询")
        
        val gbc = GridBagConstraints()
        gbc.insets = JBUI.insets(5)
        gbc.anchor = GridBagConstraints.WEST
        
        // 源方法
        gbc.gridx = 0; gbc.gridy = 0
        panel.add(JLabel("源方法:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(sourceMethodField, gbc)
        
        // 目标方法
        gbc.gridx = 0; gbc.gridy = 1; gbc.fill = GridBagConstraints.NONE; gbc.weightx = 0.0
        panel.add(JLabel("目标方法:"), gbc)
        gbc.gridx = 1; gbc.fill = GridBagConstraints.HORIZONTAL; gbc.weightx = 1.0
        panel.add(targetMethodField, gbc)
        
        return panel
    }
    
    private fun createResultPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.border = TitledBorder("生成的查询语句")
        
        val scrollPane = JScrollPane(queryResultArea)
        scrollPane.verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        scrollPane.horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED
        
        panel.add(scrollPane, BorderLayout.CENTER)
        return panel
    }
    
    private fun createButtonPanel(): JPanel {
        val panel = JPanel(FlowLayout(FlowLayout.RIGHT))
        panel.add(generateButton)
        panel.add(copyButton)
        return panel
    }
    
    private fun updateInputFields() {
        val selectedType = queryTypeComboBox.selectedItem as QueryType
        
        // 根据查询类型启用/禁用相关字段
        when (selectedType) {
            QueryType.CLASS_INFO, QueryType.CLASS_METHODS -> {
                classNameField.isEnabled = true
                methodNameField.isEnabled = false
                sourceMethodField.isEnabled = false
                targetMethodField.isEnabled = false
            }
            QueryType.METHOD_CALLS, QueryType.METHOD_CALLERS -> {
                classNameField.isEnabled = false
                methodNameField.isEnabled = false
                sourceMethodField.isEnabled = true
                targetMethodField.isEnabled = false
            }
            QueryType.CALL_PATH -> {
                classNameField.isEnabled = false
                methodNameField.isEnabled = false
                sourceMethodField.isEnabled = true
                targetMethodField.isEnabled = true
            }
            QueryType.INTERFACE_IMPLEMENTATIONS -> {
                classNameField.isEnabled = true
                methodNameField.isEnabled = false
                sourceMethodField.isEnabled = false
                targetMethodField.isEnabled = false
            }
        }
    }
    
    private fun generateQuery() {
        val queryType = queryTypeComboBox.selectedItem as QueryType
        val className = classNameField.text.trim()
        val methodName = methodNameField.text.trim()
        val sourceMethod = sourceMethodField.text.trim()
        val targetMethod = targetMethodField.text.trim()
        
        try {
            val query = when (queryType) {
                QueryType.CLASS_INFO -> {
                    if (className.isEmpty()) {
                        throw IllegalArgumentException("请输入类名")
                    }
                    queryTemplateService.generateClassInfoQuery(className)
                }
                QueryType.CLASS_METHODS -> {
                    if (className.isEmpty()) {
                        throw IllegalArgumentException("请输入类名")
                    }
                    queryTemplateService.generateClassMethodsQuery(className)
                }
                QueryType.METHOD_CALLS -> {
                    if (sourceMethod.isEmpty()) {
                        throw IllegalArgumentException("请输入方法ID")
                    }
                    queryTemplateService.generateMethodCallsQuery(sourceMethod)
                }
                QueryType.METHOD_CALLERS -> {
                    if (sourceMethod.isEmpty()) {
                        throw IllegalArgumentException("请输入方法ID")
                    }
                    queryTemplateService.generateMethodCallersQuery(sourceMethod)
                }
                QueryType.CALL_PATH -> {
                    if (sourceMethod.isEmpty() || targetMethod.isEmpty()) {
                        throw IllegalArgumentException("请输入源方法和目标方法ID")
                    }
                    queryTemplateService.generateCallPathQuery(sourceMethod, targetMethod)
                }
                QueryType.INTERFACE_IMPLEMENTATIONS -> {
                    if (className.isEmpty()) {
                        throw IllegalArgumentException("请输入接口名")
                    }
                    queryTemplateService.generateInterfaceImplementationsQuery(className)
                }
            }
            
            queryResultArea.text = query
            copyButton.isEnabled = true
            
        } catch (e: Exception) {
            queryResultArea.text = "生成查询时出错：${e.message}"
            copyButton.isEnabled = false
        }
    }
    
    private fun copyQuery() {
        val query = queryResultArea.text
        if (query.isNotBlank()) {
            val selection = StringSelection(query)
            Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, null)
            
            // 显示复制成功提示
            JOptionPane.showMessageDialog(
                this.contentPane,
                "查询语句已复制到剪贴板！",
                "复制成功",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }
    
    override fun createActions(): Array<Action> {
        return arrayOf(cancelAction)
    }
    
    /**
     * 查询类型枚举
     */
    enum class QueryType(val displayName: String) {
        CLASS_INFO("类基本信息"),
        CLASS_METHODS("类的所有方法"),
        METHOD_CALLS("方法调用了谁"),
        METHOD_CALLERS("谁调用了方法"),
        CALL_PATH("两方法调用链路"),
        INTERFACE_IMPLEMENTATIONS("接口实现关系");
        
        override fun toString(): String = displayName
    }
}