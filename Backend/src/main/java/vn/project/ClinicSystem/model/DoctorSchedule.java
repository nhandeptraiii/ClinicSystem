// package vn.project.ClinicSystem.model;

// import java.time.Instant;
// import java.time.LocalDate;
// import java.time.LocalTime;

// import jakarta.persistence.Column;
// import jakarta.persistence.Entity;
// import jakarta.persistence.GeneratedValue;
// import jakarta.persistence.GenerationType;
// import jakarta.persistence.Id;
// import jakarta.persistence.JoinColumn;
// import jakarta.persistence.ManyToOne;
// import jakarta.persistence.PrePersist;
// import jakarta.persistence.PreUpdate;
// import jakarta.persistence.Table;
// import lombok.Getter;
// import lombok.Setter;

// @Getter
// @Setter
// @Entity
// @Table(name = "doctor_schedules")
// public class DoctorSchedule {
// @Id
// @GeneratedValue(strategy = GenerationType.IDENTITY)
// private Long id;

// @ManyToOne(optional = false)
// @JoinColumn(name = "doctor_id")
// private Doctor doctor;

// @ManyToOne(optional = false)
// @JoinColumn(name = "clinic_room_id")
// private ClinicRoom clinicRoom;

// private LocalDate shiftDate;

// private LocalTime startTime;

// private LocalTime endTime;

// @Column(length = 120)
// private String shiftLabel; // Ví dụ “Ca sáng”, “Trực tối”

// @Column(length = 255)
// private String note;

// private Instant createdAt;
// private Instant updatedAt;

// @PrePersist
// public void handleBeforeCreate() {
// this.createdAt = Instant.now();
// }

// @PreUpdate
// public void handleBeforeUpdate() {
// this.updatedAt = Instant.now();
// }
// }
