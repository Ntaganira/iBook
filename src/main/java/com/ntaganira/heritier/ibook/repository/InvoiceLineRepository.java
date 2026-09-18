/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : InvoiceLineRepository.java
 * - Date      : 2026. 09. 17.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for invoice line items
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.InvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoiceIdOrderBySortOrderAsc(Long invoiceId);

    @Query("select l.revenueAccountId, sum(l.lineSubtotal) from InvoiceLine l "
            + "where l.invoice.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.DRAFT "
            + "and l.invoice.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.VOID "
            + "group by l.revenueAccountId")
    List<Object[]> revenueByAccount();

    @Query("select l.description, sum(l.quantity), sum(l.lineTotal) from InvoiceLine l "
            + "where l.invoice.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.DRAFT "
            + "and l.invoice.status <> com.ntaganira.heritier.ibook.enums.DocumentStatus.VOID "
            + "group by l.description order by sum(l.lineTotal) desc")
    List<Object[]> topItems();
}
