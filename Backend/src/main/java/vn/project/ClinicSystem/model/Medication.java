// package vn.project.ClinicSystem.model;

// import java.math.BigDecimal;
// import java.time.Instant;
// import java.util.ArrayList;
// import java.util.List;

// import com.fasterxml.jackson.annotation.JsonIgnore;

// import jakarta.persistence.Column;
// import jakarta.persistence.Entity;
// import jakarta.persistence.GeneratedValue;
// import jakarta.persistence.GenerationType;
// import jakarta.persistence.Id;
// import jakarta.persistence.OneToMany;
// import jakarta.persistence.PrePersist;
// import jakarta.persistence.PreUpdate;
// import jakarta.persistence.Table;
// import lombok.Getter;
// import lombok.Setter;

// @Getter
// @Setter
// @Entity
// @Table(name = "medications")
// public class Medication {
// @Id
// @GeneratedValue(strategy = GenerationType.IDENTITY)
// private Long id;

// @Column(nullable = false, length = 150)
// private String name;

// @Column(length = 150)
// private String activeIngredient;

// @Column(length = 50)
// private String form;

// @Column(length = 30)
// private String unit;

// private BigDecimal unitPrice;

// private Integer stockQuantity;

// @JsonIgnore
// @OneToMany(mappedBy = "medication")
// private List<PrescriptionItem> prescriptionItems = new ArrayList<>();

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
