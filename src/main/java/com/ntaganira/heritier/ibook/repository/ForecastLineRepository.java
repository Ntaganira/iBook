/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ForecastLineRepository.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for forecast lines
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.ForecastLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ForecastLineRepository extends JpaRepository<ForecastLine, Long> {

    List<ForecastLine> findByForecastIdOrderBySortOrderAscIdAsc(Long forecastId);
}
