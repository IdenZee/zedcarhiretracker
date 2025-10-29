package com.zedcarhire.zedcarhiretracker.protocol;

import java.net.Socket;
import java.time.LocalDateTime;

public class FallbackDecoder implements Decoder {
    @Override public String id(){ return "FALLBACK"; }

    @Override
    public Decoded decode(byte[] pkt, Socket socket) {
        Decoded d = new Decoded();
        d.rawHex = toHex(pkt);
        // Heuristic: look for 15+ digit BCD-ish sequence (IMEI)
        String hex = d.rawHex;
        // scan for 8 bytes that look like imei BCD (starts with 35.. typical)
        for (int i=0;i+16<=hex.length();i+=2){
            String maybe = hex.substring(i, i+16);
            // crude rule: contains mostly digits (0-9)
            if (maybe.matches("[0-9A-F]{16}")){
                // attempt to decode BCD-looking IMEI
                StringBuilder imei = new StringBuilder();
                for(int j=0;j<16;j+=2){
                    imei.append(maybe.charAt(j));
                    imei.append(maybe.charAt(j+1));
                }
                d.imei = imei.toString();
                break;
            }
        }
        // We do not try to fill lat/lng here; store raw and manual inspect later
        return d;
    }

    private static String toHex(byte[] pkt){
        StringBuilder sb=new StringBuilder();
        for(byte b:pkt) sb.append(String.format("%02X",b));
        return sb.toString();
    }
}
