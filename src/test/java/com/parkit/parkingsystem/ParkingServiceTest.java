package com.parkit.parkingsystem;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.dao.ParkingSpotDAO;
import com.parkit.parkingsystem.dao.TicketDAO;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;
import com.parkit.parkingsystem.service.ParkingService;
import com.parkit.parkingsystem.util.InputReaderUtil;

@ExtendWith(MockitoExtension.class)
public class ParkingServiceTest {

    private ParkingService parkingService;

    @Mock
    private InputReaderUtil inputReaderUtil;
    @Mock
    private ParkingSpotDAO parkingSpotDAO;
    @Mock
    private TicketDAO ticketDAO;

    @BeforeEach
    public void setUpPerTest() {
        // Only create the service here. Stubbings will be done inside each test.
        parkingService = new ParkingService(inputReaderUtil, parkingSpotDAO, ticketDAO);
    }

    @Test
    public void processExitingVehicleTest() throws Exception {
        when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

        ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
        Ticket ticket = new Ticket();
        ticket.setId(1);
        ticket.setInTime(new Date(System.currentTimeMillis() - (60 * 60 * 1000)));
        ticket.setParkingSpot(parkingSpot);
        ticket.setVehicleRegNumber("ABCDEF");

        when(ticketDAO.getTicket("ABCDEF")).thenReturn(ticket);
        when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(2);
        when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(true);
        when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);

        parkingService.processExitingVehicle();

        verify(ticketDAO, times(1)).getTicket("ABCDEF");
        verify(ticketDAO, times(1)).getNbTicket("ABCDEF");
        verify(ticketDAO, times(1)).updateTicket(any(Ticket.class));
        verify(parkingSpotDAO, times(1)).updateParking(any(ParkingSpot.class));
    }

    @Test
    public void testGetNextParkingNumberIfAvailable() {
        when(inputReaderUtil.readSelection()).thenReturn(1);
        when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(1);

        ParkingSpot parkingSpot = parkingService.getNextParkingNumberIfAvailable();

        assertNotNull(parkingSpot);
        assertEquals(1, parkingSpot.getId());
        assertEquals(ParkingType.CAR, parkingSpot.getParkingType());
        assertTrue(parkingSpot.isAvailable());
    }

    @Test
public void testGetNextParkingNumberIfAvailableParkingNumberNotFound() {
    // User selects CAR
    when(inputReaderUtil.readSelection()).thenReturn(1);
    // No spot available
    when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(0);

    ParkingSpot parkingSpot = parkingService.getNextParkingNumberIfAvailable();

    assertNull(parkingSpot);
}

@Test
public void testGetNextParkingNumberIfAvailableParkingNumberWrongArgument() {
    // User selects an invalid option
    when(inputReaderUtil.readSelection()).thenReturn(3);

    ParkingSpot parkingSpot = parkingService.getNextParkingNumberIfAvailable();

    assertNull(parkingSpot);
    // Optional but nice: DAO should never be called if input is invalid
    verify(parkingSpotDAO, never()).getNextAvailableSlot(any(ParkingType.class));
}

@Test
public void testProcessIncomingVehicle() throws Exception {
    when(inputReaderUtil.readSelection()).thenReturn(1); // CAR
    when(parkingSpotDAO.getNextAvailableSlot(ParkingType.CAR)).thenReturn(1);
    when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

    when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(0);
    when(parkingSpotDAO.updateParking(any(ParkingSpot.class))).thenReturn(true);
    when(ticketDAO.saveTicket(any(Ticket.class))).thenReturn(true);

    parkingService.processIncomingVehicle();

    verify(parkingSpotDAO, times(1)).getNextAvailableSlot(ParkingType.CAR);
    verify(parkingSpotDAO, times(1)).updateParking(any(ParkingSpot.class));
    verify(ticketDAO, times(1)).saveTicket(any(Ticket.class));
    verify(ticketDAO, times(1)).getNbTicket("ABCDEF");
}

@Test
public void processExitingVehicleTestUnableUpdate() throws Exception {
    when(inputReaderUtil.readVehicleRegistrationNumber()).thenReturn("ABCDEF");

    ParkingSpot parkingSpot = new ParkingSpot(1, ParkingType.CAR, false);
    Ticket ticket = new Ticket();
    ticket.setId(1);
    ticket.setInTime(new Date(System.currentTimeMillis() - (60 * 60 * 1000)));
    ticket.setParkingSpot(parkingSpot);
    ticket.setVehicleRegNumber("ABCDEF");

    when(ticketDAO.getTicket("ABCDEF")).thenReturn(ticket);
    when(ticketDAO.getNbTicket("ABCDEF")).thenReturn(2);
    when(ticketDAO.updateTicket(any(Ticket.class))).thenReturn(false);

    parkingService.processExitingVehicle();

    verify(ticketDAO, times(1)).getTicket("ABCDEF");
    verify(ticketDAO, times(1)).getNbTicket("ABCDEF");
    verify(ticketDAO, times(1)).updateTicket(any(Ticket.class));

    // Important: parking spot should NOT be updated if ticket update fails
    verify(parkingSpotDAO, never()).updateParking(any(ParkingSpot.class));
}

}
