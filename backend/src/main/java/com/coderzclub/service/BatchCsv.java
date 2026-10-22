package com.coderzclub.service;

import java.util.Locale;

public final class BatchCsv {
    private BatchCsv() {}

    public static String escape(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value;
        if (needsFormulaGuard(leadingSignificant(escaped))) {
            escaped = "'" + escaped;
        }
        boolean quote = escaped.indexOf(',') >= 0
            || escaped.indexOf('"') >= 0
            || escaped.indexOf('\n') >= 0
            || escaped.indexOf('\r') >= 0;
        if (quote) {
            escaped = "\"" + escaped.replace("\"", "\"\"") + "\"";
        }
        return escaped;
    }

    static boolean needsFormulaGuard(String value) {
        if (value.isEmpty()) {
            return false;
        }
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@'
            || first == '\t' || first == '\r';
    }

    static String leadingSignificant(String value) {
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            if (c == ' ' || c == '\t' || c == '\r' || c == '\n' || c == '\u0000' || c == '\ufeff') {
                i++;
                continue;
            }
            return value.substring(i);
        }
        return "";
    }

    public static String percentage(int solved, int total) {
        if (total <= 0) {
            return "0.00";
        }
        return String.format(Locale.US, "%.2f", (100.0 * solved) / total);
    }
}
