/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : VendorRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for vendors
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface VendorRepository extends JpaRepository<Vendor, Long> {

    @Query("select v from Vendor v where (:q is null or :q = '' "
            + "   or lower(v.name) like lower(concat('%', :q, '%')) "
            + "   or lower(v.companyName) like lower(concat('%', :q, '%')) "
            + "   or lower(v.email) like lower(concat('%', :q, '%')) "
            + "   or lower(v.phone) like lower(concat('%', :q, '%')) "
            + "   or lower(v.city) like lower(concat('%', :q, '%')))"
            + "   and (:type is null or :type = '' "
            + "       or (:type = 'balance' and v.openingBalance <> 0) "
            + "       or (:type = 'inactive' and v.active = false) "
            + "       or (:type = 'active' and v.active = true))")
    Page<Vendor> search(@Param("q") String q, @Param("type") String type, Pageable pageable);

    List<Vendor> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByActiveTrue();
}
