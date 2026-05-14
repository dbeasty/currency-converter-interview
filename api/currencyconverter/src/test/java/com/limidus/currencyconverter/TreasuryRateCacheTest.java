package com.limidus.currencyconverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.client.TreasuryApiClient;
import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import com.limidus.currencyconverter.service.TreasuryRateCache;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TreasuryRateCacheTest {

    @Mock
    private TreasuryApiClient treasuryApiClient;

    @InjectMocks
    private TreasuryRateCache treasuryRateCache;

    @Test
    void load_usesSixMonthWindowStart() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        LocalDate asOf = LocalDate.of(2026, 5, 14);
        LocalDate windowStart = LocalDate.of(2023, 12, 15);
        var row = new TreasuryRateRow("Canada-Dollar", "1.3", "2024-03-31");
        when(treasuryApiClient.fetchBestRateWithinWindow("Canada-Dollar", purchaseDate, windowStart))
                .thenReturn(Optional.of(row));

        TreasuryRateRow result = treasuryRateCache.load("Canada-Dollar", purchaseDate, asOf);

        assertThat(result).isSameAs(row);
        verify(treasuryApiClient).fetchBestRateWithinWindow("Canada-Dollar", purchaseDate, windowStart);
    }

    @Test
    void load_returnsNullWhenTreasuryEmpty() {
        LocalDate purchaseDate = LocalDate.of(2024, 6, 15);
        LocalDate asOf = LocalDate.of(2026, 5, 14);
        LocalDate windowStart = LocalDate.of(2023, 12, 15);
        when(treasuryApiClient.fetchBestRateWithinWindow("X", purchaseDate, windowStart))
                .thenReturn(Optional.empty());

        TreasuryRateRow result = treasuryRateCache.load("X", purchaseDate, asOf);

        assertThat(result).isNull();
    }
}
