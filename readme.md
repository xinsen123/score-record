# Score Record 备忘

## 当前功能

- 模组命令：`/score-record`
- 当前支持的显示项：
  - `placed` 方块放置量
  - `mined` 方块挖掘量
  - `kills` 生物击杀数
  - `deaths` 死亡次数
  - `playtime` 在线时间（分钟）
  - `distance` 里程数（米）
- 辅助命令：
  - `list`
  - `refresh`
  - `hide`

## 当前实现方式

- 统计来源优先复用原版 `StatHandler`
- 每秒同步一次玩家统计到自定义 `dummy` 计分板 objective
- 侧边栏显示通过切换 `ScoreboardDisplaySlot.SIDEBAR`
- 当前侧边栏是全服共用，不是每个玩家独立显示

## 统计口径注意点

### placed

- 当前实现是遍历所有可放置方块物品，累加 `Stats.USED`
- 这更接近“玩家手动使用方块物品放置”的次数
- 不能严格等同于“世界里成功放下了多少个方块”
- 可能不包含：
  - `/setblock`、`/fill` 等命令放置
  - 活塞、发射器等机械行为
  - 某些特殊方块或非标准放置来源

### mined

- 当前实现是遍历所有方块，累加 `Stats.MINED`
- 这是原版挖掘统计，口径稳定

### kills

- 当前实现使用 `Stats.MOB_KILLS`
- 只统计生物击杀，不含玩家击杀
- 如果以后要加玩家击杀，建议单独做 `player_kills`

### deaths

- 当前实现使用 `Stats.DEATHS`
- 是累计死亡次数

### playtime

- 当前实现使用 `Stats.PLAY_TIME`
- 已换算为分钟显示
- 属于累计在线时间，不是本次登录会话时间

### distance

- 当前实现是多种移动统计之和，再由厘米换算为米
- 已包含步行、疾跑、潜行、游泳、鞘翅、骑乘等常见移动方式
- 不保证和地图直线位移一致，它本质上是累计移动里程

## 当前命令行为

- `/score-record <category>` 会切换全服侧边栏显示项
- `/score-record hide` 会隐藏当前侧边栏
- `/score-record refresh` 会立即重算一次在线玩家统计
- 当前没有做权限限制，默认任何可执行命令的玩家都能切换显示
- 如果服务器需要管理权限，后面应补成仅 OP 可用

## 已知限制

- 当前只同步在线玩家
- 离线玩家不会主动重算，但原版统计本身会保留
- 当前没有做“排行榜轮播”
- 当前没有做“总计/本次在线”双口径
- 当前没有做数据持久化配置
- 当前没有做按玩家单独显示面板

## 推荐后续迭代

### 优先级高

- 给 `/score-record` 增加权限控制
- 增加 `player_kills`
- 增加 `reset <category> [player]`
- 增加 `top <category>` 或排行榜输出

### 可选增强

- 做自动轮播显示
- 做“累计值 / 本次在线”双统计
- 支持特定方块统计
- 支持特定生物击杀统计
- 支持配置文件定义默认显示项和刷新周期

## 开发流程

- WSL 中开发与构建：`./gradlew build`
- 同步到 Windows 客户端：`./scripts/sync-windows-mod.sh`
- Windows HMCL 原生客户端测试

## 关键源码位置

- `src/main/java/com/joys/scorerecord/ScoreRecordMod.java`
- `src/main/java/com/joys/scorerecord/ScoreRecordManager.java`
- `src/main/java/com/joys/scorerecord/ScoreRecordCategory.java`
