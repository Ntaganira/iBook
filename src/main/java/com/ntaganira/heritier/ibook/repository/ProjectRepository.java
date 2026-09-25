/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : ProjectRepository.java
 * - Date      : 2026. 09. 25.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for projects
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Project;
import com.ntaganira.heritier.ibook.enums.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    long countByStatus(ProjectStatus status);

    List<Project> findAllByOrderByCodeAsc();

    @Query("select p from Project p where (:q is null or :q = '' "
            + "   or lower(p.code) like lower(concat('%', :q, '%')) "
            + "   or lower(p.name) like lower(concat('%', :q, '%')) "
            + "   or lower(p.customerName) like lower(concat('%', :q, '%')) "
            + "   or lower(p.manager) like lower(concat('%', :q, '%'))) "
            + "   and (:status is null or p.status = :status) "
            + "   and (:customerId is null or p.customerId = :customerId)")
    Page<Project> search(@Param("q") String q,
                         @Param("status") ProjectStatus status,
                         @Param("customerId") Long customerId,
                         Pageable pageable);

    /**
     * What the timesheet form may book against. A completed or cancelled job is left out, plus
     * whatever the entry being edited already carries, so an old entry keeps its project.
     */
    @Query("select p from Project p where p.status in ("
            + "com.ntaganira.heritier.ibook.enums.ProjectStatus.DRAFT, "
            + "com.ntaganira.heritier.ibook.enums.ProjectStatus.ACTIVE, "
            + "com.ntaganira.heritier.ibook.enums.ProjectStatus.ON_HOLD) "
            + "order by p.code asc")
    List<Project> openForTime();

}
