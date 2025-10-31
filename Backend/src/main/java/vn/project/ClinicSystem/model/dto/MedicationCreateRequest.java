package vn.project.ClinicSystem.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MedicationCreateRequest {

    @NotBlank(message = "Tên thuốc không được để trống")
    @Size(max = 150, message = "Tên thuốc tối đa 150 ký tự")
    private String name;

    @Size(max = 150, message = "Hoạt chất tối đa 150 ký tự")
    private String activeIngredient;

    @Size(max = 100, message = "Hàm lượng tối đa 100 ký tự")
    private String strength;

    @NotBlank(message = "Mã lô không được để trống")
    @Size(max = 50, message = "Mã lô tối đa 50 ký tự")
    private String batchNo;

    @Size(max = 30, message = "Đơn vị tối đa 30 ký tự")
    private String unit;

    @NotNull(message = "Đơn giá không được bỏ trống")
    private BigDecimal unitPrice;

    @NotBlank(message = "Nhà sản xuất không được để trống")
    @Size(max = 150, message = "Nhà sản xuất tối đa 150 ký tự")
    private String manufacturer;

    @NotNull(message = "Hạn sử dụng không được bỏ trống")
    private LocalDate expiryDate;

    @NotNull(message = "Số lượng không được bỏ trống")
    @PositiveOrZero(message = "Số lượng phải >= 0")
    private Integer stockQuantity = 0;
}
