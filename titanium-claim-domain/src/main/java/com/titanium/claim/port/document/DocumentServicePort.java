package com.titanium.claim.port.document;

/**
 * 文档服务出口 Port（对端域：document）
 * <p>
 * 领域需要的文档能力契约：理赔单证归档（M2 单证管理）。
 * 实现为 infrastructure 层 {@code adapter/document/DocumentServiceAdapter}（M2 接 document 域 Feign）。
 * </p>
 */
public interface DocumentServicePort {

    /**
     * 归档理赔单证。
     *
     * @param document 单证信息
     * @return 归档后的文档ID
     */
    String archiveClaimDocument(ClaimDocument document);

    /**
     * 理赔单证（DocumentServicePort 入参，领域出站契约 record）
     *
     * @param claimId     理赔案件ID
     * @param docTypeCode 单证类型编码
     * @param fileName    文件名
     * @param contentPath 内容存储路径
     */
    record ClaimDocument(
            String claimId,
            String docTypeCode,
            String fileName,
            String contentPath
    ) {
    }
}
