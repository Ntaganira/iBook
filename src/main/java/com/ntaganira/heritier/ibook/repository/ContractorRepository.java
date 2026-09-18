/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ContractorRepository.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for contractors
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Contractor;
import com.ntaganira.heritier.ibook.enums.ContractorType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ContractorRepository extends JpaRepository<Contractor, Long> {

    Optional<Contractor> findByNameIgnoreCase(String name);

    @Query("select c from Contractor c where (:q is null or :q = '' "
            + "   or lower(c.name) like lower(concat('%', :q, '%')) "
            + "   or lower(c.trade) like lower(concat('%', :q, '%')) "
            + "   or lower(c.email) like lower(concat('%', :q, '%')) "
            + "   or lower(c.taxId) like lower(concat('%', :q, '%'))) "
            + "   and (:type is null or c.contractorType = :type) "
            + "   and (:active is null or c.active = :active)")
    Page<Contractor> search(@Param("q") String q,
                            @Param("type") ContractorType type,
                            @Param("active") Boolean active,
                            Pageable pageable);

    long countByContractorType(ContractorType type);

    long countByActive(boolean active);

    @Query("select count(c) from Contractor c where c.contractEnd is not null and c.contractEnd < :today")
    long countExpired(@Param("today") LocalDate today);

    @Query("select count(c) from Contractor c where c.active = true and c.contractEnd is not null "
            + "and c.contractEnd >= :today and c.contractEnd <= :horizon")
    long countExpiringSoon(@Param("today") LocalDate today, @Param("horizon") LocalDate horizon);

    List<Contractor> findByActiveTrueOrderByNameAsc();

    List<Contractor> findByVendorId(Long vendorId);
}
