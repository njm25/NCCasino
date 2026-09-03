package org.nc.nccasino.budget;

/** Result of atomically reducing an existing dealer reservation. */
public record ReservationAdjustment(boolean success, Reservation reservation) {
    public static ReservationAdjustment updated(Reservation reservation) {
        return new ReservationAdjustment(true, reservation);
    }

    public static ReservationAdjustment closed() {
        return new ReservationAdjustment(true, null);
    }

    public static ReservationAdjustment failed() {
        return new ReservationAdjustment(false, null);
    }
}
