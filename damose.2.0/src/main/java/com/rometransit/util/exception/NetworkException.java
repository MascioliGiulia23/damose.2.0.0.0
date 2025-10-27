package com.rometransit.util.exception;

public class NetworkException extends TransitException {
    public NetworkException(String message) {
        super(message);
    }

    public NetworkException(String message, Throwable cause) {
        super(message, cause);
    }
}