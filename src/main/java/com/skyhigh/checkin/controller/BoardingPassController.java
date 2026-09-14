package com.skyhigh.checkin.controller;

import com.skyhigh.checkin.dto.response.BoardingPassResponse;
import com.skyhigh.checkin.security.PassengerPrincipal;
import com.skyhigh.checkin.service.BoardingPassService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/boarding-pass")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Boarding Pass", description = "Boarding pass generation and download APIs")
@SecurityRequirement(name = "bearerAuth")
public class BoardingPassController {

    private final BoardingPassService boardingPassService;

    @GetMapping("/{checkInId}")
    @PreAuthorize("@flightAccessChecker.isCheckInOwner(#checkInId)")
    @Operation(summary = "Get boarding pass",
               description = "Get boarding pass details including QR code")
    public ResponseEntity<BoardingPassResponse> getBoardingPass(
            @PathVariable UUID checkInId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Getting boarding pass for check-in: {} by passenger: {}", checkInId, principal.getPassengerId());
        BoardingPassResponse response = boardingPassService.getBoardingPass(checkInId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{checkInId}/download")
    @PreAuthorize("@flightAccessChecker.isCheckInOwner(#checkInId)")
    @Operation(summary = "Download boarding pass PDF",
               description = "Download the boarding pass as a PDF file")
    public ResponseEntity<byte[]> downloadBoardingPass(
            @PathVariable UUID checkInId,
            @AuthenticationPrincipal PassengerPrincipal principal) {
        log.info("Downloading boarding pass PDF for check-in: {} by passenger: {}", checkInId, principal.getPassengerId());

        byte[] pdfContent = boardingPassService.generateBoardingPassPdf(checkInId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("attachment", "boarding-pass-" + checkInId + ".pdf");
        headers.setContentLength(pdfContent.length);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfContent);
    }
}

