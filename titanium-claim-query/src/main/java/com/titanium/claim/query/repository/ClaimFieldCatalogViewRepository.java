package com.titanium.claim.query.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.titanium.claim.query.view.ClaimFieldCatalogView;

/**
 * 客户字段目录读模型仓储。
 *
 * <p>目录是一次全量读取的静态元数据，故只有一个取数方法；查询走
 * {@code findAllByOrderByFieldCodeAsc}（排序下推到 SQL）而非 {@code findAll()}——
 * 目录的 {@code contentHash} 由字段顺序参与计算，顺序不稳定会让同一份表数据算出不同哈希。</p>
 */
public interface ClaimFieldCatalogViewRepository extends JpaRepository<ClaimFieldCatalogView, String> {

    /** 按稳定字段码升序取全量目录行。 */
    List<ClaimFieldCatalogView> findAllByOrderByFieldCodeAsc();
}
