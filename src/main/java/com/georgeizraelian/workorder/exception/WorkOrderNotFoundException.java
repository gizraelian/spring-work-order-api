package com.georgeizraelian.workorder.exception;

public class WorkOrderNotFoundException extends RuntimeException {

    public WorkOrderNotFoundException(Long id) {
        super("Work order " + id + " was not found");
    }
}
