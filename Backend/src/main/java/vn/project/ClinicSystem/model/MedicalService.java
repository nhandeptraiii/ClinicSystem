package vn.project.ClinicSystem.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import vn.project.ClinicSystem.model.enums.ServiceType;

@Getter
@Setter
@Entity
@Table(name = "medical_services")
public class MedicalService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Mã dịch vụ không được để trống")
    @Column(nullable = false, length = 60, unique = true)
    private String code;

    @NotBlank(message = "Tên dịch vụ không được để trống")
    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Long basePrice; // đơn vị: đồng

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ServiceType type;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_room_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private ClinicRoom clinicRoom;

    @JsonIgnore
    @OneToMany(mappedBy = "medicalService", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceIndicatorMapping> indicatorMappings = new ArrayList<>();

    private Instant createdAt;
    private Instant updatedAt;

    @PrePersist
    public void handleBeforeCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        this.updatedAt = Instant.now();
    }
}
