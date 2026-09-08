package com.ntaganira.heritier.ibook.repository;

import com.ntaganira.heritier.ibook.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByName(String name);
}