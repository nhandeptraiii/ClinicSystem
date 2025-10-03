// package vn.project.ClinicSystem.model;

// import java.time.Instant;
// import java.time.LocalDateTime;

// import com.fasterxml.jackson.annotation.JsonIgnore;

// import jakarta.persistence.Column;
// import jakarta.persistence.Entity;
// import jakarta.persistence.GeneratedValue;
// import jakarta.persistence.GenerationType;
// import jakarta.persistence.Id;
// import jakarta.persistence.JoinColumn;
// import jakarta.persistence.Lob;
// import jakarta.persistence.ManyToOne;
// import jakarta.persistence.PrePersist;
// import jakarta.persistence.PreUpdate;
// import jakarta.persistence.Table;
// import lombok.Getter;
// import lombok.Setter;

// @Getter
// @Setter
// @Entity
// @Table(name = "medical_records")
// public class MedicalRecord {
// @Id
// @GeneratedValue(strategy = GenerationType.IDENTITY)
// private Long id;

// @JsonIgnore
// @ManyToOne
// @JoinColumn(name = "appointment_id")
// private Appointment appointment;

// @ManyToOne(optional = false)
// @JoinColumn(name = "patient_id")
// private Patient patient;

// @ManyToOne(optional = false)
// @JoinColumn(name = "doctor_id")
// private Doctor doctor;

// private LocalDateTime visitDate = LocalDateTime.now();

// @Lob
// private String symptoms;

// @Lob
// private String diagnosis;

// @Column(columnDefinition = "TEXT")
// private String vitalsJson;

// @Column(length = 255)
// private String medicalAdvice;

// private LocalDateTime followUpDate;

// @JsonIgnore
// @ManyToOne
// @JoinColumn(name = "ai_diagnosis_id")
// private DiagnosisHistory diagnosisHistory;

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
