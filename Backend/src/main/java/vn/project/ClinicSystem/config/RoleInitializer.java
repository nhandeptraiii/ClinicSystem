package vn.project.ClinicSystem.config;

import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import vn.project.ClinicSystem.model.enums.StaffRole;
import vn.project.ClinicSystem.service.RoleService;

@Configuration
public class RoleInitializer {

    @Bean
    public ApplicationRunner staffRolesInitializer(RoleService roleService) {
        return args -> {
            for (StaffRole role : StaffRole.values()) {
                roleService.ensureRole(role.getName(), role.getDescription());
            }
        };
    }
}
