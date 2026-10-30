package com.assetmanager.exception;

/** Thrown when a record does not exist OR belongs to another user (we never reveal which). */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) { super(message); }
}
