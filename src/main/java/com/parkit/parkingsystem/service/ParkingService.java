package com.parkit.parkingsystem.service;

import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.util.InputReaderUtil;

public class ParkingService {

    private static final Logger logger = LogManager.getLogger(ParkingService.class);
    private static final FareCalculatorService fareCalculatorService = new FareCalculatorService();

    private final InputReaderUtil inputReaderUtil;
    private final ParkingSpotDAO parkingSpotDAO;
    private final TicketDAO ticketDAO;

    public ParkingService(InputReaderUtil inputReaderUtil,
                          ParkingSpotDAO parkingSpotDAO,
                          TicketDAO ticketDAO) {
        this.inputReaderUtil = inputReaderUtil;
        this.parkingSpotDAO = parkingSpotDAO;
        this.ticketDAO = ticketDAO;
    }

    /**
     * Handles vehicle entry
     */
    public void processIncomingVehicle() {
        try {
            ParkingSpot parkingSpot = getNextParkingNumberIfAvailable();

            if (parkingSpot == null) {
                return;
            }

            String vehicleRegNumber = getVehicleRegNumber();

            // Check recurring user
            if (ticketDAO.getNbTicket(vehicleRegNumber) > 0) {
                System.out.println(
                        "Heureux de vous revoir ! En tant qu’utilisateur régulier, vous bénéficiez d’une remise de 5 %");
            }

            // Occupy parking spot
            parkingSpot.setAvailable(false);
            if (!parkingSpotDAO.updateParking(parkingSpot)) {
                System.out.println("Unable to allocate parking spot.");
                return;
            }

            // Create ticket
            Ticket ticket = new Ticket();
            ticket.setParkingSpot(parkingSpot);
            ticket.setVehicleRegNumber(vehicleRegNumber);
            ticket.setPrice(0);
            ticket.setInTime(new Date());
            ticket.setOutTime(null);

            if (!ticketDAO.saveTicket(ticket)) {
                System.out.println("Unable to save ticket.");
                parkingSpot.setAvailable(true);
                parkingSpotDAO.updateParking(parkingSpot);
                return;
            }

            System.out.println("Ticket generated successfully.");
            System.out.println("Please park at spot number: " + parkingSpot.getId());

        } catch (Exception e) {
            logger.error("Unable to process incoming vehicle", e);
        }
    }

    /**
     * Handles vehicle exit
     */
    public void processExitingVehicle() {
        try {
            String vehicleRegNumber = getVehicleRegNumber();

            Ticket ticket = ticketDAO.getTicket(vehicleRegNumber);

            if (ticket == null) {
                System.out.println("Ticket not found for vehicle: " + vehicleRegNumber);
                return;
            }

            // Set exit time
            ticket.setOutTime(new Date());

            // Discount if recurring (more than 1 ticket)
            boolean discount = ticketDAO.getNbTicket(vehicleRegNumber) > 1;

            // Calculate fare
            fareCalculatorService.calculateFare(ticket, discount);

            // Update ticket
            if (!ticketDAO.updateTicket(ticket)) {
                System.out.println("Unable to update ticket information.");
                return;
            }

            // Free parking spot
            ParkingSpot parkingSpot = ticket.getParkingSpot();
            parkingSpot.setAvailable(true);
            parkingSpotDAO.updateParking(parkingSpot);

            System.out.println("Please pay: " + ticket.getPrice());

        } catch (Exception e) {
            logger.error("Unable to process exiting vehicle", e);
        }
    }

    /**
     * Returns next available parking spot
     */
    public ParkingSpot getNextParkingNumberIfAvailable() {
        try {
            ParkingType parkingType = getVehicleType();
            int parkingNumber = parkingSpotDAO.getNextAvailableSlot(parkingType);

            if (parkingNumber > 0) {
                return new ParkingSpot(parkingNumber, parkingType, true);
            }

        } catch (IllegalArgumentException e) {
            logger.error("Invalid vehicle type selected", e);
        } catch (Exception e) {
            logger.error("Error fetching next available parking slot", e);
        }

        return null;
    }

    private String getVehicleRegNumber() throws Exception {
        System.out.println("Enter vehicle registration number:");
        return inputReaderUtil.readVehicleRegistrationNumber();
    }

    private ParkingType getVehicleType() {
        System.out.println("Select vehicle type:");
        System.out.println("1 CAR");
        System.out.println("2 BIKE");

        int input = inputReaderUtil.readSelection();

        switch (input) {
            case 1:
                return ParkingType.CAR;
            case 2:
                return ParkingType.BIKE;
            default:
                System.out.println("Invalid input.");
                throw new IllegalArgumentException("Invalid vehicle type");
        }
    }
}