/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : RecurringJournalRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for recurring journal schedules
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.RecurringJournal;
import com.ntaganira.heritier.ibook.enums.RecurringJournalStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface RecurringJournalRepository extends JpaRepository<RecurringJournal, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByStatus(RecurringJournalStatus status);

    @Query("select r from RecurringJournal r where (:q is null or :q = '' "
            + "   or lower(r.name) like lower(concat('%', :q, '%')) "
            + "   or lower(r.description) like lower(concat('%', :q, '%')) "
            + "   or lower(r.reference) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or r.status = :status)")
    Page<RecurringJournal> search(@Param("q") String q,
                                  @Param("status") RecurringJournalStatus status,
                                  Pageable pageable);

    @Query("select r from RecurringJournal r "
            + "where r.status = com.ntaganira.heritier.ibook.enums.RecurringJournalStatus.ACTIVE "
            + "and r.nextRunDate is not null and r.nextRunDate <= :today "
            + "order by r.nextRunDate asc, r.id asc")
    List<RecurringJournal> findDue(@Param("today") LocalDate today);
}
