package vn.project.ClinicSystem.model;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

/**
 * Bảng trung gian (junction table) liên kết MedicalService và
 * IndicatorTemplate.
 * Chỉ lưu metadata (required, displayOrder), dữ liệu chỉ số thực tế nằm trong
 * IndicatorTemplate.
 */
@Getter
@Setter
@Entity
@Table(name = "service_indicator_mappings", uniqueConstraints = @UniqueConstraint(name = "unique_service_indicator", columnNames = {
        "medical_service_id", "indicator_template_id" }))
public class ServiceIndicatorMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "medical_service_id", nullable = false)
    private MedicalService medicalService;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "indicator_template_id", nullable = false)
    private IndicatorTemplate indicatorTemplate;

    @Column(nullable = false)
    private Boolean required = true;

    @Column(nullable = false)
    private Integer displayOrder = 0;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void handleBeforeCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.required == null) {
            this.required = true;
        }
        if (this.displayOrder == null) {
            this.displayOrder = 0;
        }
    }

    @PreUpdate
    public void handleBeforeUpdate() {
        this.updatedAt = Instant.now();
    }
}
