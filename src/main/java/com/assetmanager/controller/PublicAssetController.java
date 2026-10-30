package com.assetmanager.controller;

import com.assetmanager.exception.ResourceNotFoundException;
import com.assetmanager.model.*;
import com.assetmanager.repository.*;
import com.assetmanager.service.AssetService;
import com.assetmanager.service.CalcService;
import com.lowagie.text.Document;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public, read-only pages reached by scanning the QR code (no login needed).
 * The link contains a random token, and only a safe summary is shown:
 * no owner details, notes, expenses or uploaded documents.
 */
@Controller
public class PublicAssetController {
    private final AssetRepository assetRepo; private final AssetService svc; private final CalcService calc;
    private final WarrantyRepository warrantyRepo; private final InsuranceRepository insuranceRepo;

    public PublicAssetController(AssetRepository assetRepo, AssetService svc, CalcService calc,
                                 WarrantyRepository warrantyRepo, InsuranceRepository insuranceRepo) {
        this.assetRepo = assetRepo; this.svc = svc; this.calc = calc; this.warrantyRepo = warrantyRepo; this.insuranceRepo = insuranceRepo;
    }

    private Asset find(String token) {
        return assetRepo.findByPublicToken(token).orElseThrow(() -> new ResourceNotFoundException("This QR code is not valid"));
    }

    private static String nv(String s) { return s == null || s.isBlank() ? "-" : s; }
    private static String money(java.math.BigDecimal v) { return "Rs. " + String.format("%,.2f", AssetService.nz(v)); }

    /** The label/value rows shown on the web page AND written into the PDF. */
    private Map<String, String> facts(Asset a) {
        LocalDate today = LocalDate.now();
        WarrantyRecord w = warrantyRepo.findByAssetId(a.getId()).orElse(null);
        InsuranceRecord i = insuranceRepo.findByAssetId(a.getId()).orElse(null);
        var ws = calc.dateStatus(w == null ? null : w.getEndDate(), today);
        var is = calc.dateStatus(i == null ? null : i.getEndDate(), today);
        Map<String, String> f = new LinkedHashMap<>();
        f.put("Asset ID", String.valueOf(a.getId()));
        f.put("Category", nv(a.getCategory()));
        f.put("Brand", nv(a.getBrand()));
        f.put("Model", nv(a.getModel()));
        f.put("Serial number", nv(a.getSerialNumber()));
        f.put("Purchase date", String.valueOf(a.getPurchaseDate()));
        f.put("Purchase price", money(a.getPurchasePrice()));
        f.put("Current estimated value", money(svc.currentValue(a)));
        f.put("Condition", nv(a.getAssetCondition()));
        f.put("Lifecycle status", a.getStatus());
        f.put("Warranty", w == null ? "Not available" : nv(w.getProvider()) + ", until " + w.getEndDate() + " (" + ws.status() + ")");
        f.put("Insurance", i == null ? "Not available" : nv(i.getProvider()) + ", until " + i.getEndDate() + " (" + is.status() + ")");
        if (!"DISPOSED".equals(a.getStatus())) {
            var h = svc.healthOf(a);
            f.put("Health score", h.score() + " / 100 (" + h.status() + ")");
        }
        return f;
    }

    @GetMapping("/a/{token}")
    public String view(@PathVariable String token, Model m) {
        Asset a = find(token);
        m.addAttribute("name", a.getName()); m.addAttribute("status", a.getStatus());
        m.addAttribute("facts", facts(a)); m.addAttribute("token", token);
        return "public-asset";
    }

    @GetMapping("/a/{token}/pdf")
    public ResponseEntity<byte[]> pdf(@PathVariable String token) throws Exception {
        Asset a = find(token);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font titleF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, new Color(30, 58, 138));
        Font smallF = FontFactory.getFont(FontFactory.HELVETICA, 10, Color.GRAY);
        Font keyF = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(71, 85, 105));
        Font valF = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        doc.add(new Paragraph(a.getName(), titleF));
        doc.add(new Paragraph("Asset details  |  Personal Asset Lifecycle Manager  |  Generated on " + LocalDate.now(), smallF));
        doc.add(new Paragraph(" "));
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1f, 2f});
        for (Map.Entry<String, String> e : facts(a).entrySet()) {
            PdfPCell k = new PdfPCell(new Phrase(e.getKey(), keyF));
            k.setBackgroundColor(new Color(239, 244, 255)); k.setPadding(7);
            PdfPCell v = new PdfPCell(new Phrase(e.getValue(), valF));
            v.setPadding(7);
            table.addCell(k); table.addCell(v);
        }
        doc.add(table);
        doc.close();
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_PDF);
        h.setContentDispositionFormData("attachment", "asset-" + a.getId() + "-details.pdf");
        return new ResponseEntity<>(out.toByteArray(), h, HttpStatus.OK);
    }
}
