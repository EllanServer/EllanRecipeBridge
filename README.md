# EllanRecipeBridge

适用于 Paper/Folia 1.21 的艾尔岚食谱桥接插件。它将 CraftEngine 成品与 The Brewing Project 的封口酒样提交动作接入 zMenu 玩家数据，使菜单可以根据研究进度解锁配方信息。

## 功能

- 提交背包中的一个 CraftEngine 成品，记录对应食谱的解锁状态并消耗该物品。
- 提交 The Brewing Project 的封口酒样，根据评分推进 0–5 星、每 0.5 星一级的酿造研究进度。
- 归档一次零星失败笔记；其不会推进星级，但会避免重复消耗同类失败酒样。
- 在玩家加入后延迟刷新 zMenu 共享玩家数据，支持跨服传送后的菜单读取。
- 注册 PlaceholderAPI 占位符，用于在 zMenu 菜单中展示酒谱研究状态。

## 前置依赖

服务器需运行 Paper 或兼容的 Folia 服务端，并安装以下插件：

- CraftEngine
- zMenu
- PlaceholderAPI
- The Brewing Project（仅酒样提交功能需要）

前三项为 `plugin.yml` 中的硬依赖；缺失时插件不会启用。

## 命令与权限

| 命令 | 说明 | 权限 |
| --- | --- | --- |
| `/ellanrecipesubmit <namespace:id>` | 提交一个 CraftEngine 成品，例如 `ellan:apple_pie`。 | `ellanrecipe.submit` |
| `/ellanbrewsubmit <recipe-id>` | 提交一个已封口的酒样并推进对应酒谱研究。 | `ellanrecipe.submit` |

`ellanrecipe.submit` 默认授予所有玩家。命令通常应由 zMenu 的按钮调用；插件会校验参数格式、物品归属和可推进的研究等级。

## PlaceholderAPI 占位符

将 `<recipe-id>` 替换为酒谱 ID：

| 占位符 | 返回内容 |
| --- | --- |
| `%ellanrecipe_brew_progress_<recipe-id>%` | 十格进度条与当前星级。 |
| `%ellanrecipe_brew_stage_<recipe-id>%` | 当前研究阶段描述。 |
| `%ellanrecipe_brew_target_<recipe-id>%` | 下一次可推进研究的目标。 |

解锁数据保存在 zMenu 玩家数据中。成品使用 `ellan_recipe_<id>`，酒谱使用 `ellan_brew_<id>` 及其进度后缀；菜单配置应引用 zMenu 提供的玩家数据占位符。

## 构建

需要 JDK 25（当前 zMenu 1.1.1.6 的编译目标）：

```bash
./gradlew build
```

CraftEngine 或 zMenu 的官方 Maven 仓库在部分网络环境中可能出现 TLS 握手失败。遇到该情况时，请提供本机或服务器上的对应 JAR；这些第三方二进制文件不会随本项目发布：

```bash
./gradlew \
  -PcraftEngineJar=/path/to/craft-engine-paper-plugin.jar \
  -PzMenuJar=/path/to/zMenu.jar \
  build
```

构建产物位于 `build/libs/EllanRecipeBridge-1.3.2.jar`。该仓库不会提交构建产物、第三方 JAR 或服务器数据。

## 许可证

本项目以 [MIT License](LICENSE) 发布。
