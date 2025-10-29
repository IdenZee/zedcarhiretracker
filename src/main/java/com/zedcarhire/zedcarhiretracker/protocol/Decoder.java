package com.zedcarhire.zedcarhiretracker.protocol;

import java.net.Socket;

public interface Decoder {
    String id();
    Decoded decode(byte[] pkt, Socket socket);
}
