# 观测中心 Android 客户端（原生轻量版）

对接 acg-faka「观测中心」插件，在安卓手机上看实时动态、处理客服会话、审核提现与订单。不修改网站任何源代码，所有数据均通过现有后台接口获取。

> 通用客户端：登录时自行填写站点地址、账号、密码，代码中不预置任何站点或凭据。

## 功能

### 实时概览
- 账号密码 + 图形验证码登录，会话 Cookie 自动保存，过期自动回登录页
- 顶栏实时统计：在线人数、今日访客、今日订单、成交额（SSE 推送）
- SSE 长连接断线自动重连，新消息本地通知（前台常驻监听服务）

### 动态
- 进店 / 查看商品 / 下单 / 已付款 / 发货 / 登录 / 封禁等事件流，事件类型中文显示
- 历史事件与实时推送合并展示

### 客服会话
- 会话列表（未读角标），点进收发文字消息
- 发送图片（相册选图）、发送商品卡片 / 本会话订单卡片
- 撤回自己发出的消息、访客消息在线翻译、会话内部备注
- 快捷回复话术一键填入、结束对话、封 IP / 封会员

### 订单与管理
- 订单列表（分页）、重新发货
- 提现审核（通过 / 驳回填理由）、支付通道暂停 / 恢复
- 访客列表（在线 / 今日 / 全部）、访客详情（IP、地区、设备、访问次数、时间线、历史订单）
- 会员详情（账户信息、关联访客、订单、同设备关联）、工单列表

## 编译运行

1. 安装 Android Studio（Hedgehog 或更新），自带 JDK 17 即可。
2. `Open` → 选择本 `ObsApp` 文件夹，等待 Gradle 同步（首次下载依赖，需联网）。
3. 连上安卓手机（开启 USB 调试）或启动模拟器，点 Run；也可 Build APKs 后安装。
4. 登录页填写：
   - 站点地址：`https://你的域名`（自己后台的访问地址）
   - 账号 / 密码：后台管理员账号
   - 验证码：点图片刷新后输入

## 主要接口备忘

| 用途 | 方法与路径 | 参数 |
|---|---|---|
| 登录 | POST `/admin/api/authentication/login` | username, password, captcha, remember |
| 验证码图 | GET `/user/captcha/image?action=adminLogin` | — |
| 初始快照 | POST `/plugin/ObservationCenter/admin/snapshot` | — |
| 实时流 | GET `/plugin/ObservationCenter/stream/admin` | SSE：ready/ev/stats/todo/thread/end |
| 会话列表 | POST `/plugin/ObservationCenter/admin/threads` | — |
| 消息记录 | POST `/plugin/ObservationCenter/admin/messages` | thread_id |
| 发消息 / 图 / 卡片 | POST `/plugin/ObservationCenter/adminChat/send\|sendImage\|sendCard` | thread_id, cid, ... |
| 撤回 / 翻译 / 备注 / 话术 | POST `/plugin/ObservationCenter/adminChat/recall\|translate\|note\|quick` | — |
| 订单 / 重发 | POST `/plugin/ObservationCenter/admin/orders\|redeliver` | — |
| 提现 / 通道 | POST `/plugin/ObservationCenter/admin/cashes\|cashDecide\|payments\|payPause\|payResume` | — |
| 访客 / 会员 / 工单 | POST `/plugin/ObservationCenter/admin/visitor\|member\|visitorsPage\|tickets` | — |

所有业务接口需带会话 Cookie，并在请求头带 `Referer: https://你的域名/`（服务端校验同源 host）。

## 未包含

- 离线推送（FCM / 厂商通道，需服务端配合）
- 快捷回复 / 自动回复规则的增删管理、Telegram 转接、数据导出 CSV、封禁名单管理、库存预警、全局搜索等（均为后台设置项，按需后续补齐）

## 说明

- 仅对接自己已合法持有的店铺后台，遵守平台规则与当地法律法规。
