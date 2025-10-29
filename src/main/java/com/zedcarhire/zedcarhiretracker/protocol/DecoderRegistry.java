package com.zedcarhire.zedcarhiretracker.protocol;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class DecoderRegistry {

    private final List<Decoder> decoders = new ArrayList<>();

    public DecoderRegistry() {
        // Register supported decoders here
        decoders.add(new Gt06Decoder());
        // You can add future decoders like:
        // decoders.add(new Tk103Decoder());
    }

    /**
     * Tries each decoder until one returns a non-null result
     */
    public Decoded decode(byte[] pkt, Socket socket) {
        for (Decoder decoder : decoders) {
            Decoded result = decoder.decode(pkt, socket);
            if (result != null) {
                result.protocol = decoder.id();
                return result;
            }
        }
        return null;
    }
}
