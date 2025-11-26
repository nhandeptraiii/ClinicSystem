package vn.project.ClinicSystem.model.enums;

public enum VisitStatus {
    OPEN,
    COMPLETED,
    CANCELLED;

    public static VisitStatus[] inProgressStatuses() {
        return new VisitStatus[] { OPEN };
    }

    public static VisitStatus[] completedStatuses() {
        return new VisitStatus[] { COMPLETED };
    }
}
