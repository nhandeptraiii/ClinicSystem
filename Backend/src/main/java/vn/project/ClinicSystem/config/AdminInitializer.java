package vn.project.ClinicSystem.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import vn.project.ClinicSystem.model.Role;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.repository.UserRepository;
import vn.project.ClinicSystem.service.RoleService;

@Component
public class AdminInitializer implements CommandLineRunner {
    private final UserRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    public AdminInitializer(UserRepository userRepository,
            RoleService roleService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Role adminRole = roleService.createRole("ADMIN", "ToĂ n quyá»n há»‡ thá»‘ng");
        userRepository.findByEmail("admin@clinicsystem.com").orElseGet(() -> {
            User admin = new User();
            admin.setFullName("System Administrator");
            admin.setEmail("admin@clinicsystem.com");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.setStatus("ACTIVE");
            if (adminRole != null) {
                admin.getRoles().add(adminRole);
            }

            return userRepository.save(admin);
        });
    }
}
