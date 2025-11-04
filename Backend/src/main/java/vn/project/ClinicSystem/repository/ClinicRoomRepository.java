package vn.project.ClinicSystem.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import vn.project.ClinicSystem.model.ClinicRoom;

@Repository
public interface ClinicRoomRepository extends JpaRepository<ClinicRoom, Long> {
  Optional<ClinicRoom> findByCode(String code);

  boolean existsByCodeIgnoreCase(String code);

  List<ClinicRoom> findByFloorIgnoreCase(String floor);

  @Query("""
      SELECT cr FROM ClinicRoom cr
      WHERE (:keyword IS NULL OR LOWER(cr.name) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(cr.code) LIKE LOWER(CONCAT('%', :keyword, '%')))
        AND (:floor IS NULL OR LOWER(cr.floor) = LOWER(:floor))
      """)
  Page<ClinicRoom> search(@Param("keyword") String keyword, @Param("floor") String floor, Pageable pageable);

  // Lấy các phòng khám có code bắt đầu bằng prefix
  List<ClinicRoom> findByCodeStartingWithIgnoreCase(String prefix);
}
