package com.example.datalifepolicyenforcer.connection;

import org.springframework.stereotype.Component;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.Properties;

@Component
public class TargetConnectionFactory {
    public Connection open(DatabaseConnection config) throws SQLException {
        Properties properties = new Properties();
        properties.setProperty("user", config.getUsername());
        properties.setProperty("password", config.getPassword());
        properties.setProperty("sslmode", config.getSslMode());
        properties.setProperty("connectTimeout", "5");
        properties.setProperty("socketTimeout", "30");
        properties.setProperty("readOnlyMode", "always");
        properties.setProperty("options", "-c default_transaction_read_only=on -c statement_timeout=15000 -c search_path=pg_catalog -c TimeZone=UTC -c DateStyle=ISO,YMD");
        String host = config.getHost().contains(":") ? "[" + config.getHost() + "]" : config.getHost();
        String database = URLEncoder.encode(config.getDatabase(), StandardCharsets.UTF_8).replace("+", "%20");
        Connection connection = DriverManager.getConnection(
                "jdbc:postgresql://" + host + ":" + config.getPort() + "/" + database, properties);
        try {
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            return connection;
        } catch (SQLException ex) {
            connection.close();
            throw ex;
        }
    }
}

