package com.skyhigh.checkin.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import com.skyhigh.checkin.dto.response.BoardingPassResponse;
import com.skyhigh.checkin.exception.ResourceNotFoundException;
import com.skyhigh.checkin.model.entity.BoardingPass;
import com.skyhigh.checkin.model.entity.CheckIn;
import com.skyhigh.checkin.model.entity.Flight;
import com.skyhigh.checkin.model.entity.Passenger;
import com.skyhigh.checkin.repository.BoardingPassRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BoardingPassService {

    private final BoardingPassRepository boardingPassRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("ddMMMyyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @Transactional
    public BoardingPass generateBoardingPass(CheckIn checkIn) {
        log.info("Generating boarding pass for check-in: {}", checkIn);

        // Check if boarding pass already exists
        if (boardingPassRepository.existsByCheckInId(checkIn.getId())) {
            return boardingPassRepository.findByCheckInId(checkIn.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("BoardingPass", checkIn.getId()));
        }

        Flight flight = checkIn.getBooking().getFlight();
        Passenger passenger = checkIn.getBooking().getPassenger();

        String passengerName = (passenger.getLastName() + "/" + passenger.getFirstName()).toUpperCase();
        LocalDateTime boardingTime = flight.getDepartureTime().minusMinutes(30);

        // Generate unique barcode data
        String barcodeData = generateBarcodeData(flight, passenger, checkIn);

        // Generate QR code
        String qrCodeData = generateQRCodeBase64(barcodeData);

        BoardingPass boardingPass = BoardingPass.builder()
                .checkIn(checkIn)
                .passengerName(passengerName)
                .flightNumber(flight.getFlightNumber())
                .seatNumber(checkIn.getSeat().getSeatNumber())
                .seatClass(checkIn.getSeat().getSeatClass().name())
                .origin(flight.getOrigin())
                .destination(flight.getDestination())
                .departureTime(flight.getDepartureTime())
                .gate(flight.getGate())
                .boardingTime(boardingTime)
                .barcodeData(barcodeData)
                .qrCodeData(qrCodeData)
                .build();

        boardingPass = boardingPassRepository.save(boardingPass);
        log.info("Boarding pass generated: {}", boardingPass);

        return boardingPass;
    }


    @Transactional(readOnly = true)
    public BoardingPassResponse getBoardingPass(UUID checkInId) {
        BoardingPass boardingPass = boardingPassRepository.findByCheckInId(checkInId)
                .orElseThrow(() -> new ResourceNotFoundException("BoardingPass", checkInId));

        return mapToResponse(boardingPass);
    }

    @Transactional(readOnly = true)
    public byte[] generateBoardingPassPdf(UUID checkInId) {
        BoardingPass boardingPass = boardingPassRepository.findByCheckInId(checkInId)
                .orElseThrow(() -> new ResourceNotFoundException("BoardingPass", checkInId));

        return createPdf(boardingPass);
    }

    private String generateBarcodeData(Flight flight, Passenger passenger, CheckIn checkIn) {
        // Format: FLIGHTNUMBER-LASTNAME-SEAT-DATE-SEQUENCE
        String date = flight.getDepartureTime().format(DATE_FORMATTER).toUpperCase();
        String sequence = checkIn.getId().toString().substring(0, 8).toUpperCase();
        return String.format("%s%s%s%s%s",
                flight.getFlightNumber(),
                passenger.getLastName().toUpperCase().substring(0, Math.min(5, passenger.getLastName().length())),
                checkIn.getSeat().getSeatNumber(),
                date,
                sequence);
    }

    private String generateQRCodeBase64(String data) {
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(data, BarcodeFormat.QR_CODE, 200, 200);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", outputStream);

            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            log.error("Error generating QR code: {}", e.getMessage());
            return null;
        }
    }

    private byte[] createPdf(BoardingPass boardingPass) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A5.rotate());
            PdfWriter.getInstance(document, outputStream);

            document.open();

            // Title
            Font titleFont = new Font(Font.FontFamily.HELVETICA, 24, Font.BOLD, BaseColor.DARK_GRAY);
            Paragraph title = new Paragraph("SKYHIGH AIRLINES", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("BOARDING PASS", new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD));
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // Main table
            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setSpacingBefore(10);

            Font labelFont = new Font(Font.FontFamily.HELVETICA, 10, Font.NORMAL, BaseColor.GRAY);
            Font valueFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);

            // Passenger Name
            addCell(table, "PASSENGER NAME", labelFont);
            addCell(table, "FLIGHT", labelFont);
            addCell(table, boardingPass.getPassengerName(), valueFont);
            addCell(table, boardingPass.getFlightNumber(), valueFont);

            // From/To
            addCell(table, "FROM", labelFont);
            addCell(table, "TO", labelFont);
            addCell(table, boardingPass.getOrigin(), valueFont);
            addCell(table, boardingPass.getDestination(), valueFont);

            // Date/Time
            addCell(table, "DATE", labelFont);
            addCell(table, "BOARDING TIME", labelFont);
            addCell(table, boardingPass.getDepartureTime().format(DATE_FORMATTER).toUpperCase(), valueFont);
            addCell(table, boardingPass.getBoardingTime() != null ?
                    boardingPass.getBoardingTime().format(TIME_FORMATTER) : "TBA", valueFont);

            // Seat/Gate
            addCell(table, "SEAT", labelFont);
            addCell(table, "GATE", labelFont);
            addCell(table, boardingPass.getSeatNumber() + " (" + boardingPass.getSeatClass() + ")", valueFont);
            addCell(table, boardingPass.getGate() != null ? boardingPass.getGate() : "TBA", valueFont);

            document.add(table);

            // Barcode
            Paragraph barcodeLabel = new Paragraph("\nBOARDING CODE: " + boardingPass.getBarcodeData(),
                    new Font(Font.FontFamily.COURIER, 10));
            barcodeLabel.setAlignment(Element.ALIGN_CENTER);
            document.add(barcodeLabel);

            // QR Code (if available)
            if (boardingPass.getQrCodeData() != null) {
                try {
                    byte[] qrBytes = Base64.getDecoder().decode(boardingPass.getQrCodeData());
                    Image qrImage = Image.getInstance(qrBytes);
                    qrImage.scaleToFit(100, 100);
                    qrImage.setAlignment(Element.ALIGN_CENTER);
                    document.add(qrImage);
                } catch (Exception e) {
                    log.warn("Could not add QR code to PDF: {}", e.getMessage());
                }
            }

            document.close();
            return outputStream.toByteArray();

        } catch (Exception e) {
            log.error("Error creating PDF: {}", e.getMessage());
            throw new RuntimeException("Failed to generate boarding pass PDF", e);
        }
    }

    private void addCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private BoardingPassResponse mapToResponse(BoardingPass boardingPass) {
        return BoardingPassResponse.builder()
                .id(boardingPass.getId())
                .passengerName(boardingPass.getPassengerName())
                .flightNumber(boardingPass.getFlightNumber())
                .seatNumber(boardingPass.getSeatNumber())
                .seatClass(boardingPass.getSeatClass())
                .origin(boardingPass.getOrigin())
                .destination(boardingPass.getDestination())
                .departureTime(boardingPass.getDepartureTime())
                .gate(boardingPass.getGate())
                .boardingTime(boardingPass.getBoardingTime())
                .barcode(boardingPass.getBarcodeData())
                .qrCodeBase64(boardingPass.getQrCodeData())
                .downloadUrl("/api/v1/boarding-pass/" + boardingPass.getCheckIn().getId() + "/download")
                .generatedAt(boardingPass.getGeneratedAt())
                .build();
    }
}

