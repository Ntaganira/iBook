/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ForecastRepository.java
 * - Date      : 2026. 09. 20.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for revenue and expense forecasts
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Forecast;
import com.ntaganira.heritier.ibook.enums.ForecastKind;
import com.ntaganira.heritier.ibook.enums.ForecastStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ForecastRepository extends JpaRepository<Forecast, Long> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    List<Forecast> findByKindOrderByStartDateDescIdDesc(ForecastKind kind);

    long countByKindAndStatus(ForecastKind kind, ForecastStatus status);

    /**
     * The forecast the cash flow reads by default. Only one of each kind can be published at a
     * time, so a cash flow is never quietly built on a figure nobody agreed was the current one.
     */
    Optional<Forecast> findFirstByKindAndStatusOrderByStartDateDescIdDesc(ForecastKind kind,
                                                                         ForecastStatus status);

    @Query("select f from Forecast f where f.kind = :kind and f.status = :status and f.id <> :id")
    List<Forecast> othersWithStatus(@Param("kind") ForecastKind kind,
                                    @Param("status") ForecastStatus status,
                                    @Param("id") Long id);
}
