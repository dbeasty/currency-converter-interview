package com.limidus.currencyconverter.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.limidus.currencyconverter.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class MonthYearTest {

    @Test
    void parse_validMonthYear() {
        MonthYear monthYear = MonthYear.parse("06-2024");

        assertThat(monthYear.formatted()).isEqualTo("06-2024");
        assertThat(monthYear.rangeStart()).isEqualTo(LocalDate.of(2024, 6, 1));
        assertThat(monthYear.rangeEndExclusive()).isEqualTo(LocalDate.of(2024, 7, 1));
    }

    @Test
    void parse_rejectsIsoFormat() {
        assertThatThrownBy(() -> MonthYear.parse("2024-06"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("month must be MM-YYYY");
    }

    @Test
    void parse_rejectsUnpaddedMonth() {
        assertThatThrownBy(() -> MonthYear.parse("6-2024"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("month must be MM-YYYY");
    }

    @Test
    void parse_rejectsInvalidMonth() {
        assertThatThrownBy(() -> MonthYear.parse("13-2024"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("month must be MM-YYYY");
    }

    @Test
    void parse_rejectsBlank() {
        assertThatThrownBy(() -> MonthYear.parse("  "))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("month is required");
    }

    @Test
    void validateNotInFuture_rejectsFutureMonth() {
        YearMonth future = YearMonth.now().plusMonths(1);
        String raw = "%02d-%04d".formatted(future.getMonthValue(), future.getYear());

        MonthYear monthYear = MonthYear.parse(raw);

        assertThatThrownBy(monthYear::validateNotInFuture)
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("month cannot be in the future");
    }
}
