package vn.project.ClinicSystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "diseases")
public class Disease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Mã bệnh không được để trống")
    @Size(max = 50, message = "Mã bệnh không được vượt quá 50 ký tự")
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @NotBlank(message = "Tên bệnh không được để trống")
    @Size(max = 255, message = "Tên bệnh không được vượt quá 255 ký tự")
    @Column(nullable = false, length = 255)
    private String name;

    @Size(max = 2000, message = "Mô tả không được vượt quá 2000 ký tự")
    @Column(length = 2000)
    private String description;
}
