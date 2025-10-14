package vn.project.ClinicSystem.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.project.ClinicSystem.model.RevokedToken;
import vn.project.ClinicSystem.repository.RevokedTokenRepository;

@Service
public class RevokedTokenService {

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    @Transactional
    public void revoke(String token, Instant expiresAt) {
        if (token == null || expiresAt == null) {
            return;
        }
        if (revokedTokenRepository.existsByToken(token)) {
            return;
        }
        RevokedToken revokedToken = new RevokedToken();
        revokedToken.setToken(token);
        revokedToken.setExpiresAt(expiresAt);
        revokedTokenRepository.save(revokedToken);
    }

    @Transactional(readOnly = true)
    public boolean isRevoked(String token) {
        if (token == null) {
            return false;
        }
        return revokedTokenRepository.existsByToken(token);
    }

    @Transactional
    public void purgeExpired() {
        revokedTokenRepository.deleteByExpiresAtBefore(Instant.now());
    }
}
