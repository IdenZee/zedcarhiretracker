package com.zedcarhire.zedcarhiretracker.protocol;

public class Detector {

    /** quick look at header bytes and length */
    public static String detect(byte[] pkt){
        if (pkt.length < 4) return "FALLBACK";
        // GT06 / many Chinese trackers: starts with 0x78 0x78
        if ((pkt[0] & 0xFF) == 0x78 && (pkt[1] & 0xFF) == 0x78){
            int proto = pkt[3] & 0xFF;
            if (proto == 0x01 || proto == 0x12 || proto == 0x22) return "GT06";
            // some send 0x80 login etc - still GT06 family
            if (proto >= 0x80 && proto <= 0x90) return "GT06";
            return "GT06"; // default for 0x78 0x78 family
        }
        // Teltonika / AVL: often starts with 0x00 0x00 0x00 0x1F (length) or 0x08 0x00 etc
        if ((pkt[0] & 0xFF) == 0x00 && pkt.length > 8) {
            // crude check for Teltonika AVL (not exhaustive)
            return "TELTONIKA";
        }
        // ASCII trackers (SinoTrack web clients) sometimes start with IMEI digits ASCII like 'imei:'
        String s = new String(pkt);
        if (s.startsWith("imei:") || s.toLowerCase().contains("imei")) return "ASCII-IMEI";
        return "FALLBACK";
    }
}
