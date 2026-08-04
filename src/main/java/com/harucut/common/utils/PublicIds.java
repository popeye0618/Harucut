package com.harucut.common.utils;

import java.security.SecureRandom;

public class PublicIds {

    private static final char[] ALPHABET =
            "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PublicIds() {
    }

    public static String generate() {
        char[] buf = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            buf[i] = ALPHABET[RANDOM.nextInt(ALPHABET.length)];
        }

        return new String(buf);
    }
}
