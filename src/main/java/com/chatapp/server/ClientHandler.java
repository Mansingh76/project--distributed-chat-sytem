package com.chatapp.server;

import com.chatapp.util.PasswordUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.chatapp.model.Message;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * ClientHandler reads newline-delimited JSON commands from client and executes them.
 * Uses Gson to parse JSON into Map<String,Object>.
 */
public class ClientHandler implements Runnable {
    private final ChatServer server;
    private final Socket socket;
    private final Gson gson = new Gson();
    private BufferedReader in;
    private BufferedWriter out;

    // global shared maps
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Map<String, CopyOnWriteArraySet<ClientHandler>> rooms = new ConcurrentHashMap<>();

    private String username = null;
    private final Set<String> joinedRooms = new HashSet<>();
    private volatile boolean alive = true;

    public ClientHandler(ChatServer server, Socket socket) throws IOException {
        this.server = server;
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
    }

    private void sendJson(Map<String,Object> m) {
        try {
            String s = gson.toJson(m) + "\n";
            synchronized (out) {
                out.write(s);
                out.flush();
            }
        } catch (IOException e) {
            alive = false;
        }
    }

    private void sendOk(String msg) { sendJson(Map.of("type","ok","msg",msg)); }
    private void sendErr(String msg) { sendJson(Map.of("type","error","msg",msg)); }
    private void sendServerMsg(String text) { sendJson(Map.of("type","server_msg","text",text)); }

    @Override
    public void run() {
        try {
            sendServerMsg("Welcome! Use /register or /login. Register requires invite token.");
            String line;
            Type mapType = new TypeToken<Map<String,Object>>(){}.getType();
            while (alive && (line = in.readLine()) != null) {
                Map payload;
                try {
                    payload = gson.fromJson(line, mapType);
                } catch (Exception e) {
                    sendErr("Invalid JSON"); continue;
                }
                if (payload == null || !payload.containsKey("cmd")) { sendErr("Missing cmd"); continue; }
                String cmd = ((String)payload.get("cmd")).toLowerCase();
                switch (cmd) {
                    case "register" -> handleRegister((String)payload.getOrDefault("username",""), (String)payload.getOrDefault("password",""), (String)payload.getOrDefault("token",""));
                    case "login"    -> handleLogin((String)payload.getOrDefault("username",""), (String)payload.getOrDefault("password",""));
                    case "join"     -> handleJoin((String)payload.getOrDefault("room",""));
                    case "leave"    -> handleLeave((String)payload.getOrDefault("room",""));
                    case "rooms"    -> handleRooms();
                    case "msg"      -> handleMsg((String)payload.getOrDefault("room",""), (String)payload.getOrDefault("text",""));
                    case "pm"       -> handlePm((String)payload.getOrDefault("to",""), (String)payload.getOrDefault("text",""));
                    case "history"  -> handleHistory((String)payload.getOrDefault("target",""), ((Double)payload.getOrDefault("limit",50.0)).intValue());
                    case "quit"     -> { sendOk("Bye"); alive = false; }
                    default -> sendErr("Unknown cmd: " + cmd);
                }
            }
        } catch (IOException e) {
            // ignore
        } finally {
            cleanup();
        }
    }

    private void handleRegister(String user, String pass, String token) {
        if (user.isBlank() || pass.isBlank() || token.isBlank()) { sendErr("username,password,token required"); return; }
        if (!DBManager.useInvite(token)) { sendErr("Invalid or used invite token"); return; }
        String hashed = PasswordUtils.hashPassword(pass.toCharArray());
        if (DBManager.createUser(user, hashed)) {
            this.username = user;
            clients.put(user, this);
            sendOk("Registered & logged in");
        } else {
            sendErr("Username already exists");
        }
    }

    private void handleLogin(String user, String pass) {
        if (user.isBlank() || pass.isBlank()) { sendErr("username/password required"); return; }
        String stored = DBManager.fetchStoredPassword(user);
        if (stored == null) { sendErr("Invalid credentials"); return; }
        boolean ok = PasswordUtils.verifyPassword(stored, pass.toCharArray());
        if (ok) {
            this.username = user;
            clients.put(user, this);
            sendOk("Logged in");
        } else {
            sendErr("Invalid credentials");
        }
    }

    private boolean requireAuth() {
        if (this.username == null) { sendErr("You must register/login first"); return false; }
        return true;
    }

    private void handleJoin(String room) {
        if (!requireAuth()) return;
        if (room.isBlank()) { sendErr("room required"); return; }
        rooms.putIfAbsent(room, new CopyOnWriteArraySet<>());
        rooms.get(room).add(this);
        joinedRooms.add(room);
        DBManager.ensureRoom(room);
        DBManager.addMembership(this.username, room);
        sendOk("Joined " + room);
        for (ClientHandler ch : rooms.get(room)) {
            if (ch != this) ch.sendServerMsg("[" + room + "] " + username + " has joined");
        }
    }

    private void handleLeave(String room) {
        if (!requireAuth()) return;
        if (!joinedRooms.contains(room)) { sendErr("Not in room"); return; }
        var set = rooms.get(room);
        if (set != null) set.remove(this);
        joinedRooms.remove(room);
        DBManager.removeMembership(this.username, room);
        sendOk("Left " + room);
    }

    private void handleRooms() {
        sendJson(Map.of("type","rooms","rooms", rooms.keySet()));
    }

    private void handleMsg(String room, String text) {
        if (!requireAuth()) return;
        if (!joinedRooms.contains(room)) { sendErr("Join room first"); return; }
        var set = rooms.get(room);
        if (set == null) { sendErr("Room not found"); return; }
        for (ClientHandler ch : set) {
            if (ch != this) ch.sendServerMsg("[" + room + "] " + username + ": " + text);
        }
        DBManager.saveMessage(new Message(username, room, true, text));
        sendOk("Message sent");
    }

    private void handlePm(String to, String text) {
        if (!requireAuth()) return;
        var target = clients.get(to);
        if (target == null) { sendErr("User not online"); return; }
        target.sendServerMsg("[PM] " + username + ": " + text);
        DBManager.saveMessage(new Message(username, to, false, text));
        sendOk("PM sent");
    }

    private void handleHistory(String target, int limit) {
        if (!requireAuth()) return;
        if (target == null || target.isBlank()) { sendErr("target required"); return; }
        var rows = DBManager.fetchHistory(this.username, target, limit);
        Map<String,Object> resp = new HashMap<>();
        resp.put("type", "history");
        resp.put("target", target);
        resp.put("messages", rows);
        sendJson(resp);
    }

    private void cleanup() {
        try {
            if (username != null) clients.remove(username);
            for (String r : joinedRooms) {
                var set = rooms.get(r);
                if (set != null) set.remove(this);
            }
            try { socket.close(); } catch (IOException ignore) {}
        } catch (Exception ignore) {}
    }
}
