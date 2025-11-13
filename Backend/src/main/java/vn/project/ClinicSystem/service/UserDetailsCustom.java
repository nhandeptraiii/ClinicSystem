package vn.project.ClinicSystem.service;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@Component("userDetailService")
public class UserDetailsCustom implements UserDetailsService {
    final UserService userService;

    public UserDetailsCustom(UserService userService) {
        this.userService = userService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        vn.project.ClinicSystem.model.User user = userService.handleGetUserByUsername(username);
        if (user == null) {
            throw new UsernameNotFoundException("User/ password không hợp lệ");
        }
        String normalizedStatus = user.getStatus() == null ? "ACTIVE" : user.getStatus().trim().toUpperCase();
        if ("INACTIVE".equals(normalizedStatus)) {
            throw new DisabledException("Tài khoản đang tạm ngưng. Vui lòng liên hệ quản trị viên.");
        }
        if ("BANNED".equals(normalizedStatus)) {
            throw new DisabledException("Tài khoản đã bị khóa. Vui lòng liên hệ quản trị viên.");
        }
        Set<SimpleGrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()))
                .collect(Collectors.toSet());
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        }
        return new User(user.getEmail(), user.getPassword(), authorities);

    }
}
