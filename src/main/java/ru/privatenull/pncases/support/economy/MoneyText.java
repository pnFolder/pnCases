package ru.privatenull.pncases.support.economy;

import java.util.regex.Pattern;

/** Text clean-up for amounts formatted by an economy plugin. */
public final class MoneyText {

    /** One or two zeros of a fraction right after a digit (".0", ",00"); "1,000" is thousands and is kept. */
    private static final Pattern ZERO_FRACTION = Pattern.compile("(?<=\\d)[.,]0{1,2}(?!\\d)(?![.,]\\d)");

    private MoneyText() {
    }

    /** "500.0 монет" becomes "500 монет"; "12.5 монет" and "1,000 монет" stay as they are. */
    public static String stripZeroFraction(String formatted) {
        return formatted == null ? "" : ZERO_FRACTION.matcher(formatted).replaceAll("");
    }
}