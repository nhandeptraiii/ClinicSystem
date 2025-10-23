package vn.project.ClinicSystem.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    long countDistinctByRoles_NameIgnoreCase(String roleName);

    @Query(value = """
            SELECT DISTINCT u FROM User u
            LEFT JOIN u.roles r
            WHERE (:roleName IS NULL OR UPPER(r.name) = :roleName)
              AND (
                :keyword IS NULL
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR u.phone LIKE CONCAT('%', :keyword, '%')
              )
            """,
            countQuery = """
            SELECT COUNT(DISTINCT u) FROM User u
            LEFT JOIN u.roles r
            WHERE (:roleName IS NULL OR UPPER(r.name) = :roleName)
              AND (
                :keyword IS NULL
                OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(u.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR u.phone LIKE CONCAT('%', :keyword, '%')
              )
            """)
    Page<User> searchStaff(@Param("roleName") String roleName, @Param("keyword") String keyword, Pageable pageable);
}
