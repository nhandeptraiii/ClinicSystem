package vn.project.ClinicSystem.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import vn.project.ClinicSystem.model.User;

public interface UserRepository extends JpaRepository<User, Long> {
    User findByEmail(String email);

}
