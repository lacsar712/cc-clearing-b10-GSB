# 多币种轧差清算工作台（Clearing Netting Workbench）

单币种多边轧差清算全栈演示：录入义务 → 执行轧差 → 查看净头寸 → 确认 settle。

支持多结算日任务排队串行轧差：一次提交多个「交割日 + 币种」任务，队列按提交顺序串行执行，队列页实时展示等待 / 进行 / 完成 / 失败状态。失败策略：**单项失败不清空后续任务，队列按序继续推进（CONTINUE_ON_FAILURE）**，策略说明展示在队列页顶部。

## How to Run

```bash
cd projects/01-clearing-netting
docker compose up --build
```

镜像默认走 `docker.m.daocloud.io` 与 Maven/npm 国内源，便于在受限网络下构建。若本机已有同名官方镜像亦可直接使用。

后台运行：

```bash
docker compose up --build -d
```

停止：

```bash
docker compose down
```

## Services

| 服务 | 宿主机地址 |
|------|------------|
| Frontend | http://localhost:3171 |
| Backend API | http://localhost:8171 |
| PostgreSQL | localhost:54371 |

容器内：backend 监听 `8080`，frontend nginx 将 `/api` 反代到 `backend:8080`。

## 测试账号

| 用户名 | 密码 | 权限 |
|--------|------|------|
| operator | op123456 | 可写（轧差、settle、新建会员/义务） |
| viewer | view123456 | 只读 |

## Verification

1. 打开 http://localhost:3171 ，使用 `operator` / `op123456` 登录
2. 首页查看 seed 灌入的待轧差义务摘要与最近批次
3. 「会员」页确认演示会员为 ACTIVE；可新建或启停
4. 「义务」页筛选 OPEN 义务，或新建一笔同币种义务
5. 「轧差执行」选择 settleDate + currency（如 USD），执行轧差
6. 确认净头寸表 ΣnetAmount = 0，批次状态 COMPLETED
7. 进入批次详情，点击 Settle，义务变为 SETTLED
8. 使用 `viewer` 登录，确认只能浏览、无法执行写操作

任务队列（串行轧差）：

1. 从侧边菜单「轧差队列」、或「轧差执行」/ 批次详情页的「任务队列」按钮进入队列页
2. 默认两行任务（可增删），点击「提交队列」一次提交多项「交割日 + 币种」任务
3. 队列页每秒轮询，可看到任务按序号顺序推进：等待 QUEUED → 进行 RUNNING → 完成 COMPLETED（失败 FAILED）
4. 完成的任务可点击批次链接跳转批次详情
5. 提交一项无 OPEN 义务的币种（如 EUR）可观察到该项 FAILED，后续任务仍继续推进（页面上方展示失败策略）

队列接口：

```bash
# 提交两项任务（operator）
curl -X POST http://localhost:8171/api/netting-tasks \
  -H "Authorization: Bearer <token>" -H "Content-Type: application/json" \
  -d '{"items":[{"settleDate":"2026-09-19","currency":"USD"},{"settleDate":"2026-09-19","currency":"CNY"}]}'

# 查看队列（含失败策略说明）
curl http://localhost:8171/api/netting-tasks -H "Authorization: Bearer <token>"
```

健康检查：

```bash
curl http://localhost:8171/api/health
```

登录：

```bash
curl -X POST http://localhost:8171/api/auth/login \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"operator\",\"password\":\"op123456\"}"
```

## 技术栈

- Backend: Java 17、Spring Boot 3、Hexagonal、JPA、PostgreSQL、JWT
- Frontend: Vue 3、Vite、Element Plus、Pinia、Vue Router、nginx
- Infra: Docker Compose（db / backend / seed / frontend）

## 项目结构

```
01-clearing-netting/
├── PRD.md
├── README.md
├── docker-compose.yml
├── backend/
├── frontend/
└── seed/
```
