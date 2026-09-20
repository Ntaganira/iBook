/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : AssetLocationRepository.java
 * - Date      : 2026. 09. 19.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for fixed asset locations
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.AssetLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssetLocationRepository extends JpaRepository<AssetLocation, Long> {

    List<AssetLocation> findAllByOrderByNameAsc();

    List<AssetLocation> findByActiveTrueOrderByNameAsc();

    Optional<AssetLocation> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    @Query("select l from AssetLocation l where (:q is null or :q = '' "
            + "   or lower(l.name) like lower(concat('%', :q, '%')) "
            + "   or lower(l.code) like lower(concat('%', :q, '%')) "
            + "   or lower(l.site) like lower(concat('%', :q, '%')) "
            + "   or lower(l.city) like lower(concat('%', :q, '%')) "
            + "   or lower(l.manager) like lower(concat('%', :q, '%'))) "
            + "order by l.name asc")
    List<AssetLocation> search(@Param("q") String q);
}
