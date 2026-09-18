/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : CustomerRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for customers
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("select c from Customer c where (:q is null or :q = '' " +
            "   or lower(c.name) like lower(concat('%', :q, '%')) " +
            "   or lower(c.companyName) like lower(concat('%', :q, '%')) " +
            "   or lower(c.email) like lower(concat('%', :q, '%')) " +
            "   or lower(c.phone) like lower(concat('%', :q, '%')) " +
            "   or lower(c.city) like lower(concat('%', :q, '%')))" +
            "   and (:type is null or :type = '' or (:type = 'overdue' and c.openingBalance > 0) " +
            "       or (:type = 'inactive' and c.active = false) " +
            "       or (:type = 'balance' and c.openingBalance <> 0) " +
            "       or (:type = 'active' and c.active = true))")
    Page<Customer> search(@Param("q") String q, @Param("type") String type, Pageable pageable);

    List<Customer> findByActiveTrueOrderByNameAsc();

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByActiveTrue();
}