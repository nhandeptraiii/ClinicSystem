package vn.project.ClinicSystem.service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
import vn.project.ClinicSystem.model.Billing;
import vn.project.ClinicSystem.model.BillingItem;
import vn.project.ClinicSystem.model.Patient;
import vn.project.ClinicSystem.repository.BillingRepository;
import org.springframework.core.io.ClassPathResource;

@Service
@Transactional(readOnly = true)
public class BillingPrintService {

    private final BillingRepository billingRepository;
    private final vn.project.ClinicSystem.repository.UserRepository userRepository;

    public BillingPrintService(BillingRepository billingRepository,
            vn.project.ClinicSystem.repository.UserRepository userRepository) {
        this.billingRepository = billingRepository;
        this.userRepository = userRepository;
    }

    public byte[] generateBillingPdf(Long billingId) {
        Billing billing = billingRepository.findById(billingId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy hóa đơn với id: " + billingId));

        Patient patient = billing.getVisit() != null ? billing.getVisit().getPatient() : null;

        java.util.List<BillingItem> items = billing.getItems() != null ? billing.getItems()
                : new java.util.ArrayList<>();
        java.util.List<BillingItem> serviceItems = new java.util.ArrayList<>();
        java.util.List<BillingItem> medicationItems = new java.util.ArrayList<>();
        java.util.List<BillingItem> otherItems = new java.util.ArrayList<>();

        for (BillingItem item : items) {
            if (vn.project.ClinicSystem.model.enums.BillingItemType.SERVICE.equals(item.getItemType())) {
                serviceItems.add(item);
            } else if (vn.project.ClinicSystem.model.enums.BillingItemType.MEDICATION.equals(item.getItemType())) {
                medicationItems.add(item);
            } else {
                otherItems.add(item);
            }
        }

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
            Font sectionTitleFont = new Font(baseFont, 11, Font.BOLD);

            // Header Section
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

            Paragraph title = new Paragraph("HÓA ĐƠN THANH TOÁN", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" ", textFont));

            PdfPTable patientInfo = new PdfPTable(2);
            patientInfo.setWidthPercentage(100);
            patientInfo.setWidths(new float[] { 2.2f, 1.8f });
            patientInfo.addCell(createLabelValueCell("Bệnh nhân:", patient != null ? patient.getFullName() : "N/A",
                    labelFont, textFont, Element.ALIGN_LEFT));
            patientInfo.addCell(createLabelValueCell(
                    "Ngày lập:",
                    billing.getIssuedAt() != null
                            ? DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", Locale.forLanguageTag("vi"))
                                    .format(billing.getIssuedAt())
                            : "N/A",
                    labelFont, textFont, Element.ALIGN_RIGHT));
            document.add(patientInfo);

            PdfPTable addressLine = new PdfPTable(2);
            addressLine.setWidthPercentage(100);
            addressLine.setWidths(new float[] { 2.2f, 1.8f });
            addressLine.addCell(createLabelValueCell("SĐT:",
                    (patient != null
                            ? (patient.getPhone() != null ? patient.getPhone() : "—")
                            : "N/A"),
                    labelFont, textFont, Element.ALIGN_LEFT));

            String currentCashier = "Admin";
            try {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (auth != null && auth.getName() != null) {
                    String email = auth.getName();
                    vn.project.ClinicSystem.model.User cashierUser = userRepository.findByEmail(email).orElse(null);
                    if (cashierUser != null && cashierUser.getFullName() != null) {
                        currentCashier = cashierUser.getFullName();
                    } else {
                        currentCashier = email;
                    }
                }
            } catch (Exception ignored) {
            }

            addressLine.addCell(
                    createLabelValueCell("Người lập:", currentCashier, labelFont, textFont, Element.ALIGN_RIGHT));
            document.add(addressLine);

            document.add(new Paragraph(" ", textFont));

            document.add(new Paragraph(" ", textFont));

            // 1. Services Table
            if (!serviceItems.isEmpty()) {
                Paragraph p = new Paragraph("Dịch vụ khám", sectionTitleFont);
                p.setSpacingAfter(4f);
                document.add(p);

                int serviceIdx = 1;
                PdfPTable serviceTable = new PdfPTable(3);
                serviceTable.setWidthPercentage(100);
                serviceTable.setWidths(new float[] { 0.6f, 4.4f, 1.5f }); // Adjusted widths
                serviceTable.addCell(createCellAligned("STT", labelFont, Element.ALIGN_CENTER));
                serviceTable.addCell(createCellAligned("Tên dịch vụ", labelFont, Element.ALIGN_CENTER));
                serviceTable.addCell(createCellAligned("Thành tiền", labelFont, Element.ALIGN_CENTER));

                for (BillingItem item : serviceItems) {
                    serviceTable
                            .addCell(createCellAligned(String.valueOf(serviceIdx++), textFont, Element.ALIGN_CENTER));
                    serviceTable.addCell(createCell(item.getDescription(), textFont));
                    serviceTable
                            .addCell(
                                    createCellAligned(formatDecimal(item.getAmount()), textFont, Element.ALIGN_CENTER));
                }
                document.add(serviceTable);
                document.add(new Paragraph(" ", textFont));
            }

            // 2. Medications Table
            if (!medicationItems.isEmpty()) {
                Paragraph p = new Paragraph("Đơn thuốc", sectionTitleFont);
                p.setSpacingAfter(4f);
                document.add(p);

                int medIdx = 1;
                PdfPTable medTable = new PdfPTable(5);
                medTable.setWidthPercentage(100);
                medTable.setWidths(new float[] { 0.6f, 3.4f, 0.8f, 1.2f, 1.2f });
                medTable.addCell(createCellAligned("STT", labelFont, Element.ALIGN_CENTER));
                medTable.addCell(createCellAligned("Tên thuốc", labelFont, Element.ALIGN_CENTER));
                medTable.addCell(createCellAligned("SL", labelFont, Element.ALIGN_CENTER));
                medTable.addCell(createCellAligned("Đơn giá", labelFont, Element.ALIGN_CENTER));
                medTable.addCell(createCellAligned("Thành tiền", labelFont, Element.ALIGN_CENTER));

                for (BillingItem item : medicationItems) {
                    medTable.addCell(createCellAligned(String.valueOf(medIdx++), textFont, Element.ALIGN_CENTER));
                    medTable.addCell(createCell(item.getDescription(), textFont));
                    medTable.addCell(
                            createCellAligned(String.valueOf(item.getQuantity()), textFont, Element.ALIGN_CENTER));
                    medTable.addCell(
                            createCellAligned(formatDecimal(item.getUnitPrice()), textFont, Element.ALIGN_CENTER));
                    medTable.addCell(
                            createCellAligned(formatDecimal(item.getAmount()), textFont, Element.ALIGN_CENTER));
                }
                document.add(medTable);
                document.add(new Paragraph(" ", textFont));
            }

            // 3. Other Items Table
            if (!otherItems.isEmpty()) {
                Paragraph p = new Paragraph("Chi phí khác", sectionTitleFont);
                p.setSpacingAfter(4f);
                document.add(p);

                int otherIdx = 1;
                PdfPTable otherTable = new PdfPTable(5);
                otherTable.setWidthPercentage(100);
                otherTable.setWidths(new float[] { 0.6f, 3.4f, 0.8f, 1.2f, 1.2f });
                otherTable.addCell(createCellAligned("STT", labelFont, Element.ALIGN_CENTER));
                otherTable.addCell(createCellAligned("Mô tả", labelFont, Element.ALIGN_CENTER));
                otherTable.addCell(createCellAligned("SL", labelFont, Element.ALIGN_CENTER));
                otherTable.addCell(createCellAligned("Đơn giá", labelFont, Element.ALIGN_CENTER));
                otherTable.addCell(createCellAligned("Thành tiền", labelFont, Element.ALIGN_CENTER));

                for (BillingItem item : otherItems) {
                    otherTable.addCell(createCellAligned(String.valueOf(otherIdx++), textFont, Element.ALIGN_CENTER));
                    otherTable.addCell(createCell(item.getDescription(), textFont));
                    otherTable.addCell(
                            createCellAligned(String.valueOf(item.getQuantity()), textFont, Element.ALIGN_CENTER));
                    otherTable.addCell(
                            createCellAligned(formatDecimal(item.getUnitPrice()), textFont, Element.ALIGN_CENTER));
                    otherTable
                            .addCell(
                                    createCellAligned(formatDecimal(item.getAmount()), textFont, Element.ALIGN_CENTER));
                }
                document.add(otherTable);
                document.add(new Paragraph(" ", textFont));
            }

            // Totals Summary Table
            PdfPTable totalTable = new PdfPTable(2);
            totalTable.setWidthPercentage(40);
            totalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
            totalTable.setWidths(new float[] { 1f, 1f });

            PdfPCell labelCell = new PdfPCell(new Phrase("Tổng cộng:", labelFont));
            labelCell.setBorderWidth(0);
            labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            labelCell.setPadding(4f);

            PdfPCell valueCell = new PdfPCell(
                    new Phrase(formatDecimal(billing.getTotalAmount()), new Font(baseFont, 12, Font.BOLD)));
            valueCell.setBorderWidth(0);
            valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
            valueCell.setPadding(4f);

            totalTable.addCell(labelCell);
            totalTable.addCell(valueCell);
            document.add(totalTable);

            document.add(new Paragraph(" ", textFont));

            PdfPTable paymentTable = new PdfPTable(2);
            paymentTable.setWidthPercentage(100);
            paymentTable.setWidths(new float[] { 3f, 1f });
            paymentTable.addCell(createLabelValueCell("Phương thức thanh toán:",
                    billing.getPaymentMethod() != null ? billing.getPaymentMethod() : "—", labelFont, textFont,
                    Element.ALIGN_LEFT));
            paymentTable.addCell(createLabelValueCell("", "", labelFont, textFont, Element.ALIGN_RIGHT));
            document.add(paymentTable);

            document.add(new Paragraph(" ", textFont));
            document.add(new Paragraph(" ", textFont));

            PdfPTable signatureTable = new PdfPTable(1);
            signatureTable.setWidthPercentage(40);
            signatureTable.setHorizontalAlignment(Element.ALIGN_RIGHT);

            PdfPCell sigCell = new PdfPCell();
            sigCell.setBorder(0);
            // sigCell.setHorizontalAlignment(Element.ALIGN_CENTER); // Not strictly needed
            // if elements are aligned, but good practice

            Paragraph p1 = new Paragraph("Người thanh toán", labelFont);
            p1.setAlignment(Element.ALIGN_CENTER);
            sigCell.addElement(p1);

            Paragraph p2 = new Paragraph("(Ký, ghi rõ họ tên)", new Font(baseFont, 9, Font.ITALIC));
            p2.setAlignment(Element.ALIGN_CENTER);
            sigCell.addElement(p2);

            signatureTable.addCell(sigCell);

            document.add(signatureTable);

            document.close();
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Không thể tạo PDF hóa đơn: " + ex.getMessage(), ex);
        }
    }

    private PdfPCell createCell(String text, Font font) {
        PdfPCell cell = new PdfPCell(new Paragraph(text != null ? text : "", font));
        cell.setPadding(6f);
        cell.setBorderWidth(0.5f);
        return cell;
    }

    private PdfPCell createCellAligned(String text, Font font, int alignment) {
        Paragraph p = new Paragraph(text != null ? text : "", font);
        p.setAlignment(alignment);
        PdfPCell cell = new PdfPCell(p);
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

    private String formatDecimal(java.math.BigDecimal value) {
        if (value == null) {
            return "0";
        }
        java.text.DecimalFormatSymbols symbols = new java.text.DecimalFormatSymbols(new Locale("vi", "VN"));
        symbols.setGroupingSeparator('.');
        symbols.setDecimalSeparator(',');
        java.text.DecimalFormat decimalFormat = new java.text.DecimalFormat("#,###.##", symbols);
        return decimalFormat.format(value);
    }
}
