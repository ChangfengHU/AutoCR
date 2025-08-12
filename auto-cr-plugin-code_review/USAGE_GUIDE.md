# 使用指南 - 如何找到代码评估按钮

## 🎯 快速开始

### 方法一：工具栏按钮（推荐）
1. 打开IntelliJ IDEA
2. 修改一些代码文件
3. 打开 **Git** 面板（通常在IDE底部或左侧）
4. 在Git Changes面板的工具栏中，你会看到 **"Commit & Review"** 按钮
5. 点击该按钮开始代码评估流程

### 方法二：右键菜单
1. 在Git Changes面板中
2. 右键点击任意变更的文件
3. 在弹出菜单中选择 **"Code Review"**

### 方法三：快捷键
- `Ctrl+Shift+C`: 提交并评估（最常用）
- `Ctrl+Shift+R`: 快速代码评估
- `Ctrl+Alt+Shift+R`: 测试功能（使用模拟数据）

## 📍 按钮位置说明

### Git Changes面板位置
Git Changes面板通常位于：
- **底部工具栏**: 点击底部的 "Git" 标签
- **左侧工具栏**: 点击左侧的 "Git" 图标
- **菜单访问**: `View` → `Tool Windows` → `Git`

### 工具栏按钮
在Git Changes面板中，工具栏通常包含以下按钮：
```
[Commit] [Commit & Review] [Push] [Pull] [...]
```

我们的 **"Commit & Review"** 按钮就在标准的 "Commit" 按钮旁边。

## 🔧 如果找不到按钮

### 检查插件是否正确安装
1. 打开 `File` → `Settings` → `Plugins`
2. 搜索 "Auto CR Plugin" 或 "Translation Plugin"
3. 确保插件已启用

### 检查是否有代码变更
按钮只在有待提交的代码变更时才会显示：
1. 确保你修改了一些文件
2. 这些文件应该出现在Git Changes面板中
3. 如果没有变更，按钮会被隐藏

### 重启IDE
如果插件刚安装，可能需要重启IntelliJ IDEA：
1. 关闭IDE
2. 重新打开项目
3. 检查按钮是否出现

## 🎮 所有可用的访问方式

### 1. 工具栏按钮
- **位置**: Git Changes面板工具栏
- **名称**: "Commit & Review"
- **快捷键**: `Ctrl+Shift+C`

### 2. 右键菜单
- **位置**: Git Changes面板右键菜单
- **名称**: "Code Review"
- **快捷键**: `Ctrl+Shift+R`

### 3. VCS菜单
- **位置**: 主菜单 `VCS` → "Commit and Review"
- **快捷键**: `Ctrl+Alt+R`

### 4. 测试功能
- **位置**: 主菜单 `Tools` → "Test Code Review"
- **快捷键**: `Ctrl+Alt+Shift+R`
- **说明**: 使用模拟数据测试功能

## 🚀 推荐使用流程

### 首次使用
1. 使用 `Ctrl+Alt+Shift+R` 测试功能，熟悉界面
2. 配置AI服务API Key（Settings → Tools → Code Review Settings）
3. 调整评估阈值和规则

### 日常使用
1. 修改代码
2. 使用 `Ctrl+Shift+C` 或点击工具栏按钮
3. 查看评估结果
4. 根据建议修复问题
5. 重新评估直到通过
6. 提交代码

## ❓ 常见问题

### Q: 为什么看不到按钮？
A: 确保有待提交的代码变更，按钮只在有变更时显示。

### Q: 按钮是灰色的？
A: 检查是否有有效的代码变更，或者插件是否正确加载。

### Q: 点击按钮没反应？
A: 检查IDE控制台是否有错误信息，可能需要配置AI服务。

### Q: 如何配置AI服务？
A: 打开 Settings → Tools → Code Review Settings，输入相应的API Key。

## 📞 获取帮助

如果仍然找不到按钮或遇到问题：
1. 检查IDE的Event Log（右下角通知）
2. 查看插件是否有错误信息
3. 尝试重启IDE
4. 检查插件版本是否最新

记住：**"Commit & Review"** 按钮是你进行代码评估的主要入口！🎯
