package com.chatapp.server;

import com.chatapp.model.Message;
import com.chatapp.util.Config;

import java.sql.*;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/**
 * DBManager handles JDBC connection and common DB operations.
 * Update Config.DB_URL, DB_USER, DB_PASS before running.
 */
public class DBManager {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static Connection conn = null;

    public static void init() throws SQLException {
        LOCK.lock();
        try {
            if (conn != null && !conn.isClosed()) return;
            conn = DriverManager.getConnection(Config.DB_URL, Config.DB_USER, Config.DB_PASS);
            createSchema();
        } finally {
            LOCK.unlock();
        }
    }

    private static void createSchema() throws SQLException {
        try (Statement s = conn.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS users (id INT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(100) NOT NULL UNIQUE, password_hash VARCHAR(512) NOT NULL, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"); 
            s.executeUpdate("CREATE TABLE IF NOT EXISTS rooms (id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL UNIQUE)"); 
            s.executeUpdate("CREATE TABLE IF NOT EXISTS memberships (user_id INT NOT NULL, room_id INT NOT NULL, PRIMARY KEY(user_id, room_id))"); 
            s.executeUpdate("CREATE TABLE IF NOT EXISTS messages (id BIGINT AUTO_INCREMENT PRIMARY KEY, sender VARCHAR(100) NOT NULL, receiver VARCHAR(100) NOT NULL, is_room BOOLEAN NOT NULL, text TEXT NOT NULL, ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"); 
            s.executeUpdate("CREATE TABLE IF NOT EXISTS invites (token VARCHAR(128) PRIMARY KEY, used BOOLEAN DEFAULT FALSE, created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP)"); 
        }
    }

    // ========== Users ==========
    public static boolean createUser(String username, String passwordHash) {
        String sql = "INSERT INTO users (username, password_hash) VALUES (?,?)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, username);
            p.setString(2, passwordHash);
            p.executeUpdate();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    public static String fetchStoredPassword(String username) {
        String sql = "SELECT password_hash FROM users WHERE username=?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, username);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return null;
                return rs.getString(1);
            }
        } catch (SQLException e) {
            return null;
        }
    }

    // ========== Rooms & memberships ==========
    public static void ensureRoom(String name) {
        String sql = "INSERT IGNORE INTO rooms (name) VALUES (?)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, name);
            p.executeUpdate();
        } catch (SQLException ignore) {}
    }

    public static void addMembership(String username, String room) {
        String getUser = "SELECT id FROM users WHERE username=?";
        String getRoom = "SELECT id FROM rooms WHERE name=?";
        try {
            conn.setAutoCommit(false);
            Integer uid = null, rid = null;
            try (PreparedStatement p = conn.prepareStatement(getUser)) {
                p.setString(1, username);
                try (ResultSet rs = p.executeQuery()) { if (rs.next()) uid = rs.getInt(1); }
            }
            try (PreparedStatement p = conn.prepareStatement(getRoom)) {
                p.setString(1, room);
                try (ResultSet rs = p.executeQuery()) { if (rs.next()) rid = rs.getInt(1); }
            }
            if (uid != null && rid != null) {
                try (PreparedStatement p = conn.prepareStatement("INSERT IGNORE INTO memberships (user_id, room_id) VALUES (?,?)")) {
                    p.setInt(1, uid); p.setInt(2, rid); p.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            try { conn.rollback(); } catch (SQLException ignore) {}
        } finally {
            try { conn.setAutoCommit(true); } catch (SQLException ignore) {}
        }
    }

    public static void removeMembership(String username, String room) {
        String getUser = "SELECT id FROM users WHERE username=?";
        String getRoom = "SELECT id FROM rooms WHERE name=?";
        try {
            Integer uid = null, rid = null;
            try (PreparedStatement p = conn.prepareStatement(getUser)) {
                p.setString(1, username);
                try (ResultSet rs = p.executeQuery()) { if (rs.next()) uid = rs.getInt(1); }
            }
            try (PreparedStatement p = conn.prepareStatement(getRoom)) {
                p.setString(1, room);
                try (ResultSet rs = p.executeQuery()) { if (rs.next()) rid = rs.getInt(1); }
            }
            if (uid != null && rid != null) {
                try (PreparedStatement p = conn.prepareStatement("DELETE FROM memberships WHERE user_id=? AND room_id=?")) {
                    p.setInt(1, uid); p.setInt(2, rid); p.executeUpdate();
                }
            }
        } catch (SQLException ignore) {}
    }

    // ========== Messages ==========
    public static void saveMessage(Message m) {
        String sql = "INSERT INTO messages (sender, receiver, is_room, text) VALUES (?,?,?,?)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, m.getSender());
            p.setString(2, m.getReceiver());
            p.setBoolean(3, m.isRoom());
            p.setString(4, m.getText());
            p.executeUpdate();
        } catch (SQLException ignore) {}
    }

    public static List<Map<String,Object>> fetchHistory(String username, String target, int limit) {
        List<Map<String,Object>> out = new ArrayList<>();
        try {
            if (isRoom(target)) {
                String sql = "SELECT sender, receiver, text, ts FROM messages WHERE receiver=? AND is_room=1 ORDER BY ts DESC LIMIT ?";
                try (PreparedStatement p = conn.prepareStatement(sql)) {
                    p.setString(1, target);
                    p.setInt(2, limit);
                    try (ResultSet rs = p.executeQuery()) {
                        while (rs.next()) {
                            Map<String,Object> row = new HashMap<>();
                            row.put("sender", rs.getString("sender"));
                            row.put("receiver", rs.getString("receiver"));
                            row.put("text", rs.getString("text"));
                            row.put("ts", rs.getTimestamp("ts").toString());
                            out.add(row);
                        }
                    }
                }
            } else {
                String sql = "SELECT sender, receiver, text, ts FROM messages WHERE ((sender=? AND receiver=? AND is_room=0) OR (sender=? AND receiver=? AND is_room=0)) ORDER BY ts DESC LIMIT ?";
                try (PreparedStatement p = conn.prepareStatement(sql)) {
                    p.setString(1, username); p.setString(2, target);
                    p.setString(3, target); p.setString(4, username);
                    p.setInt(5, limit);
                    try (ResultSet rs = p.executeQuery()) {
                        while (rs.next()) {
                            Map<String,Object> row = new HashMap<>();
                            row.put("sender", rs.getString("sender"));
                            row.put("receiver", rs.getString("receiver"));
                            row.put("text", rs.getString("text"));
                            row.put("ts", rs.getTimestamp("ts").toString());
                            out.add(row);
                        }
                    }
                }
            }
        } catch (SQLException ignore) {}
        return out;
    }

    public static boolean isRoom(String name) {
        String sql = "SELECT 1 FROM rooms WHERE name=?";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, name);
            try (ResultSet rs = p.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { return false; }
    }

    // ========== Invites ==========
    public static void createInvite(String token) {
        String sql = "INSERT IGNORE INTO invites (token, used) VALUES (?, FALSE)";
        try (PreparedStatement p = conn.prepareStatement(sql)) {
            p.setString(1, token);
            p.executeUpdate();
        } catch (SQLException ignore) {}
    }

    public static boolean useInvite(String token) {
        String check = "SELECT used FROM invites WHERE token=?";
        try (PreparedStatement p = conn.prepareStatement(check)) {
            p.setString(1, token);
            try (ResultSet rs = p.executeQuery()) {
                if (!rs.next()) return false;
                if (rs.getBoolean("used")) return false;
            }
        } catch (SQLException e) { return false; }
        try (PreparedStatement p = conn.prepareStatement("UPDATE invites SET used=TRUE WHERE token=?")) {
            p.setString(1, token);
            p.executeUpdate();
            return true;
        } catch (SQLException e) { return false; }
    }
}
