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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    @Query("select l from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED " +
            "and l.accountId = :accountId order by l.entry.entryDate asc, l.entry.id asc, l.sortOrder asc")
    List<JournalLine> postedByAccount(@Param("accountId") Long accountId);

    @Query("select l.accountId, sum(l.debit), sum(l.credit) from JournalLine l " +
            "where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED group by l.accountId")
    List<Object[]> postedTotalsByAccount();

    /**
     * Posted movement on one account up to a date, oldest first. Bank reconciliation reads this:
     * everything the books say went through the account by the statement date, which is the side
     * the statement has to be matched against.
     */
    @Query("select l from JournalLine l "
            + "where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED "
            + "and l.accountId = :accountId and l.entry.entryDate <= :asOf "
            + "order by l.entry.entryDate asc, l.entry.id asc, l.sortOrder asc")
    List<JournalLine> postedByAccountUpTo(@Param("accountId") Long accountId,
                                          @Param("asOf") LocalDate asOf);

    @Query("select l from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED " +
            "order by l.entry.entryDate asc, l.entry.id asc, l.sortOrder asc")
    List<JournalLine> postedLines();

    @Query("select l.accountId, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) from JournalLine l " +
            "where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED " +
            "and l.entry.entryDate >= :from and l.entry.entryDate <= :to group by l.accountId")
    List<Object[]> postedTotalsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select l.accountId, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) from JournalLine l " +
            "where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED " +
            "and l.entry.entryDate <= :asOf group by l.accountId")
    List<Object[]> postedTotalsUpTo(@Param("asOf") LocalDate asOf);

    @Query("select l from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED " +
            "and l.entry.entryDate >= :from and l.entry.entryDate <= :to " +
            "order by l.accountId asc, l.entry.entryDate asc, l.entry.id asc, l.sortOrder asc")
    List<JournalLine> postedLinesBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    // ----- Job costing -----

    @Query("select l from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED "
            + "and l.entry.entryDate >= :from and l.entry.entryDate <= :to "
            + "and l.accountId in :tradingAccountIds "
            + "and (:accountId is null or l.accountId = :accountId) "
            + "and (:projectId is null or l.projectId = :projectId)")
    Page<JournalLine> costingLines(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                   @Param("tradingAccountIds") Collection<Long> tradingAccountIds,
                                   @Param("accountId") Long accountId,
                                   @Param("projectId") Long projectId, Pageable pageable);

    @Query("select l from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED "
            + "and l.entry.entryDate >= :from and l.entry.entryDate <= :to "
            + "and l.accountId in :tradingAccountIds "
            + "and (:accountId is null or l.accountId = :accountId) "
            + "and l.projectId is null")
    Page<JournalLine> untaggedLines(@Param("from") LocalDate from, @Param("to") LocalDate to,
                                    @Param("tradingAccountIds") Collection<Long> tradingAccountIds,
                                    @Param("accountId") Long accountId, Pageable pageable);

    @Query("select l.projectId, l.accountId, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) "
            + "from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED "
            + "and l.projectId is not null "
            + "and l.entry.entryDate >= :from and l.entry.entryDate <= :to "
            + "group by l.projectId, l.accountId")
    List<Object[]> taggedTotalsBetween(@Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select l.accountId, coalesce(sum(l.debit), 0), coalesce(sum(l.credit), 0) "
            + "from JournalLine l where l.entry.status = com.ntaganira.heritier.ibook.enums.JournalEntryStatus.POSTED "
            + "and l.projectId = :projectId "
            + "and l.entry.entryDate >= :from and l.entry.entryDate <= :to "
            + "group by l.accountId")
    List<Object[]> taggedTotalsForProject(@Param("projectId") Long projectId,
                                          @Param("from") LocalDate from, @Param("to") LocalDate to);

    long countByProjectId(Long projectId);
}