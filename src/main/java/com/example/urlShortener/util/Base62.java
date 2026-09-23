package com.example.urlShortener.util;

public class Base62 {
    private static final String CHARACTERS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final int BASE = CHARACTERS.length();
    private static final int MIN_LENGTH = 3;

    public static String encode(long input) {
        StringBuilder sb = new StringBuilder();
        while (input > 0) {
            sb.append(CHARACTERS.charAt((int) (input % BASE)));
            input /= BASE;
        }
        
        while (sb.length() < MIN_LENGTH) {
            sb.append(CHARACTERS.charAt(0));
        }
        
        return sb.reverse().toString();
    }

    public static long decode(String input) {
        long decoded = 0;
        for (int i = 0; i < input.length(); i++) {
            decoded = decoded * BASE + CHARACTERS.indexOf(input.charAt(i));
        }
        return decoded;
    }
}