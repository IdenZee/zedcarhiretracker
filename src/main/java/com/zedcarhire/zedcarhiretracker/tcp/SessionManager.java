package com.zedcarhire.zedcarhiretracker.tcp;

import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SessionManager {

    // Socket -> IMEI mapping
    private static final Map<Socket, String> sessionImeis = new ConcurrentHashMap<>();

    public static void bind(Socket socket, String imei) {
        sessionImeis.put(socket, imei);
    }

    public static String getImei(Socket socket) {
        return sessionImeis.get(socket);
    }

    public static void remove(Socket socket) {
        sessionImeis.remove(socket);
    }
}
