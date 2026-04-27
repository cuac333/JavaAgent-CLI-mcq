# JavaAgent CLI

JavaAgent CLI 是一个面向课堂展示的 Java 命令行 Agent 项目，用来演示 Claude Code 风格工具调用的核心思想。

项目重点不是做一个复杂聊天机器人，而是用尽量清晰的 Java 代码展示这些工程能力：

- 同步 agent 循环
- 模型返回 tool call 后执行本地工具
- 工具执行结果回灌给模型继续回答
- 敏感操作审批
- 审批缓存
- workspace-first 权限规则
- mock 模式稳定演示
- OpenAI-compatible real 模式可选接入真实模型
- 多会话保存与切换
- Maven 打包成可执行 jar
- 单元测试覆盖核心逻辑

## 项目结构

```text
javaagent-cli/
├── pom.xml
├── README.md
├── docs/
│   ├── architecture-report.md
│   └── defense-demo-script.md
├── src/main/java/com/javagent/
│   ├── JavaAgentCLI.java
│   ├── core/
│   ├── model/
│   └── tools/
└── src/test/java/com/javagent/
```

核心目录说明：

- `core/`：agent 循环、配置、审批、会话管理
- `model/`：mock 模型客户端、OpenAI-compatible 客户端、消息结构
- `tools/`：读文件、搜索、列目录、写文件、删文件、bash 工具
- `docs/`：答辩脚本和架构说明

## 环境要求

需要提前安装：

- Java 21 或更高版本
- Maven 3.8 或更高版本

检查命令：

```bash
java -version
mvn -v
```

如果 macOS 终端提示找不到 Java，但本机用 Homebrew 安装过 OpenJDK，可以先试这些路径：

```bash
/opt/homebrew/opt/openjdk/bin/java -version
/usr/local/opt/openjdk/bin/java -version
```

## 第一次运行

先 `cd` 进入你本地安装 `javaagent-cli` 项目的位置。不要直接在用户主目录、桌面目录或下载目录运行 Maven，否则会找不到 `pom.xml`。

```bash
# 把下面路径替换成你本地安装 javaagent-cli 项目的位置
cd /path/to/javaagent-cli
```

确认当前目录里有 `pom.xml`：

```bash
ls pom.xml
```

演示前打包并启动 mock 模式：

```bash
mvn clean package
java -jar target/javaagent-cli-1.0.0.jar --mock
```

如果 `java` 命令不可用，用完整 Java 路径启动：

```bash
/opt/homebrew/opt/openjdk/bin/java -jar target/javaagent-cli-1.0.0.jar --mock
```

启动成功后会看到类似输出：

```text
==========================================
四川农业大学
信息工程学院
JavaAgent CLI 课程项目
作者：莫承潜 黄麟淞 王郅为 黄春云 胡鸿扬
==========================================
Mode: mock
Type /help for commands.
javaagent>
```

看到 `javaagent>` 就说明程序已经进入交互模式。

## 课堂演示策略

课堂主线必须使用 mock 模式。mock 模式结果稳定，不依赖课堂网络、API key、代理服务、余额或模型限流。

mock 主线用于证明：

- agent loop 是完整的
- 模型能发起 tool call
- 工具结果会返回给模型
- 写文件、删文件等危险操作需要审批
- 会话可以保存和切换

real 模式只作为加分展示。它用来证明同一套架构可以接入真实 OpenAI-compatible 模型服务，但不要把它作为唯一演示路径。

## Mock 主线演示流程

启动程序后，按下面顺序输入。建议一行一行输入，等上一条输出结束后再输入下一条。

```text
/help
/status
/tools
/clear
读取 pom.xml
搜索 agent
/stream on
列出 src/main/java/com/javagent
把 "hello from demo" 写入 notes.txt
yes
/save demo-session
/sessions
/load latest
/exit
```

每一步要说明的点：

- `/help`：展示 CLI 支持哪些命令，让老师先看到系统入口
- `/status`：展示当前模式、工具数量、审批缓存、会话目录
- `/tools`：展示已注册工具，以及哪些工具自动通过、哪些需要审批
- `/clear`：清空当前上下文，避免恢复旧会话影响演示
- `读取 pom.xml`：触发 `read_file`，证明只读工具自动执行
- `搜索 agent`：触发 `grep`，证明搜索工具可以扫描项目
- `/stream on`：打开控制台流式输出
- `列出 src/main/java/com/javagent`：触发 `list_directory`，展示核心包结构
- `把 "hello from demo" 写入 notes.txt`：触发 `write_file`，程序会要求审批
- `yes`：批准写文件
- `/save demo-session`：保存当前会话
- `/sessions`：列出保存过的会话
- `/load latest`：加载最新会话
- `/exit`：退出程序

写文件时出现下面提示是正常的：

```text
Approval required:
  tool: write_file
  args: append=false, content=hello from demo, path=notes.txt
Approve? [yes/no/cancel]:
```

这里输入：

```text
yes
```

## Real 加分演示流程

real 模式需要提前配置 API。没有 API key 时不要演示 real 模式，直接跳过，不影响主线演示结论。

真实 API key 不要写进 README，也不要发到群文件、PPT、代码仓库或截图里。组员需要演示 real 模式时，向负责人私下获取临时演示 key，然后只写入自己本机的 `config.properties`。

在项目根目录创建 `config.properties`。项目根目录就是有 `pom.xml` 的目录。

先进入项目根目录：

```bash
# 把下面路径替换成你本地安装 javaagent-cli 项目的位置
cd /path/to/javaagent-cli
```

确认当前目录正确：

```bash
ls pom.xml
```

能看到 `pom.xml` 后，在同一个终端里执行下面命令创建配置文件：

```bash
cat > config.properties <<'EOF'
agent.mock_mode=false
agent.base_url=https://right.codes/codex/v1
agent.model=gpt-5.4-mini
agent.api_key=<临时演示 key>
EOF
```

然后打开 `config.properties`，把 `<临时演示 key>` 替换成负责人提供的真实临时 key。

注意：

- `config.properties` 已经写入 `.gitignore`
- 不要提交真实 API key
- 不要在答辩屏幕上打开包含真实 API key 的配置文件
- 建议使用临时演示 key，用完后废弃或轮换

启动仍然可以先用 mock：

```bash
java -jar target/javaagent-cli-1.0.0.jar --mock
```

进入交互界面后输入：

```text
/mode real
/status
你是谁？
```

预期效果：模型会通过 `gpt-5.4-mini` 返回真实回答。

如果输入 `/mode real` 后看到：

```text
Warning: real mode is selected but no API key is configured.
```

说明当前项目目录没有配置 `agent.api_key`，或者 `config.properties` 不在项目根目录。先退出程序，确认当前目录是项目根目录，再检查：

```bash
pwd
ls config.properties
```

如果只是主线演示，可以忽略 real 模式并切回 mock：

```text
/mode mock
```

讲解话术：

```text
这里切到 real 模式后，同一个 Java Agent CLI 不再使用 mock 模型，而是通过 OpenAI-compatible API 调用真实模型 gpt-5.4-mini。
这个问题不需要工具调用，所以可以直接验证真实模型回复链路。
```

如果 real 模式失败，常见原因是网络、API key、代理服务或模型额度问题。课堂上不要现场排查太久，直接回到 mock 主线。

## 常用命令

```text
/help
/exit
/quit
/clear
/new [title]
/save [title]
/load [id|title|latest]
/sessions
/tools
/mode mock|real
/stream on|off
/bash on|off
/prompt show
/prompt set <text>
/prompt reset
/approvals clear
/status
```

命令说明：

- `/help`：查看命令列表
- `/clear`：开启一个新会话
- `/new [title]`：开启一个带标题的新会话
- `/save [title]`：保存当前会话
- `/load latest`：加载最新会话
- `/load demo-session`：按标题加载会话
- `/sessions`：列出历史会话
- `/tools`：列出当前注册的工具
- `/mode mock`：切回 mock 模式
- `/mode real`：切到 real 模式
- `/stream on`：开启流式输出
- `/stream off`：关闭流式输出
- `/bash on`：启用 bash 工具
- `/bash off`：关闭 bash 工具
- `/prompt show`：查看自定义 system prompt
- `/prompt set <text>`：设置自定义 system prompt
- `/prompt reset`：清除自定义 system prompt
- `/approvals clear`：清空审批缓存
- `/status`：查看运行状态

## 工具说明

当前默认注册 5 个工具：

- `read_file`：读取文本文件，自动通过
- `grep`：搜索文本，自动通过
- `list_directory`：列目录，自动通过
- `write_file`：写文件，需要审批
- `delete_file`：删除普通文件，需要审批

`bash` 工具默认关闭。只有执行 `/bash on` 后才会注册，并且始终需要审批。课堂主线不建议打开 bash。

## 常见问题

### Maven 报错：找不到 POM

报错示例：

```text
The goal you specified requires a project to execute but there is no POM in this directory
```

原因：没有进入项目目录。

解决：先进入你自己的项目目录，再打包。

```bash
cd /path/to/javaagent-cli
mvn clean package
```

### 报错：Unable to access jarfile

原因：还没有成功执行 `mvn clean package`，或者当前目录不对。

解决：先进入你自己的项目目录，重新打包，再启动。

```bash
cd /path/to/javaagent-cli
mvn clean package
java -jar target/javaagent-cli-1.0.0.jar --mock
```

### 报错：Unable to locate a Java Runtime

原因：当前终端找不到 Java。

解决：

```bash
/opt/homebrew/opt/openjdk/bin/java -jar target/javaagent-cli-1.0.0.jar --mock
/usr/local/opt/openjdk/bin/java -jar target/javaagent-cli-1.0.0.jar --mock
```

### Maven shade 出现 warning

`mvn clean package` 时可能看到 `maven-shade-plugin` 的 warning，例如重复的 `LICENSE`、`NOTICE`、`MANIFEST.MF`。

这不是构建失败。只要最后看到：

```text
BUILD SUCCESS
```

就说明 jar 已经打包成功。

### 启动后自动恢复旧会话

程序启动时可能显示：

```text
Restored previous session
```

这是正常现象。演示时先输入：

```text
/clear
```

就可以从新会话开始。

### real 模式报 API key 错误

如果看到：

```text
Real mode requires an API key
```

说明没有配置 `agent.api_key`。如果只是课堂主线演示，直接切回 mock：

```text
/mode mock
```

### real 模式报模型不可用

如果代理端点提示模型不存在或未配置，需要确认 `config.properties` 中是：

```properties
agent.model=gpt-5.4-mini
```

## 清理演示产生的文件

演示可能产生这些文件或目录：

```text
notes.txt
demo-output.txt
cache-demo.txt
last_session.json
.javaagent-cli/
target/
```

这些都已经在 `.gitignore` 中忽略，不会被提交。需要恢复干净演示环境时，可以删除它们：

```bash
rm -f notes.txt demo-output.txt cache-demo.txt last_session.json
rm -rf .javaagent-cli target
```

然后重新打包：

```bash
mvn clean package
```

## 测试

运行全部单元测试：

```bash
mvn test
```

正常情况下会看到：

```text
Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 答辩材料

- `docs/defense-demo-script.md`
- `docs/architecture-report.md`

建议组员先按 README 跑通项目，再看 `docs/defense-demo-script.md` 练习 3-5 分钟讲解。
