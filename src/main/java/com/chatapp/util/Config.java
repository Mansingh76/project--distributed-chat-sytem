package com.chatapp.util;

public class Config {
    // MySQL JDBC URL - update host, port, database name if needed
    public static final String DB_URL = "jdbc:mysql://localhost:3306/chat_system?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    public static final String DB_USER = "chatuser";
    public static final String DB_PASS = "chatpass";

    // Server port
    public static final int SERVER_PORT = 5000;

    private Config() {}
}
