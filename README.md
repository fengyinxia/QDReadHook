# 起点轻读 · QDReadHook

起点阅读 Xposed / LSPosed 模块，提供签到与奖励流程辅助、界面定制和按需拦截，使用独立的 MD3 设置页。

应用显示名与设置页标题为 **起点轻读**，包名 `cn.xihan.qdds`，仓库名 `QDReadHook`；现有配置与作用域不需迁移。

![Android](https://img.shields.io/badge/Android-7.0%2B-brightgreen)
[![Releases](https://img.shields.io/github/v/release/fengyinxia/QDReadHook)](../../releases)

> 当前适配基于起点 **7.9.428 / 1706**；宿主版本变更需重新核对混淆类、方法签名与事件链。

## 功能

| 分类 | 功能 |
| --- | --- |
| 自动操作 | 自动签到、免广告领取奖励辅助 |
| 页面定制 | 我 Tab 菜单、底部导航、主页选项、搜索分组 |
| 阅读体验 | 章评原图入口、图片地址弹框、音频导出 / 复制地址、评论复制、章末模块隐藏、会员卡背景展示调整 |
| 内容净化 | 小红点、11 项广告拦截 |
| 高级设置 | 15 项拦截选项、宿主包名 |

实现边界：

- 自动签到仅识别“签到”按钮后延迟点击并短时去重，不是每日任务调度器。
- 奖励免广告通过 DexKit 定位特定 Web 奖励入口，带失败回退。
- 我 Tab / 导航仅交换菜单标题来隐藏条目，不交换账户信息或未读计数，宿主不能改写设置与隐藏名单。
- 多选项目按需启用。**GDT 广告拦截会停用广点通初始化，可能影响原生视频奖励回退，默认不选中。**
- 章评音频输出到 `Music/QDReader`（Android 10+ 用 MediaStore）。
- 搜索隐藏只改显示列表，不清除历史数据库；章末未知模块保留。
- 会员卡背景只调整本地背景列表字段，不改会员身份、余额、购买记录或服务端响应。
- 每日自动完成福利中心广告任务未实现。

## 启动稳定性

- 界面 Hook 仅在宿主主进程安装。
- 启动时直接安装明确目标与缓存目标，未缓存任务在首帧后由单一后台线程补查，主线程不等待 DexKit。
- 缓存只存方法描述符，校验宿主版本 / 安装时间、APK 身份和定位规则版本；失效时回退后台定位。
- 首次无缓存时部分启动期拦截可能赶不上本次调用；定位失败不等于零匹配。

## 使用

1. 安装模块，在 LSPosed 中启用，并将起点加入作用域。
2. 打开起点轻读设置页按需开启功能；新功能默认关闭。
3. 我 Tab 与底部导航需先由起点上报菜单目录，再在子页选择隐藏项（导航目录采集需开启自定义底部导航并重启起点）。
4. 多选页点“保存”才写入；改完设置手动重启起点。

## 已知问题

- Android 13 单色主题未真机实测。
- 我 Tab / 导航目录存在历史 `IllegalArgumentException`，同步稳定性待确认。
- 签到结果、奖励到账、媒体操作、章末过滤、背景下载与使用未做完整真机验证。

## 构建

- JDK 21、Gradle 8.7、AGP 8.5.2、Kotlin 1.9.22、KSP 1.9.22-1.0.17。
- `compileSdk=33`、`minSdk=24`、`targetSdk=32`，JVM target 11。
- 依赖：Material Components 1.9.0、AndroidX Preference 1.2.0、YukiHookAPI 1.0.92、Xposed API 82、DexKit 2.0.3。
- SDK 路径写在 `local.properties`。

```powershell
.\gradlew.bat :app:assembleDebug --no-daemon --console=plain --max-workers=4
```

产物：`app/build/outputs/apk/debug/app-debug.apk`，未配置私有签名时使用标准 Debug 签名。打包后自动安装约定见 [AGENTS.md](AGENTS.md)。

## 源码

- `HookEntry.kt`：初始化与设置共享。
- `hooks/`：按功能划分的 Hook、DexKit 定位与安全日志。
- `MainActivity.kt`、`OptionEditor.kt`、`SettingsViews.kt`：MD3 设置界面与编辑流程。
- `MeTabCatalogProvider.kt`：受限菜单目录上报接口。
