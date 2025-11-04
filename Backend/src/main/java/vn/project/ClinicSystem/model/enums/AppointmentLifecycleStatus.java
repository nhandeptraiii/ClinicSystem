package vn.project.ClinicSystem.model.enums;

/**
 * Enum chung cho lifecycle của Appointment và AppointmentRequest
 * 
 * Flow:
 * Request: PENDING → CONFIRMED (tạo Appointment) → CANCELLED (từ chối)
 * Appointment: CONFIRMED → CHECKED_IN → COMPLETED → CANCELLED (hủy)
 */
public enum AppointmentLifecycleStatus {
    // Trạng thái cho AppointmentRequest
    PENDING, // Yêu cầu chờ duyệt

    // Trạng thái chung
    CONFIRMED, // Đã xác nhận (Request đã duyệt, Appointment đã xác nhận)

    // Trạng thái cho Appointment sau khi đã tạo
    CHECKED_IN, // Bệnh nhân đã đến (đã tạo Visit)
    COMPLETED, // Khám xong

    // Trạng thái chung cho cả 2 (thay thế REJECTED và CANCELLED cũ)
    CANCELLED; // Hủy/Từ chối (Request bị từ chối HOẶC Appointment bị hủy)

    /**
     * Kiểm tra status này có hợp lệ cho AppointmentRequest không
     */
    public boolean isValidForRequest() {
        return this == PENDING || this == CONFIRMED || this == CANCELLED;
    }

    /**
     * Kiểm tra status này có hợp lệ cho Appointment không
     */
    public boolean isValidForAppointment() {
        return this == CONFIRMED || this == CHECKED_IN ||
                this == COMPLETED || this == CANCELLED;
    }
}
