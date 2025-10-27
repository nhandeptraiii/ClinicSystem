package vn.project.ClinicSystem.model;

import java.time.DayOfWeek;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import vn.project.ClinicSystem.model.ClinicRoom;

@Getter
@Setter
@Entity
@Table(name = "user_work_schedules", uniqueConstraints = {
        @UniqueConstraint(name = "uk_user_day", columnNames = { "user_id", "day_of_week" })
})
public class UserWorkSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "roles" })
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 12)
    private DayOfWeek dayOfWeek;

    @Column(name = "morning_shift", nullable = false)
    private boolean morning;

    @Column(name = "afternoon_shift", nullable = false)
    private boolean afternoon;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_room_id")
    @JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
    private ClinicRoom clinicRoom;
}
