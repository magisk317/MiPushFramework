# MiPushFramework × XposedSmsCode UI/M3E 完全统一执行规格（评审版）

日期：2026-02-23  
仓库：`/root/play/MiPushFramework`  
对标基线：`/root/play/XposedSmsCode` `dev` 分支（截至 `906f01c`）

---

## 1. 目标与定义

### 1.1 总目标（“完全统一”）
将 `MiPushFramework` 的 Compose UI 体系与 `XposedSmsCode` 当前 M3E 方案统一到同一套交互与组件策略，包含：

1. 加载体验策略统一
2. 对话框动作区统一（M3E ButtonGroup + Overflow）
3. 下拉刷新行为统一（PullToRefresh + 最小可见时长）
4. 主题策略统一（动态色 + 纯黑模式 + 系统栏策略）
5. 视觉 token 与共用组件统一（间距/尺寸/指示器）
6. 页面行为统一（首屏加载、手动刷新、反馈提示）

### 1.2 本次“完全统一”边界
- **包含**：`push` 模块全部 Compose 主路径（Main/Settings/Events/Apps/ApplicationInfo/Help）
- **不包含**：推送协议、hook、服务层逻辑（非 UI）
- **允许**：在视觉结构统一前提下保留 MiPushFramework 业务文案差异

### 1.3 统一验收口径
统一不是“抄代码”，而是满足以下可验证标准：
- 同类场景采用同类组件（例如所有双动作弹窗都走同一动作行方案）
- 同类加载状态遵循同一显示规则（最短可见时长 + 首屏 session 去抖）
- 同类刷新交互使用同一行为（PullToRefresh 指示器位置/时机一致）
- 主题切换语义一致（动态色、纯黑、系统栏）

---

## 2. 基线对比（现状）

## 2.1 XposedSmsCode 近期 UI/M3E 基线
关键提交：
- `c6b24a4`：loading token 与封装统一
- `906f01c`：settings/faq loading 行为与 token 对齐
- `6480874` / `6e597ac` / `fb2144b`：ButtonGroup + Overflow API 统一动作区
- `05379e8`：M3 pull-to-refresh 统一
- `17b2f00`：动态色 + 纯黑模式

关键文件：
- `app/src/main/java/com/tianma/xsmscode/ui/common/LoadingIndicatorTokens.kt`
- `app/src/main/java/com/tianma/xsmscode/ui/common/LoadingVisibilityController.kt`
- `app/src/main/java/com/tianma/xsmscode/ui/common/SessionLoadingRegistry.kt`
- `app/src/main/java/com/tianma/xsmscode/ui/common/PolygonMorphLoadingIndicator.kt`
- `app/src/main/java/com/tianma/xsmscode/ui/home/ComposeSettingsScreen.kt`
- `app/src/main/java/com/tianma/xsmscode/ui/record/CodeRecordScreen.kt`
- `app/src/main/java/com/tianma/xsmscode/ui/home/MainActivity.kt`

## 2.2 MiPushFramework 当前状态
已具备：
- `PullToRefresh` 基础封装：`push/src/main/java/top/trumeet/mipushframework/component/RefreshableLazyColumn.kt`
- 动态色主题：`push/src/main/java/top/trumeet/ui/theme/Theme.kt`
- 多处 Compose 对话框

缺口：
- 无统一 loading token / min-duration / session registry
- 对话框动作区分散（大量 `TextButton` 手写）
- 无纯黑主题语义
- 无 M3E ButtonGroup 统一策略
- 不同页面刷新与加载反馈体验不一致

---

## 3. 统一后目标架构

## 3.1 UI Common 层新增能力
在 `push/src/main/java/top/trumeet/mipushframework/component/` 新增或归并：

1. `LoadingIndicatorTokens.kt`
- `MIN_VISIBLE_DURATION_MILLIS`
- `OverlayTopSpacing`
- `ContainedSize`

2. `LoadingVisibilityController.kt`
- `rememberMinDurationLoading(actualLoading, minDurationMillis)`

3. `SessionLoadingRegistry.kt`
- `shouldShowInitial(key)`
- `markShown(key)`

4. `AppLoadingIndicators.kt`
- `AppLinearLoadingIndicator()`

5. `PolygonMorphLoadingIndicator.kt`
- M3E `ContainedLoadingIndicator` 封装

6. `DialogActionRow.kt`（新增）
- 统一 `AlertDialog` 动作区 API（内部使用 ButtonGroup + Overflow）

## 3.2 Theme 层目标
在 `push/src/main/java/top/trumeet/ui/theme/Theme.kt` 引入：
- `MaterialExpressiveTheme`（替换当前 `MaterialTheme` 容器）
- 纯黑模式（Dark base + surface/background 全黑）
- 动态色保持开启（S+）
- 系统栏色策略与主题同步

## 3.3 页面层目标
所有主页面遵循同一模式：
- 初次加载：`SessionLoadingRegistry + rememberMinDurationLoading`
- 手动刷新：最短显示时长控制
- 顶部刷新指示器偏移：`OverlayTopSpacing`
- 大多数双/三动作弹窗：`DialogActionRow` 统一

---

## 4. 文件级改造清单（执行粒度）

## 4.1 新增文件
- `push/src/main/java/top/trumeet/mipushframework/component/LoadingIndicatorTokens.kt`
- `push/src/main/java/top/trumeet/mipushframework/component/LoadingVisibilityController.kt`
- `push/src/main/java/top/trumeet/mipushframework/component/SessionLoadingRegistry.kt`
- `push/src/main/java/top/trumeet/mipushframework/component/AppLoadingIndicators.kt`
- `push/src/main/java/top/trumeet/mipushframework/component/PolygonMorphLoadingIndicator.kt`
- `push/src/main/java/top/trumeet/mipushframework/component/DialogActionRow.kt`

## 4.2 修改文件（第一批）
- `push/src/main/java/top/trumeet/ui/theme/Theme.kt`
- `push/src/main/java/top/trumeet/mipushframework/component/RefreshableLazyColumn.kt`
- `push/src/main/java/top/trumeet/mipushframework/main/MainActivity.kt`
- `push/src/main/java/top/trumeet/mipushframework/main/subpage/SettingsPage.kt`
- `push/src/main/java/top/trumeet/mipushframework/main/subpage/EventListPage.kt`
- `push/src/main/java/top/trumeet/mipushframework/main/ApplicationInfoPage.kt`
- `push/src/main/java/top/trumeet/mipushframework/component/SettingsComponent.kt`

## 4.3 修改文件（第二批，收尾统一）
- `push/src/main/java/top/trumeet/mipushframework/main/HelpPage.kt`
- `push/src/main/java/top/trumeet/mipushframework/main/subpage/ApplicationListPage.kt`
- 其他存在弹窗动作区分散实现的 Compose 文件

---

## 5. 分阶段执行计划

## 阶段 A：基础能力落地（不改页面行为）
目标：先建共用能力，确保可编译、可复用。

执行项：
1. 新增 6 个 common 文件（见 4.1）
2. 在 `Theme.kt` 引入 M3E 容器与 pure black 模式（先兼容，不切换入口）
3. 在 `RefreshableLazyColumn.kt` 接入 token（不改外部参数）

验收：
- `./gradlew :push:assembleNormalDebug -PbuildSplits=true`
- 编译无新增 warning（或仅可接受 experimental opt-in）

回滚点：
- 单 commit 可逆

## 阶段 B：加载行为统一（页面级）
目标：统一加载体验，消除闪烁/时间不一致。

执行项：
1. `SettingsPage`、`EventListPage`、`ApplicationListPage` 接入 `rememberMinDurationLoading`
2. 设定每页 session key，首屏加载仅展示一次（本进程会话内）
3. 手动刷新增加最短可见时长保护

验收：
- 首屏加载不闪烁
- 手动刷新反馈稳定
- UI 自动化 smoke（若无自动化，至少 adb 录屏 + 人工点检）

## 阶段 C：对话框动作区统一（M3E ButtonGroup）
目标：所有关键对话框统一动作结构。

执行项：
1. `SettingsComponent.kt` 改造成统一 `DialogActionRow`
2. `SettingsPage`、`EventListPage`、`ApplicationInfoPage` 替换散落 `TextButton`
3. 对 3+ action 场景启用 overflow indicator

验收：
- 关键页面对话框动作区视觉一致
- 横屏/小屏无挤压错位
- TalkBack 聚焦顺序正确

## 阶段 D：主题策略完全统一
目标：动态色 + pure black + expressive 主题语义完全对齐。

执行项：
1. 引入主题模式枚举/设置项（若当前无 pure black 开关则新增）
2. `MaterialExpressiveTheme` 全局生效
3. 系统栏更新逻辑校准（深色/浅色图标）

验收：
- Android 12+ 动态色正确
- pure black 模式全页面黑底一致
- 状态栏/导航栏图标可读性稳定

## 阶段 E：统一回归 + 文档固化
目标：将“统一”产物固化为工程规范。

执行项：
1. 添加 `UI Conventions` 文档
2. 新增 lint/detekt 规则（可选）限制直接手写对话框按钮行
3. 更新 `README` 的 UI/Theme 段落

验收：
- 新增页面必须复用 common 组件

---

## 6. 设计细节（关键接口草案）

## 6.1 DialogActionRow API（草案）
```kotlin
@Composable
fun DialogActionRow(
    primary: ActionSpec,
    secondary: ActionSpec? = null,
    tertiary: ActionSpec? = null,
    modifier: Modifier = Modifier,
)
```

`ActionSpec` 字段建议：
- `label: String`
- `onClick: () -> Unit`
- `style: ActionStyle`（Primary / Secondary / Danger）
- `enabled: Boolean = true`

## 6.2 Loading API（草案）
- `rememberMinDurationLoading(actualLoading, minDurationMillis = 500)`
- `SessionLoadingRegistry.shouldShowInitial("settings")`
- `SessionLoadingRegistry.markShown("settings")`

## 6.3 Theme API（草案）
```kotlin
@Composable
fun Theme(
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
)
```

`ThemeMode`：`System | Light | Dark | PureBlack`

---

## 7. 风险、影响与缓解

## 7.1 风险
1. M3E API 仍为 experimental，后续 alpha 版本可能变更
2. ButtonGroup 在小屏弹窗中的布局拥挤风险
3. 纯黑模式可能影响部分 surface 分层辨识

## 7.2 缓解
1. 所有 M3E 使用集中封装在 common，避免散落调用
2. 对话框动作统一从 `DialogActionRow` 出口控制（可按屏宽降级）
3. pure black 模式对 divider/outline 保留轻微对比色

## 7.3 兼容性策略
- 若某些 ROM/版本对 expressive 组件异常，保留降级开关回退到 `MaterialTheme + Row Buttons`

---

## 8. 验收矩阵

## 8.1 功能验收
- 所有主页面可正常进入、刷新、弹窗交互
- 设置项保存与提示无回归
- 事件/应用页滚动加载行为无回归

## 8.2 视觉验收
- 加载指示器位置、时机统一
- 对话框动作区样式统一
- 主题模式切换过渡一致

## 8.3 技术验收
- `assembleNormalDebug` 成功
- 分包安装 arm64 成功
- 无新增崩溃日志（UI 主流程）

建议命令：
```bash
cd /root/play/MiPushFramework
./gradlew :push:assembleNormalDebug -PbuildSplits=true
adb install -r push/build/outputs/apk/normal/debug/push-normal-arm64-v8a-debug.apk
adb logcat -d -v threadtime | rg -n "FATAL EXCEPTION|Compose|Material3|IllegalStateException" -i
```

---

## 9. 提交策略（建议）

建议按阶段拆 commit，便于 review 与回滚：
1. `refactor(ui-common): add shared loading and dialog action abstractions`
2. `refactor(ui): unify loading visibility across settings/events/apps`
3. `refactor(m3e): migrate dialog action areas to button group`
4. `feat(theme): align expressive theme with dynamic color and pure black mode`
5. `docs(ui): add m3e unification conventions and migration notes`

---

## 10. 评审待确认项（你 review 后需要拍板）

1. 是否接受“全局切换到 MaterialExpressiveTheme”作为硬目标？
2. pure black 模式是否要新增用户设置入口？
3. 对话框动作区是否强制全部迁移（含低频页面）？
4. 是否接受在阶段 C 前出现短期“新旧样式共存”？
5. 是否需要在本轮同时做 UI 动效（如主题切换 reveal）统一？

---

## 11. 执行说明

该文档是执行规格，不是提案摘要。你确认后，我将按 **阶段 A -> E** 顺序逐步实施，并在每阶段结束给你：
- 变更文件清单
- 构建与安装结果
- 可复现验证步骤
- 风险与回滚建议

