package com.sun.note.dto;

public record LoginResponse(String accessToken, UserInfo user) {
    public static LoginResponse of(String accessToken, UserInfo user) {
        return new LoginResponse(accessToken, user);
    }

    public static record UserInfo(String name, String email) {
        public static UserInfo of(String name, String email) {
            return new UserInfo(name, email);
        }
    }
}
