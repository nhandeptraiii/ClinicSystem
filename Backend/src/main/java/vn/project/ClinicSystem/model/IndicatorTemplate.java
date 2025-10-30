package vn.project.ClinicSystem.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "indicator_templates")
public class IndicatorTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Mã chỉ số không được để trống")
    @Size(max = 60, message = "Mã chỉ số tối đa 60 ký tự")
    @Column(unique = true, nullable = false, length = 60)
    private String code;

    @NotBlank(message = "Tên chỉ số không được để trống")
    @Size(max = 150, message = "Tên chỉ số tối đa 150 ký tự")
    @Column(nullable = false, length = 150)
    private String name;

    @Size(max = 30, message = "Đơn vị tối đa 30 ký tự")
    @Column(length = 30)
    private String unit;

    @Digits(integer = 12, fraction = 4, message = "Giá trị chuẩn không hợp lệ")
    @Column(precision = 12, scale = 4)
    private BigDecimal normalMin;

    @Digits(integer = 12, fraction = 4, message = "Giá trị chuẩn không hợp lệ")
    @Column(precision = 12, scale = 4)
    private BigDecimal normalMax;

    @Digits(integer = 12, fraction = 4, message = "Giá trị cảnh báo không hợp lệ")
    @Column(precision = 12, scale = 4)
    private BigDecimal criticalMin;

    @Digits(integer = 12, fraction = 4, message = "Giá trị cảnh báo không hợp lệ")
    @Column(precision = 12, scale = 4)
    private BigDecimal criticalMax;

    @Size(max = 500, message = "Ghi chú tối đa 500 ký tự")
    @Column(length = 500)
    private String referenceNote;

    @Size(max = 50, message = "Danh mục tối đa 50 ký tự")
    @Column(length = 50)
    private String category;

    @JsonIgnore
    @OneToMany(mappedBy = "indicatorTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ServiceIndicatorMapping> serviceMappings = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
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
