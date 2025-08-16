package com.chatapp.client;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.net.Socket;
import java.util.Map;
import java.util.Scanner;

public class ChatClient {
    private final Socket socket;
    private final BufferedReader in;
    private final BufferedWriter out;
    private final Gson gson = new Gson();

    public ChatClient(String host, int port) throws IOException {
        this.socket = new Socket(host, port);
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
        this.out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"));
    }

    private void sendJson(Map<String,Object> payload) throws IOException {
        String s = gson.toJson(payload) + "\n";
        synchronized (out) {
            out.write(s);
            out.flush();
        }
    }

    private Map<String,Object> recvJson() throws IOException {
        String line = in.readLine();
        if (line == null) return null;
        Type t = new TypeToken<Map<String,Object>>(){}.getType();
        return gson.fromJson(line, t);
    }

    public void start() {
        Thread reader = new Thread(() -> {
            try {
                Map<String,Object> m;
                while ((m = recvJson()) != null) {
                    String type = (String)m.get("type");
                    if ("server_msg".equals(type)) System.out.println((String)m.get("text"));
                    else if ("ok".equals(type)) System.out.println("[OK] " + m.get("msg"));
                    else if ("error".equals(type)) System.out.println("[ERR] " + m.get("msg"));
                    else if ("rooms".equals(type)) System.out.println("Rooms: " + m.get("rooms"));
                    else if ("history".equals(type)) {
                        System.out.println("--- history " + m.get("target") + " ---");
                        System.out.println(m.get("messages"));
                        System.out.println("--- end ---");
                    } else {
                        System.out.println(m);
                    }
                }
            } catch (IOException e) {
                System.out.println("Disconnected from server.");
            }
        });
        reader.setDaemon(true);
        reader.start();

        Scanner sc = new Scanner(System.in);
        System.out.println("Commands:");
        System.out.println("/register <user> <pass> <token>");
        System.out.println("/login <user> <pass>");
        System.out.println("/join <room>");
        System.out.println("/leave <room>");
        System.out.println("/rooms");
        System.out.println("/msg <room> <text>");
        System.out.println("/pm <user> <text>");
        System.out.println("/history <target> [limit]");
        System.out.println("/quit");
        try {
            while (true) {
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equals("/quit")) { sendJson(Map.of("cmd","quit")); break; }
                else if (line.startsWith("/register ")) {
                    String[] parts = line.split(" ",4);
                    if (parts.length < 4) { System.out.println("Usage: /register <user> <pass> <token>"); continue; }
                    sendJson(Map.of("cmd","register","username",parts[1],"password",parts[2],"token",parts[3]));
                } else if (line.startsWith("/login ")) {
                    String[] parts = line.split(" ",3);
                    if (parts.length < 3) { System.out.println("Usage: /login <user> <pass>"); continue; }
                    sendJson(Map.of("cmd","login","username",parts[1],"password",parts[2]));
                } else if (line.startsWith("/join ")) {
                    String[] parts = line.split(" ",2);
                    sendJson(Map.of("cmd","join","room",parts[1]));
                } else if (line.startsWith("/leave ")) {
                    String[] parts = line.split(" ",2);
                    sendJson(Map.of("cmd","leave","room",parts[1]));
                } else if (line.equals("/rooms")) {
                    sendJson(Map.of("cmd","rooms"));
                } else if (line.startsWith("/msg ")) {
                    String[] parts = line.split(" ",3);
                    if (parts.length < 3) { System.out.println("Usage: /msg <room> <text>"); continue; }
                    sendJson(Map.of("cmd","msg","room",parts[1],"text",parts[2]));
                } else if (line.startsWith("/pm ")) {
                    String[] parts = line.split(" ",3);
                    if (parts.length < 3) { System.out.println("Usage: /pm <user> <text>"); continue; }
                    sendJson(Map.of("cmd","pm","to",parts[1],"text",parts[2]));
                } else if (line.startsWith("/history ")) {
                    String[] parts = line.split(" ",3);
                    if (parts.length == 2) sendJson(Map.of("cmd","history","target",parts[1]));
                    else sendJson(Map.of("cmd","history","target",parts[1],"limit",Integer.parseInt(parts[2])));
                } else {
                    System.out.println("Unknown command");
                }
            }
        } catch (IOException e) {
            System.out.println("Send error: " + e.getMessage());
        } finally {
            try { socket.close(); } catch (IOException ignore) {}
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: java -cp target/chat-system-1.0-SNAPSHOT.jar com.chatapp.client.ChatClient <host> <port>");
            return;
        }
        String host = args[0]; int port = Integer.parseInt(args[1]);
        ChatClient c = new ChatClient(host, port);
        c.start();
    }
}
