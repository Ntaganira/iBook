/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.repository
 * - File      : UserRepository.java
 * - Date      : 2026. 09. 09.
 * - User      : Hntaganira
 * - Desc      : Spring Data repository for users
 * </pre>
 */
package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameOrEmail(String username, String email);

    Optional<User> findByPasswordResetToken(String token);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    @Query("select distinct u from User u left join u.roles r " +
            "where (:q is null or :q = '' " +
            "   or lower(u.firstName) like lower(concat('%', :q, '%')) " +
            "   or lower(u.lastName) like lower(concat('%', :q, '%')) " +
            "   or lower(u.email) like lower(concat('%', :q, '%')) " +
            "   or lower(u.username) like lower(concat('%', :q, '%'))) " +
            "   and (:roleId is null or :roleId = 0L or r.id = :roleId)")
    Page<User> search(@Param("q") String q, @Param("roleId") Long roleId, Pageable pageable);
}