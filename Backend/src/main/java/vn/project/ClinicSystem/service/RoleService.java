package vn.project.ClinicSystem.service;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import vn.project.ClinicSystem.model.Role;
import vn.project.ClinicSystem.model.User;
import vn.project.ClinicSystem.repository.RoleRepository;
import vn.project.ClinicSystem.repository.UserRepository;

@Service
@Transactional
public class RoleService {
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    public RoleService(RoleRepository roleRepository, UserRepository userRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
    }

    public Role createRole(String rawName, String description) {
        String roleName = rawName.trim().toUpperCase();
        return roleRepository.findByName(roleName)
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName(roleName);
                    role.setDescription(description);
                    return roleRepository.save(role);
                });
    }

    // cap quyen user
    public void assignRoleToUser(Long userId, String rawRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Role role = roleRepository.findByName(rawRole.trim().toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Role not found"));
        if (user.getRoles().add(role)) {
            userRepository.save(user);
        }
    }

    // thu hoi quyen user
    public void revokeRoleFromUser(Long userId, String rawRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        roleRepository.findByName(rawRole.trim().toUpperCase())
                .ifPresent(role -> {
                    if (user.getRoles().remove(role)) {
                        userRepository.save(user);
                    }
                });
    }

}
