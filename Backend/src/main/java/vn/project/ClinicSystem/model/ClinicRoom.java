package vn.project.ClinicSystem.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import vn.project.ClinicSystem.model.enums.ClinicRoomType;

@Getter
@Setter
@Entity
@Table(name = "clinic_rooms")
public class ClinicRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Mã phòng không được để trống")
    @Column(nullable = false, length = 80, unique = true)
    private String code;

    @NotBlank(message = "Tên phòng không được để trống")
    @Column(length = 120)
    private String name;

    @Column(length = 60)
    private String floor;

    @Column(length = 255)
    private String note;

    @Min(value = 1, message = "Sức chứa phải từ 1 trở lên")
    @Column(nullable = false)
    private Integer capacity = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", length = 30, nullable = false)
    private ClinicRoomType type = ClinicRoomType.CLINIC;

    private Instant createdAt;

    private Instant updatedAt;

    @PrePersist
    public void handleBeforeCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.type == null) {
            this.type = ClinicRoomType.CLINIC;
        }
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        this.updatedAt = Instant.now();
    }
}
