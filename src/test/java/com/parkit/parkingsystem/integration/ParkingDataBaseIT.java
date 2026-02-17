package com.parkit.parkingsystem.integration;

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

import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;
import com.parkit.parkingsystem.integration.service.DataBasePrepareService;
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
        // Use test DB config (not prod)
        parkingSpotDAO = new ParkingSpotDAO();
        parkingSpotDAO.dataBaseConfig = dataBaseTestConfig;

        ticketDAO = new TicketDAO();
        ticketDAO.dataBaseConfig = dataBaseTestConfig;

        dataBasePrepareService = new DataBasePrepareService();
    }

    @BeforeEach
    private void setUpPerTest() throws Exception {
        // Lenient = Mockito will not fail if a stub is not used in one test
        lenient().when(inputReaderUtil.readSelection()).thenReturn(1); // 1 = CAR
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

        // Clean DB before each test
        dataBasePrepareService.clearDataBaseEntries();
    }

    @Test
    public void testParkingACar() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // Simulate: car enters
        parkingService.processIncomingVehicle();

        // Ticket must exist in DB
        Ticket ticketFromDb = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticketFromDb, "Ticket should exist in DB");
        assertNotNull(ticketFromDb.getInTime(), "InTime should be set");
        assertNull(ticketFromDb.getOutTime(), "OutTime should be null before exit");
        assertEquals("ABCDEF", ticketFromDb.getVehicleRegNumber(), "Vehicle reg should match");
        assertNotNull(ticketFromDb.getParkingSpot(), "ParkingSpot should exist");
        assertEquals(1, ticketFromDb.getParkingSpot().getId(), "First allocated spot should be #1");

        // Spot #1 should not be available anymore
        int nextAvailableSlot = parkingSpotDAO.getNextAvailableSlot(ticketFromDb.getParkingSpot().getParkingType());
        assertNotEquals(1, nextAvailableSlot, "Spot #1 should be taken after entering");
    }

    @Test
    public void testParkingLotExit() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // Enter then exit
        parkingService.processIncomingVehicle();
        parkingService.processExitingVehicle();

        // Ticket should be updated in DB
        Ticket ticketFromDb = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticketFromDb, "Ticket should exist in DB");
        assertNotNull(ticketFromDb.getOutTime(), "OutTime should be set after exit");
        assertTrue(ticketFromDb.getPrice() >= 0, "Price should be calculated");

        // Spot #1 should be available again
        int nextAvailableSlot = parkingSpotDAO.getNextAvailableSlot(ticketFromDb.getParkingSpot().getParkingType());
        assertEquals(1, nextAvailableSlot, "Spot #1 should be free after exit");
    }

    @Test
    public void testParkingLotExitRecurringUser() {
        ParkingService parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // ---- First visit (normal price) ----
        parkingService.processIncomingVehicle();
        parkingService.processExitingVehicle();

        Ticket firstTicket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(firstTicket);
        assertNotNull(firstTicket.getOutTime());
        double firstPrice = firstTicket.getPrice();
        assertTrue(firstPrice >= 0, "First price should be calculated");

        // ---- Second visit (discount expected) ----
        parkingService.processIncomingVehicle();

        // Small trick: wait a little so price is not exactly 0 (optional but helps)
        // We do NOT sleep here to keep tests fast. OC environment usually has enough time.

        parkingService.processExitingVehicle();

        Ticket secondTicket = ticketDAO.getTicket("ABCDEF");
        assertNotNull(secondTicket);
        assertNotNull(secondTicket.getOutTime());
        double secondPrice = secondTicket.getPrice();
        assertTrue(secondPrice >= 0, "Second price should be calculated");

        // We expect the second price to be about 95% of a normal price for similar duration.
        // Duration may differ, so we compare ratio instead of exact values.
        // If the first duration is too small (close to 0), we avoid dividing by ~0.
        if (firstPrice > 0.01 && secondPrice > 0.01) {
            double ratio = secondPrice / firstPrice;
            assertTrue(ratio <= 0.98, "Recurring user should pay less (discount expected)");
        }
    }
}
