package com.limidus.currencyconverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.client.TreasuryApiClient.TreasuryRateRow;
import com.limidus.currencyconverter.domain.Transaction;
import com.limidus.currencyconverter.exception.InvalidRequestException;
import com.limidus.currencyconverter.repository.ConversionRepository;
import com.limidus.currencyconverter.service.CurrencyConversionService;
import com.limidus.currencyconverter.service.ExchangeRateService;
import com.limidus.currencyconverter.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CurrencyConversionServiceTest {

    @Mock
    private TransactionService transactionService;

    @Mock
    private ExchangeRateService exchangeRateService;

    @Mock
    private ConversionRepository conversionRepository;

    @InjectMocks
    private CurrencyConversionService currencyConversionService;

    @Test
    void convert_multipliesAndRoundsToTwoDecimals() {
        UUID id = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(id)
                .description("Desk")
                .transactionDate(LocalDate.of(2024, 1, 10))
                .amountUsd(new BigDecimal("100.00"))
                .build();
        when(transactionService.getById(id)).thenReturn(tx);
        when(exchangeRateService.findBestRate("Canada-Dollar", tx.getTransactionDate()))
                .thenReturn(Optional.of(new TreasuryRateRow("Canada-Dollar", "1.355", "2024-01-01")));

        var response = currencyConversionService.getConvertedPurchase(id, "Canada-Dollar");

        assertThat(response.getConvertedAmount()).isEqualByComparingTo("135.50");
        assertThat(response.getExchangeRateUsed()).isEqualByComparingTo("1.355000");
        verify(conversionRepository).save(any());
    }

    @Test
    void convert_whenNoRate_throwsWithSpecMessage() {
        UUID id = UUID.randomUUID();
        var tx = Transaction.builder()
                .id(id)
                .description("Desk")
                .transactionDate(LocalDate.of(2024, 1, 10))
                .amountUsd(new BigDecimal("10.00"))
                .build();
        when(transactionService.getById(id)).thenReturn(tx);
        when(exchangeRateService.findBestRate("Nowhere-Dollar", tx.getTransactionDate()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> currencyConversionService.getConvertedPurchase(id, "Nowhere-Dollar"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(CurrencyConversionService.CONVERSION_UNAVAILABLE);
    }
}
