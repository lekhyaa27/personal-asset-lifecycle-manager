package com.assetmanager.controller;

import com.assetmanager.exception.ResourceNotFoundException;
import com.assetmanager.model.*;
import com.assetmanager.repository.*;
import com.assetmanager.service.AssetService;
import com.assetmanager.service.CalcService;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

@Controller
public class AssetController {
    static final List<String> CATEGORIES = List.of("Electronics", "Appliances", "Furniture", "Vehicles", "Personal Accessories", "Other");
    static final List<String> CONDITIONS = List.of("EXCELLENT", "GOOD", "FAIR", "POOR");
    static final List<String> DOC_TYPES = List.of("Purchase Invoice", "Warranty Document", "Insurance Document", "Repair Invoice", "Maintenance Receipt", "Other");
    static final Set<String> ALLOWED_EXT = Set.of("pdf", "png", "jpg", "jpeg");

    public record AssetRow(Asset asset, BigDecimal current, String warrantyStatus) {}

    private final AssetService svc; private final CalcService calc;
    private final AssetRepository assetRepo; private final MaintenanceRepository maintRepo; private final RepairRepository repairRepo;
    private final UpgradeRepository upgradeRepo; private final WarrantyRepository warrantyRepo; private final InsuranceRepository insuranceRepo;
    private final HistoryRepository historyRepo; private final DocumentRepository docRepo;
    @Value("${app.upload-dir}") private String uploadDir;

    public AssetController(AssetService svc, CalcService calc, AssetRepository assetRepo, MaintenanceRepository maintRepo,
                           RepairRepository repairRepo, UpgradeRepository upgradeRepo, WarrantyRepository warrantyRepo,
                           InsuranceRepository insuranceRepo, HistoryRepository historyRepo, DocumentRepository docRepo) {
        this.svc = svc; this.calc = calc; this.assetRepo = assetRepo; this.maintRepo = maintRepo; this.repairRepo = repairRepo;
        this.upgradeRepo = upgradeRepo; this.warrantyRepo = warrantyRepo; this.insuranceRepo = insuranceRepo;
        this.historyRepo = historyRepo; this.docRepo = docRepo;
    }

    // ---------------- LIST ----------------
    @GetMapping("/assets")
    public String list(@SessionAttribute("userId") Long uid,
                       @RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "") String category,
                       @RequestParam(defaultValue = "") String status, @RequestParam(defaultValue = "purchaseDate") String sort,
                       @RequestParam(defaultValue = "desc") String dir, @RequestParam(defaultValue = "0") int page, Model m) {
        String sortField = sort.equals("purchasePrice") ? "purchasePrice" : "purchaseDate";      // whitelist
        Sort s = dir.equals("asc") ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
        Page<Asset> result = assetRepo.search(uid, q.trim(), category, status, PageRequest.of(Math.max(page, 0), 8, s));

        Map<Long, WarrantyRecord> warranties = new HashMap<>();
        for (WarrantyRecord w : warrantyRepo.findByAssetUserId(uid)) warranties.put(w.getAsset().getId(), w);
        List<AssetRow> rows = new ArrayList<>();
        for (Asset a : result.getContent()) {
            WarrantyRecord w = warranties.get(a.getId());
            rows.add(new AssetRow(a, svc.currentValue(a), calc.dateStatus(w == null ? null : w.getEndDate(), LocalDate.now()).status()));
        }
        m.addAttribute("rows", rows); m.addAttribute("pageData", result);
        m.addAttribute("q", q); m.addAttribute("category", category); m.addAttribute("status", status);
        m.addAttribute("sort", sortField); m.addAttribute("dir", dir);
        m.addAttribute("categories", CATEGORIES); m.addAttribute("statuses", AssetService.STATUSES);
        return "assets/list";
    }

    // ---------------- ADD / EDIT ----------------
    @GetMapping("/assets/new")
    public String newForm(Model m) {
        m.addAttribute("form", new AssetForm()); m.addAttribute("edit", false);
        formLists(m);
        return "assets/form";
    }

    @PostMapping("/assets")
    public String create(@SessionAttribute("userId") Long uid, @Valid @ModelAttribute("form") AssetForm form,
                         BindingResult br, Model m, RedirectAttributes ra) {
        extraChecks(form, br);
        if (br.hasErrors()) { m.addAttribute("edit", false); formLists(m); return "assets/form"; }
        Asset a = svc.register(uid, form);
        ra.addFlashAttribute("msg", "Asset registered successfully (ID " + a.getId() + ")");
        return "redirect:/assets/" + a.getId();
    }

    @GetMapping("/assets/{id}/edit")
    public String editForm(@SessionAttribute("userId") Long uid, @PathVariable Long id, Model m) {
        Asset a = svc.getOwned(id, uid);
        m.addAttribute("form", AssetForm.from(a)); m.addAttribute("edit", true); m.addAttribute("assetId", id);
        formLists(m);
        return "assets/form";
    }

    @PostMapping("/assets/{id}/edit")
    public String update(@SessionAttribute("userId") Long uid, @PathVariable Long id, @Valid @ModelAttribute("form") AssetForm form,
                         BindingResult br, Model m, RedirectAttributes ra) {
        svc.getOwned(id, uid);
        extraChecks(form, br);
        if (br.hasErrors()) { m.addAttribute("edit", true); m.addAttribute("assetId", id); formLists(m); return "assets/form"; }
        svc.update(id, uid, form);
        ra.addFlashAttribute("msg", "Asset updated");
        return "redirect:/assets/" + id;
    }

    private void formLists(Model m) { m.addAttribute("categories", CATEGORIES); m.addAttribute("conditions", CONDITIONS); }

    private void extraChecks(AssetForm f, BindingResult br) {
        if (f.getSalvageValue() != null && f.getPurchasePrice() != null && f.getSalvageValue().compareTo(f.getPurchasePrice()) > 0)
            br.rejectValue("salvageValue", "salv", "Salvage value cannot be greater than the purchase price");
        if (f.getWarrantyEndDate() != null && (f.getWarrantyStartDate() == null || f.getWarrantyEndDate().isBefore(f.getWarrantyStartDate())))
            br.rejectValue("warrantyEndDate", "w", "Warranty needs a start date and the end date must not be before it");
        if (f.getInsuranceEndDate() != null && (f.getInsuranceStartDate() == null || f.getInsuranceEndDate().isBefore(f.getInsuranceStartDate())))
            br.rejectValue("insuranceEndDate", "i", "Insurance needs a start date and the end date must not be before it");
    }

    // ---------------- DISPOSE / STATUS ----------------
    @PostMapping("/assets/{id}/delete")
    public String delete(@SessionAttribute("userId") Long uid, @PathVariable Long id, RedirectAttributes ra) {
        svc.dispose(id, uid);
        ra.addFlashAttribute("msg", "Asset marked as DISPOSED. Its history has been kept.");
        return "redirect:/assets";
    }

    @PostMapping("/assets/{id}/status")
    public String status(@SessionAttribute("userId") Long uid, @PathVariable Long id, @RequestParam String status,
                         @RequestParam(defaultValue = "") String note, RedirectAttributes ra) {
        svc.changeStatus(id, uid, status, note);
        ra.addFlashAttribute("msg", "Status updated to " + status);
        return "redirect:/assets/" + id;
    }

    // ---------------- DETAILS ----------------
    @GetMapping("/assets/{id}")
    public String details(@SessionAttribute("userId") Long uid, @PathVariable Long id, Model m) {
        Asset a = svc.getOwned(id, uid);
        svc.ensureToken(a);
        LocalDate today = LocalDate.now();
        var maint = maintRepo.findByAssetIdOrderByMaintenanceDateDesc(id);
        var repairs = repairRepo.findByAssetIdOrderByRepairDateDesc(id);
        var warranty = warrantyRepo.findByAssetId(id).orElse(null);
        var insurance = insuranceRepo.findByAssetId(id).orElse(null);
        var ws = calc.dateStatus(warranty == null ? null : warranty.getEndDate(), today);
        var is = calc.dateStatus(insurance == null ? null : insurance.getEndDate(), today);
        var dep = calc.depreciation(a, today);
        var costs = svc.costs(a);
        boolean pending = repairs.stream().anyMatch(r -> !"COMPLETED".equals(r.getStatus()));

        m.addAttribute("asset", a); m.addAttribute("dep", dep); m.addAttribute("costs", costs);
        m.addAttribute("warranty", warranty); m.addAttribute("ws", ws);
        m.addAttribute("insurance", insurance); m.addAttribute("is", is);
        m.addAttribute("health", calc.health(a, repairs, maint, ws.status(), today));
        m.addAttribute("rec", calc.recommend(a, dep.current(), costs.repair(), pending, today));
        m.addAttribute("history", historyRepo.findByAssetIdOrderByEventTimeDesc(id));
        m.addAttribute("maint", maint); m.addAttribute("repairs", repairs);
        m.addAttribute("upgrades", upgradeRepo.findByAssetIdOrderByUpgradeDateDesc(id));
        m.addAttribute("docs", docRepo.findByAssetIdOrderByUploadedAtDesc(id));
        m.addAttribute("docTypes", DOC_TYPES); m.addAttribute("statuses", AssetService.STATUSES);
        m.addAttribute("disposed", "DISPOSED".equals(a.getStatus()));
        return "assets/details";
    }

    // ---------------- QR CODE ----------------
    @GetMapping("/assets/{id}/qr")
    public ResponseEntity<byte[]> qr(@SessionAttribute("userId") Long uid, @PathVariable Long id,
                                     @RequestParam(defaultValue = "false") boolean download,
                                     HttpServletRequest request) throws Exception {
        Asset asset = svc.getOwned(id, uid); // Confirm the signed-in user owns this asset.
        String token = svc.ensureToken(asset);

        // Use the public host when the app is opened through a tunnel/reverse proxy.
        // This prevents QR codes from accidentally containing localhost or the internal port.
        String forwardedHost = request.getHeader("X-Forwarded-Host");
        String host = (forwardedHost != null && !forwardedHost.isBlank())
                ? forwardedHost.split(",")[0].trim()
                : request.getHeader("Host");
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        String scheme = (forwardedProto != null && !forwardedProto.isBlank())
                ? forwardedProto.split(",")[0].trim()
                : request.getScheme();

        if (host == null || host.isBlank()) {
            host = request.getServerName();
            int port = request.getServerPort();
            if (port > 0 && !(("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443))) {
                host += ":" + port;
            }
        }

        // QR must point to this exact asset's public read-only page, not the home page.
        String url = scheme + "://" + host + "/a/" + token;
        BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 260, 260);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(matrix, "PNG", out);
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.IMAGE_PNG);
        h.setCacheControl("no-store, no-cache, must-revalidate, max-age=0");
        h.setPragma("no-cache");
        if (download) h.setContentDispositionFormData("attachment", "asset-" + id + "-qr.png");
        return new ResponseEntity<>(out.toByteArray(), h, HttpStatus.OK);
    }

    // ---------------- DOCUMENTS ----------------
    @PostMapping("/assets/{id}/documents")
    public String upload(@SessionAttribute("userId") Long uid, @PathVariable Long id, @RequestParam MultipartFile file,
                         @RequestParam String docType, RedirectAttributes ra) throws IOException {
        Asset a = svc.getOwned(id, uid);
        String original = file.getOriginalFilename() == null ? "file" : Paths.get(file.getOriginalFilename()).getFileName().toString();
        String ext = original.contains(".") ? original.substring(original.lastIndexOf('.') + 1).toLowerCase() : "";
        if (file.isEmpty()) throw new IllegalArgumentException("Please choose a file");
        if (!ALLOWED_EXT.contains(ext)) throw new IllegalArgumentException("Only PDF, PNG and JPG files are allowed");
        if (file.getSize() > 5 * 1024 * 1024) throw new IllegalArgumentException("File is too large (maximum 5 MB)");
        if (!DOC_TYPES.contains(docType)) docType = "Other";

        Path dir = Paths.get(uploadDir, String.valueOf(uid)).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String stored = UUID.randomUUID() + "." + ext;                 // random name: no path tricks, no overwriting
        file.transferTo(dir.resolve(stored));

        AssetDocument d = new AssetDocument();
        d.setAsset(a); d.setDocType(docType); d.setOriginalName(original); d.setStoredName(stored);
        d.setContentType(file.getContentType()); d.setSizeBytes(file.getSize());
        docRepo.save(d);
        svc.log(a, "DOCUMENT", "Document uploaded: " + original + " (" + docType + ")", a.getStatus(), a.getStatus());
        ra.addFlashAttribute("msg", "Document uploaded");
        return "redirect:/assets/" + id;
    }

    @GetMapping("/documents/{docId}")
    public ResponseEntity<Resource> download(@SessionAttribute("userId") Long uid, @PathVariable Long docId) {
        AssetDocument d = docRepo.findByIdAndAssetUserId(docId, uid).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        Path file = Paths.get(uploadDir, String.valueOf(uid), d.getStoredName()).toAbsolutePath().normalize();
        if (!Files.exists(file)) throw new ResourceNotFoundException("File is missing on disk");
        HttpHeaders h = new HttpHeaders();
        h.setContentDispositionFormData("attachment", d.getOriginalName());
        h.setContentType(d.getStoredName().endsWith(".pdf") ? MediaType.APPLICATION_PDF : MediaType.APPLICATION_OCTET_STREAM);
        return new ResponseEntity<>(new FileSystemResource(file), h, HttpStatus.OK);
    }

    @PostMapping("/documents/{docId}/delete")
    public String deleteDoc(@SessionAttribute("userId") Long uid, @PathVariable Long docId, RedirectAttributes ra) throws IOException {
        AssetDocument d = docRepo.findByIdAndAssetUserId(docId, uid).orElseThrow(() -> new ResourceNotFoundException("Document not found"));
        Files.deleteIfExists(Paths.get(uploadDir, String.valueOf(uid), d.getStoredName()).toAbsolutePath().normalize());
        Long assetId = d.getAsset().getId();
        docRepo.delete(d);
        ra.addFlashAttribute("msg", "Document deleted");
        return "redirect:/assets/" + assetId;
    }
}
