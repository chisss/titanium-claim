package com.titanium.claim.port.document;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文档服务出口 Port（对端域：document）
 * <p>
 * 领域需要的文档能力契约：理赔结案单证归档。实现为 infrastructure 层
 * {@code adapter/document/DocumentServiceAdapter}：经 Kafka {@code claim-closed} 主题外发结案事实，
 * 由 document 域防腐消费后渲染正文、落盘并建档（对齐该域既有的「保单签发 → 出保单文档」入站范式）。
 * </p>
 * <p>
 * 🔴 <b>单证类型与文件名由 document 域自决</b>：入参不携带 {@code docTypeCode}/{@code fileName}——
 * 单证类型是 document 域自有的分类维度（{@code DocumentType} 属 document-common，非跨域共享枚举），
 * 文件名由该域按赔案号派生。claim 只表达「赔案已结案，请归档结案单证」这一事实与渲染所需的最小要素集。
 * </p>
 * <p>
 * 🔴 <b>为何经 Kafka 而非 Feign</b>：单证生成是理赔结案的下游副作用，其渲染、落盘与建档职责整体归属
 * document 域（该域已有 {@code FileStoragePort} 与完整入站链路）。走 Feign 会把「渲染 + 落盘」倒灌回
 * claim 域，与该域的职责划分冲突；且同步调用会把单证可用性绑进结案主流程。异步外发的事实语义与同域
 * {@code PaymentServicePort}（Kafka 派发支付单）同构。判据详见
 * {@code docs/技术文档/跨域事件目录-2026-09.md} 与任务 m14-1705 记载。
 * </p>
 */
public interface DocumentServicePort {

    /**
     * 归档理赔结案单证（赔付结算与拒赔结案共用本方法）。
     *
     * @param document 结案单证要素
     * @return 派发标识——本端口实现为事件外发，返回入参 {@code claimId}（非单证ID；单证ID由 document 域生成）
     */
    String archiveClaimDocument(ClaimDocument document);

    /**
     * 理赔结案单证要素（DocumentServicePort 入参，领域出站契约 record）
     * <p>
     * 字段即结案单证渲染所需的最小事实集，跨域载荷即本 record 的 fastjson2 JSON 形态；
     * document 域以其防腐镜像 {@code ClaimClosedMessage} 承接。
     * </p>
     *
     * @param claimId          理赔案件ID
     * @param policyId         关联保单ID
     * @param settledAmount    核定赔付金额（拒赔结案为 {@code null}——无赔付事实，单证按空值渲染）
     * @param payoutMethodCode 给付方式编码（拒赔结案为 {@code null}；中文名由持有该枚举的一端渲染）
     * @param conclusion       结案说明（赔付结案取核赔意见；拒赔结案取「拒赔原因码/批注」）
     * @param closedAt         结案时间
     * @param tenantId         租户ID
     */
    record ClaimDocument(
            String claimId,
            String policyId,
            BigDecimal settledAmount,
            String payoutMethodCode,
            String conclusion,
            LocalDateTime closedAt,
            String tenantId
    ) {
    }
}
