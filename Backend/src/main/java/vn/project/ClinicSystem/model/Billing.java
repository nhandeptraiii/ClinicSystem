// package vn.project.ClinicSystem.model;

// import java.math.BigDecimal;
// import java.time.Instant;
// import java.time.LocalDateTime;
// import java.util.ArrayList;
// import java.util.List;

// import com.fasterxml.jackson.annotation.JsonIgnore;

// import jakarta.persistence.CascadeType;
// import jakarta.persistence.Column;
// import jakarta.persistence.Entity;
// import jakarta.persistence.GeneratedValue;
// import jakarta.persistence.GenerationType;
// import jakarta.persistence.Id;
// import jakarta.persistence.JoinColumn;
// import jakarta.persistence.ManyToOne;
// import jakarta.persistence.OneToMany;
// import jakarta.persistence.PrePersist;
// import jakarta.persistence.PreUpdate;
// import jakarta.persistence.Table;
// import lombok.Getter;
// import lombok.Setter;

// @Getter
// @Setter
// @Entity
// @Table(name = "billings")
// public class Billing {
// @Id
// @GeneratedValue(strategy = GenerationType.IDENTITY)
// private Long id;

// @JsonIgnore
// @ManyToOne(optional = false)
// @JoinColumn(name = "medical_record_id")
// private MedicalRecord medicalRecord;

// @ManyToOne(optional = false)
// @JoinColumn(name = "patient_id")
// private Patient patient;

// private BigDecimal totalAmount = BigDecimal.ZERO;

// @Column(length = 30)
// private String status = "UNPAID";

// @Column(length = 60)
// private String paymentMethod;

// private LocalDateTime issuedAt = LocalDateTime.now();

// @Column(length = 255)
// private String notes;

// @OneToMany(mappedBy = "billing", cascade = CascadeType.ALL, orphanRemoval =
// true)
// private List<BillingItem> items = new ArrayList<>();

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
