package com.motadev.clone_reddit.media.validator;

import java.util.Locale;

final class MediaMagicBytes {

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };
    private static final byte[] GIF87A = {0x47, 0x49, 0x46, 0x38, 0x37, 0x61};
    private static final byte[] GIF89A = {0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46};
    private static final byte[] WEBP = {0x57, 0x45, 0x42, 0x50};

    private MediaMagicBytes() {
    }

    static boolean matches(String contentType, byte[] header) {
        if (contentType == null || header == null) {
            return false;
        }

        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> startsWith(header, JPEG);
            case "image/png" -> startsWith(header, PNG);
            case "image/gif" -> startsWith(header, GIF87A) || startsWith(header, GIF89A);
            case "image/webp" -> isWebp(header);
            default -> false;
        };
    }

    private static boolean isWebp(byte[] header) {
        return header.length >= 12
                && startsWith(header, RIFF)
                && header[8] == WEBP[0]
                && header[9] == WEBP[1]
                && header[10] == WEBP[2]
                && header[11] == WEBP[3];
    }

    private static boolean startsWith(byte[] header, byte[] prefix) {
        if (header.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (header[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
