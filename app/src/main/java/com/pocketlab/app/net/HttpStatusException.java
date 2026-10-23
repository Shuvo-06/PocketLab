package com.pocketlab.app.net;

import java.io.IOException;

/** The server answered, but not with success. */
public class HttpStatusException extends IOException {
    private final int code;

    public HttpStatusException(int code) {
        super("HTTP " + code);
        this.code = code;
    }

    public int getCode() { return code; }
}
