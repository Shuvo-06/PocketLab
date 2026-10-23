package com.pocketlab.app.cloud;

/** A sync failure with a category, so callers can decide whether trying again could help. */
public final class SyncException extends Exception {

    public enum Category { NOT_CONFIGURED, NOT_SIGNED_IN, NETWORK, AUTH, PERMISSION, OTHER }

    private final Category category;

    public SyncException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public Category getCategory() { return category; }

    /** Network trouble and unknown errors may pass; configuration, sign-in and permission problems will not. */
    public boolean isTransient() {
        return category == Category.NETWORK || category == Category.OTHER;
    }
}
