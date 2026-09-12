package com.sindhueventpay;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class JdbcUpdate {
    public static void main(String[] args) throws Exception {
        String url = "jdbc:mysql://localhost:3306/sindhu_event_db";
        String user = "root";
        String pass = "root";
        try (Connection c = DriverManager.getConnection(url, user, pass);
             Statement s = c.createStatement()) {
            int rows = s.executeUpdate("UPDATE users SET password = '$2a$10$sHcB4H5p8JxNM3jBUZsh9.opJ1XlPlnOOgziq29kdgQx1aqOviKPW' WHERE email = 'admin@sindhurunners.com'");
            System.out.println("DONE UPDATING VIA JDBC, ROWS AFFECTED: " + rows);
        }
    }
}
