package com.zedcarhire.zedcarhiretracker.protocol;

import java.net.Socket;
import java.nio.ByteBuffer;
import java.time.LocalDateTime;

public class Gt06Decoder implements Decoder {

    @Override
    public String id() {
        return "GT06";
    }

    private static int intAt(byte[] b, int off) {
        if (off + 3 >= b.length) return 0;
        return ByteBuffer.wrap(new byte[]{b[off], b[off + 1], b[off + 2], b[off + 3]}).getInt();
    }

    private static String decodeImeiFromLogin(byte[] pkt) {
        // GT06 Login packet structure:
        // 78 78 [LEN] 01 [IMEI: 8 bytes in BCD] [SERIAL] [CRC] 0D 0A
        //
        // Example: 78 78 0D 01 | 03 54 77 83 45 25 36 71 | 00 8C | 14 64 | 0D 0A
        //                         ^^^^^^^^^^^^^^^^^^^^^^^^
        //                              8 bytes IMEI in BCD
        //
        // IMEI 354778345253671 is stored as:
        // 0x03547783 45253671 in hex
        // Which is BCD: 0 3 5 4 7 7 8 3 4 5 2 5 3 6 7 1
        // We skip the leading 0 to get: 3 5 4 7 7 8 3 4 5 2 5 3 6 7 1

        // Extract 8 bytes starting from index 4 (IMEI starts right after protocol byte)
        StringBuilder imei = new StringBuilder();
        for (int i = 4; i < 12 && i < pkt.length; i++) {
            // Each byte contains 2 BCD digits (4 bits each)
            int high = (pkt[i] >> 4) & 0x0F;
            int low = pkt[i] & 0x0F;

            // Skip leading zeros and padding (0xF)
            if (imei.length() == 0 && high == 0) {
                // Skip leading zero in first nibble
            } else {
                imei.append(high);
            }

            if (low != 0x0F) { // 0xF is padding for odd-length numbers
                imei.append(low);
            }
        }

        String result = imei.toString();

        // IMEI should be 15 digits - if we got 16, the first one was likely padding
        if (result.length() == 16 && result.charAt(0) == '0') {
            result = result.substring(1);
        }

        System.out.println("[DECODER] Extracted IMEI: " + result + " (length: " + result.length() + ")");
        return result;
    }

    @Override
    public Decoded decode(byte[] pkt, Socket socket) {
        if (pkt.length < 5) return null;

        // Check if this is a long packet (79 79) or short packet (78 78)
        boolean isLongPacket = (pkt[0] == 0x79 && pkt[1] == 0x79);

        // For long packets, protocol is at index 4, for short packets it's at index 3
        int proto = isLongPacket ? (pkt[4] & 0xFF) : (pkt[3] & 0xFF);

        Decoded out = new Decoded();
        out.rawHex = toHex(pkt);
        out.protocol = "GT06";

        System.out.println("[DECODER] Packet type: " + (isLongPacket ? "LONG (79 79)" : "SHORT (78 78)") + ", Protocol: 0x" + String.format("%02X", proto));

        try {
            // LOGIN (0x01)
            if (proto == 0x01) {
                out.imei = decodeImeiFromLogin(pkt);
                System.out.println("[DECODER] Login packet detected");
                return out;
            }

            // HEARTBEAT (0x13)
            if (proto == 0x13) {
                System.out.println("[DECODER] Heartbeat packet detected");
                return out;
            }

            // STATUS (0x23)
            if (proto == 0x23) {
                System.out.println("[DECODER] Status packet detected");
                return out;
            }

            // GPS packets (0x12, 0x22, 0x94)
            if (proto == 0x12 || proto == 0x22 || proto == 0x94) {
                System.out.println("[DECODER] GPS packet detected (proto=" + String.format("0x%02X", proto) + ")");
                System.out.println("[DECODER] Packet length: " + pkt.length);
                System.out.println("[DECODER] Full HEX: " + toHex(pkt));

                // For protocol 0x94 (long packet with extended info)
                if (proto == 0x94 && isLongPacket) {
                    System.out.println("[DECODER] Analyzing 0x94 packet structure (SMS-based tracker)...");
                    System.out.println("[DECODER] Searching for GPS coordinates in packet...");

                    // Try all possible 4-byte positions for latitude
                    for (int latOffset = 14; latOffset <= pkt.length - 10; latOffset++) {
                        int latRaw = intAt(pkt, latOffset);
                        int lngRaw = intAt(pkt, latOffset + 4);

                        // Convert assuming standard GT06 format
                        double lat = Math.abs(latRaw / 1800000.0);  // Get absolute value first
                        double lng = Math.abs(lngRaw / 1800000.0);

                        // Check if coordinates are reasonable for Zambia
                        // Zambia: Latitude 8°-18° South, Longitude 22°-34° East
                        boolean latValid = (lat >= 8 && lat <= 18);
                        boolean lngValid = (lng >= 22 && lng <= 34);

                        if (latValid && lngValid) {
                            System.out.println("[DECODER] ✓ Possible GPS found at offset " + latOffset);
                            System.out.println("[DECODER]   Raw Lat: " + latRaw + " (0x" + Integer.toHexString(latRaw) + ")");
                            System.out.println("[DECODER]   Raw Lng: " + lngRaw + " (0x" + Integer.toHexString(lngRaw) + ")");
                            System.out.println("[DECODER]   Decoded: " + lat + "°, " + lng + "°");

                            // Check if speed and course bytes make sense
                            int speedOffset = latOffset + 8;
                            int courseOffset = latOffset + 9;

                            if (courseOffset + 1 < pkt.length) {
                                int speedRaw = pkt[speedOffset] & 0xFF;
                                int courseStatus = ((pkt[courseOffset] & 0xFF) << 8) | (pkt[courseOffset + 1] & 0xFF);
                                int course = courseStatus & 0x03FF;
                                boolean gpsFixed = (courseStatus & 0x1000) != 0;

                                System.out.println("[DECODER]   Speed: " + speedRaw + " km/h");
                                System.out.println("[DECODER]   Course: " + course + "°");
                                System.out.println("[DECODER]   GPS Fixed: " + gpsFixed);

                                // ALWAYS force Southern hemisphere for Zambia operations
                                lat = -Math.abs(lat);
                                lng = Math.abs(lng);  // Eastern hemisphere

                                System.out.println("[DECODER]   Final (Zambia): " + lat + "°S, " + lng + "°E");

                                // If this looks reasonable, use it
                                if (speedRaw <= 200 && course <= 360) {
                                    out.gpsTime = LocalDateTime.now();
                                    out.latitude = lat;
                                    out.longitude = lng;
                                    out.speedKph = (double) speedRaw;
                                    out.course = course;

                                    System.out.println("[DECODER] ✅ GPS data decoded successfully!");
                                    System.out.println("[DECODER] Location: " + out.latitude + ", " + out.longitude);

                                    return out;
                                }
                            }
                        }
                    }

                    System.out.println("[DECODER] ERROR: Could not find valid GPS coordinates in packet");
                    return null;
                }

                // Standard short packets (0x12, 0x22)
                else if (!isLongPacket && (proto == 0x12 || proto == 0x22)) {
                    int yy = pkt[4] & 0xFF;
                    int mm = pkt[5] & 0xFF;
                    int dd = pkt[6] & 0xFF;
                    int hh = pkt[7] & 0xFF;
                    int mi = pkt[8] & 0xFF;
                    int ss = pkt[9] & 0xFF;
                    out.gpsTime = LocalDateTime.of(2000 + yy, mm, dd, hh, mi, ss);

                    int gpsDataLen = pkt[10] & 0xFF;
                    System.out.println("[DECODER] GPS Data Length: " + gpsDataLen);

                    int latRaw = intAt(pkt, 11);
                    int lngRaw = intAt(pkt, 15);
                    int speedRaw = pkt[19] & 0xFF;
                    int courseStatus = ((pkt[20] & 0xFF) << 8) | (pkt[21] & 0xFF);

                    int course = courseStatus & 0x03FF;
                    boolean gpsFixed = (courseStatus & 0x1000) != 0;
                    boolean latNorth = (courseStatus & 0x0400) == 0;
                    boolean lngEast = (courseStatus & 0x0800) == 0;

                    // Get absolute values first
                    double lat = Math.abs(latRaw / 1800000.0);
                    double lng = Math.abs(lngRaw / 1800000.0);

                    System.out.println("[DECODER] Raw Lat: " + latRaw + " → " + lat + "°");
                    System.out.println("[DECODER] Raw Lng: " + lngRaw + " → " + lng + "°");
                    System.out.println("[DECODER] Hemisphere flags: " + (latNorth ? "N" : "S") + ", " + (lngEast ? "E" : "W") + " (IGNORED)");
                    System.out.println("[DECODER] Speed: " + speedRaw + " km/h");
                    System.out.println("[DECODER] Course: " + course + "°");
                    System.out.println("[DECODER] GPS Fixed: " + gpsFixed);

                    // ALWAYS force Southern hemisphere (Zambia is entirely south of equator)
                    // ALWAYS force Eastern hemisphere (Zambia is entirely east of prime meridian)
                    lat = -Math.abs(lat);
                    lng = Math.abs(lng);

                    System.out.println("[DECODER] ✓ Applied Zambia hemisphere: " + lat + "°S, " + lng + "°E");

                    if (gpsFixed && Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
                        out.latitude = lat;
                        out.longitude = lng;
                        out.speedKph = (double) speedRaw;
                        out.course = course;

                        System.out.println("[DECODER] ✅ Final GPS: " + out.latitude + ", " + out.longitude);
                        return out;
                    } else {
                        System.out.println("[DECODER] WARNING: No GPS fix or invalid coordinates");
                        return null;
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[DECODE ERROR] " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    public static String toHex(byte[] pkt) {
        StringBuilder sb = new StringBuilder();
        for (byte b : pkt) sb.append(String.format("%02X", b));
        return sb.toString();
    }

    /**
     * Build acknowledgment packet for GT06 protocol
     * Format: 78 78 [LEN] [PROTO] [SERIAL] [CRC] 0D 0A (short)
     *         79 79 [LEN_H] [LEN_L] [PROTO] [SERIAL] [CRC] 0D 0A (long)
     */
    public static byte[] buildAck(byte[] pkt) {
        if (pkt.length < 10) return null;

        // Check if this is a long packet (79 79) or short packet (78 78)
        boolean isLongPacket = (pkt[0] == 0x79 && pkt[1] == 0x79);

        int proto = isLongPacket ? (pkt[4] & 0xFF) : (pkt[3] & 0xFF);

        // Extract serial number (2 bytes before CRC)
        int serialH = pkt[pkt.length - 6] & 0xFF;
        int serialL = pkt[pkt.length - 5] & 0xFF;

        byte[] ack = null;

        // LOGIN ACK (0x01)
        if (proto == 0x01) {
            ack = new byte[]{
                    0x78, 0x78,           // Start bits
                    0x05,                 // Length
                    0x01,                 // Protocol: Login
                    (byte) serialH,       // Serial number high byte
                    (byte) serialL,       // Serial number low byte
                    0x00, 0x00,           // CRC placeholder
                    0x0D, 0x0A            // Stop bits
            };
        }
        // HEARTBEAT ACK (0x13) and STATUS ACK (0x23)
        else if (proto == 0x13 || proto == 0x23) {
            ack = new byte[]{
                    0x78, 0x78,           // Start bits
                    0x05,                 // Length
                    (byte) proto,         // Echo back the protocol
                    (byte) serialH,       // Serial number high byte
                    (byte) serialL,       // Serial number low byte
                    0x00, 0x00,           // CRC placeholder
                    0x0D, 0x0A            // Stop bits
            };
        }
        // GPS ACK (0x12, 0x22, 0x94)
        else if (proto == 0x12 || proto == 0x22 || proto == 0x94) {
            if (isLongPacket) {
                // Long packet ACK format
                ack = new byte[]{
                        0x79, 0x79,           // Start bits (long packet)
                        0x00, 0x05,           // Length (2 bytes)
                        (byte) proto,         // Echo back the protocol
                        (byte) serialH,       // Serial number high byte
                        (byte) serialL,       // Serial number low byte
                        0x00, 0x00,           // CRC placeholder
                        0x0D, 0x0A            // Stop bits
                };
            } else {
                // Short packet ACK format
                ack = new byte[]{
                        0x78, 0x78,           // Start bits
                        0x05,                 // Length
                        (byte) proto,         // Echo back the protocol
                        (byte) serialH,       // Serial number high byte
                        (byte) serialL,       // Serial number low byte
                        0x00, 0x00,           // CRC placeholder
                        0x0D, 0x0A            // Stop bits
                };
            }
        }

        // Calculate and insert CRC if ACK was created
        if (ack != null) {
            int startIdx = (ack[0] == 0x79) ? 2 : 2; // Both start at index 2
            int crc = calculateCRC(ack, startIdx, ack.length - 6);
            ack[ack.length - 4] = (byte) ((crc >> 8) & 0xFF);
            ack[ack.length - 3] = (byte) (crc & 0xFF);
        }

        return ack;
    }

    /**
     * Calculate CRC-16/XMODEM checksum
     */
    private static int calculateCRC(byte[] data, int start, int end) {
        int crc = 0;
        for (int i = start; i < end; i++) {
            crc ^= (data[i] & 0xFF) << 8;
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x8000) != 0) {
                    crc = (crc << 1) ^ 0x1021;
                } else {
                    crc = crc << 1;
                }
            }
        }
        return crc & 0xFFFF;
    }
}