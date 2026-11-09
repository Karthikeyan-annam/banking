package com.banking.core.dto.request;

import com.banking.core.domain.enums.TransactionStatus;
import com.banking.core.domain.enums.TransactionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Filter criteria for querying account transaction history.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionFilterCriteria {

    private TransactionType type;

    private TransactionStatus status;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant endDate;
}
