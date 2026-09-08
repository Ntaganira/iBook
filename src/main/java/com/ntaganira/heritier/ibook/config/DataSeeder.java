package com.ntaganira.heritier.ibook.config;

import com.ntaganira.heritier.ibook.entity.Role;
import com.ntaganira.heritier.ibook.entity.User;
import com.ntaganira.heritier.ibook.repository.RoleRepository;
import com.ntaganira.heritier.ibook.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataSeeder {

    @Bean
    CommandLineRunner seedRolesAndAdmin(RoleRepository roleRepository,
                                        UserRepository userRepository,
                                        PasswordEncoder passwordEncoder) {
        return args -> {
            Role adminRole = roleRepository.findByName("ADMIN").orElseGet(() ->
                    roleRepository.save(Role.builder().name("ADMIN")
                            .description("Administrator").build()));
            roleRepository.findByName("USER").orElseGet(() ->
                    roleRepository.save(Role.builder().name("USER")
                            .description("Standard user").build()));

            if (!userRepository.existsByEmail("admin@ebookonline.rw")) {
                User admin = User.builder()
                        .firstName("Heritier")
                        .lastName("NTAGANIRA")
                        .email("admin@ebookonline.rw")
                        .username("admin@ebookonline.rw")
                        .password(passwordEncoder.encode("Admin#2026"))
                        .phone("+250788533669")
                        .enabled(true)
                        .emailVerified(true)
                        .roles(new java.util.HashSet<>(java.util.Set.of(adminRole)))
                        .build();
                userRepository.save(admin);
            }
        };
    }
}