package com.example.resay.domain.credit.service;

import com.example.resay.domain.credit.config.CreditProperties;
import com.example.resay.domain.credit.dto.CreditHistoryResponseDto;
import com.example.resay.domain.credit.dto.CreditPriceResponseDto;
import com.example.resay.domain.credit.dto.CreditSummaryResponseDto;
import com.example.resay.domain.credit.repository.CreditLedgerRepository;
import com.example.resay.domain.credit.repository.CreditWalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CreditQueryService {

    private static final int MAX_PAGE_SIZE = 50;

    private final CreditWalletRepository walletRepository;
    private final CreditLedgerRepository ledgerRepository;
    private final CreditProperties creditProperties;

    // 한 번도 크레딧을 쓰거나 받지 않은 사용자는 지갑이 없다. 잔액 0, 무료 기회 있음으로 본다
    public CreditSummaryResponseDto getSummary(Long userId) {
        return walletRepository.findById(userId)
                .map(wallet -> new CreditSummaryResponseDto(wallet.getBalance(), wallet.hasCharacterFreeChance()))
                .orElseGet(() -> new CreditSummaryResponseDto(0, true));
    }

    public CreditHistoryResponseDto getHistory(Long userId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return CreditHistoryResponseDto.from(
                ledgerRepository.findByUserIdOrderByIdDesc(userId, PageRequest.of(safePage, safeSize)));
    }

    public CreditPriceResponseDto getPrices() {
        return CreditPriceResponseDto.from(creditProperties.price());
    }
}
