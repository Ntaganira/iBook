/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : JournalEntryRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for journal entries
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.JournalEntry;
import com.ntaganira.heritier.ibook.enums.JournalEntryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    Optional<JournalEntry> findByEntryNo(String entryNo);

    @Query("select j from JournalEntry j where (:q is null or :q = '' " +
            "   or lower(j.entryNo) like lower(concat('%', :q, '%')) " +
            "   or lower(j.reference) like lower(concat('%', :q, '%')) " +
            "   or lower(j.memo) like lower(concat('%', :q, '%'))) " +
            "   and (:status is null or j.status = :status)")
    Page<JournalEntry> search(@Param("q") String q, @Param("status") JournalEntryStatus status, Pageable pageable);

    long countByStatus(JournalEntryStatus status);
    /** Every entry a recurring schedule raised, found by the reference it stamps on them. */
    List<JournalEntry> findByReferenceOrderByEntryDateDescIdDesc(String reference);
}
