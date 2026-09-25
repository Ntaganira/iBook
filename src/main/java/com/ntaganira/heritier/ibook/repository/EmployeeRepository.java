/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : EmployeeRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for employees
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Employee;
import com.ntaganira.heritier.ibook.enums.EmployeeStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmployeeNoIgnoreCase(String employeeNo);

    boolean existsByEmployeeNoIgnoreCase(String employeeNo);

    long countByStatus(EmployeeStatus status);

    @Query("select e from Employee e where (:q is null or :q = '' "
            + "   or lower(e.firstName) like lower(concat('%', :q, '%')) "
            + "   or lower(e.lastName) like lower(concat('%', :q, '%')) "
            + "   or lower(e.employeeNo) like lower(concat('%', :q, '%')) "
            + "   or lower(e.jobTitle) like lower(concat('%', :q, '%')) "
            + "   or lower(e.rssbNumber) like lower(concat('%', :q, '%')) "
            + "   or lower(e.email) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or e.status = :status) "
            + "   and (:department is null or :department = '' or e.department = :department)")
    Page<Employee> search(@Param("q") String q,
                          @Param("status") EmployeeStatus status,
                          @Param("department") String department,
                          Pageable pageable);

    /**
     * Everybody a run should consider. Terminated and suspended people are excluded here, and the
     * date rules are applied again per person, because somebody who joined or left mid-period is
     * still in this list and only the entity knows what to do about them.
     */
    @Query("select e from Employee e "
            + "where e.status <> com.ntaganira.heritier.ibook.enums.EmployeeStatus.TERMINATED "
            + "and e.status <> com.ntaganira.heritier.ibook.enums.EmployeeStatus.SUSPENDED "
            + "order by e.lastName asc, e.firstName asc")
    List<Employee> findRunnable();

    List<Employee> findByStatusOrderByLastNameAscFirstNameAsc(EmployeeStatus status);

    @Query("select distinct e.department from Employee e where e.department is not null "
            + "and e.department <> '' order by e.department asc")
    List<String> findDepartments();

    @Query("select coalesce(sum(e.basicSalary), 0) from Employee e "
            + "where e.status = com.ntaganira.heritier.ibook.enums.EmployeeStatus.ACTIVE")
    BigDecimal activeBasicTotal();

    @Query("select count(e) from Employee e where e.endDate is not null and e.endDate <= :horizon "
            + "and e.status <> com.ntaganira.heritier.ibook.enums.EmployeeStatus.TERMINATED")
    long countEndingBy(@Param("horizon") LocalDate horizon);
}
