package vn.project.ClinicSystem.service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.model.Prescription;
import vn.project.ClinicSystem.model.PrescriptionItem;
import vn.project.ClinicSystem.repository.PrescriptionRepository;

@Service
@Transactional(readOnly = true)
public class PrescriptionPrintService {

    private final PrescriptionRepository prescriptionRepository;

    public PrescriptionPrintService(PrescriptionRepository prescriptionRepository) {
        this.prescriptionRepository = prescriptionRepository;
    }

    public byte[] generatePrescriptionPdf(Long prescriptionId) {
        Prescription prescription = prescriptionRepository.findById(prescriptionId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy đơn thuốc với id: " + prescriptionId));
        Patient patient = prescription.getVisit() != null ? prescription.getVisit().getPatient() : null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A4);
            PdfWriter.getInstance(document, baos);
            document.open();

            BaseFont baseFont = loadUnicodeFont();
            Font titleFont = new Font(baseFont, 14, Font.BOLD);
            Font labelFont = new Font(baseFont, 10, Font.BOLD);
            Font textFont = new Font(baseFont, 10, Font.NORMAL);
            Font headerFont = new Font(baseFont, 9, Font.NORMAL);

            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[] { 1.2f, 2f });

            Image logo = loadLogo();
            if (logo != null) {
                logo.scaleToFit(70, 40);
                PdfPCell logoCell = new PdfPCell(logo, false);
                logoCell.setBorderWidth(0);
                logoCell.setHorizontalAlignment(Element.ALIGN_LEFT);
                header.addCell(logoCell);
            } else {
                PdfPCell emptyLogo = new PdfPCell(new Paragraph(""));
                emptyLogo.setBorderWidth(0);
                header.addCell(emptyLogo);
            }

            Paragraph clinicInfo = new Paragraph();
            clinicInfo.setFont(headerFont);
            clinicInfo.add(new Paragraph("PHÒNG KHÁM ĐA KHOA DUYÊN HẠNH", new Font(baseFont, 10, Font.BOLD)));
            clinicInfo.add(new Paragraph("36 Cách mạng tháng 8, Phường Cái Khế, Tp Cần Thơ", headerFont));
            clinicInfo.add(new Paragraph("Email: duyenhanh@clinic.vn", headerFont));
            PdfPCell clinicCell = new PdfPCell(clinicInfo);
            clinicCell.setBorderWidth(0);
            clinicCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            header.addCell(clinicCell);

            document.add(header);
            document.add(new Paragraph(" ", textFont));

            Paragraph title = new Paragraph("ĐƠN THUỐC", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" ", textFont));

            PdfPTable patientInfo = new PdfPTable(2);
            patientInfo.setWidthPercentage(100);
            patientInfo.setWidths(new float[] { 2.2f, 1.8f });
            patientInfo.addCell(createLabelValueCell("Bệnh nhân:", patient != null ? patient.getFullName() : "N/A",
                    labelFont, textFont, Element.ALIGN_LEFT));
            patientInfo.addCell(createLabelValueCell(
                    "Năm sinh:",
                    (patient != null && patient.getDateOfBirth() != null
                            ? String.valueOf(patient.getDateOfBirth().getYear())
                            : "N/A") +
                            "    Giới tính: " + (patient != null && patient.getGender() != null ? patient.getGender() : "N/A"),
                    labelFont, textFont, Element.ALIGN_RIGHT));
            document.add(patientInfo);

            PdfPTable addressLine = new PdfPTable(1);
            addressLine.setWidthPercentage(100);
            addressLine.addCell(createLabelValueCell("Địa chỉ:",
                    (patient != null && patient.getAddress() != null
                            ? patient.getAddress()
                            : "N/A"),
                    labelFont, textFont, Element.ALIGN_LEFT));
            document.add(addressLine);

            PdfPTable doctorLine = new PdfPTable(2);
            doctorLine.setWidthPercentage(100);
            doctorLine.setWidths(new float[] { 2f, 1f });
            doctorLine.addCell(createLabelValueCell(
                    "Bác sĩ kê đơn:",
                    (prescription.getPrescribedBy() != null && prescription.getPrescribedBy().getAccount() != null
                            ? prescription.getPrescribedBy().getAccount().getFullName()
                            : "N/A"),
                    labelFont, textFont, Element.ALIGN_LEFT));
            doctorLine.addCell(createLabelValueCell(
                    "Ngày kê:",
                    DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi"))
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(java.time.Instant.now()),
                    labelFont, textFont, Element.ALIGN_RIGHT));
            document.add(doctorLine);

            if (prescription.getNotes() != null) {
                document.add(new Paragraph("Ghi chú: " + prescription.getNotes(), textFont));
            }

            document.add(new Paragraph(" ", textFont));

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(100);
            table.setWidths(new float[] { 0.4f, 5.6f });
            table.addCell(createCellAligned("STT", labelFont, Element.ALIGN_CENTER));
            table.addCell(createCellAligned("Thuốc / Hướng dẫn", labelFont, Element.ALIGN_CENTER));

            int idx = 1;
            for (PrescriptionItem item : prescription.getItems()) {
                table.addCell(createCellAligned(String.valueOf(idx++), textFont, Element.ALIGN_CENTER));
                table.addCell(buildMedicationCell(item, labelFont, textFont));
            }
            document.add(table);

            document.add(new Paragraph(" ", textFont));
            PdfPTable signTable = new PdfPTable(1);
            signTable.setWidthPercentage(100);
            PdfPCell signCell = new PdfPCell(new Paragraph("Bác sĩ ký", labelFont));
            signCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            signCell.setBorderWidth(0);
            signCell.setPaddingTop(20f);
            signCell.setPaddingBottom(40f);
            signCell.setPaddingRight(40f);
            signTable.addCell(signCell);
            document.add(signTable);

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo PDF đơn thuốc: " + ex.getMessage(), ex);
        }
    }

    private PdfPCell buildMedicationCell(PrescriptionItem item, Font labelFont, Font textFont) {
        Phrase phrase = new Phrase();
        String medName = item.getMedicationName() != null ? item.getMedicationName()
                : (item.getMedication() != null ? item.getMedication().getName() : "Thuốc không xác định");
        Font medNameFont = new Font(labelFont);
        medNameFont.setSize(11f);
        phrase.add(new com.lowagie.text.Chunk(medName + "\n", medNameFont));

        Font italicFont = new Font(textFont);
        italicFont.setStyle(Font.ITALIC);
        String freqText = formatFrequency(item.getFrequency());
        String detail = "SL: " + (item.getQuantity() != null ? item.getQuantity() : "-")
                + " | Tần suất: " + freqText
                + " | Liều dùng: " + (item.getDosage() != null ? item.getDosage() : "-");
        phrase.add(new com.lowagie.text.Chunk(detail, italicFont));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setPaddingTop(8f);
        cell.setPaddingBottom(8f);
        cell.setPaddingLeft(6f);
        cell.setPaddingRight(6f);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private String formatFrequency(String frequency) {
        if (frequency == null) {
            return "N/A";
        }
        String normalized = frequency.trim();
        if (normalized.equals("2") || normalized.equalsIgnoreCase("2 lần")) {
            return "Sáng - Chiều";
        }
        if (normalized.equals("1") || normalized.equalsIgnoreCase("1 lần")) {
            return "Sáng";
        }
        return normalized;
    }

    private PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(6f);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private PdfPCell createCellAligned(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(6f);
        cell.setBorderWidth(0.5f);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private PdfPCell createLabelValueCell(String label, String value, Font labelFont, Font valueFont, int alignment) {
        Phrase phrase = new Phrase();
        phrase.add(new com.lowagie.text.Chunk(label + " ", labelFont));
        phrase.add(new com.lowagie.text.Chunk(value != null ? value : "", valueFont));
        PdfPCell cell = new PdfPCell(phrase);
        cell.setBorderWidth(0f);
        cell.setPadding(4f);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private BaseFont loadUnicodeFont() throws Exception {
        ClassPathResource resource = new ClassPathResource("fonts/NotoSans-Regular.ttf");
        try (java.io.InputStream is = resource.getInputStream()) {
            byte[] fontBytes = is.readAllBytes();
            return BaseFont.createFont("NotoSans-Regular.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, false, fontBytes,
                    null);
        }
    }

    private Image loadLogo() {
        try {
            ClassPathResource resource = new ClassPathResource("logo/LogoDuyenHanh.png");
            if (!resource.exists()) {
                return null;
            }
            return Image.getInstance(resource.getURL());
        } catch (Exception ex) {
            return null;
        }
    }
}
