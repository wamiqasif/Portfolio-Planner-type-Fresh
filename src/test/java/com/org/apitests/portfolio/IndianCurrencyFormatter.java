package com.org.apitests.portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Formats a rupee amount the same way the Planner API's own {@code *_fmt}
 * fields do (Indian digit grouping - last 3 digits, then groups of 2, e.g.
 * {@code 10000000 -> "1,00,00,000"}), confirmed against the live sample
 * ({@code planner-fresh-response.json}: {@code monthly_amount=50000} ->
 * {@code "₹50,000"}, {@code lumpsum_amount=10000000} -> {@code "₹1,00,00,000"}).
 * Used by {@code InvestmentInputValidator} to check that {@code
 * input_summary_data.text_fmt} actually reflects the request's amounts,
 * rather than just asserting the field is non-blank.
 */
final class IndianCurrencyFormatter {

    private IndianCurrencyFormatter() {
    }

    /** Digit-grouped, no currency symbol and no decimal part (whole rupees only - every amount this suite formats is a whole-rupee input). */
    static String groupDigits(long amount) {
        boolean negative = amount < 0;
        String digits = new BigDecimal(Math.abs(amount)).setScale(0, RoundingMode.UNNECESSARY).toPlainString();

        if (digits.length() <= 3) {
            return (negative ? "-" : "") + digits;
        }

        String lastThree = digits.substring(digits.length() - 3);
        String remaining = digits.substring(0, digits.length() - 3);

        StringBuilder grouped = new StringBuilder();
        int count = 0;
        for (int i = remaining.length() - 1; i >= 0; i--) {
            grouped.append(remaining.charAt(i));
            count++;
            if (count == 2 && i != 0) {
                grouped.append(',');
                count = 0;
            }
        }
        return (negative ? "-" : "") + grouped.reverse() + "," + lastThree;
    }

    /** With the {@code ₹} prefix the Planner API's {@code _fmt} fields use. */
    static String withRupeeSymbol(long amount) {
        return "₹" + groupDigits(amount);
    }
}
