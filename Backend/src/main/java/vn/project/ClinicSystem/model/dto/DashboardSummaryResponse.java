package vn.project.ClinicSystem.model.dto;

public class DashboardSummaryResponse {
    private long appointmentsToday;
    private long appointmentsConfirmed;
    private long appointmentsCheckedIn;
    private long pendingRequests;
    private long visitsInProgress;
    private long visitsCompleted;
    private long activeDoctors;

    public DashboardSummaryResponse() {
    }

    public DashboardSummaryResponse(long appointmentsToday, long appointmentsConfirmed, long appointmentsCheckedIn,
            long pendingRequests, long visitsInProgress, long visitsCompleted, long activeDoctors) {
        this.appointmentsToday = appointmentsToday;
        this.appointmentsConfirmed = appointmentsConfirmed;
        this.appointmentsCheckedIn = appointmentsCheckedIn;
        this.pendingRequests = pendingRequests;
        this.visitsInProgress = visitsInProgress;
        this.visitsCompleted = visitsCompleted;
        this.activeDoctors = activeDoctors;
    }

    public long getAppointmentsToday() {
        return appointmentsToday;
    }

    public void setAppointmentsToday(long appointmentsToday) {
        this.appointmentsToday = appointmentsToday;
    }

    public long getAppointmentsConfirmed() {
        return appointmentsConfirmed;
    }

    public void setAppointmentsConfirmed(long appointmentsConfirmed) {
        this.appointmentsConfirmed = appointmentsConfirmed;
    }

    public long getAppointmentsCheckedIn() {
        return appointmentsCheckedIn;
    }

    public void setAppointmentsCheckedIn(long appointmentsCheckedIn) {
        this.appointmentsCheckedIn = appointmentsCheckedIn;
    }

    public long getPendingRequests() {
        return pendingRequests;
    }

    public void setPendingRequests(long pendingRequests) {
        this.pendingRequests = pendingRequests;
    }

    public long getVisitsInProgress() {
        return visitsInProgress;
    }

    public void setVisitsInProgress(long visitsInProgress) {
        this.visitsInProgress = visitsInProgress;
    }

    public long getVisitsCompleted() {
        return visitsCompleted;
    }

    public void setVisitsCompleted(long visitsCompleted) {
        this.visitsCompleted = visitsCompleted;
    }

    public long getActiveDoctors() {
        return activeDoctors;
    }

    public void setActiveDoctors(long activeDoctors) {
        this.activeDoctors = activeDoctors;
    }
}
