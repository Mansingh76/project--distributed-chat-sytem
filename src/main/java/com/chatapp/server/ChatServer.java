package com.chatapp.server;

import com.chatapp.util.Config;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ChatServer {
    private final int port;
    private final ServerSocket serverSocket;
    private final ExecutorService clientPool;

    public ChatServer(int port) throws IOException {
        this.port = port;
        this.serverSocket = new ServerSocket(port);
        this.clientPool = Executors.newCachedThreadPool();
    }

    public void serve() {
        System.out.println("Server listening on 0.0.0.0:" + port);
        while (true) {
            try {
                Socket client = serverSocket.accept();
                ClientHandler handler = new ClientHandler(this, client);
                clientPool.submit(handler);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws Exception {
        // initialize DB
        DBManager.init();
        System.out.println("DB initialized");
        int port = Config.SERVER_PORT;
        if (args.length >= 1) port = Integer.parseInt(args[0]);
        ChatServer server = new ChatServer(port);
        server.serve();
    }
}
