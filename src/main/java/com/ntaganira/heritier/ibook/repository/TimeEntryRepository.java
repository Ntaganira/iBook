/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : TimeEntryRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for booked time
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.TimeEntry;
import com.ntaganira.heritier.ibook.enums.TimeEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, Long> {

    @Query("select t from TimeEntry t where (:q is null or :q = '' "
            + "   or lower(t.person) like lower(concat('%', :q, '%')) "
            + "   or lower(t.task) like lower(concat('%', :q, '%')) "
            + "   or lower(t.description) like lower(concat('%', :q, '%')) "
            + "   or lower(t.projectCode) like lower(concat('%', :q, '%')) "
            + "   or lower(t.projectName) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or t.status = :status) "
            + "   and (:projectId is null or t.projectId = :projectId) "
            + "   and (:person is null or :person = '' or lower(t.person) = lower(:person)) "
            + "   and (:from is null or t.workDate >= :from) "
            + "   and (:to is null or t.workDate <= :to)")
    Page<TimeEntry> search(@Param("q") String q,
                           @Param("status") TimeEntryStatus status,
                           @Param("projectId") Long projectId,
                           @Param("person") String person,
                           @Param("from") LocalDate from,
                           @Param("to") LocalDate to,
                           Pageable pageable);

    List<TimeEntry> findByProjectIdOrderByWorkDateDescIdDesc(Long projectId);

    long countByProjectId(Long projectId);

    @Query("select distinct t.person from TimeEntry t order by t.person asc")
    List<String> distinctPeople();

    /**
     * Approved, billable, not yet invoiced, and on a job that bills by the hour for a customer.
     * The filter lives here rather than in the service so the same hours cannot be picked up twice
     * through some other path.
     */
    @Query("select t from TimeEntry t "
            + "where t.status = com.ntaganira.heritier.ibook.enums.TimeEntryStatus.APPROVED "
            + "and t.billable = true and t.customerId is not null "
            + "and t.projectId in (select p.id from Project p "
            + "     where p.billingType = com.ntaganira.heritier.ibook.enums.ProjectBillingType.TIME_AND_MATERIALS "
            + "     and p.status <> com.ntaganira.heritier.ibook.enums.ProjectStatus.CANCELLED) "
            + "and (:customerId is null or t.customerId = :customerId) "
            + "and (:projectId is null or t.projectId = :projectId) "
            + "and (:from is null or t.workDate >= :from) "
            + "and (:to is null or t.workDate <= :to) "
            + "order by t.customerName asc, t.projectCode asc, t.workDate asc, t.id asc")
    List<TimeEntry> readyToBill(@Param("customerId") Long customerId,
                                @Param("projectId") Long projectId,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    @Query("select coalesce(sum(t.hours), 0) from TimeEntry t where t.projectId = :projectId "
            + "and t.status <> com.ntaganira.heritier.ibook.enums.TimeEntryStatus.REJECTED")
    BigDecimal hoursOn(@Param("projectId") Long projectId);

    @Query("select coalesce(sum(t.costAmount), 0) from TimeEntry t where t.projectId = :projectId "
            + "and t.status <> com.ntaganira.heritier.ibook.enums.TimeEntryStatus.REJECTED")
    BigDecimal costOn(@Param("projectId") Long projectId);

    @Query("select coalesce(sum(t.billableAmount), 0) from TimeEntry t where t.projectId = :projectId "
            + "and t.status = com.ntaganira.heritier.ibook.enums.TimeEntryStatus.INVOICED")
    BigDecimal invoicedOn(@Param("projectId") Long projectId);

    @Query("select coalesce(sum(t.billableAmount), 0) from TimeEntry t where t.projectId = :projectId "
            + "and t.status = com.ntaganira.heritier.ibook.enums.TimeEntryStatus.APPROVED "
            + "and t.billable = true")
    BigDecimal awaitingBillingOn(@Param("projectId") Long projectId);

    long countByStatus(TimeEntryStatus status);

    @Query("select coalesce(sum(t.hours), 0) from TimeEntry t "
            + "where t.status = :status")
    BigDecimal hoursByStatus(@Param("status") TimeEntryStatus status);
}
