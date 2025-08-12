# Auto CR Plugin for IntelliJ IDEA

一个功能强大的IntelliJ IDEA插件，集成了翻译和自动代码评估功能，帮助开发者提高代码质量和开发效率。

## 功能特性

### 🔍 自动代码评估 (Auto Code Review)
- 🤖 **AI驱动评估**: 支持DeepSeek、阿里通义千问、Google Gemini等多种AI服务
- 📊 **详细分析过程**: 实时展示代码分析过程，包括变更检测、AI分析、结果生成
- 🎯 **智能评分系统**: 0-100分评分，多维度评估代码质量
- ⚠️ **风险等级评估**: 低、中、高、严重四级风险评估
- 🚫 **阈值控制**: 可配置最低评分和风险等级阈值，自动阻止低质量代码提交
- 📋 **问题分类**: 代码风格、性能、安全、Bug风险、可维护性等多维度问题检测
- 💡 **改进建议**: 针对发现的问题提供具体的修复建议

### 🌍 智能翻译
- 🚀 **快速翻译**: 选中文本后右键点击或使用快捷键即可翻译
- 🌐 **多语言支持**: 支持中文、英语、日语、韩语、法语、德语、西班牙语等多种语言
- ⚙️ **可配置设置**: 可自定义默认目标语言、源语言检测等设置
- 💬 **美观界面**: 提供美观的翻译结果对话框
- 📋 **一键复制**: 可快速复制翻译结果到剪贴板

## 安装方法

### 从源码构建

1. 克隆项目到本地:
```bash
git clone <repository-url>
cd auto-cr-plugin
```

2. 构建插件:
```bash
./gradlew buildPlugin
 ./gradlew buildPlugin -x buildSearchableOptions
```

3. 在IntelliJ IDEA中安装:
   - 打开 `File` -> `Settings` -> `Plugins`
   - 点击齿轮图标 -> `Install Plugin from Disk...`
   - 选择 `build/distributions/` 目录下的zip文件

## 使用方法

### 🔍 代码评估功能

#### 基本使用
**方式一：测试功能（推荐）**
1. 使用快捷键 `Ctrl+Alt+Shift+R` 或菜单 `Tools` -> `Test Code Review`
2. 插件会使用模拟数据演示完整的代码评估流程
3. 实时查看分析过程，包括：
   - 📁 **代码变更检测**: 显示修改的文件和变更统计
   - 🤖 **AI分析过程**: 展示AI服务调用和分析过程
   - 📊 **评估结果**: 显示评分、风险等级和具体问题
4. 根据评估结果决定是否允许提交：
   - ✅ **通过阈值**: 显示绿色提交按钮，允许提交
   - ❌ **未通过阈值**: 禁用提交按钮，显示具体原因

**方式二：实际使用**
有三种不同的代码评估场景：

1. **评估已暂存变更** (git add 后的代码):
   - 工具栏按钮: "Review Staged Changes"
   - 快捷键: `Ctrl+Shift+A`
   - 用途: 评估已经 `git add` 但还没有 `commit` 的代码

2. **评估已提交变更** (git commit 后的代码):
   - 工具栏按钮: "Review Committed Changes"
   - 快捷键: `Ctrl+Shift+P`
   - 用途: 评估已经 `git commit` 但还没有 `push` 的代码

3. **完整提交流程**:
   - 工具栏按钮: "Commit & Review"
   - 快捷键: `Ctrl+Shift+M`
   - 用途: 一站式评估并提交代码

4. **右键菜单快速评估**:
   - 右键菜单: "Code Review"
   - 快捷键: `Ctrl+Alt+C`
   - 用途: 在任何地方右键快速进行代码评估

#### 配置AI服务
1. 打开 `File` -> `Settings` -> `Tools` -> `Code Review Settings`
2. 配置AI服务：
   - **DeepSeek**: 输入API Key，适合代码分析
   - **阿里通义千问**: 输入API Key，中文支持良好
   - **Google Gemini**: 输入API Key，多语言支持
   - **主要服务**: 选择优先使用的AI服务
3. 配置评估规则：
   - **最低评分要求**: 设置代码提交的最低评分（0-100）
   - **阻止高风险提交**: 启用后会阻止高风险和严重风险的代码提交
   - **检查项目**: 选择要检查的代码质量维度

#### 评估维度
- 🎨 **代码风格**: 命名规范、格式化、注释等
- ⚡ **性能问题**: 算法效率、资源使用等
- 🔒 **安全风险**: 潜在的安全漏洞和风险
- 🐛 **Bug风险**: 可能导致运行时错误的代码
- 🔧 **可维护性**: 代码复杂度、可读性等
- 📚 **文档问题**: 缺失的注释和文档
- ✨ **最佳实践**: 是否遵循语言和框架的最佳实践

### 🌍 翻译功能

#### 基本使用
1. 在编辑器中选中要翻译的文本
2. 右键点击选择 "Translate" 或使用快捷键 `Ctrl+Alt+T`
3. 查看翻译结果对话框

#### 配置设置
1. 打开 `File` -> `Settings` -> `Tools` -> `Translation Settings`
2. 配置以下选项:
   - **默认目标语言**: 设置翻译的目标语言
   - **默认源语言**: 设置源语言（可选择自动检测）
   - **自动检测源语言**: 启用后会自动检测源语言
   - **显示翻译结果对话框**: 控制是否显示详细的翻译对话框

## 支持的语言

- 中文 (zh)
- 英语 (en)
- 日语 (ja)
- 韩语 (ko)
- 法语 (fr)
- 德语 (de)
- 西班牙语 (es)
- 俄语 (ru)
- 意大利语 (it)
- 葡萄牙语 (pt)
- 阿拉伯语 (ar)
- 印地语 (hi)
- 泰语 (th)
- 越南语 (vi)

## 技术实现

- **语言**: Kotlin
- **框架**: IntelliJ Platform SDK
- **翻译服务**: Google Translate API (免费版)
- **UI**: Swing + IntelliJ Platform UI组件
- **异步处理**: Kotlin Coroutines

## 项目结构

```
src/main/kotlin/com/vyibc/autocrplugin/
├── action/
│   ├── TranslateAction.kt              # 翻译Action
│   └── CommitAndReviewAction.kt        # 代码评估Action
├── service/
│   ├── TranslationService.kt           # 翻译服务接口
│   ├── CodeReviewService.kt            # 代码评估服务接口
│   ├── AIService.kt                    # AI服务接口
│   ├── AIServiceManager.kt             # AI服务管理器
│   ├── GitChangeAnalyzer.kt            # Git变更分析器
│   └── impl/
│       ├── GoogleTranslationService.kt # Google翻译实现
│       ├── AICodeReviewService.kt      # AI代码评估实现
│       ├── DeepSeekService.kt          # DeepSeek AI服务
│       ├── TongyiService.kt            # 阿里通义千问服务
│       └── GeminiService.kt            # Google Gemini服务
├── settings/
│   ├── TranslationSettings.kt          # 翻译设置存储
│   ├── TranslationConfigurable.kt      # 翻译设置UI
│   ├── CodeReviewSettings.kt           # 代码评估设置存储
│   └── CodeReviewConfigurable.kt       # 代码评估设置UI
├── ui/
│   ├── TranslationDialog.kt            # 翻译结果对话框
│   ├── CodeReviewDialog.kt             # 代码评估结果对话框
│   └── CodeReviewProcessDialog.kt      # 代码评估过程对话框
├── vcs/
│   └── CommitReviewExtension.kt        # Git提交扩展
└── util/
    ├── TranslationBundle.kt            # 国际化工具类
    └── CodeReviewUtils.kt              # 代码评估工具类
```

## 开发说明

### 环境要求

- JDK 21+
- IntelliJ IDEA 2024.2+
- Gradle 8.0+

### 开发命令

```bash
# 运行插件开发环境
./gradlew runIde

# 构建插件
./gradlew buildPlugin

# 运行测试
./gradlew test
```

## 贡献指南

欢迎提交Issue和Pull Request来改进这个插件！

## 许可证

MIT License

## 更新日志

### v2.0.0 (当前版本)
- 🆕 **新增自动代码评估功能**
  - 集成DeepSeek、阿里通义千问、Google Gemini等AI服务
  - 实时展示代码分析过程
  - 智能评分和风险等级评估
  - 可配置的质量阈值控制
  - 详细的问题分类和改进建议
- 🔧 **增强的Git集成**
  - Git提交界面扩展
  - "Commit and Review"功能
  - 自动阻止低质量代码提交
- ⚙️ **完善的配置系统**
  - 独立的代码评估设置页面
  - 多AI服务配置和管理
  - 灵活的评估规则配置
- 🎨 **改进的用户界面**
  - 代码评估过程展示对话框
  - 详细的评估结果展示
  - 更好的用户体验

### v1.0.0
- 初始版本
- 基本翻译功能
- Google Translate集成
- 设置配置页面
- 翻译结果对话框
