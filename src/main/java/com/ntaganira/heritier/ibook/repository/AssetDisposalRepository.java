/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : AssetDisposalRepository.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for asset disposals
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.AssetDisposal;
import com.ntaganira.heritier.ibook.enums.DisposalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface AssetDisposalRepository extends JpaRepository<AssetDisposal, Long> {

    Optional<AssetDisposal> findByDisposalNo(String disposalNo);

    @Query("select d from AssetDisposal d where (:q is null or :q = '' "
            + "   or lower(d.disposalNo) like lower(concat('%', :q, '%')) "
            + "   or lower(d.assetNo) like lower(concat('%', :q, '%')) "
            + "   or lower(d.assetName) like lower(concat('%', :q, '%')) "
            + "   or lower(d.buyer) like lower(concat('%', :q, '%')) "
            + "   or lower(d.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or d.status = :status)")
    Page<AssetDisposal> search(@Param("q") String q,
                               @Param("status") DisposalStatus status,
                               Pageable pageable);

    long countByStatus(DisposalStatus status);

    @Query("select coalesce(sum(d.proceeds), 0) from AssetDisposal d where d.status = "
            + "com.ntaganira.heritier.ibook.enums.DisposalStatus.POSTED")
    BigDecimal totalProceeds();

    @Query("select coalesce(sum(d.gainOrLoss), 0) from AssetDisposal d where d.status = "
            + "com.ntaganira.heritier.ibook.enums.DisposalStatus.POSTED")
    BigDecimal totalGainOrLoss();

    /** A disposal that is still live, so the same asset is not disposed of twice. */
    @Query("select d from AssetDisposal d where d.assetId = :assetId and d.status <> "
            + "com.ntaganira.heritier.ibook.enums.DisposalStatus.VOID")
    List<AssetDisposal> findLiveForAsset(@Param("assetId") Long assetId);
}
