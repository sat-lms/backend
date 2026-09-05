package com.sat.lms.member.entity;

public class InvalidMemberStateException extends RuntimeException {
    public InvalidMemberStateException(String message) {
        super(message);
    }
}
