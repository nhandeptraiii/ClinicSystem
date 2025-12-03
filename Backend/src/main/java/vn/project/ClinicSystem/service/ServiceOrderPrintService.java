package vn.project.ClinicSystem.service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Image;
import com.lowagie.text.Font;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import jakarta.persistence.EntityNotFoundException;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.model.PatientVisit;
import vn.project.ClinicSystem.model.ServiceOrder;
import vn.project.ClinicSystem.repository.ServiceOrderRepository;
import org.springframework.core.io.ClassPathResource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@Transactional(readOnly = true)
public class ServiceOrderPrintService {

    private final ServiceOrderRepository serviceOrderRepository;

    public ServiceOrderPrintService(ServiceOrderRepository serviceOrderRepository) {
        this.serviceOrderRepository = serviceOrderRepository;
    }

    public byte[] generateServiceOrderPdf(Long orderId) {
        ServiceOrder order = serviceOrderRepository.findById(orderId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy phiếu dịch vụ với id: " + orderId));

        PatientVisit visit = order.getVisit();
        Patient patient = visit != null ? visit.getPatient() : null;

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Document document = new Document(PageSize.A5.rotate());
            PdfWriter.getInstance(document, baos);
            document.open();

            BaseFont baseFont = loadUnicodeFont();
            Font titleFont = new Font(baseFont, 14, Font.BOLD);
            Font labelFont = new Font(baseFont, 10, Font.BOLD);
            Font textFont = new Font(baseFont, 10, Font.NORMAL);
            Font headerFont = new Font(baseFont, 9, Font.NORMAL);

            // Header with logo + clinic info
            PdfPTable header = new PdfPTable(2);
            header.setWidthPercentage(100);
            header.setWidths(new float[] { 1.2f, 2f });

            Image logo = loadLogo();
            if (logo != null) {
                logo.scaleToFit(80, 50);
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

            Paragraph title = new Paragraph("PHIẾU CHỈ ĐỊNH CẬN LÂM SÀNG", new Font(baseFont, 13, Font.BOLD));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" ", textFont));

            // Patient info block (no border)
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
                            "    Giới tính: "
                            + (patient != null && patient.getGender() != null ? patient.getGender() : "N/A"),
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

            PdfPTable roomLine = new PdfPTable(1);
            roomLine.setWidthPercentage(100);
            String roomText = "N/A";
            if (order.getMedicalService() != null && order.getMedicalService().getClinicRoom() != null) {
                var room = order.getMedicalService().getClinicRoom();
                String code = room.getCode() != null ? room.getCode() : "N/A";
                String floor = room.getFloor() != null ? room.getFloor() : "N/A";
                roomText = room.getName() + "/" + code + "/" + floor;
            }
            roomLine.addCell(
                    createLabelValueCell("Phòng chỉ định:", roomText, labelFont, textFont, Element.ALIGN_LEFT));
            document.add(roomLine);

            PdfPTable doctorLine = new PdfPTable(1);
            doctorLine.setWidthPercentage(100);
            doctorLine.addCell(createLabelValueCell(
                    "Bác sĩ phụ trách:",
                    (order.getAssignedDoctor() != null && order.getAssignedDoctor().getAccount() != null
                            ? order.getAssignedDoctor().getAccount().getFullName()
                            : "N/A"),
                    labelFont, textFont, Element.ALIGN_LEFT));
            document.add(doctorLine);

            PdfPTable serviceLine = new PdfPTable(2);
            serviceLine.setWidthPercentage(100);
            serviceLine.setWidths(new float[] { 2f, 1f });
            serviceLine.addCell(createLabelValueCell(
                    "Dịch vụ:",
                    (order.getMedicalService() != null ? order.getMedicalService().getName() : "N/A"),
                    labelFont, textFont, Element.ALIGN_LEFT));
            serviceLine.addCell(createLabelValueCell(
                    "Giờ chỉ định:",
                    DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi"))
                            .withZone(java.time.ZoneId.systemDefault())
                            .format(java.time.Instant.now()),
                    labelFont, textFont, Element.ALIGN_RIGHT));
            document.add(serviceLine);

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo PDF phiếu dịch vụ: " + ex.getMessage(), ex);
        }
    }

    private PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setPadding(6f);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private PdfPCell createBorderlessCell(String text, Font font) {
        return createBorderlessCell(text, font, Element.ALIGN_LEFT);
    }

    private PdfPCell createBorderlessCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Paragraph(text, font));
        cell.setBorderWidth(0f);
        cell.setPadding(4f);
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
        try (InputStream is = resource.getInputStream()) {
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
