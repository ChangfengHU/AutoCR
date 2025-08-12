# 故障排除指南

## 🔍 看不到自动CR功能和配置的解决方案

### 第一步：检查插件状态
1. 打开IntelliJ IDEA
2. 点击菜单 `Tools` → `Auto CR Plugin Status`
3. 查看状态信息，确认插件是否正确加载

### 第二步：查找配置页面
配置页面应该在以下位置：
- `File` → `Settings` → `Tools` → `Auto Code Review`

如果找不到，请尝试：
1. 在Settings搜索框中输入 "Auto Code Review"
2. 或者搜索 "Code Review"
3. 或者直接搜索 "CR"

### 第三步：查找功能按钮
代码评估功能可以通过以下方式访问：

#### 方式1：快捷键（最可靠）
- `Ctrl+Alt+Shift+R`: 测试功能（使用模拟数据）
- `Ctrl+Shift+C`: 提交并评估
- `Ctrl+Shift+R`: 快速代码评估

#### 方式2：菜单
- `Tools` → `Test Code Review` (测试功能)
- `Tools` → `Auto CR Plugin Status` (状态检查)

#### 方式3：Git界面
- 在Git Changes面板的工具栏中找 "Commit & Review" 按钮
- 在Git Changes面板右键菜单中找 "Code Review"

### 第四步：验证插件安装
1. 打开 `File` → `Settings` → `Plugins`
2. 搜索插件名称（可能是 "Auto CR Plugin" 或 "Translation Plugin"）
3. 确保插件已启用（有绿色勾选标记）
4. 如果插件显示为禁用，点击启用
5. 重启IDE

### 第五步：检查项目类型
确保你的项目是Git项目：
1. 项目根目录应该有 `.git` 文件夹
2. IDE底部应该有Git工具栏
3. 如果不是Git项目，初始化Git：`git init`

### 第六步：检查代码变更
某些功能只在有代码变更时才显示：
1. 修改一些文件
2. 确保这些文件出现在Git Changes面板中
3. 然后再查找相关按钮

## 🚀 快速测试方法

### 最简单的测试方式
1. 按 `Ctrl+Alt+Shift+R`
2. 如果弹出测试对话框，说明插件工作正常
3. 如果没有反应，说明插件可能没有正确加载

### 检查配置的方法
1. 按 `Ctrl+Shift+,` 打开Settings
2. 在搜索框输入 "Auto Code Review"
3. 如果找到配置页面，说明插件已正确注册

### 检查菜单的方法
1. 点击 `Tools` 菜单
2. 查找以下项目：
   - "Test Code Review"
   - "Auto CR Plugin Status"
3. 如果找到，说明Actions已正确注册

## 🔧 常见问题解决

### 问题1：插件安装了但看不到功能
**解决方案：**
1. 重启IntelliJ IDEA
2. 检查插件是否启用
3. 检查IDE版本兼容性

### 问题2：配置页面找不到
**解决方案：**
1. 在Settings中搜索 "Code Review"
2. 检查是否在 Tools 分类下
3. 尝试重新安装插件

### 问题3：按钮不显示
**解决方案：**
1. 确保有Git项目
2. 确保有代码变更
3. 检查Git Changes面板是否打开
4. 尝试使用快捷键

### 问题4：快捷键不工作
**解决方案：**
1. 检查快捷键是否被其他插件占用
2. 在 Settings → Keymap 中搜索相关Action
3. 重新分配快捷键

## 📋 完整的功能清单

如果插件正确安装，你应该能看到：

### 配置页面
- `Settings` → `Tools` → `Auto Code Review`

### 菜单项
- `Tools` → `Test Code Review`
- `Tools` → `Auto CR Plugin Status`

### 快捷键
- `Ctrl+Alt+T`: 翻译功能
- `Ctrl+Alt+Shift+R`: 测试代码评估
- `Ctrl+Shift+C`: 提交并评估
- `Ctrl+Shift+R`: 快速代码评估

### Git界面按钮
- Git Changes工具栏中的 "Commit & Review" 按钮
- Git Changes右键菜单中的 "Code Review" 选项

## 🆘 如果仍然无法解决

1. **检查IDE日志**：
   - `Help` → `Show Log in Explorer/Finder`
   - 查看 `idea.log` 文件中的错误信息

2. **重新安装插件**：
   - 卸载当前插件
   - 重启IDE
   - 重新安装插件

3. **检查IDE版本**：
   - 确保使用的是兼容的IntelliJ IDEA版本
   - 插件支持 2024.2+ 版本

4. **清理缓存**：
   - `File` → `Invalidate Caches and Restart`
   - 选择 "Invalidate and Restart"

记住：最可靠的测试方法是使用 `Ctrl+Alt+Shift+R` 快捷键！
