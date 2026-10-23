package com.pocketlab.app.export;

import java.io.IOException;

/** The file is not a usable PocketLab file. The message is meant for the user. */
public class ImportException extends IOException {
    public ImportException(String message) {
        super(message);
    }
}
