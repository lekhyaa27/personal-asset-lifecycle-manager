package com.assetmanager.controller;

import com.assetmanager.model.*;
import com.assetmanager.repository.*;
import com.assetmanager.service.AssetService;
import com.assetmanager.service.CalcService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

/** Warranty and insurance pages (both are "has an end date" records, so they share code). */
@Controller
public class ExpiryController {
    public record Row(Long assetId, String assetName, String provider, String policy, LocalDate start, LocalDate end, String status, Long days) {}

    private final AssetService svc; private final CalcService calc;
    private final WarrantyRepository warrantyRepo; private final InsuranceRepository insuranceRepo;

    public ExpiryController(AssetService svc, CalcService calc, WarrantyRepository warrantyRepo, InsuranceRepository insuranceRepo) {
        this.svc = svc; this.calc = calc; this.warrantyRepo = warrantyRepo; this.insuranceRepo = insuranceRepo;
    }

    private boolean match(Row r, String q, String filter) {
        boolean okQ = q.isBlank() || r.assetName().toLowerCase().contains(q.toLowerCase())
                || (r.provider() != null && r.provider().toLowerCase().contains(q.toLowerCase()));
        return okQ && (filter.isBlank() || filter.equals(r.status()));
    }

    @GetMapping("/warranty")
    public String warranty(@SessionAttribute("userId") Long uid, @RequestParam(defaultValue = "") String q,
                           @RequestParam(defaultValue = "") String filter, Model m) {
        Map<Long, WarrantyRecord> map = new HashMap<>();
        for (WarrantyRecord w : warrantyRepo.findByAssetUserId(uid)) map.put(w.getAsset().getId(), w);
        List<Row> rows = new ArrayList<>(), soon = new ArrayList<>();
        for (Asset a : svc.activeAssets(uid)) {
            WarrantyRecord w = map.get(a.getId());
            var s = calc.dateStatus(w == null ? null : w.getEndDate(), LocalDate.now());
            Row r = new Row(a.getId(), a.getName(), w == null ? null : w.getProvider(), null, w == null ? null : w.getStartDate(),
                    w == null ? null : w.getEndDate(), s.status(), s.days());
            if ("EXPIRING_SOON".equals(s.status())) soon.add(r);
            if (match(r, q, filter)) rows.add(r);
        }
        m.addAttribute("rows", rows); m.addAttribute("soon", soon); m.addAttribute("assets", svc.activeAssets(uid));
        m.addAttribute("q", q); m.addAttribute("filter", filter);
        return "warranty";
    }

    @PostMapping("/warranty")
    public String saveWarranty(@SessionAttribute("userId") Long uid, @RequestParam Long assetId, @RequestParam String provider,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, RedirectAttributes ra) {
        svc.saveWarranty(uid, assetId, provider, startDate, endDate);
        ra.addFlashAttribute("msg", "Warranty saved");
        return "redirect:/warranty";
    }

    @GetMapping("/insurance")
    public String insurance(@SessionAttribute("userId") Long uid, @RequestParam(defaultValue = "") String q,
                            @RequestParam(defaultValue = "") String filter, Model m) {
        Map<Long, InsuranceRecord> map = new HashMap<>();
        for (InsuranceRecord i : insuranceRepo.findByAssetUserId(uid)) map.put(i.getAsset().getId(), i);
        List<Row> rows = new ArrayList<>(), soon = new ArrayList<>();
        for (Asset a : svc.activeAssets(uid)) {
            InsuranceRecord i = map.get(a.getId());
            var s = calc.dateStatus(i == null ? null : i.getEndDate(), LocalDate.now());
            Row r = new Row(a.getId(), a.getName(), i == null ? null : i.getProvider(), i == null ? null : i.getPolicyNumber(),
                    i == null ? null : i.getStartDate(), i == null ? null : i.getEndDate(), s.status(), s.days());
            if ("EXPIRING_SOON".equals(s.status())) soon.add(r);
            if (match(r, q, filter)) rows.add(r);
        }
        m.addAttribute("rows", rows); m.addAttribute("soon", soon); m.addAttribute("assets", svc.activeAssets(uid));
        m.addAttribute("q", q); m.addAttribute("filter", filter);
        return "insurance";
    }

    @PostMapping("/insurance")
    public String saveInsurance(@SessionAttribute("userId") Long uid, @RequestParam Long assetId, @RequestParam String provider,
                                @RequestParam(defaultValue = "") String policyNumber,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, RedirectAttributes ra) {
        svc.saveInsurance(uid, assetId, provider, policyNumber, startDate, endDate);
        ra.addFlashAttribute("msg", "Insurance saved");
        return "redirect:/insurance";
    }
}
