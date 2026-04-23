# 配置管理计划 (Configuration Management Plan)

> 项目：云南省企业就业失业数据采集系统
> 版本：v1.0
> 适用范围：项目从启动到答辩交付的全生命周期

## 1. 配置管理目标

- **可追溯性**：每一项变更可定位到具体提交、作者、时间、变更原因
- **可重现性**：任何历史里程碑可通过 tag 一键 checkout 回溯
- **变更受控**：功能性变更通过分支 + Pull Request 流程评审后再合并
- **基线明确**：每个里程碑结束打基线 tag，作为正式参考点
- **构建可靠**：每次推送自动触发 CI 编译 + 构建，避免主分支被破坏

## 2. 配置项标识 (Configuration Items)

| 类别 | 配置项 | 位置 |
|---|---|---|
| 源代码 | 后端 Java 源 | `backend/src/main/java/**` |
| 源代码 | 前端 Vue 源 | `frontend/src/**` |
| 配置 | 后端运行配置 | `backend/src/main/resources/application.yml` |
| 配置 | 数据库 Schema | `backend/src/main/resources/schema.sql` |
| 配置 | 字典数据 | `backend/src/main/resources/data.sql` |
| 构建 | 后端依赖 | `backend/pom.xml` |
| 构建 | 前端依赖 | `frontend/package.json` + `frontend/package-lock.json` |
| CI | 自动构建脚本 | `.github/workflows/ci.yml` |
| 文档 | 项目说明 | `README.md` |
| 文档 | 变更记录 | `CHANGELOG.md` |
| 文档 | 配置管理计划 | `docs/CM-Plan.md`（本文件）|
| 工具 | 本机环境引导 | `env.sh` |

不纳入版本控制的：
- 编译产物 (`backend/target/`、`frontend/dist/`、`frontend/.vite/`)
- 依赖目录 (`frontend/node_modules/`)
- 运行时数据 (`data/*.db`)
- IDE 个人配置 (`.idea/`、`.vscode/`、`*.iml`)
- 系统文件 (`.DS_Store`)
- 日志 (`*.log`)

清单见 [.gitignore](../.gitignore)。

## 3. 版本控制工具

- **Git** + **GitHub** 远程托管
- 远程仓库：https://github.com/qiantaiwen2050-ops/yn_employement
- 默认主分支：`main`

## 4. 分支策略

参考 GitHub Flow，简化版：

```
main  (受保护、始终可发布)
 │
 ├─── feature/xxx          (新功能)
 ├─── fix/xxx              (bug 修复)
 ├─── docs/xxx             (文档)
 ├─── refactor/xxx         (重构，不改行为)
 └─── ci/xxx               (CI/CD)
```

**规则**：
- `main` 仅通过 Pull Request 合入，不允许直接 push
- 分支命名：`<type>/<short-description>`（如 `feature/cm-docs`、`ci/github-actions`）
- 一个 PR 一个聚焦主题；合并后立刻删除 feature 分支
- 合并方式：默认 **Merge commit**（保留分支拓扑，体现 CM 过程）

## 5. 提交信息规范

参考 [Conventional Commits](https://www.conventionalcommits.org/zh-hans/v1.0.0/) 格式：

```
<type>: <subject>

<body — 可选，多行说明>

Co-Authored-By: ...
```

**type** 取值：
- `M1`–`M7`：里程碑提交（本项目专用）
- `feat`：新功能
- `fix`：bug 修复
- `docs`：文档
- `refactor`：重构
- `chore`：构建/工具/配置
- `test`：测试
- `ci`：CI/CD 配置

## 6. 基线 (Baseline) 与发布

每个里程碑完成后打**基线 tag**：

| Tag | Commit | 含义 |
|---|---|---|
| `v0.1.0-M1` | M1 完成 | 项目骨架 + 鉴权 + 字典 |
| `v0.2.0-M2` | M2 完成 | 企业端备案 + 月度填报 |
| `v0.3.0-M3` | M3 完成 | 市级审核 + 通知 |
| `v0.4.0-M4` | M4 完成 | 省级管理类 |
| `v0.5.0-M5` | M5 完成 | 省级分析类 |
| `v0.6.0-M6` | M6 完成 | 系统管理 + 国家接口 |
| `v0.7.0-M7` | M7 完成 | 操作日志 + Excel 导出 |
| `v1.0.0`    | 首次正式发布 | 全功能 + CM 基线 + CI |

回溯命令：`git checkout v0.4.0-M4` 即可看到 M4 完成时的快照。

## 7. 变更控制流程 (Change Control)

```
 提需求 (Issue)  ─►  开分支  ─►  提交 commits  ─►  开 Pull Request
                                                       │
                                                       ▼
                                                CI 自动构建（必须绿）
                                                       │
                                                       ▼
                                                  Code Review
                                                       │
                                                       ▼
                                                  Merge → main
                                                       │
                                                       ▼
                                                关闭 Issue + 删除分支
```

**变更类型与处理**：

| 变更级别 | 触发条件 | 流程 |
|---|---|---|
| 重大变更 | 影响多模块 / 改 API / 改 schema | 必须开 Issue + PR + Review |
| 一般变更 | 单模块新增 / bug 修复 | 必须开 PR + CI 通过 |
| 紧急修复 | 生产环境阻塞性问题 | 走 `hotfix/` 分支，事后补 Issue |
| 文档调整 | README / 注释 / CHANGELOG | 可直接 PR，免 Review |

## 8. 操作日志 (Audit Log)

应用层在 `sys_log` 表落地所有"有后果"的操作（登录 / 改密 / 备案审核 / 报表审核退回修改删除 / 通知 CUD / 调查期增改 / 用户角色 CUD / 国家上报）。
省级用户在「系统管理 → 操作日志」页可按操作类型 / 操作人 / 日期范围查询。

> 这是**应用层**的审计；与 git 提交日志（**版本层**审计）共同构成完整的"谁在什么时候做了什么"的追溯链。

## 9. 持续集成 (CI)

`.github/workflows/ci.yml` 触发条件：
- 任意分支 push
- 任意 PR 提交

执行步骤：
1. **后端 job**：JDK 17 + Maven cache → `mvn -B compile`
2. **前端 job**：Node 20 + npm cache → `npm ci` → `npm run build`

PR 合并前必须看到绿色 ✓。

## 10. 发布流程

1. `main` 通过所有 PR + CI
2. 更新 `CHANGELOG.md` 的 `[Unreleased]` 区段 → 提名版本号 `vX.Y.Z`
3. 提 PR 合入 `main`
4. 在 main 上打 `git tag -a vX.Y.Z -m "..."`
5. `git push --tags`
6. 在 GitHub 网页 Releases 处发布该 tag（可附产物）

## 11. 工具与角色

- **CM 工具**：Git / GitHub / GitHub Actions / Pull Request / Issues / Releases
- **CM 责任人**：项目负责人（也是唯一开发者）
- **代码审查**：自审 + 答辩答辩老师 / 评审专家
- **基线评审**：每个里程碑完成后自评 + README 标记完成
