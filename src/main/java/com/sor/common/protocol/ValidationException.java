package com.sor.common.protocol;

/**
 * Thrown when an incoming request fails structural or business validation
 * before it is acted upon.
 */
public class ValidationException extends Exception {

    public ValidationException(String message) {
        super(message);
    }
}
