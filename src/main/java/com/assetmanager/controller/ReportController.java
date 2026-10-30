package com.assetmanager.controller;

import com.assetmanager.repository.AssetRepository;
import com.assetmanager.service.NotificationService;
import com.assetmanager.service.ReportService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

@Controller
public class ReportController {
    private final ReportService reports; private final NotificationService notifications; private final AssetRepository assetRepo;
    public ReportController(ReportService reports, NotificationService notifications, AssetRepository assetRepo) {
        this.reports = reports; this.notifications = notifications; this.assetRepo = assetRepo;
    }

    @GetMapping("/reports")
    public String reports(@SessionAttribute("userId") Long uid, @RequestParam(defaultValue = "summary") String type,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          @RequestParam(defaultValue = "") String category, @RequestParam(required = false) Long assetId, Model m) {
        m.addAttribute("report", reports.build(type, uid, from, to, category, assetId));
        m.addAttribute("types", ReportService.TYPES); m.addAttribute("type", type);
        m.addAttribute("from", from); m.addAttribute("to", to); m.addAttribute("category", category);
        m.addAttribute("assetId", assetId); m.addAttribute("categories", AssetController.CATEGORIES);
        m.addAttribute("assets", assetRepo.findByUserIdOrderByNameAsc(uid));
        return "reports";
    }

    @GetMapping("/reports/export")
    public ResponseEntity<byte[]> export(@SessionAttribute("userId") Long uid, @RequestParam(defaultValue = "summary") String type,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(defaultValue = "") String category, @RequestParam(required = false) Long assetId) {
        String csv = reports.toCsv(reports.build(type, uid, from, to, category, assetId));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(new MediaType("text", "csv", StandardCharsets.UTF_8));
        h.setContentDispositionFormData("attachment", "report-" + type + ".csv");
        return new ResponseEntity<>(("\uFEFF" + csv).getBytes(StandardCharsets.UTF_8), h, HttpStatus.OK);
    }

    @GetMapping("/notifications")
    public String notifications(@SessionAttribute("userId") Long uid, Model m) {
        m.addAttribute("notifs", notifications.build(uid));
        return "notifications";
    }
}
