# JavaAgent CLI 3-5 分钟答辩脚本

## 目标

主线使用稳定的 mock 模式演示，最后可选使用 real 模式作为加分展示。

mock 主线要证明三件事：

1. agent loop 是完整的。
2. 工具调用和审批机制是可见、可控的。
3. 项目不只是玩具 CLI，而是有配置、会话、权限、测试和打包的完整工程。

real 加分项只证明一件事：同一套架构可以切换到真实 OpenAI-compatible 模型服务。

## 演示前准备

先进入你本地安装 `javaagent-cli` 的项目目录：

```bash
cd /path/to/javaagent-cli
```

确认当前目录正确：

```bash
ls pom.xml
```

打包并启动 mock 模式：

```bash
mvn clean package
java -jar target/javaagent-cli-1.0.0.jar --mock
```

如果 `java` 命令不可用，可以尝试：

```bash
/opt/homebrew/opt/openjdk/bin/java -jar target/javaagent-cli-1.0.0.jar --mock
```

看到 `javaagent>` 后开始演示。

## 时间安排

- 0:00 - 0:30：项目定位
- 0:30 - 1:10：命令、状态、工具列表
- 1:10 - 2:00：只读工具链
- 2:00 - 3:10：审批和写文件
- 3:10 - 4:00：会话保存和加载
- 4:00 - 4:40：架构总结
- 可选 0:30 - 1:00：real 模式加分展示

## Part 1：开场

讲解：

```text
我们的项目是一个课堂级 Java Agent CLI。它把 Claude Code 风格的核心机制迁移成了纯 Java 实现，包括 agent loop、tool calling、审批机制、会话持久化，以及 mock/real 模型切换。

今天主线用 mock 模式演示，因为课堂环境不应该依赖网络和 API 稳定性。最后如果条件允许，再切 real 模式展示真实模型接入。
```

## Part 2：展示命令、状态和工具

输入：

```text
/help
/status
/tools
/clear
```

讲解：

```text
先看 CLI 支持的命令，再看当前运行状态。这里可以看到 mode=mock、streamResponses、approvalCacheEnabled、tools 等信息。

/tools 会列出所有工具，并标出哪些是 auto approved，哪些需要 approval required。

/clear 用来开启干净的新会话，避免历史 session 影响演示。
```

重点指出：

- `mode=mock`
- `tools=5`
- `read_file`、`grep`、`list_directory` 是自动通过
- `write_file`、`delete_file` 需要审批
- `bash` 默认关闭，主线不演示

## Part 3：只读工具链

输入：

```text
读取 pom.xml
搜索 agent
/stream on
列出 src/main/java/com/javagent
```

讲解：

```text
这里展示只读工具链。用户输入自然语言后，模型决定调用哪个工具；工具结果会写回对话上下文；最后 agent 基于工具结果生成回答。

读取 pom.xml 会触发 read_file，搜索 agent 会触发 grep，列出目录会触发 list_directory。这些都是只读工具，所以不需要人工审批。
```

重点指出：

- 回答不是硬编码输出，而是工具执行后的结果摘要
- `src/main/java/com/javagent` 能展示 `JavaAgentCLI.java`、`core`、`model`、`tools`
- `/stream on` 展示控制台流式输出能力

## Part 4：审批和写文件

输入：

```text
把 "hello from demo" 写入 notes.txt
yes
```

讲解：

```text
现在演示安全机制。写文件属于危险操作，Agent 不会直接执行，而是先暂停并询问用户是否批准。输入 yes 后工具才会真正写入文件。
```

看到这个提示是正常现象：

```text
Approval required:
  tool: write_file
  args: append=false, content=hello from demo, path=notes.txt
Approve? [yes/no/cancel]:
```

重点指出：

- `write_file` 需要人工审批
- 用户可以输入 `yes`、`no` 或取消
- 这证明项目不是无保护地让模型操作本地文件

## Part 5：会话保存和加载

输入：

```text
/save demo-session
/sessions
/load latest
```

讲解：

```text
这里展示多会话持久化。当前会话可以保存成 demo-session，/sessions 可以列出历史会话，/load latest 可以恢复最新会话。
```

重点指出：

- 每个 session 有 id、title、更新时间和 message count
- 会话存储在 `.javaagent-cli/sessions/*.json`
- 兼容旧的 `last_session.json`

## Part 6：架构收尾

讲解：

```text
这个项目虽然体量小，但链路是完整的：CLI 接收用户输入，Agent 调用 ModelClient，模型返回 tool calls，ToolRegistry 找到工具，ApprovalManager 做权限判断，工具结果回灌给模型，ConversationManager 保存上下文和会话。

所以它不是简单命令行壳子，而是一个小而完整的 agent runtime。
```

## Optional Part 7：real 模式加分展示

只在 mock 主线成功、并且已经配置临时 API key 时演示。

real 配置写在项目根目录的 `config.properties`，不要在答辩屏幕上打开这个文件。

输入：

```text
/mode real
/status
你是谁？
```

讲解：

```text
这里切到 real 模式后，同一个 Java Agent CLI 不再使用 mock 模型，而是通过 OpenAI-compatible API 调用真实模型。当前默认模型是 gpt-5.4-mini。

这个问题不需要工具调用，所以可以直接验证真实模型回复链路。
```

如果出现：

```text
Warning: real mode is selected but no API key is configured.
```

不要现场纠缠，直接说：

```text
real 模式依赖外部 API key 和网络，所以只是加分项；主线能力已经通过 mock 稳定证明。
```

然后切回：

```text
/mode mock
```

## 精简备份流程

如果时间不够，只跑这一版：

```text
/help
/status
/tools
/clear
读取 pom.xml
把 "hello from demo" 写入 notes.txt
yes
/save demo-session
/sessions
/exit
```

## 常见问答

### 为什么主线用 mock 模式？

答：

```text
mock 模式保证课堂演示稳定，不依赖网络、API key、余额或模型限流。它证明的是架构链路，而不是外部模型服务是否可用。
```

### real 模式有什么意义？

答：

```text
real 模式证明 ModelClient 是可替换的，同一套 agent loop 可以从 mock 模型切换到 OpenAI-compatible 模型服务。它是加分项，不是主线依赖。
```

### 为什么 bash 默认关闭？

答：

```text
bash 能力风险更高，所以默认关闭。项目主线已经能通过读文件、搜索、列目录、写文件、删文件展示工具调用和审批机制，不需要依赖 bash。
```

### 这个项目的工程亮点是什么？

答：

```text
完整 agent loop、工具抽象、审批机制、权限策略、审批缓存、多会话持久化、mock/real 模型切换、可执行 fat jar、单元测试。
```

### Maven shade warning 是不是错误？

答：

```text
不是。那些是 maven-shade-plugin 合并依赖时的资源重复 warning，比如 LICENSE、NOTICE、MANIFEST.MF。只要最后是 BUILD SUCCESS，jar 就已经打包成功。
```

补充：

```text
我们验证的是真实验收标准：mvn clean package 成功、单元测试通过、生成的 jar 可以在 mock 模式运行。
```
