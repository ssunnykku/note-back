package com.sun.note.dto;

public record SignupRequest(String name, String email, String password, String passwordConfirm) {
}