package com.vitalis.demo.util;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.text.Normalizer;
import java.util.Locale;

public final class SearchText {
    private SearchText() {}

    public static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replaceAll("[\\s\\p{Z}]+", "").toLowerCase(Locale.ROOT);
    }

    public static String pattern(String value) {
        return "%" + normalize(value).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    public static Expression<String> normalized(CriteriaBuilder cb, Expression<String> value) {
        Expression<String> result = cb.function("translate", String.class, cb.lower(value),
                cb.literal("áàâãäåéèêëíìîïóòôõöúùûüçñýÿ"),
                cb.literal("aaaaaaeeeeiiiiooooouuuucnyy"));
        // Explicit replace has the same deletion semantics on PostgreSQL and H2.
        for (char character : " \t\r\n\u00a0\u0300\u0301\u0302\u0303\u0308\u0327".toCharArray()) {
            result = cb.function("replace", String.class, result, cb.literal(String.valueOf(character)), cb.literal(""));
        }
        return result;
    }
}
