/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : JournalLineRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for journal lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.JournalLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    @Query("select l from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED " +
            "and l.accountId = :accountId order by l.entry.entryDate asc, l.entry.id asc, l.sortOrder asc")
    List<JournalLine> postedByAccount(@Param("accountId") Long accountId);

    @Query("select l.accountId, sum(l.debit), sum(l.credit) from JournalLine l " +
            "where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED group by l.accountId")
    List<Object[]> postedTotalsByAccount();

    @Query("select l from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED " +
            "order by l.entry.entryDate asc, l.entry.id asc, l.sortOrder asc")
    List<JournalLine> postedLines();
}