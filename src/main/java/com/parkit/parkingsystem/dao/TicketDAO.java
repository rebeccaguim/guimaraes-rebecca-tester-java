package com.parkit.parkingsystem.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.parkit.parkingsystem.config.DataBaseConfig;
import com.parkit.parkingsystem.constants.DBConstants;
import com.parkit.parkingsystem.constants.ParkingType;
import com.parkit.parkingsystem.model.ParkingSpot;
import com.parkit.parkingsystem.model.Ticket;

public class TicketDAO {

    private static final Logger logger = LogManager.getLogger("TicketDAO");

    public DataBaseConfig dataBaseConfig = new DataBaseConfig();

    public boolean saveTicket(Ticket ticket) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        try {
            con = dataBaseConfig.getConnection();
            ps = con.prepareStatement(DBConstants.SAVE_TICKET, PreparedStatement.RETURN_GENERATED_KEYS);

            // 1) Parking number
            ps.setInt(1, ticket.getParkingSpot().getId());

            // 2) Vehicle registration number
            ps.setString(2, ticket.getVehicleRegNumber());

            // 3) Price (0 at entry)
            ps.setDouble(3, ticket.getPrice());

            // 4) Entry time
            ps.setTimestamp(4, new Timestamp(ticket.getInTime().getTime()));

            // 5) OUT_TIME is null at entry
            ps.setNull(5, java.sql.Types.TIMESTAMP);

            ps.executeUpdate();

            // Retrieve generated ticket ID
            rs = ps.getGeneratedKeys();
            if (rs.next()) {
                ticket.setId(rs.getInt(1));
            }

            // Commit if auto-commit is disabled
            if (!con.getAutoCommit()) {
                con.commit();
            }

            return true;

        } catch (Exception ex) {
            logger.error("Error saving ticket info", ex);

            // Rollback if needed
            try {
                if (con != null && !con.getAutoCommit()) {
                    con.rollback();
                }
            } catch (Exception ignored) {
            }

            return false;

        } finally {
            dataBaseConfig.closeResultSet(rs);
            dataBaseConfig.closePreparedStatement(ps);
            dataBaseConfig.closeConnection(con);
        }
    }

    public Ticket getTicket(String vehicleRegNumber) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        Ticket ticket = null;

        try {
            con = dataBaseConfig.getConnection();
            ps = con.prepareStatement(DBConstants.GET_TICKET);

            // VEHICLE_REG_NUMBER
            ps.setString(1, vehicleRegNumber);

            rs = ps.executeQuery();
            if (rs.next()) {
                ticket = new Ticket();

                ParkingSpot parkingSpot = new ParkingSpot(
                        rs.getInt(1),
                        ParkingType.valueOf(rs.getString(6)),
                        false
                );

                ticket.setParkingSpot(parkingSpot);
                ticket.setId(rs.getInt(2));
                ticket.setVehicleRegNumber(vehicleRegNumber);
                ticket.setPrice(rs.getDouble(3));
                ticket.setInTime(rs.getTimestamp(4));   // Timestamp extends Date
                ticket.setOutTime(rs.getTimestamp(5));  // Can be null
            }

        } catch (Exception ex) {
            logger.error("Error fetching ticket", ex);

        } finally {
            dataBaseConfig.closeResultSet(rs);
            dataBaseConfig.closePreparedStatement(ps);
            dataBaseConfig.closeConnection(con);
        }

        return ticket;
    }

    public boolean updateTicket(Ticket ticket) {
        Connection con = null;
        PreparedStatement ps = null;

        try {
            con = dataBaseConfig.getConnection();
            ps = con.prepareStatement(DBConstants.UPDATE_TICKET);

            // 1) Updated price
            ps.setDouble(1, ticket.getPrice());

            // 2) OUT_TIME
            if (ticket.getOutTime() != null) {
                ps.setTimestamp(2, new Timestamp(ticket.getOutTime().getTime()));
            } else {
                ps.setNull(2, java.sql.Types.TIMESTAMP);
            }

            // 3) Update by ticket ID (reliable)
            ps.setInt(3, ticket.getId());

            int updated = ps.executeUpdate();

            // Commit if auto-commit is disabled
            if (!con.getAutoCommit()) {
                con.commit();
            }

            return updated > 0;

        } catch (Exception ex) {
            logger.error("Error updating ticket info", ex);

            // Rollback if needed
            try {
                if (con != null && !con.getAutoCommit()) {
                    con.rollback();
                }
            } catch (Exception ignored) {
            }

            return false;

        } finally {
            dataBaseConfig.closePreparedStatement(ps);
            dataBaseConfig.closeConnection(con);
        }
    }

    public int getNbTicket(String vehicleRegNumber) {
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;

        int nbTickets = 0;

        try {
            con = dataBaseConfig.getConnection();
            ps = con.prepareStatement(DBConstants.COUNT_TICKETS);
            ps.setString(1, vehicleRegNumber);

            rs = ps.executeQuery();
            if (rs.next()) {
                nbTickets = rs.getInt(1);
            }

        } catch (Exception ex) {
            logger.error("Error counting tickets for vehicle", ex);

        } finally {
            dataBaseConfig.closeResultSet(rs);
            dataBaseConfig.closePreparedStatement(ps);
            dataBaseConfig.closeConnection(con);
        }

        return nbTickets;
    }
}