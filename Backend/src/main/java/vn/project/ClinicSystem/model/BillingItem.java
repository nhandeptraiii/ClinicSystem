// package vn.project.ClinicSystem.model;

// import java.math.BigDecimal;
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
// @Table(name = "billing_items")
// public class BillingItem {
// @Id
// @GeneratedValue(strategy = GenerationType.IDENTITY)
// private Long id;

// @ManyToOne(optional = false)
// @JoinColumn(name = "billing_id")
// private Billing billing;

// @Column(length = 150)
// private String description;

// private Integer quantity = 1;

// private BigDecimal unitPrice = BigDecimal.ZERO;

// private BigDecimal amount = BigDecimal.ZERO;

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
