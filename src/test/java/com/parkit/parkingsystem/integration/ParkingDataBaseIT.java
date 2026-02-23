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
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;

@ExtendWith(MockitoExtension.class)
public class ParkingDataBaseIT {

    private static DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();
    private static ParkingSpotDAO parkingSpotDAO;
    private static TicketDAO ticketDAO;
    private static DataBasePrepareService dataBasePrepareService;

    @Mock
    private InputReaderUtil inputReaderUtil; // IMPORTANT: NOT static

    @BeforeAll
    private static void setUp() throws Exception {
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
        // Nothing to close
    }

    @Test
    public void testParkingACar() {

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // Vehicle enters
        parkingService.processIncomingVehicle();

        Ticket ticket = ticketDAO.getTicket("ABCDEF");

        assertNotNull(ticket, "Ticket should exist in DB after entry");
        assertNotNull(ticket.getInTime(), "InTime should be set");
        assertNull(ticket.getOutTime(), "OutTime should be null before exit");
        assertEquals("ABCDEF", ticket.getVehicleRegNumber());

        assertNotNull(ticket.getParkingSpot());
        int parkedSpotId = ticket.getParkingSpot().getId();
        assertTrue(parkedSpotId > 0);

        int nextAvailable = parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR);
        assertNotEquals(parkedSpotId, nextAvailable,
                "The parked spot should not be available anymore");
    }

    @Test
    public void testParkingLotExit() {

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        parkingService.processIncomingVehicle();
        parkingService.processExitingVehicle();

        Ticket ticketAfterExit = ticketDAO.getTicket("ABCDEF");

        assertNotNull(ticketAfterExit);
        assertNotNull(ticketAfterExit.getOutTime(), "OutTime should be set after exit");
        assertTrue(ticketAfterExit.getPrice() >= 0, "Price should be calculated");

        int nextAvailable = parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR);
        assertEquals(1, nextAvailable,
                "Spot #1 should be available again after exit");
    }

    @Test
    public void testParkingLotExitRecurringUser() {

        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // First visit
        parkingService.processIncomingVehicle();
        parkingService.processExitingVehicle();

        int nbTickets = ticketDAO.getNbTicket("ABCDEF");
        assertTrue(nbTickets > 0, "User should now be recognized as recurring");

        // Second visit
        parkingService.processIncomingVehicle();

        // Simulate 3 hours parking
        Ticket ticketBeforeExit = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticketBeforeExit);

        ticketBeforeExit.setInTime(
                new Date(System.currentTimeMillis() - (3 * 60 * 60 * 1000))
        );

        ticketDAO.updateTicket(ticketBeforeExit);

        parkingService.processExitingVehicle();

        Ticket ticketAfterExit = ticketDAO.getTicket("ABCDEF");

        assertNotNull(ticketAfterExit);
        assertNotNull(ticketAfterExit.getOutTime(),
                "OutTime should be set after exit");

        assertTrue(ticketAfterExit.getPrice() > 0);

        double expectedPrice = 3 * Fare.CAR_RATE_PER_HOUR * 0.95;

        assertEquals(expectedPrice,
                ticketAfterExit.getPrice(),
                0.01,
                "Recurring user should receive 5% discount");
    }
}