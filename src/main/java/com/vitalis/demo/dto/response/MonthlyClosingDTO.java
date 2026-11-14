package com.vitalis.demo.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record MonthlyClosingDTO(int year, int month, String timeZone, int movementDays,
                               List<Day> days, Totals totals, Summary summary, List<String> warnings) {
    public record Day(LocalDate date, BigDecimal waterProfit, BigDecimal gasProfit,
                      BigDecimal purchases, BigDecimal expenses, BigDecimal dailyBalance,
                      BigDecimal accumulatedBalance, BigDecimal withdrawals) { }
    public record Totals(BigDecimal waterProfit, BigDecimal gasProfit, BigDecimal purchases,
                         BigDecimal expenses, BigDecimal dailyBalance, BigDecimal accumulatedBalance,
                         BigDecimal withdrawals) { }
    public record Metric(BigDecimal value, BigDecimal percentage) { }
    public record Summary(Metric revenue, Metric purchases, Metric expenses, Metric profitMargin) { }
}
