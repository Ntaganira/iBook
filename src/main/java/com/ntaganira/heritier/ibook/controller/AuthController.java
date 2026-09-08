package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.ForgotPasswordForm;
import com.ntaganira.heritier.ibook.dto.RegisterForm;
import com.ntaganira.heritier.ibook.dto.ResetPasswordForm;
import com.ntaganira.heritier.ibook.service.AuthService;
import com.ntaganira.heritier.ibook.service.MailService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    private final AuthService authService;
    private final MailService mailService;

    public AuthController(AuthService authService, MailService mailService) {
        this.authService = authService;
        this.mailService = mailService;
    }

    @GetMapping("/login")
    public String login(@RequestParam(value = "redirect", required = false) String redirect, Model model) {
        if (redirect != null && !redirect.isBlank()) {
            model.addAttribute("redirect", redirect);
        }
        return "auth/login";
    }

    @GetMapping("/register")
    public String registerForm(Model model) {
        model.addAttribute("registerForm", new RegisterForm("", "", "", "", "", ""));
        return "auth/register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerForm") RegisterForm form,
                           BindingResult bindingResult, Model model) {
        if (!form.passwordsMatch()) {
            bindingResult.reject("register.passwordMismatch");
        }
        if (!bindingResult.hasErrors() && authService.existsByEmail(form.email())) {
            bindingResult.reject("register.emailExists");
        }
        if (bindingResult.hasErrors()) {
            return "auth/register";
        }
        authService.register(form);
        return "redirect:/login?registered";
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordForm(Model model) {
        model.addAttribute("forgotPasswordForm", new ForgotPasswordForm(""));
        return "auth/forgot-password";
    }

    @PostMapping("/forgot-password")
    public String forgotPassword(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
                                 BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            return "auth/forgot-password";
        }
        String token = authService.createPasswordResetToken(form.email());
        if (token != null) {
            mailService.sendPasswordResetEmail(form.email(), token);
        }
        redirectAttributes.addFlashAttribute("forgotSent", true);
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/reset-password")
    public String resetPasswordForm(@RequestParam("token") String token, Model model) {
        if (!authService.isResetTokenValid(token)) {
            return "redirect:/forgot-password?invalid";
        }
        model.addAttribute("resetPasswordForm", new ResetPasswordForm("", ""));
        model.addAttribute("token", token);
        return "auth/reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@RequestParam("token") String token,
                                @Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
                                BindingResult bindingResult, RedirectAttributes redirectAttributes) {
        if (!authService.isResetTokenValid(token)) {
            return "redirect:/forgot-password?invalid";
        }
        if (!form.passwordsMatch()) {
            bindingResult.reject("reset.passwordMismatch");
        }
        if (bindingResult.hasErrors()) {
            return "auth/reset-password";
        }
        authService.resetPassword(token, form.password());
        redirectAttributes.addFlashAttribute("resetDone", true);
        return "redirect:/login?reset";
    }
}