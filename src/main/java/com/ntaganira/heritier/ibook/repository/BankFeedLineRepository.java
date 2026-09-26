/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : BankFeedLineRepository.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for imported statement lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.BankFeedLine;
import com.ntaganira.heritier.ibook.enums.FeedLineStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BankFeedLineRepository extends JpaRepository<BankFeedLine, Long> {

    List<BankFeedLine> findByFeedImportIdOrderByLineDateAscIdAsc(Long importId);

    long countByStatus(FeedLineStatus status);

    @Query("select l from BankFeedLine l "
            + "where l.feedImport.status = com.ntaganira.heritier.ibook.enums.FeedImportStatus.IMPORTED "
            + "and (:q is null or :q = '' "
            + "   or lower(l.description) like lower(concat('%', :q, '%')) "
            + "   or lower(l.reference) like lower(concat('%', :q, '%'))) "
            + "and (:accountId is null or l.accountId = :accountId) "
            + "and (:status is null or l.status = :status)")
    Page<BankFeedLine> search(@Param("q") String q,
                              @Param("accountId") Long accountId,
                              @Param("status") FeedLineStatus status,
                              Pageable pageable);

    @Query("select count(l) from BankFeedLine l "
            + "where l.feedImport.status = com.ntaganira.heritier.ibook.enums.FeedImportStatus.IMPORTED "
            + "and l.status = com.ntaganira.heritier.ibook.enums.FeedLineStatus.UNCATEGORISED")
    long countWaiting();

    @Query("select coalesce(sum(l.moneyIn - l.moneyOut), 0) from BankFeedLine l "
            + "where l.feedImport.status = com.ntaganira.heritier.ibook.enums.FeedImportStatus.IMPORTED "
            + "and l.status = com.ntaganira.heritier.ibook.enums.FeedLineStatus.UNCATEGORISED")
    BigDecimal waitingNet();
}
