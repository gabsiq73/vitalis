package com.vitalis.demo.service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

// Exact rational arithmetic: partial receipts are never rounded per item or day.
record ClosingFraction(BigInteger numerator, BigInteger denominator) {
    static final ClosingFraction ZERO = of(BigDecimal.ZERO);
    ClosingFraction {
        if (denominator.signum() == 0) throw new ArithmeticException("Zero denominator");
        if (denominator.signum() < 0) { numerator = numerator.negate(); denominator = denominator.negate(); }
        BigInteger gcd = numerator.gcd(denominator);
        numerator = numerator.divide(gcd); denominator = denominator.divide(gcd);
    }
    static ClosingFraction of(BigDecimal value) {
        return value.scale() >= 0 ? new ClosingFraction(value.unscaledValue(), BigInteger.TEN.pow(value.scale()))
                : new ClosingFraction(value.unscaledValue().multiply(BigInteger.TEN.pow(-value.scale())), BigInteger.ONE);
    }
    ClosingFraction add(ClosingFraction other) {
        return new ClosingFraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)), denominator.multiply(other.denominator));
    }
    ClosingFraction subtract(ClosingFraction other) { return add(new ClosingFraction(other.numerator.negate(), other.denominator)); }
    ClosingFraction multiply(ClosingFraction other) { return new ClosingFraction(numerator.multiply(other.numerator), denominator.multiply(other.denominator)); }
    ClosingFraction divide(ClosingFraction other) { return new ClosingFraction(numerator.multiply(other.denominator), denominator.multiply(other.numerator)); }
    boolean isZero() { return numerator.signum() == 0; }
    BigDecimal decimal() { return new BigDecimal(numerator).divide(new BigDecimal(denominator), MathContext.DECIMAL128); }
}
