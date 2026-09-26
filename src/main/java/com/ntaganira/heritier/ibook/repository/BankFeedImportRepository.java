/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : BankFeedImportRepository.java
 * - Date      : 2026. 09. 26.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for imported bank statements
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.BankFeedImport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BankFeedImportRepository extends JpaRepository<BankFeedImport, Long> {

    @Query("select i from BankFeedImport i where (:q is null or :q = '' "
            + "   or lower(i.reference) like lower(concat('%', :q, '%')) "
            + "   or lower(i.fileName) like lower(concat('%', :q, '%')) "
            + "   or lower(i.accountName) like lower(concat('%', :q, '%'))) "
            + "   and (:accountId is null or i.accountId = :accountId)")
    Page<BankFeedImport> search(@Param("q") String q,
                                @Param("accountId") Long accountId,
                                Pageable pageable);

    /**
     * Imports of the same file on the same account. Used to warn before a month is brought in
     * twice, which would double every figure on it.
     */
    @Query("select i from BankFeedImport i where i.accountId = :accountId and i.checksum = :checksum "
            + "and i.status = com.ntaganira.heritier.ibook.enums.FeedImportStatus.IMPORTED")
    List<BankFeedImport> sameFile(@Param("accountId") Long accountId,
                                  @Param("checksum") String checksum);
}
