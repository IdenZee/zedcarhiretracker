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
        return ByteBuffer.wrap(new byte[]{b[off], b[off + 1], b[off + 2], b[off + 3]}).getInt();
    }

    private static String decodeImeiFromLogin(byte[] pkt) {
        StringBuilder sb = new StringBuilder();
        for (int i = 4; i < 12 && i < pkt.length; i++) {
            sb.append(String.format("%02X", pkt[i]));
        }
        return sb.toString();
    }

    @Override
    public Decoded decode(byte[] pkt, Socket socket) {
        if (pkt.length < 5) return null;

        int proto = pkt[3] & 0xFF;
        Decoded out = new Decoded();
        out.rawHex = toHex(pkt);
        out.protocol = "GT06";

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
                // Heartbeat is valid but contains no GPS data
                // Just return empty Decoded object so ACK is sent
                return out;
            }

            // STATUS (0x23)
            if (proto == 0x23) {
                System.out.println("[DECODER] Status packet detected");
                return out;
            }

            // GPS packets (0x12, 0x22)
            if (proto == 0x12 || proto == 0x22) {
                System.out.println("[DECODER] GPS packet detected (proto=" + String.format("0x%02X", proto) + ")");

                // Time block is always [4..9]
                int yy = pkt[4] & 0xFF;
                int mm = pkt[5] & 0xFF;
                int dd = pkt[6] & 0xFF;
                int hh = pkt[7] & 0xFF;
                int mi = pkt[8] & 0xFF;
                int ss = pkt[9] & 0xFF;
                out.gpsTime = LocalDateTime.of(2000 + yy, mm, dd, hh, mi, ss);

                // Device variants differ here. Try both lat/lng offsets.
                int[] latOffsets = {10, 11};
                for (int latOffset : latOffsets) {
                    try {
                        int latRaw = intAt(pkt, latOffset);
                        int lngRaw = intAt(pkt, latOffset + 4);
                        double lat = latRaw / 1800000.0;
                        double lng = lngRaw / 1800000.0;

                        // sanity check (if it fails, try next offset)
                        if (Math.abs(lat) > 0.1 && Math.abs(lng) > 0.1) {
                            out.latitude = lat;
                            out.longitude = lng;

                            int speedOff = latOffset + 8;
                            if (speedOff < pkt.length) out.speedKph = (double) (pkt[speedOff] & 0xFF);

                            int cOff = speedOff + 1;
                            if (cOff + 1 < pkt.length) {
                                int cs = ((pkt[cOff] & 0xFF) << 8) | (pkt[cOff + 1] & 0xFF);
                                out.course = cs & 0x03FF;
                            }
                            return out;
                        }
                    } catch (Exception ignored) {}
                }
            }

        } catch (Exception e) {
            System.err.println("[DECODE ERROR] " + e.getMessage());
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
     * Format: 78 78 [LEN] [PROTO] [SERIAL] [CRC] 0D 0A
     */
    public static byte[] buildAck(byte[] pkt) {
        if (pkt.length < 10) return null;

        int proto = pkt[3] & 0xFF;

        // Extract serial number (2 bytes before CRC)
        // Packet structure: 78 78 [LEN] [PROTO] [...data...] [SERIAL_H] [SERIAL_L] [CRC_H] [CRC_L] 0D 0A
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
        // GPS ACK (0x12, 0x22)
        else if (proto == 0x12 || proto == 0x22) {
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

        // Calculate and insert CRC if ACK was created
        if (ack != null) {
            int crc = calculateCRC(ack, 2, ack.length - 6);
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