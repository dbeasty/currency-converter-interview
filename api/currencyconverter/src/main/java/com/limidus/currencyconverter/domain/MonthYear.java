package com.limidus.currencyconverter.domain;

import com.limidus.currencyconverter.exception.InvalidRequestException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MonthYear {

    private static final Pattern PATTERN = Pattern.compile("^(\\d{2})-(\\d{4})$");

    private final YearMonth yearMonth;

    private MonthYear(YearMonth yearMonth) {
        this.yearMonth = yearMonth;
    }

    public static MonthYear parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new InvalidRequestException("month is required");
        }
        Matcher matcher = PATTERN.matcher(raw.trim());
        if (!matcher.matches()) {
            throw new InvalidRequestException("month must be MM-YYYY");
        }
        int month = Integer.parseInt(matcher.group(1));
        int year = Integer.parseInt(matcher.group(2));
        try {
            return new MonthYear(YearMonth.of(year, month));
        } catch (DateTimeException e) {
            throw new InvalidRequestException("month must be MM-YYYY");
        }
    }

    public String formatted() {
        return "%02d-%04d".formatted(yearMonth.getMonthValue(), yearMonth.getYear());
    }

    public LocalDate rangeStart() {
        return yearMonth.atDay(1);
    }

    public LocalDate rangeEndExclusive() {
        return yearMonth.plusMonths(1).atDay(1);
    }

    public void validateNotInFuture() {
        if (yearMonth.isAfter(YearMonth.now())) {
            throw new InvalidRequestException("month cannot be in the future");
        }
    }
}
