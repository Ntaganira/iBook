/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : AssetTransferRepository.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for fixed asset transfers
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.AssetTransfer;
import com.ntaganira.heritier.ibook.enums.AssetTransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AssetTransferRepository extends JpaRepository<AssetTransfer, Long> {

    Optional<AssetTransfer> findByTransferNo(String transferNo);

    @Query("select t from AssetTransfer t where (:q is null or :q = '' "
            + "   or lower(t.transferNo) like lower(concat('%', :q, '%')) "
            + "   or lower(t.assetNo) like lower(concat('%', :q, '%')) "
            + "   or lower(t.assetName) like lower(concat('%', :q, '%')) "
            + "   or lower(t.fromLocation) like lower(concat('%', :q, '%')) "
            + "   or lower(t.toLocation) like lower(concat('%', :q, '%')) "
            + "   or lower(t.toCustodian) like lower(concat('%', :q, '%')) "
            + "   or lower(t.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or t.status = :status)")
    Page<AssetTransfer> search(@Param("q") String q,
                               @Param("status") AssetTransferStatus status,
                               Pageable pageable);

    long countByStatus(AssetTransferStatus status);

    @Query("select count(t) from AssetTransfer t where t.reclassified = true and t.status = "
            + "com.ntaganira.heritier.ibook.enums.AssetTransferStatus.COMPLETED")
    long countReclassifications();

    /** A draft already waiting on this asset, so the same move is not raised twice. */
    @Query("select t from AssetTransfer t where t.assetId = :assetId and t.status = "
            + "com.ntaganira.heritier.ibook.enums.AssetTransferStatus.DRAFT")
    List<AssetTransfer> findDraftsForAsset(@Param("assetId") Long assetId);

    List<AssetTransfer> findByAssetIdOrderByTransferDateDescIdDesc(Long assetId);

    /**
     * Completed moves of the same asset that happened after this one. Voiding while one of these
     * exists would stamp the asset with a location it has already left.
     */
    @Query("select t from AssetTransfer t where t.assetId = :assetId and t.id <> :id "
            + "and t.status = com.ntaganira.heritier.ibook.enums.AssetTransferStatus.COMPLETED "
            + "and (t.transferDate > :transferDate or (t.transferDate = :transferDate and t.id > :id))")
    List<AssetTransfer> findLaterCompleted(@Param("assetId") Long assetId,
                                           @Param("id") Long id,
                                           @Param("transferDate") LocalDate transferDate);
}
