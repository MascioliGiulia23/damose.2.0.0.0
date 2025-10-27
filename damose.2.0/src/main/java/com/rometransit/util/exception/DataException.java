package com.rometransit.util.exception;

public class DataException extends TransitException {
    public DataException(String message) {
        super(message);
    }

    public DataException(String message, Throwable cause) {
        super(message, cause);
    }
}