// package vn.project.ClinicSystem.model;

// import java.time.Instant;

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
// @Table(name = "prescription_items")
// public class PrescriptionItem {
// @Id
// @GeneratedValue(strategy = GenerationType.IDENTITY)
// private Long id;

// @ManyToOne(optional = false)
// @JoinColumn(name = "prescription_id")
// private Prescription prescription;

// @ManyToOne
// @JoinColumn(name = "medication_id")
// private Medication medication;

// @Column(length = 120)
// private String medicationName;

// @Column(length = 80)
// private String dosage;

// @Column(length = 80)
// private String frequency;

// @Column(length = 80)
// private String duration;

// @Column(length = 255)
// private String instruction;

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
