package com.parkit.parkingsystem.integration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Date;

import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parkit.parkingsystem.constants.Fare;
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
    private InputReaderUtil inputReaderUtil;

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
        lenient().when(inputReaderUtil.readSelection()).thenReturn(1);
        lenient().when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");
        dataBasePrepareService.clearDataBaseEntries();
    }

    @AfterAll
    private static void tearDown() {
    }

    @Test
    public void testParkingLotExitRecurringUser() throws Exception {

        ParkingService parkingService =
                new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);

        // First visit
        parkingService.processIncomingVehicle();
        parkingService.processExitingVehicle();

        // Second visit
        parkingService.processIncomingVehicle();

        Ticket ticketBeforeExit = ticketDAO.getTicket("ABCDEF");
        assertNotNull(ticketBeforeExit);

        // Force IN_TIME to 3 hours ago directly in DB
        updateInTimeInDb(ticketBeforeExit.getId(),
                new Date(System.currentTimeMillis() - (3L * 60 * 60 * 1000)));

        parkingService.processExitingVehicle();

        Ticket ticketAfterExit = ticketDAO.getTicket("ABCDEF");

        assertNotNull(ticketAfterExit);
        assertNotNull(ticketAfterExit.getOutTime());

        assertTrue(ticketAfterExit.getPrice() > 0);

        double expectedPrice = 3 * Fare.CAR_RATE_PER_HOUR * 0.95;

        assertEquals(expectedPrice,
                ticketAfterExit.getPrice(),
                0.01);
    }

    // ✅ Helper method to update IN_TIME
    private void updateInTimeInDb(int ticketId, Date newInTime) throws Exception {

        Connection con = null;
        PreparedStatement ps = null;

        try {
            con = dataBaseTestConfig.getConnection();
            ps = con.prepareStatement(
                    "update ticket set IN_TIME=? where ID=?"
            );

            ps.setTimestamp(1, new Timestamp(newInTime.getTime()));
            ps.setInt(2, ticketId);
            ps.executeUpdate();

            if (!con.getAutoCommit()) {
                con.commit();
            }

        } finally {
            dataBaseTestConfig.closePreparedStatement(ps);
            dataBaseTestConfig.closeConnection(con);
        }
    }
}