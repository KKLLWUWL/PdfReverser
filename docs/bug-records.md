# PdfReverser Bug Records

## Bug Summary [BUG-001]（已修复）
- **Symptom**: v1.1 在真机（Redmi Turbo 3 / Android 14）上启动即闪退，系统提示空指针。
- **Root Cause**: `MainActivity.buildTargetRow()` 中对新建的 `LinearLayout row` 直接执行
  `((ViewGroup.MarginLayoutParams) row.getLayoutParams()).bottomMargin = ...`。
  Android 框架语义：View 未加入父容器且未 `setLayoutParams()` 时，`getLayoutParams()` 恒为 `null`，
  对其解引用赋值必然 NPE。该方法在每次 `onResume → rebuild()` 无条件执行，故启动必崩（可复现率 100%）。
- **Why v1.0 正常**: `buildTargetRow()` 为 v1.1 重构新增（v1.0 用独立 SettingsActivity），属 v1.1 引入的回归。
- **Fix**: 显式 `new ViewGroup.MarginLayoutParams(...)` 后 `row.setLayoutParams(mp)`（不依赖 getLayoutParams 返回值）。
- **Files Modified**: `app/src/main/java/com/lingxi/pdfreverser/MainActivity.java`
- **Severity**: P1（应用无法启动）
- **Verification**: 静态证据链完整（框架 getLayoutParams 语义 + 无条件执行路径）；编译通过；同类扫描无残留 NPE。

### 自我反思
| 维度 | 评分 | 说明 |
|------|------|------|
| 首次正确率 | 2 | 引入 buildTargetRow 时未检查 getLayoutParams 可空性 |
| 范围准确性 | 5 | 同类扫描覆盖全部 getLayoutParams 调用点 |
| 最小改动 | 5 | 3 行修复 |
| 副作用预测 | 4 | 附加引擎就绪加固（预防性） |
| 根因深度 | 5 | 明确 Android 框架 View.getLayoutParams 语义 |
| **合计** | **21/25** | |

**教训（Pattern）**：Android 中 **`getLayoutParams()` 在布局参数未初始化时返回 null**，任何对其成员的读写都必须先 `setLayoutParams()`。程序化构建 View 时统一先构造 LayoutParams 再 set，不依赖 get 返回值；新增 UI 代码后至少做一次"启动路径静态审查"。

## 附带加固
- PdfEngine：增加 WebView 引擎就绪等待（`window.__pdfEngineReady` + 轮询重试，最多 3s），避免首次点击 PDF 时引擎未加载导致"点了没反应/进度卡住"。
