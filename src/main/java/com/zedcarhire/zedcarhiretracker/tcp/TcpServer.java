package com.zedcarhire.zedcarhiretracker.tcp;

import com.zedcarhire.zedcarhiretracker.model.TrackerData;
import com.zedcarhire.zedcarhiretracker.protocol.Decoded;
import com.zedcarhire.zedcarhiretracker.protocol.Decoder;
import com.zedcarhire.zedcarhiretracker.protocol.DecoderRegistry;
import com.zedcarhire.zedcarhiretracker.protocol.Gt06Decoder;
import com.zedcarhire.zedcarhiretracker.service.TrackerService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
public class TcpServer {

    @Value("${tracker.tcp.enabled:true}")
    private boolean enabled;

    @Value("${tracker.tcp.bind:0.0.0.0}")
    private String bind;

    @Value("${tracker.tcp.port:5000}")
    private int port;

    @Value("${tracker.tcp.backlog:200}")
    private int backlog;

    private final TrackerService trackerService;
    private final ExecutorService pool = Executors.newCachedThreadPool();

    // Track failed connection attempts per IP
    private final Map<String, Integer> failedAttempts = new ConcurrentHashMap<>();
    private final Map<String, Long> blockedIPs = new ConcurrentHashMap<>();

    public TcpServer(TrackerService trackerService) {
        this.trackerService = trackerService;
    }

    private final Decoder decoder = new Gt06Decoder();
    private final DecoderRegistry decoderRegistry = new DecoderRegistry();

    @PostConstruct
    public void start() {
        if (!enabled) return;

        pool.submit(() -> {
            try (ServerSocket server = new ServerSocket(port, backlog, InetAddress.getByName(bind))) {
                System.out.println("[TCP] Listening on " + bind + ":" + port);
                while (true) {
                    Socket socket = server.accept();
                    String clientIP = socket.getInetAddress().getHostAddress();

                    // Check if IP is blocked
                    if (isBlocked(clientIP)) {
                        System.out.println("[TCP] BLOCKED connection from " + clientIP);
                        socket.close();
                        continue;
                    }

                    System.out.println("[TCP] Accepted connection from " + socket.getRemoteSocketAddress());
                    pool.submit(() -> handle(socket));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    private boolean isBlocked(String ip) {
        Long blockedUntil = blockedIPs.get(ip);
        if (blockedUntil != null) {
            if (System.currentTimeMillis() < blockedUntil) {
                return true; // Still blocked
            } else {
                blockedIPs.remove(ip); // Unblock after timeout
                failedAttempts.remove(ip);
            }
        }
        return false;
    }

    private void recordFailedAttempt(String ip) {
        int attempts = failedAttempts.getOrDefault(ip, 0) + 1;
        failedAttempts.put(ip, attempts);

        if (attempts >= 3) {
            // Block for 1 hour
            blockedIPs.put(ip, System.currentTimeMillis() + (60 * 60 * 1000));
            System.out.println("[SECURITY] IP " + ip + " blocked for 1 hour after " + attempts + " invalid attempts");
        }
    }

    private void handle(Socket socket) {
        String clientIP = socket.getInetAddress().getHostAddress();
        System.out.println("[TCP] Connection opened: " + socket.getRemoteSocketAddress());

        try (Socket s = socket;
             InputStream in = s.getInputStream();
             OutputStream out = s.getOutputStream()) {

            byte[] buf = new byte[2048];
            int len;

            boolean validTrackerDetected = false;

            while ((len = in.read(buf)) != -1) {

                byte[] pkt = new byte[len];
                System.arraycopy(buf, 0, pkt, 0, len);

                // Quick validation: GT06 packets start with 0x78 0x78 or 0x79 0x79
                if (len < 2 ||
                        !((pkt[0] == 0x78 && pkt[1] == 0x78) ||
                                (pkt[0] == 0x79 && pkt[1] == 0x79))) {

                    System.out.println("[SECURITY] Invalid packet format from " + clientIP);
                    System.out.println("[SECURITY] First bytes: " + String.format("%02X %02X", pkt[0], pkt[1]));
                    recordFailedAttempt(clientIP);
                    return; // Close connection immediately
                }

                String hex = Gt06Decoder.toHex(pkt);
                System.out.println("[TCP] HEX: " + hex);

                // Decode packet
                Decoded d = decoderRegistry.decode(pkt, socket);

                if (d != null) {
                    validTrackerDetected = true;
                    System.out.println("[DECODE DEBUG] PROTO=" + (pkt[3] & 0xFF) + " LEN=" + pkt.length);

                    // LOGIN PACKET (IMEI present)
                    if (d.imei != null && !d.imei.equals("UNKNOWN")) {
                        System.out.println("[LOGIN] IMEI Bound: " + d.imei);
                        SessionManager.bind(socket, d.imei);
                        failedAttempts.remove(clientIP); // Clear failed attempts on successful login
                    }

                    // Retrieve IMEI from session (applies to GPS packets)
                    String imei = SessionManager.getImei(socket);

                    // GPS PACKET
                    if (imei != null && d.latitude != null && d.longitude != null) {

                        TrackerData td = new TrackerData();
                        td.setImei(imei);
                        td.setLatitude(d.latitude);
                        td.setLongitude(d.longitude);
                        td.setSpeedKph(d.speedKph != null ? d.speedKph : 0.0);
                        td.setCourse(d.course);
                        td.setGpsTime(d.gpsTime != null ? d.gpsTime : LocalDateTime.now());
                        td.setRawHex(hex);

                        trackerService.save(td);

                        System.out.println("[GPS] Saved → " + imei +
                                " LAT=" + d.latitude +
                                " LNG=" + d.longitude +
                                " SPEED=" + td.getSpeedKph());
                    }

                } else {
                    // UNKNOWN PACKET
                    if (!validTrackerDetected) {
                        System.out.println("[SECURITY] Unrecognized packet from unverified source: " + clientIP);
                        recordFailedAttempt(clientIP);
                    } else {
                        System.out.println("[WARN] Unrecognized packet from valid tracker. Logged for analysis.");
                        trackerService.saveRaw(hex);
                    }
                }

                // ACK IF NEEDED
                byte[] ack = Gt06Decoder.buildAck(pkt);
                if (ack != null) {
                    out.write(ack);
                    out.flush();
                    System.out.println("[ACK] Sent");
                }
            }

        } catch (Exception e) {
            System.err.println("[TCP] ERROR from " + clientIP + ": " + e.getMessage());
        } finally {
            SessionManager.remove(socket);
            System.out.println("[TCP] Connection closed: " + socket.getRemoteSocketAddress());
        }
    }
}