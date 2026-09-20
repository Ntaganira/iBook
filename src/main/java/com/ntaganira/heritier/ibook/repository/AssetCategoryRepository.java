/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : AssetCategoryRepository.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for fixed asset categories
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.AssetCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetCategoryRepository extends JpaRepository<AssetCategory, Long> {

    List<AssetCategory> findAllByOrderByNameAsc();

    List<AssetCategory> findByActiveTrueOrderByNameAsc();

    Optional<AssetCategory> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query("select c from AssetCategory c where (:q is null or :q = '' "
            + "   or lower(c.name) like lower(concat('%', :q, '%')) "
            + "   or lower(c.code) like lower(concat('%', :q, '%')) "
            + "   or lower(c.description) like lower(concat('%', :q, '%'))) "
            + "order by c.name asc")
    List<AssetCategory> search(@Param("q") String q);
}
