package com.parkit.parkingsystem.service;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.model.Ticket;

public class FareCalculatorService {

    public void calculateFare(Ticket ticket) {
        // Default behavior: no discount
        calculateFare(ticket, false);
    }

    public void calculateFare(Ticket ticket, boolean discount) {

        // Validate out time
        if (ticket.getOutTime() == null || ticket.getOutTime().before(ticket.getInTime())) {
            throw new IllegalArgumentException("Out time provided is incorrect:" + ticket.getOutTime());
        }

        long inTimeMillis = ticket.getInTime().getTime();
        long outTimeMillis = ticket.getOutTime().getTime();

        // Calculate parking duration in minutes
        double durationInMinutes = (outTimeMillis - inTimeMillis) / (1000.0 * 60);

        // Free parking if duration is 30 minutes or less
        if (durationInMinutes <= 30) {
            ticket.setPrice(0.0);
            return;
        }

        // Convert minutes to hours for fare calculation
        double durationInHours = durationInMinutes / 60.0;

        switch (ticket.getParkingSpot().getParkingType()) {
            case CAR:
                ticket.setPrice(durationInHours * Fare.CAR_RATE_PER_HOUR);
                break;

            case BIKE:
                ticket.setPrice(durationInHours * Fare.BIKE_RATE_PER_HOUR);
                break;

            default:
                throw new IllegalArgumentException("Unknown Parking Type");
        }

        // Apply a 5% discount for recurring users
        if (discount) {
            ticket.setPrice(ticket.getPrice() * 0.95);
        }
    }
}
