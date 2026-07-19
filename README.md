# WindChat

一个轻量级的 Minecraft Paper 聊天管理插件，提供消息格式化、敏感词过滤和 PlaceholderAPI 支持。

## 版本说明

当前版本 `1.0.0-beta.2` 处在公测状态，可能不稳定，不建议用于生产环境。  
欢迎大家积极测试，提供反馈和建议。

## 分支说明

**注意：`master` 分支中的代码处于开发状态，可能不稳定、未经充分测试，甚至无法正常运行。**

如需获取**稳定版本**的源码，请前往 **[Releases](https://github.com/wgbsd616/WindChat/releases)** 页面下载对应版本的源码包或 JAR 文件。

## 功能特性

- **消息格式化** — 自定义聊天消息显示格式
- **敏感词过滤** — 基于 DFA 算法的高效敏感词检测
- **多级处理** — 支持不同等级敏感词配置不同处理动作
- **PlaceholderAPI 支持** — 在消息格式中使用占位符
- **权限控制** — bypass 权限、reload 权限
- **热重载** — `/wchat reload` 重载配置
- **配置校验** — 启动时自动检查配置文件正确性

## 安装

1. 从 **[Releases](https://github.com/wgbsd616/WindChat/releases)** 页面下载 `WindChat-1.0.0-beta.2.jar`
2. 放入服务器的 `plugins/` 目录
3. 重启服务器
4. 编辑 `plugins/WindChat/config.yml` 进行配置
5. 使用 `/wchat reload` 重载配置

## 命令

| 命令 | 权限 | 说明 |
|------|------|------|
| `/wchat reload` | `windchat.reload` | 重载配置文件 |

## 权限

| 权限 | 默认值 | 说明 |
|------|--------|------|
| `windchat.reload` | OP | 允许使用 `/wchat reload` |
| `windchat.bypass` | false | 绕过敏感词检测 |

## 配置说明

### 消息格式

```yaml
chat_format:
  enable: true
  format: "§a[§f玩家消息§a]§r {player}：{message}"
```

| 占位符 | 说明 |
|--------|------|
| `{player}` | 玩家名称 |
| `{message}` | 消息内容 |

### 敏感词

```yaml
sensitive:
  enable: true
  words:
    "敏感词1": 1
    "敏感词2": 2
```

### 动作类型

| 动作 | 说明 |
|------|------|
| `change` | 将敏感词替换为 `*` |
| `block` | 屏蔽整条消息 |
| `message` | 向触发者发送提示消息 |
| `broadcast` | 向全服广播消息 |
| `command` | 执行控制台命令 |

### 高级设置

```yaml
chat_listener_priority: 2
```

| 数值 | 对应优先级 |
|------|-----------|
| 1 | LOWEST |
| 2 | LOW |
| 3 | NORMAL |
| 4 | HIGH |
| 5 | HIGHEST |

数值越小，优先级越高（先执行）。修改后需**重启服务器**生效，`/wchat reload` 无法重载此项。

## 构建

```bash
mvn clean package
```

生成的 JAR 文件位于 `target/WindChat-1.0.0-beta.2.jar`

## 依赖

- **Paper API 1.21.1+** — 服务端核心
- **PlaceholderAPI（可选）** — 占位符支持

## 颜色代码

配置文件使用 `§` 作为颜色代码前缀。

## 许可证

MIT License

## 作者

WithWindStudio