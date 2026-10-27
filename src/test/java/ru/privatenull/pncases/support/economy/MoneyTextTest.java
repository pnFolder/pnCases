package ru.privatenull.pncases.support.economy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoneyTextTest {

    @Test
    void zeroFractionsAreRemovedWhateverTheCurrencyLooksLike() {
        assertEquals("500 монет", MoneyText.stripZeroFraction("500.0 монет"));
        assertEquals("$500", MoneyText.stripZeroFraction("$500.00"));
        assertEquals("1,000 монет", MoneyText.stripZeroFraction("1,000.00 монет"));
        assertEquals("2 500 монет", MoneyText.stripZeroFraction("2 500,0 монет"));
    }

    @Test
    void realFractionsAndPlainNumbersStayUntouched() {
        assertEquals("12.5 монет", MoneyText.stripZeroFraction("12.5 монет"));
        assertEquals("0.05 монет", MoneyText.stripZeroFraction("0.05 монет"));
        assertEquals("1.0.1", MoneyText.stripZeroFraction("1.0.1"));
        assertEquals("100 монет", MoneyText.stripZeroFraction("100 монет"));
        assertEquals("", MoneyText.stripZeroFraction(null));
    }
}