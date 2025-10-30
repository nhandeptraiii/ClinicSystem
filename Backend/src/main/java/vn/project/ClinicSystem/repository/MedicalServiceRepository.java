package vn.project.ClinicSystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.MedicalService;

@Repository
public interface MedicalServiceRepository extends JpaRepository<MedicalService, Long> {
    Optional<MedicalService> findByCode(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<MedicalService> findByClinicRoomId(Long clinicRoomId);

    @Query("""
            SELECT ms FROM MedicalService ms
            WHERE (:keyword IS NULL OR LOWER(ms.code) LIKE LOWER(CONCAT('%', :keyword, '%'))
                OR LOWER(ms.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:clinicRoomId IS NULL OR ms.clinicRoom.id = :clinicRoomId)
            """)
    Page<MedicalService> searchByKeywordAndClinicRoom(
            @Param("keyword") String keyword,
            @Param("clinicRoomId") Long clinicRoomId,
            Pageable pageable);
}
