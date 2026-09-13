package com.motadev.clone_reddit.shared.exception;

public class ResourceInvalidException extends RuntimeException {
    public ResourceInvalidException(String message) {
        super(message);
    }
}
