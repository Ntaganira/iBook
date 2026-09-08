package com.ntaganira.heritier.ibook.service;

import com.ntaganira.heritier.ibook.dto.RegisterForm;
import com.ntaganira.heritier.ibook.entity.Role;
import com.ntaganira.heritier.ibook.entity.User;
import com.ntaganira.heritier.ibook.repository.RoleRepository;
import com.ntaganira.heritier.ibook.repository.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       @Lazy PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User register(RegisterForm form) {
        Role userRole = roleRepository.findByName("USER")
                .orElseGet(() -> roleRepository.save(Role.builder().name("USER")
                        .description("Standard user").build()));

        User user = User.builder()
                .firstName(form.firstName().trim())
                .lastName(form.lastName().trim())
                .email(form.email().trim().toLowerCase())
                .username(form.email().trim().toLowerCase())
                .password(passwordEncoder.encode(form.password()))
                .phone(form.phone() == null ? null : form.phone().trim())
                .enabled(true)
                .emailVerified(true)
                .roles(new java.util.HashSet<>(java.util.Set.of(userRole)))
                .build();
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email.trim().toLowerCase());
    }

    @Transactional
    public String createPasswordResetToken(String email) {
        User user = userRepository.findByEmail(email.trim().toLowerCase()).orElse(null);
        if (user == null) {
            return null;
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        user.setPasswordResetToken(token);
        user.setPasswordResetExpiry(LocalDateTime.now().plusHours(24));
        userRepository.save(user);
        return token;
    }

    @Transactional(readOnly = true)
    public boolean isResetTokenValid(String token) {
        User user = userRepository.findByPasswordResetToken(token).orElse(null);
        if (user == null) {
            return false;
        }
        LocalDateTime expiry = user.getPasswordResetExpiry();
        return expiry != null && expiry.isAfter(LocalDateTime.now());
    }

    @Transactional
    public boolean resetPassword(String token, String newPassword) {
        User user = userRepository.findByPasswordResetToken(token).orElse(null);
        if (user == null) {
            return false;
        }
        LocalDateTime expiry = user.getPasswordResetExpiry();
        if (expiry == null || !expiry.isAfter(LocalDateTime.now())) {
            return false;
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpiry(null);
        userRepository.save(user);
        return true;
    }

    @Transactional
    public void touchLastLogin(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}