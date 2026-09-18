/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : FixedAssetRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for registered fixed assets
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.FixedAsset;
import com.ntaganira.heritier.ibook.enums.AssetStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface FixedAssetRepository extends JpaRepository<FixedAsset, Long> {

    Optional<FixedAsset> findByAssetNo(String assetNo);

    @Query("select a from FixedAsset a where (:q is null or :q = '' "
            + "   or lower(a.assetNo) like lower(concat('%', :q, '%')) "
            + "   or lower(a.name) like lower(concat('%', :q, '%')) "
            + "   or lower(a.category) like lower(concat('%', :q, '%')) "
            + "   or lower(a.location) like lower(concat('%', :q, '%')) "
            + "   or lower(a.custodian) like lower(concat('%', :q, '%')) "
            + "   or lower(a.serialNumber) like lower(concat('%', :q, '%')) "
            + "   or lower(a.tagNumber) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or a.status = :status) "
            + "   and (:category is null or :category = '' or a.category = :category)")
    Page<FixedAsset> search(@Param("q") String q,
                            @Param("status") AssetStatus status,
                            @Param("category") String category,
                            Pageable pageable);

    long countByStatus(AssetStatus status);

    @Query("select coalesce(sum(a.acquisitionCost), 0) from FixedAsset a where a.status in :statuses")
    BigDecimal totalCostFor(@Param("statuses") List<AssetStatus> statuses);

    @Query("select coalesce(sum(coalesce(a.openingAccumulated, 0) + coalesce(a.postedAccumulated, 0)), 0) "
            + "from FixedAsset a where a.status in :statuses")
    BigDecimal totalAccumulatedFor(@Param("statuses") List<AssetStatus> statuses);

    @Query("select distinct a.category from FixedAsset a "
            + "where a.category is not null and trim(a.category) <> '' order by a.category asc")
    List<String> distinctCategories();

    @Query("select distinct a.location from FixedAsset a "
            + "where a.location is not null and trim(a.location) <> '' order by a.location asc")
    List<String> distinctLocations();

    List<FixedAsset> findByStatusOrderByAssetNoAsc(AssetStatus status);
}
