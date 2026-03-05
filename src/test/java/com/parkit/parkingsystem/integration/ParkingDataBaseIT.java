package com.parkit.parkingsystem.integration;

import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parkit.parkingsystem.constants.Fare;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static final DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private InputReaderUtil inputReaderUtil;

    @BeforeAll
    private static void setUp() {
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;

        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;

        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    private void setUpPerTest() throws Exception {
        lenient().when(inputReaderUtil.readSelection()).thenReturn(1); // CAR
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    private static void tearDown() {
    }

    @Test
    public void testParkingACar() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // When: a car enters
        parkingService.processIncomingVehicle();

        // Then: ticket exists in DB
        Ticket ticket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticket, "Ticket should exist in DB after entry");
        assertNotNull(ticket.getInTime(), "InTime should be set after entry");
        assertNull(ticket.getOutTime(), "OutTime should be null before exit");
        assertNotNull(ticket.getParkingSpot(), "Parking spot should be assigned");

        // And: parking spot should not be available anymore
        int nextAvailable = parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR);
        assertNotEquals(ticket.getParkingSpot().getId(), nextAvailable,
                "The parked spot should not be available anymore");
    }

    @Test
    public void testParkingLotExit() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // Given: a car enters
        parkingService.processIncomingVehicle();

        // When: the car exits
        parkingService.processExitingVehicle();

        // Then: ticket is updated
        Ticket ticketAfterExit = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticketAfterExit, "Ticket should exist after exit");
        assertNotNull(ticketAfterExit.getOutTime(), "OutTime should be set after exit");
        assertTrue(ticketAfterExit.getPrice() >= 0, "Price should be calculated");

        // And: spot is free again (in the test DB, first spot is usually 1)
        int nextAvailable = parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR);
        assertEquals(1, nextAvailable, "Spot #1 should be available again after exit");
    }

    @Test
    public void testParkingLotExitRecurringUser() {
        // Create a recurring user: first entry + exit
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
        parkingService.processIncomingVehicle();
        parkingService.processExitingVehicle();

        // Create a second "open" ticket in DB with an IN_TIME 45 minutes ago
        ParkingSpot parkingSpot = new ParkingSpot(2, ParkingType.CAR, false);
        parkingSpotDAO.updateParking(parkingSpot);

        Ticket secondTicket = new Ticket();
        secondTicket.setParkingSpot(parkingSpot);
        secondTicket.setVehicleRegNumber("ABCDEF");

        Date inTime = new Date();
        inTime.setTime(System.currentTimeMillis() - (45 * 60 * 1000)); // 45 minutes ago

        secondTicket.setInTime(inTime);
        secondTicket.setOutTime(null);
        secondTicket.setPrice(0.0);

        ticketDAO.saveTicket(secondTicket);

        // When: car exits again (processExitingVehicle will fetch the last ticket)
        parkingService.processExitingVehicle();

        // Then: ticket is updated with OUT_TIME and discounted price
        Ticket ticketAfterExit = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticketAfterExit, "Ticket should exist after exit");
        assertNotNull(ticketAfterExit.getOutTime(), "OutTime should be set after exit");
        assertTrue(ticketAfterExit.getPrice() > 0, "Price should be > 0 for 45 minutes parking");

        // Expected: 45 minutes = 0.75 hour, and 5% discount
        double expectedPrice = (45.0 / 60.0) * Fare.CAR_RATE_PER_HOUR * 0.95;
        assertEquals(expectedPrice, ticketAfterExit.getPrice(), 0.01,
                "Recurring user should receive 5% discount");
    }
}