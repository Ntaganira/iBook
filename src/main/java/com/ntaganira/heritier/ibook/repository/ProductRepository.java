/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ProductRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for products
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Product;
import com.ntaganira.heritier.ibook.enums.ProductType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("select p from Product p where (:q is null or :q = '' "
            + "   or lower(p.sku) like lower(concat('%', :q, '%')) "
            + "   or lower(p.name) like lower(concat('%', :q, '%')) "
            + "   or lower(p.brand) like lower(concat('%', :q, '%'))) "
            + "   and (:categoryId is null or p.categoryId = :categoryId) "
            + "   and (:type is null or p.type = :type)")
    Page<Product> search(@Param("q") String q,
                         @Param("categoryId") Long categoryId,
                         @Param("type") ProductType type,
                         Pageable pageable);

    List<Product> findByActiveTrueOrderByNameAsc();

    List<Product> findByTrackStockTrueAndActiveTrueOrderByNameAsc();

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, Long id);

    long countByActiveTrue();

    long countByCategoryId(Long categoryId);

    long countByBrandId(Long brandId);

    List<Product> findByBrandId(Long brandId);

    /** Products whose brand was typed as free text before brands became records of their own. */
    @Query("select p from Product p where p.brandId is null "
            + "and p.brand is not null and trim(p.brand) <> '' order by p.brand asc")
    List<Product> findWithUnlinkedBrand();
}
