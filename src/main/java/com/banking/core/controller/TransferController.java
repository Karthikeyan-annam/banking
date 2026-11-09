package com.banking.core.controller;

import com.banking.core.dto.request.TransferRequest;
import com.banking.core.dto.response.TransferResponse;
import com.banking.core.service.TransferService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller exposing endpoints for peer-to-peer (P2P) transfers.
 */
@RestController
@RequestMapping("/api/v1/transfers")
@RequiredArgsConstructor
@Slf4j
public class TransferController {

    private final TransferService transferService;

    /**
     * Executes a peer-to-peer fund transfer.
     *
     * @param request validated transfer request
     * @param httpReq raw HTTP request to capture caller IP address
     * @return 201 Created with the transfer execution details
     */
    @PostMapping
    public ResponseEntity<TransferResponse> executeTransfer(
        @Valid @RequestBody TransferRequest request,
        HttpServletRequest httpReq
    ) {
        String clientIp = extractClientIp(httpReq);
        log.info("P2P transfer request initiated from source account {} for amount {}",
            request.getSourceAccountId(), request.getAmount());

        TransferResponse response = transferService.transferFunds(request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves transfer details by transaction ID or reference UUID.
     *
     * @param id transaction UUID or transfer reference UUID
     * @return 200 OK with transfer details
     */
    @GetMapping("/{id}")
    public ResponseEntity<TransferResponse> getTransfer(@PathVariable UUID id) {
        TransferResponse response = transferService.getTransferDetails(id);
        return ResponseEntity.ok(response);
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
