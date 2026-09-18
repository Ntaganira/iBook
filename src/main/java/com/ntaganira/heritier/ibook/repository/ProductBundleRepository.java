/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ProductBundleRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for product bundles
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.ProductBundle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductBundleRepository extends JpaRepository<ProductBundle, Long> {

    List<ProductBundle> findAllByOrderByNameAsc();

    List<ProductBundle> findByActiveTrueOrderByNameAsc();

    Optional<ProductBundle> findByBundleProductId(Long bundleProductId);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByBundleProductId(Long bundleProductId);

    boolean existsByBundleProductIdAndIdNot(Long bundleProductId, Long id);

    @Query("select b from ProductBundle b where (:q is null or :q = '' "
            + "   or lower(b.code) like lower(concat('%', :q, '%')) "
            + "   or lower(b.name) like lower(concat('%', :q, '%')) "
            + "   or lower(b.bundleProductSku) like lower(concat('%', :q, '%')) "
            + "   or lower(b.bundleProductName) like lower(concat('%', :q, '%'))) "
            + "order by b.name asc")
    List<ProductBundle> search(@Param("q") String q);

    /** Bundles that a given product is a component of, so it cannot be deleted blindly. */
    @Query("select distinct c.bundle from ProductBundleComponent c where c.productId = :productId")
    List<ProductBundle> findUsingComponent(@Param("productId") Long productId);

    long countByActiveTrue();
}
