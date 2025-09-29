package vn.project.ClinicSystem.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import vn.project.ClinicSystem.model.dto.ResLoginDTO;
import vn.project.ClinicSystem.model.dto.LoginDTO;
import vn.project.ClinicSystem.util.SecurityUtil;

@RestController
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityUtil securityUtil;

    public AuthController(AuthenticationManager authenticationManager, SecurityUtil securityUtil) {
        this.authenticationManager = authenticationManager;
        this.securityUtil = securityUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<ResLoginDTO> login(@Valid @RequestBody LoginDTO loginDTO) {
        // Nạp input gồm username/password vào Security
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                loginDTO.getUsername(), loginDTO.getPassword());

        // xác thực người dùng => cần viết hàm loadUserByUsername
        Authentication authentication = authenticationManager.authenticate(authenticationToken);

        String access_token = this.securityUtil.createToken(authentication);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        ResLoginDTO res = new ResLoginDTO();
        res.setAccessToken(access_token);

        return ResponseEntity.ok().body(res);
    }
}
