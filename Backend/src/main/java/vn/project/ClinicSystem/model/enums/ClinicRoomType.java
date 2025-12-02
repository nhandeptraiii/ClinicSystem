package vn.project.ClinicSystem.model.enums;

/**
 * Phân loại phòng để ràng buộc vai trò (ví dụ bác sĩ chỉ vào phòng khám/dịch vụ).
 */
public enum ClinicRoomType {
    CLINIC,          // Phòng khám chuyên khoa
    SERVICE,         // Phòng thực hiện dịch vụ/xét nghiệm/cận lâm sàng
    PHARMACY,        // Quầy thuốc
    RECEPTION,       // Tiếp tân / thu ngân
    TECHNICAL        // Kỹ thuật / thiết bị / phòng phụ trợ
}
