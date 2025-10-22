package vn.project.ClinicSystem.model.enums;

import java.util.Locale;

public enum StaffRole {
    ADMIN("ADMIN", "Quản trị viên hệ thống"),
    DOCTOR("DOCTOR", "Bác sĩ khám chữa bệnh"),
    NURSE("NURSE", "Điều dưỡng hỗ trợ chăm sóc"),
    CASHIER("CASHIER", "Thu ngân xử lý thanh toán"),
    PHARMACIST("PHARMACIST", "Dược sĩ quản lý kho thuốc");

    private final String name;
    private final String description;

    StaffRole(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public static StaffRole from(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Tên vai trò không được để trống");
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        for (StaffRole role : values()) {
            if (role.name.equals(normalized)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Vai trò không hợp lệ: " + value);
    }
}
