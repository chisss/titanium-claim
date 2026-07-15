# Titanium 保险核心系统 - 理赔域(titanium-claim)模块开发规约

> **版本**: V1.0
> **最后更新**: 2026-06-23
> **模块类型**: DDD+CQRS+事件驱动的理赔领域微服务
> **完成度**: 50%（开发中）
> **上级规约**: 继承根目录 [CLAUDE.md](../CLAUDE.md) 与 [AGENTS.md](../AGENTS.md)

---

## 一、模块概述与业务定位

**理赔域(titanium-claim)** 负责保险理赔的全流程管理，在保险业务生命周期中处于保单生效之后、监管上报之前的核心赔付环节。

### 核心业务职责
- **理赔报案**: 受理客户出险报案，创建理赔案件（`Claim` 聚合根）
- **查勘定损**: 维护出险信息、理赔金额、案件状态流转
- **赔付结案**: 状态推进至 `APPROVED`/`PAID`，对外发布理赔事件

### 当前能力边界（基于真实代码）
- 已实现：理赔案件创建/更新、状态变更、按客户/保单/状态查询
- 已实现：Kafka 事件发布（创建/更新/状态变更）
- 依赖外部：通过 Feign 调用**保单域**校验保单是否 `ACTIVE`
- 未完成：查勘定损子流程、赔付计算、多租户上下文未贯穿（详见第七章）

---

## 二、技术栈与端口

| 配置项 | 值 | 说明 |
|-------|---|------|
| **JDK** | `/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home` | Amazon Corretto 21 |
| **Spring Boot** | 4.0.1 | 继承根 POM |
| **Axon Framework** | 4.10.0 | CQRS + 事件溯源 |
| **服务端口** | `8083` | `server.port=8083` |
| **Context Path** | `/claim` | `server.servlet.context-path=/claim` |
| **数据库** | `jdbc:mysql://localhost:8066/titanium_claim` | MySQL 8 |
| **Kafka** | `localhost:9092` | group=`titanium-claim-group` |
| **Axon 序列化** | `jackson`（general/events/messages） | 事件处理器 `claim-group` 为 `subscribing` 模式 |
| **多租户** | `multi-tenant.enabled=true` | 默认租户 `default-tenant` |

> 配置来源：`titanium-claim-bootstrap/src/main/resources/application.yml`

---

## 三、子模块分层结构

理赔域目前包含 **7 个子模块**（注意：**缺少独立的 query 子模块**）：

```
titanium-claim/
├── titanium-claim-api/             # API层：Feign接口定义(api.client) + DTO
├── titanium-claim-application/     # 应用层：命令服务 + 命令定义 + 跨域Feign封装
│   └── com.titanium.claim.application
│       ├── command/                # ⚠️ 命令类在此（CreateClaimCommand 等3个）
│       ├── service/                # ClaimApplicationService / PolicyService
│       └── dto/                    # 应用层请求/响应DTO
├── titanium-claim-domain/          # 领域层
│   └── com.titanium.claim          # ⚠️ 包根为 com.titanium.claim（非 .domain）
│       ├── aggregate/              # Claim 聚合根
│       ├── event/                  # 3个事件（record）
│       ├── query/                  # ⚠️ 4个查询（record），无独立query模块
│       ├── enums/                  # ClaimStatus
│       ├── valueobject/            # ClaimId/CustomerId/PolicyId/ClaimAmount
│       └── service/                # ClaimService 领域服务（写侧纯事件溯源，无仓储 Port）
├── titanium-claim-infrastructure/  # 基础设施层（写侧纯事件溯源，无 JPA 写表/仓储/Entity/Mapper）
│   └── com.titanium.claim.infrastructure
│       ├── config/                 # AxonConfig / KafkaConfig
│       └── event/                  # KafkaEventPublisher
├── titanium-claim-query/           # 查询层：ClaimProjectionEventHandler / ClaimQueryHandler / ClaimView / ClaimViewRepository / ClaimQueryService
├── titanium-claim-common/          # 通用层：constant/exception
├── titanium-claim-web/             # Web层：ClaimController / TenantInterceptor / WebConfig
└── titanium-claim-bootstrap/       # 启动层：ClaimApplication + application.yml
```

### 关键结构偏差（与根规约不符，需知悉）
1. **无 `titanium-claim-query` 子模块**：查询类(record)放在 `domain/query`，`@QueryHandler` 实现放在 `infrastructure/projection/ClaimProjection.java`，与根规约「独立 query 层」约定不符。
2. **包根为 `com.titanium.claim`** 而非根规约示例的 `com.titanium.claim.domain`，领域层直接挂在 `com.titanium.claim` 下（`aggregate`/`event`/`query`/`valueobject`/`enums`）。
3. **命令类位于 application 层**（`com.titanium.claim.application.command`），而非领域层 `command` 包。

---

## 四、核心领域模型（基于真实代码）

### 4.1 聚合根 Claim
位置：`titanium-claim-domain/.../com/titanium/claim/aggregate/Claim.java`

```
@Aggregate  字段：claimId(@AggregateIdentifier), customerId, policyId,
            claimNumber, claimType, incidentDate, incidentDescription,
            claimAmount, status(ClaimStatus), createdAt, updatedAt
```

命令处理与事件溯源映射：

| 命令(@CommandHandler) | 触发事件(apply) | 事件溯源(@EventSourcingHandler) 状态变化 |
|----------------------|----------------|----------------------------------------|
| `CreateClaimCommand`(构造器) | `ClaimCreatedEvent` | 初始化全部字段，`status=PENDING` |
| `UpdateClaimCommand` | `ClaimUpdatedEvent` | 更新理赔类型/出险信息/金额/updatedAt |
| `ChangeClaimStatusCommand` | `ClaimStatusChangedEvent` | 更新 status / updatedAt |

**业务规则**（聚合根内）：`ChangeClaimStatusCommand` 仅当 `status != null 且新旧状态不同` 时才发布事件，否则静默返回（⚠️ 未做合法状态流转矩阵校验）。

### 4.2 命令（3个，application/command，record）
`CreateClaimCommand`、`UpdateClaimCommand`、`ChangeClaimStatusCommand`

### 4.3 事件（3个，domain/event，record）
`ClaimCreatedEvent`、`ClaimUpdatedEvent`、`ClaimStatusChangedEvent`

### 4.4 查询（4个，domain/query，record）
`ClaimQuery`（按ID）、`FindClaimsByCustomerIdQuery`、`FindClaimsByPolicyIdQuery`、`FindClaimsByStatusQuery`

### 4.5 状态枚举 ClaimStatus
```
PENDING("待处理") → PROCESSING("处理中") → APPROVED("已批准") / REJECTED("已拒绝") → PAID("已支付")
```
- `code` 来源于 `ClaimConstants`，提供 `fromCode()` / `fromDescription()`，非法值抛 `InvalidClaimStatusException`
- ⚠️ 状态流转的合法性约束当前**未在枚举或聚合根中实现**

### 4.6 值对象与异常
- 值对象：`ClaimId`/`CustomerId`/`PolicyId`/`ClaimAmount`（`com.titanium.claim.valueobject`）
- 异常（`com.titanium.claim.common.exception`）：`ClaimAlreadyProcessed`、`ClaimNotFound`、`ClaimOutOfCoverage`、`CustomerNotFound`、`InvalidClaimAmount`、`InvalidClaimStatus`、`PolicyNotActive`、`PolicyNotFound`，统一继承 `BusinessException`

### 4.7 CQRS 读写链路
- **写**：`ClaimController` → `ClaimApplicationService` → `CommandGateway` → `Claim` 聚合根 → 事件
- **事件分发**：`ClaimProjection`（投影到 `t_claim`）+ `KafkaEventPublisher`（发往 Kafka）
- **读**：`ClaimApplicationService` 通过 Axon `Repository.load` 读单笔；列表查询走 `ClaimRepository`（读侧仓储，由 `ClaimProjection` 维护的 `t_claim` 投影表）

---

## 五、编码规约（继承根规约）

- **命令/查询**：JDK 21 `record`；命令处理 `@CommandHandler`，查询处理 `@QueryHandler`
- **依赖注入**：构造器注入优先（现状用 `@AllArgsConstructor`/`@RequiredArgsConstructor`），禁用 `@Autowired` 字段注入（⚠️ `ClaimProjection` 现用字段注入，待整改）
- **日志**：SLF4J `{}` 占位符，禁止字符串拼接
- **异常**：Service 层抛自定义 `BusinessException`，`@ControllerAdvice` 全局兜底（⚠️ `ClaimApplicationService` 现抛裸 `RuntimeException`，待整改）
- **跨层转换**：web 层 MapStruct（Request/DTO↔Command/VO），禁止实体直接 new 转换。写侧已纯事件溯源，无「聚合根↔Entity」的 infra Mapper
- **持久化选型（写侧纯事件溯源）**：`Claim` 聚合为 Axon 事件溯源（`EventSourcingRepository` + `@EventSourcingHandler`），写侧状态只在事件流，**无 JPA 写表 / `*Entity` / `Jpa*Repository` / 仓储实现桩 / 领域仓储 Port**。JPA 仅承载 CQRS 读模型（`titanium-claim-query` 的 `ClaimView` / `ClaimViewRepository`，表 `t_claim_view`）。若后续新增**状态存储聚合**需保留的持久化对象，一律命名 `XxxxDO`（禁用 `Entity` 后缀），读模型投影保留 `*View`。选型细则见根 `docs/技术文档/持久化选型规范(JPA与EventSourcing).md`
- **注释**：对外实体 `@Schema`，内部实体 `/** */`，注释中文、标识符英文
- **多租户**：请求头 `X-Tenant-Id` → `TenantInterceptor` → `TenantContext`；所有表含 `tenant_id`

---

## 六、构建与运行

```bash
export JAVA_HOME=/Users/sunwei/Library/Java/JavaVirtualMachines/corretto-21.0.4/Contents/Home

# 模块构建（在仓库根目录）
cd /Users/sunwei/titanium-project
mvn -pl titanium-claim -am clean install -DskipTests

# 启动理赔服务（端口 8083，访问前缀 /claim）
cd titanium-claim/titanium-claim-bootstrap
mvn spring-boot:run
```

依赖前置：MySQL(`localhost:8066`)、Kafka(`localhost:9092`)、**保单域服务可达**（Feign 校验保单）。

---

## 七、已知缺陷与待办

| 级别 | 问题 | 位置 | 建议 |
|------|------|------|------|
| 🔴 高 | **缺独立 query 子模块**，查询与投影耦合在 `ClaimProjection` | `infrastructure/projection` | 拆出 `titanium-claim-query`，分离 `@QueryHandler` 与事件投影 |
| 🔴 高 | **包结构不符根规约**（`com.titanium.claim` 而非 `com.titanium.claim.domain`） | domain 层 | 评估与 `titanium-policy` 对齐的迁移成本 |
| 🔴 高 | **多租户未贯穿**：投影硬编码 `"default"`，保单校验硬编码 `"default-tenant"` | `ClaimProjection:58`、`ClaimApplicationService:75` | 从 `TenantContext` 取真实租户ID |
| 🟠 中 | **状态流转无合法性矩阵**，任意状态可跳转 | `Claim.java:68-80` | 在聚合根/枚举内实现状态机校验 |
| 🟠 中 | **裸 RuntimeException** 替代 `BusinessException` | `ClaimApplicationService:79/85` | 抛 `PolicyNotActive`/`PolicyNotFound` 等领域异常 |
| 🟠 中 | **事件/命令访问器风格不一致**：聚合根与 `KafkaEventPublisher` 用 `getXxx()`，`ClaimProjection` 用 record `xxx()` | 多处 | 统一为 record 访问器，避免编译歧义 |
| 🟠 中 | **DDL 自动更新**（`ddl-auto=update`）未走 Liquibase | `application.yml:15` | 改为 Liquibase 迁移脚本管理 |
| 🟡 低 | **端口 8083 冲突风险** | `application.yml:53` | 多模块本地并行启动时确认端口唯一 |
| 🟡 低 | `ClaimProjection` 使用 `@Autowired` 字段注入 | `ClaimProjection:31/34` | 改构造器注入 |
| 🟡 低 | 完成度 50%，查勘定损/赔付计算未实现 | 全模块 | 按业务生命周期补齐 |

---

*本文档为理赔域模块级开发规约，与根规约冲突处以根规约为准，结构偏差项见第三、七章。*
