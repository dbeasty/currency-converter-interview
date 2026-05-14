package com.limidus.currencyconverter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.limidus.currencyconverter.domain.Transaction;
import com.limidus.currencyconverter.dto.CreateTransactionRequest;
import com.limidus.currencyconverter.exception.InvalidRequestException;
import com.limidus.currencyconverter.repository.TransactionRepository;
import com.limidus.currencyconverter.service.TransactionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @InjectMocks
    private TransactionService transactionService;

    @Test
    void create_persistsRoundedUsd() {
        UUID id = UUID.randomUUID();
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(inv -> {
                    Transaction t = inv.getArgument(0);
                    t.setId(id);
                    return t;
                });

        var request = CreateTransactionRequest.builder()
                .description("Coffee")
                .transactionDate(LocalDate.of(2024, 3, 1))
                .purchaseAmountUsd(new BigDecimal("10.999"))
                .build();

        var response = transactionService.create(request);

        assertThat(response.getPurchaseAmountUsd()).isEqualByComparingTo("11.00");
        assertThat(response.getId()).isEqualTo(id);

        ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getAmountUsd()).isEqualByComparingTo("11.00");
    }

    @Test
    void create_rejectsSubCentAfterRounding() {
        var request = CreateTransactionRequest.builder()
                .description("X")
                .transactionDate(LocalDate.of(2024, 3, 1))
                .purchaseAmountUsd(new BigDecimal("0.004"))
                .build();

        assertThatThrownBy(() -> transactionService.create(request))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("cent");
    }
}
