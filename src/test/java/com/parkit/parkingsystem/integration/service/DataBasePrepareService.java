package com.parkit.parkingsystem.integration.service;

import java.sql.Connection;
import java.sql.PreparedStatement;

import com.parkit.parkingsystem.integration.config.DataBaseTestConfig;

public class DataBasePrepareService {

    private final DataBaseTestConfig dataBaseTestConfig = new DataBaseTestConfig();

    public void clearDataBaseEntries() {
        Connection connection = null;
        PreparedStatement ps1 = null;
        PreparedStatement ps2 = null;

        try {
            connection = dataBaseTestConfig.getConnection();

            // Set all parking spots to available
            ps1 = connection.prepareStatement("update parking set available = true");
            ps1.executeUpdate();

            // Remove all tickets (reset table)
            ps2 = connection.prepareStatement("truncate table ticket");
            ps2.executeUpdate();

            // If auto-commit is disabled, commit manually
            if (!connection.getAutoCommit()) {
                connection.commit();
            }

        } catch (Exception e) {

            // Rollback if needed
            try {
                if (connection != null && !connection.getAutoCommit()) {
                    connection.rollback();
                }
            } catch (Exception ignored) {}

            e.printStackTrace();

        } finally {
            dataBaseTestConfig.closePreparedStatement(ps1);
            dataBaseTestConfig.closePreparedStatement(ps2);
            dataBaseTestConfig.closeConnection(connection);
        }
    }
}