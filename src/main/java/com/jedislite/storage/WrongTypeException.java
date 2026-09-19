package com.jedislite.storage;

public class WrongTypeException extends RuntimeException {

    public static final String WRONG_TYPE_MESSAGE = "WRONGTYPE Operation against a key holding the wrong kind of value";

    public WrongTypeException() {
        super(WRONG_TYPE_MESSAGE);
    }

    public WrongTypeException(String message) {
        super(message);
    }
}

