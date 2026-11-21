# ResetTools

面向 **Minecraft Paper** 服务端的定时重置插件：在设定时间把玩家从指定世界传送走，然后按顺序异步执行命令（例如用 Multiverse-Core 的 `/mvunload` 卸载世界，交给其他插件完成重置），全部执行完再延迟执行收尾命令。
> **注意**：本插件由 AI 辅助开发

作者：**NSrank** ｜ 许可证：[MIT](LICENSE)

---

## 功能

- **按周定时**：可配置星期几（1-7）与执行时刻（HH:mm），也可一次配置多个星期。
- **安全疏散**：执行前把目标世界内的玩家传送到指定世界与坐标（或目标世界出生点），自动让玩家离开载具。
- **命令驱动重置**：只负责执行命令，不直接操作世界文件。用 `/mvunload`、`/mvdelete`、`/mvcreate` 等组合即可完成卸载与重建，后续的重置逻辑由 Multiverse 或其他插件负责。
- **延迟收尾**：`delay-command` 在 `command` 全部执行完毕后再等待指定时长执行，适合「重建完成后再广播/开传送门」。
- **异步执行**：整条命令链在异步线程调度，不占用服务器主线程；定时只计算一次下次触发时刻，不做每秒轮询。
- **提前提醒**：可配置 5 分钟 / 1 分钟 / 10 秒等广播倒计时，只发给目标世界内的玩家。
- **多任务**：`task.yml` 中每个顶级键就是一个独立任务。
- **热重载与手动触发**：`/resettools reload`、`/resettools run <id>`、`/resettools next`。

## 环境要求

| 项目 | 要求 |
| --- | --- |
| 服务端 | Paper 1.20.x（`api-version: 1.20`，只使用 Bukkit API） |
| Java | 17 及以上 |
| 编译 | Maven 3.6+（`pom.xml` 已配置好 Paper 仓库） |
| 可选 | Multiverse-Core 等提供世界卸载/重建命令的插件 |

## 安装

1. 构建（或直接使用 `target/ResetTools-1.0.jar`）：

   ```bash
   cd ResetTools
   mvn clean package
   ```

2. 把 `target/ResetTools-1.0.jar` 放进服务端 `plugins/` 目录。
3. 启动服务器，插件会生成 `plugins/ResetTools/task.yml`。
4. 编辑 `task.yml`，保存后执行 `/resettools reload`。

## 配置说明（task.yml）

```yaml
settings:
  command-interval: 3      # 命令间隔（秒）
  notify-player: true      # 传送时是否提示玩家

task_example:
  enabled: true            # 是否启用
  weekday: 1               # 1-7 = 周一至周日；列表 weekdays: [1, 3, 5]
  time: "04:00"            # 执行时刻，24 小时制，服务器时区
  worldid: world_nether    # 要清空玩家并重置的世界（Bukkit 世界名）
  teleport:
    world: world
    x: 0.5
    y: 65
    z: 0.5
    yaw: 0
    pitch: 0
    message: "&e世界 &f{world} &e即将重置"
  command:
    '1': '/mvunload world_nether'
    '2': '/mvdelete world_nether'
    '3': '/mvcreate world_nether nether'
  delay-command:
    time: 5m               # 支持 s/m/h/d，默认 0
    command:
      '1': '/say 世界 &f{world} &e已重置完成'
  warnings:
    '300': '&c世界 &f{world} &c将在 5 分钟后重置'
    '60':  '&c世界 &f{world} &c将在 1 分钟后重置'
    '10':  '&c世界 &f{world} &c将在 10 秒后重置，请立即离开'
```

### 字段参考

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `enabled` | boolean | 否 | 默认 `true`。为 `false` 时不排程，但仍可用 `run` 手动触发。 |
| `weekday` / `weekdays` | int / list | **是** | `1`-`7` 表示周一至周日，也接受 `mon`…`sun`、`周一`…`周日` 或列表。 |
| `time` | string | **是** | `"HH:mm"`，可带秒。**建议加引号**：YAML 会把不加引号的 `4:00` 解析成六十进制整数，插件已做兼容，但加引号更稳妥。 |
| `worldid` | string | **是** | 目标世界的 Bukkit 名称，如 `world_nether`。 |
| `teleport` | section / boolean / string | 否 | 省略 = 传送到默认世界出生点；`teleport: world` 直接指定世界名；`teleport: false` 关闭传送。 |
| `teleport.world` | string | 否 | 传送目标世界，不存在时回退到默认世界。 |
| `teleport.x/y/z` | number | 否 | 传送坐标；不写则用目标世界出生点。 |
| `teleport.yaw/pitch` | number | 否 | 传送后的朝向。 |
| `teleport.message` | string | 否 | 传送提示，支持 `&` 颜色码。 |
| `command` | section / list | 否 | 按序号（数值升序）依次执行的命令，可省略开头的 `/`。 |
| `delay-command.time` | string/number | 否 | `command` 全部完成后再等待的时长，支持 `30s`、`5m`、`2h`、`1d`，只写数字按秒处理，默认 0。 |
| `delay-command.command` | section / list | 否 | 延迟到点后按序号执行的命令。 |
| `command-interval` | int | 否 | 覆盖全局 `settings.command-interval`。 |
| `warnings` | section | 否 | 键为提前秒数，值为提示内容；只发给目标世界内的玩家。 |

### 占位符

`teleport.message`、`warnings`、`command`、`delay-command.command` 中可用：

| 占位符 | 含义 |
| --- | --- |
| `{world}` | 目标世界名 |
| `{task}` | 任务 id |
| `{time}` | 任务设定时间 |
| `{seconds}` | 提醒对应的提前秒数（命令中为 0） |
| `{players}` | 当前目标世界玩家数 |

### 执行流程

```
插件加载/重载：计算每个任务的下次触发时刻并提交定时器（不轮询、不每秒检查）
到达设定时间
  └─ 目标世界不存在 → 记录警告并结束
  └─ 疏散：把该世界玩家传送到目标位置（下车 + 传送 + 提示）  [主线程，Bukkit API]
  └─ 异步线程执行 command[1..n]，每条间隔 command-interval 秒
  └─ command 全部执行完 → 等待 delay-command.time → 异步执行 delay-command.command[1..n]
```

## 命令与权限

| 命令 | 说明 |
| --- | --- |
| `/resettools reload` | 重载 `task.yml` 并重新排程 |
| `/resettools list` | 列出任务、世界、时间、状态 |
| `/resettools next` | 显示每个任务的下次执行时间 |
| `/resettools run <id>` | 立即执行一次任务 |
| `/resettools help` | 帮助 |

别名 `/rt`。需要权限 `resettools.admin`（默认仅 OP）。

## 注意事项与限制

- **重置由命令完成**：插件只负责传送与执行命令，`/mvunload` 等命令需要自行安装对应插件（如 Multiverse-Core）。
- **命令在异步线程执行**：为了避免卡服，`command` 与 `delay-command` 全链路都在异步线程调度。Bukkit 未承诺命令线程安全，个别插件命令可能要求主线程，若日志出现线程相关异常，请把该命令改为兼容异步的替代方案或反馈。
- **传送必须在主线程**：玩家传送属于 Bukkit API 操作，仍在主线程执行，但只是瞬时操作，不会造成卡顿。
- **命令以控制台身份执行**：需要玩家身份的逻辑请自行在命令内处理。
- **错过不补跑**：服务器在该时刻未运行则本次跳过；下次触发按配置的星期与时刻重新计算。
- **重载会重置排程**：`/resettools reload` 会取消所有待执行命令与定时器，然后重新计算。

## 项目结构

```
ResetTools/
├── pom.xml
├── README.md
├── LICENSE
└── src/main/
    ├── java/ns/toolboxes/resettools/
    │   ├── ResetTools.java      # 插件入口
    │   ├── Task.java            # task.yml 单任务解析（含延迟时长解析）
    │   ├── TaskManager.java     # 排程、疏散、异步命令链
    │   ├── ResetCommand.java    # /resettools 子命令
    │   └── Text.java            # 颜色码与占位符
    └── resources/
        ├── plugin.yml
        └── task.yml
```

## 许可证

本项目基于 [MIT License](LICENSE) 开源。

Copyright (c) 2026 NSrank
