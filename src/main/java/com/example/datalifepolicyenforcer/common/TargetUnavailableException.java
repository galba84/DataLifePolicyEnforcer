package com.example.datalifepolicyenforcer.common;

public class TargetUnavailableException extends RuntimeException {
    public TargetUnavailableException() {
        super("Target database unavailable or metadata access denied.");
    }
}

