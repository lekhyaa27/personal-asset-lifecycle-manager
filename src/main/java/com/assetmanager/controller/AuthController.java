package com.assetmanager.controller;

import com.assetmanager.model.RegisterForm;
import com.assetmanager.model.User;
import com.assetmanager.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Optional;

@Controller
public class AuthController {
    private final UserRepository users;
    private final PasswordEncoder encoder;
    public AuthController(UserRepository users, PasswordEncoder encoder) { this.users = users; this.encoder = encoder; }

    @GetMapping("/")
    public String home(HttpSession s) { return s.getAttribute("userId") != null ? "redirect:/dashboard" : "redirect:/login"; }

    @GetMapping("/login")
    public String loginPage() { return "login"; }

    @PostMapping("/login")
    public String login(@RequestParam String email, @RequestParam String password,
                        HttpServletRequest request, RedirectAttributes ra) {
        Optional<User> u = users.findByEmail(email.trim().toLowerCase());
        if (u.isPresent() && encoder.matches(password, u.get().getPasswordHash())) {
            HttpSession session = request.getSession();
            request.changeSessionId();                       // prevents session-fixation attacks
            session.setAttribute("userId", u.get().getId());
            session.setAttribute("userName", u.get().getFullName());
            return "redirect:/dashboard";
        }
        ra.addFlashAttribute("error", "Invalid email or password");
        return "redirect:/login";
    }

    @GetMapping("/register")
    public String registerPage(org.springframework.ui.Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("form") RegisterForm form, BindingResult br, RedirectAttributes ra) {
        if (!br.hasFieldErrors("password") && !form.getPassword().equals(form.getConfirmPassword()))
            br.rejectValue("confirmPassword", "mismatch", "Passwords do not match");
        if (!br.hasFieldErrors("email") && users.existsByEmail(form.getEmail().trim().toLowerCase()))
            br.rejectValue("email", "dup", "This email is already registered");
        if (br.hasErrors()) return "register";
        users.save(new User(form.getFullName().trim(), form.getEmail().trim().toLowerCase(), encoder.encode(form.getPassword())));
        ra.addFlashAttribute("msg", "Registration successful. Please log in.");
        return "redirect:/login";
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) { session.invalidate(); return "redirect:/login"; }
}
