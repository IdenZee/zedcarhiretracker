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
            // LOGIN
            if (proto == 0x01) {
                out.imei = decodeImeiFromLogin(pkt);
                return out;
            }

            // GPS packets
            if (proto == 0x12 || proto == 0x22) {
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

            // HEARTBEAT (0x13) or unknown packets could be added here as fallback
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

    public static byte[] buildAck(byte[] pkt) {
        if (pkt.length < 5) return null;
        int proto = pkt[3] & 0xFF;
        if (proto == 0x01) {
            return new byte[]{
                    0x78, 0x78, 0x05, 0x01, 0x00, 0x01,
                    (byte) 0xD9, (byte) 0xDC, 0x0D, 0x0A
            };
        }
        return null;
    }
}
