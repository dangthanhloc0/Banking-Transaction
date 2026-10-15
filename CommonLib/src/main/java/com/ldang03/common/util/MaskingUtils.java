package com.ldang03.common.util;

/**
 * Masks sensitive values before they reach logs or error messages.
 */
public final class MaskingUtils {

    private static final int VISIBLE_DIGITS = 4;
    private static final String MASK = "****";

    private MaskingUtils() {
    }

    /**
     * Keeps only the last 4 characters: "0123456789" -> "****6789".
     * Values of 4 characters or less are fully masked; null stays null.
     */
    public static String maskAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return null;
        }
        String value = accountNumber.trim();
        if (value.length() <= VISIBLE_DIGITS) {
            return MASK;
        }
        return MASK + value.substring(value.length() - VISIBLE_DIGITS);
    }
}
