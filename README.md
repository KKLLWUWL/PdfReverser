# 倒序打印（PdfReverser）

解决「打印机顺序出纸，打印结果需要手动重新排序」的痛点：选好 PDF，一键把页面**倒序重排**后直接发送到打印类 APP，出纸即正确顺序。

## 使用流程

1. 安装后打开首页，每个文件夹卡片点右侧「选择」添加常用文件夹（如「下载」「文档」）
2. 点「在此文件夹中选择 PDF ›」可直接跳入该文件夹挑选任意 PDF（支持进入子目录）
3. 两个文件夹下方点「选择打开方式」固定发送目标 APP（如你的打印机 App；选定后**直接发送、无需确认**，想换时再点一次即可）
4. 点选 PDF 自动倒序重排并发送

## 核心特性

| 特性 | 说明 |
|------|------|
| 结构级倒序 | 基于 pdf-lib **仅调整页面树顺序**，不重新渲染、不重新压缩任何内容流，清晰度零损失 |
| 极速 | 实测 3-8 页 PDF 处理耗时约 1-5ms（纯结构操作，与页数无关的量级） |
| 单页跳过 | 只有 1 页时不做重排，直接发送原文件 |
| 临时文件 | 重排结果写入应用缓存并发送，**不保存在手机**，发送后自动清理 |
| 固定文件夹 | 首页固定 1-2 个常用文件夹；可一键跳转到该文件夹用系统选择器挑选任意 PDF（含子目录） |
| 打开方式固定 | 首页两文件夹下方「选择打开方式」按钮，从手机上现有可接收 PDF 的应用中选一个固定；想换时再点一次 |
| 零权限 | 全程使用 SAF（Storage Access Framework），无存储/网络权限 |
| 零依赖 | 不依赖 androidx，自定义极简 FileProvider，APK 约 280KB |
| 界面 | Apple 风格（浅灰背景、白色圆角卡片、蓝色强调） |

## 技术架构

```
MainActivity（首页/文件列表/发送）
  └─ SafFiles           零依赖 SAF 列目录（DocumentsContract）
  └─ PdfEngine          隐藏 WebView + pdf-lib（结构级倒序）
  └─ FileContentProvider 自定义极简 ContentProvider（分享临时文件）
  └─ SettingsStore      SharedPreferences（文件夹/目标 APP）
SettingsActivity（文件夹与目标 APP 设置）
```

## 目录结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/lingxi/pdfreverser/    # MainActivity/SettingsActivity/PdfEngine/SafFiles/FileContentProvider/SettingsStore
├── assets/www/
│   ├── reverser.html               # PDF 倒序处理引擎（pdf-lib）
│   └── pdf-lib.min.js              # pdf-lib UMD 构建（约 516KB）
└── res/                            # 布局/资源/图标
tests/reverser_proto.js             # Node 原型验证（4 类 PDF 结构）
```

## 构建

需 JDK 17 + Android SDK（platforms;android-34, build-tools;34.0.0）+ Gradle 8.9：

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
gradle assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

## 验证记录（2026-08）

- **4 类 PDF 结构**原型测试全过：reportlab 经典结构、pypdf 经典 xref、pikepdf 现代结构（对象流 + xref 流，Chrome/macOS 同款）、Pillow 图像型（打印机驱动常见）
- 处理耗时：1-5ms；页序经内容流标记核验严格倒序（1..5 → 5..1）
- WebView 处理页浏览器端运行时验证：多页倒序、单页跳过分支均正确，输出 PDF 经 pikepdf 打开合法
