# Titanium 理赔域(titanium-claim) - 多Agent协作指南

> **版本**: V1.0
> **最后更新**: 2026-06-23
> **适用场景**: 理赔域多Agent协作开发、跨域联调、代码审查
> **上级指南**: 继承根目录 [AGENTS.md](../AGENTS.md)

---

## 一、模块定位与边界

**理赔域(titanium-claim)** 是 Titanium 保险核心系统中负责理赔报案、查勘定损、赔付结案的领域微服务，端口 `8083`，完成度约 50%。

### 领域边界
- **拥有**：`Claim` 聚合根及其全生命周期（报案→处理→批准/拒绝→赔付）
- **依赖（上游）**：保单域(`titanium-policy`) —— 创建理赔前校验保单状态
- **被依赖（下游）**：监管域(`titanium-regulatory`) —— 消费理赔事件用于上报
- **禁止**：理赔域不得反向被保单域依赖；不得直接修改保单/客户聚合根

### 在依赖图中的位置（参见根 AGENTS.md 4.2）
```
Policy → ... → Claim → Regulatory
```
理赔域处于较下层，可依赖上层保单域（通过 Feign/事件），不得被上层反向依赖。

---

## 二、与其他域交互点

### 2.1 同步依赖：Feign 调用保单域
| 项 | 内容 |
|----|------|
| 调用方 | `ClaimApplicationService.validatePolicy()` |
| 封装类 | `application/service/PolicyService.java` |
| 依赖接口 | `com.titanium.policy.api.PolicyApi#getPolicy(policyId, tenantId)` |
| 触发时机 | `createClaim()` 创建理赔前 |
| 业务规则 | 保单必须 `status == "ACTIVE"`，否则拒绝创建 |
| Feign 注册 | `@EnableFeignClients(basePackages = "com.titanium.claim.api.client")` |
| ⚠️ 风险 | 当前硬编码 `tenantId = "default-tenant"`，未取真实租户上下文 |

### 2.2 异步输出：Kafka 事件（KafkaEventPublisher）
| 事件 | Kafka Topic（ClaimConstants.KafkaTopic） | Key | 下游消费者 |
|------|------------------------------------------|-----|-----------|
| `ClaimCreatedEvent` | `CLAIM_CREATED` | claimId | 监管域 |
| `ClaimUpdatedEvent` | `CLAIM_UPDATED` | claimId | 监管域 |
| `ClaimStatusChangedEvent` | `CLAIM_STATUS_CHANGED` | claimId | 监管域 / 通知域 |

> 事件序列化 `JSON.toJSONString`(fastjson2)；Axon 序列化为 jackson；`claim-group` 处理器为 `subscribing` 模式。

### 2.3 内部事件流（Axon）
```
Command → Claim 聚合根 → apply(Event)
  ├─→ ClaimProjection（投影写 t_claim，并承载 @QueryHandler）
  └─→ KafkaEventPublisher（对外发 Kafka）
```

---

## 三、文件锁定建议

修改以下关键文件前需声明锁定，避免并发冲突（绝对路径）：

```yaml
Agent-Claim-Domain:
  locked_files:
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-domain/src/main/java/com/titanium/claim/aggregate/Claim.java
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-domain/src/main/java/com/titanium/claim/enums/ClaimStatus.java
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-domain/src/main/java/com/titanium/claim/event/   # 3个事件

Agent-Claim-Application:
  locked_files:
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-application/src/main/java/com/titanium/claim/application/service/ClaimApplicationService.java
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-application/src/main/java/com/titanium/claim/application/service/PolicyService.java
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-application/src/main/java/com/titanium/claim/application/command/   # 3个命令

Agent-Claim-Infra:
  locked_files:
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-infrastructure/src/main/java/com/titanium/claim/infrastructure/projection/ClaimProjection.java
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-infrastructure/src/main/java/com/titanium/claim/infrastructure/event/KafkaEventPublisher.java
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-infrastructure/src/main/java/com/titanium/claim/infrastructure/config/   # AxonConfig/KafkaConfig

Agent-Claim-Web:
  locked_files:
    - /Users/sunwei/titanium-project/titanium-claim/titanium-claim-web/src/main/java/com/titanium/claim/web/controller/ClaimController.java
```

> **高冲突文件**：`ClaimProjection.java` 同时承载事件投影与 `@QueryHandler`，是缺陷整改与查询开发的交汇点，务必单写者锁定。

---

## 四、Agent 任务分工

### 4.1 角色分工（理赔域内）
| 角色 | 职责 | 主要产出物 |
|------|------|-----------|
| **Agent-Lead** | 需求拆解、跨域协调、集成验证 | 任务计划、集成测试 |
| **Agent-Domain** | 聚合根/命令/事件/状态机设计 | `Claim.java`、`ClaimStatus.java`、事件 |
| **Agent-Application** | 命令编排、Feign 保单校验、读侧查询 | `ClaimApplicationService.java` |
| **Agent-Infra** | 投影、Kafka 发布、仓储、Axon/Kafka 配置 | `ClaimProjection`、`KafkaEventPublisher` |
| **Agent-Web** | REST 接口、租户拦截 | `ClaimController`、`TenantInterceptor` |
| **Agent-Test** | 三层单测 + 跨域集成测试 | `*Test.java` |

### 4.2 典型任务分工模板

#### 任务A：补齐「理赔状态机」（领域内）
```
1. [Agent-Domain]  在 ClaimStatus / Claim 聚合根内实现合法流转矩阵
                   PENDING→PROCESSING→APPROVED/REJECTED→PAID
2. [Agent-Domain]  非法流转抛 InvalidClaimStatus / ClaimAlreadyProcessed
3. [Agent-Test]    覆盖正常流转 + 非法跳转用例
```

#### 任务B：实现「保单生效后联动理赔受理」（跨域）
```
1. [Agent-Lead]         拆解：保单域发 PolicyActivatedEvent，理赔域按需建档
2. [Agent-Policy(外部)] 由保单域 Agent 负责发布事件（理赔域只读，勿改保单代码）
3. [Agent-Infra]        理赔域新增事件监听器消费保单事件
4. [Agent-Application]  编排理赔受理逻辑，复用 PolicyService 校验
5. [Agent-Test]         端到端事件链验证
```

#### 任务C：修复「多租户未贯穿」缺陷
```
1. [Agent-Infra]        ClaimProjection 从 TenantContext 取租户ID，去除硬编码 "default"
2. [Agent-Application]  validatePolicy 从 TenantContext 取 tenantId，去除 "default-tenant"
3. [Agent-Web]          确认 TenantInterceptor 正确写入 TenantContext
4. [Agent-Test]         多租户隔离回归
```

---

## 五、协作检查清单

### 5.1 跨域协作前置检查
- [ ] 是否仅通过 **Feign(读) + Kafka事件(写)** 与保单域/监管域交互，未直接改它域代码
- [ ] 保单校验是否传递**真实租户ID**（非硬编码 `default-tenant`）
- [ ] 新增/变更事件是否同步告知监管域、通知域消费方
- [ ] Kafka Topic 常量是否统一定义在 `ClaimConstants.KafkaTopic`

### 5.2 DDD/CQRS 规范检查（继承根 AGENTS.md 第六章）
- [ ] 命令/查询为 `record`；命令处理 `@CommandHandler`，查询处理 `@QueryHandler`
- [ ] 聚合根 `Claim` 封装业务规则，状态变更经事件溯源（`@EventSourcingHandler`）
- [ ] 事件命名「对象+动作+Event」，携带足够业务信息避免回查
- [ ] 跨层转换走 `ClaimMapper`(MapStruct)，禁止裸 new 转换

### 5.3 理赔域专项检查
- [ ] 状态流转合法性是否校验（当前缺失，见 CLAUDE.md 第七章）
- [ ] Service 层是否抛 `BusinessException` 子类（非裸 `RuntimeException`）
- [ ] 事件/命令访问器风格统一（建议统一 record 访问器 `xxx()`）
- [ ] 投影 `t_claim` 表是否含 `tenant_id` 且查询自动过滤
- [ ] 三层（Application/Domain/Infrastructure）是否有对应单测

### 5.4 结构偏差认知（必须知悉）
- [ ] 理赔域**无独立 query 子模块**：查询走 `domain/query` + `infrastructure/projection`
- [ ] 包根为 `com.titanium.claim`（非 `.domain`）
- [ ] 命令类在 `application/command`（非领域层）
- [ ] 整改上述偏差前需与 Agent-Lead 确认迁移影响范围

---

## 六、参考资料

- 根协作指南：[AGENTS.md](../AGENTS.md)
- 根开发规约：[CLAUDE.md](../CLAUDE.md)
- 本模块规约：[titanium-claim/CLAUDE.md](./CLAUDE.md)
- 最佳实践参考域：`titanium-policy`（90%）、`titanium-customer`（90%）
- 上游依赖：`titanium-policy`（`PolicyApi`）
- 下游消费：`titanium-regulatory`、`titanium-notification`

---

*本指南随理赔域演进更新；与根 AGENTS.md 冲突处以根指南为准，结构偏差与缺陷以本模块 CLAUDE.md 第七章为准。*
