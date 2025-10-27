package com.rometransit.util.exception;

public class TransitException extends Exception {
    public TransitException(String message) {
        super(message);
    }

    public TransitException(String message, Throwable cause) {
        super(message, cause);
    }
}