package com.assetmanager.controller;

import com.assetmanager.model.*;
import com.assetmanager.repository.*;
import com.assetmanager.service.AssetService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.List;

/** Maintenance, repair and upgrade pages. */
@Controller
public class RecordController {
    private final AssetService svc;
    private final MaintenanceRepository maintRepo; private final RepairRepository repairRepo; private final UpgradeRepository upgradeRepo;

    public RecordController(AssetService svc, MaintenanceRepository maintRepo, RepairRepository repairRepo, UpgradeRepository upgradeRepo) {
        this.svc = svc; this.maintRepo = maintRepo; this.repairRepo = repairRepo; this.upgradeRepo = upgradeRepo;
    }

    private String firstError(BindingResult br) { return br.getAllErrors().get(0).getDefaultMessage(); }

    // ---------- MAINTENANCE ----------
    @GetMapping("/maintenance")
    public String maintenance(@SessionAttribute("userId") Long uid, @RequestParam(required = false) Long assetId, Model m) {
        m.addAttribute("assets", svc.activeAssets(uid));
        m.addAttribute("records", maintRepo.findByAssetUserIdOrderByMaintenanceDateDesc(uid));
        m.addAttribute("total", AssetService.nz(maintRepo.totalByUser(uid)));
        m.addAttribute("selectedAssetId", assetId);
        m.addAttribute("types", List.of("Cleaning", "Servicing", "Inspection", "Preventive Maintenance", "Other"));
        m.addAttribute("today", LocalDate.now());
        return "maintenance";
    }

    @PostMapping("/maintenance")
    public String saveMaintenance(@SessionAttribute("userId") Long uid, @RequestParam Long assetId,
                                  @RequestParam(defaultValue = "false") boolean markUnder,
                                  @Valid @ModelAttribute MaintenanceRecord rec, BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) { ra.addFlashAttribute("error", firstError(br)); return "redirect:/maintenance?assetId=" + assetId; }
        svc.addMaintenance(uid, assetId, rec, markUnder);
        ra.addFlashAttribute("msg", "Maintenance recorded");
        return "redirect:/maintenance";
    }

    // ---------- REPAIRS ----------
    @GetMapping("/repairs")
    public String repairs(@SessionAttribute("userId") Long uid, @RequestParam(required = false) Long assetId, Model m) {
        m.addAttribute("assets", svc.activeAssets(uid));
        m.addAttribute("records", repairRepo.findByAssetUserIdOrderByRepairDateDesc(uid));
        m.addAttribute("total", AssetService.nz(repairRepo.totalByUser(uid)));
        m.addAttribute("selectedAssetId", assetId);
        m.addAttribute("statuses", AssetService.REPAIR_STATUSES);
        m.addAttribute("conditions", AssetController.CONDITIONS);
        m.addAttribute("types", List.of("Hardware", "Software", "Electrical", "Mechanical", "Cosmetic", "Other"));
        m.addAttribute("today", LocalDate.now());
        return "repairs";
    }

    @PostMapping("/repairs")
    public String saveRepair(@SessionAttribute("userId") Long uid, @RequestParam Long assetId,
                             @RequestParam(defaultValue = "") String newCondition,
                             @Valid @ModelAttribute RepairRecord rec, BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) { ra.addFlashAttribute("error", firstError(br)); return "redirect:/repairs?assetId=" + assetId; }
        svc.addRepair(uid, assetId, rec, newCondition);
        ra.addFlashAttribute("msg", "Repair recorded");
        return "redirect:/repairs";
    }

    @PostMapping("/repairs/{id}/complete")
    public String completeRepair(@SessionAttribute("userId") Long uid, @PathVariable Long id, RedirectAttributes ra) {
        svc.completeRepair(uid, id);
        ra.addFlashAttribute("msg", "Repair marked as completed");
        return "redirect:/repairs";
    }

    // ---------- UPGRADES ----------
    @GetMapping("/upgrades")
    public String upgrades(@SessionAttribute("userId") Long uid, @RequestParam(required = false) Long assetId,
                           @RequestParam(required = false) Long editId, Model m) {
        UpgradeRecord form = new UpgradeRecord();
        form.setUpgradeDate(LocalDate.now());
        if (editId != null) {
            form = upgradeRepo.findByIdAndAssetUserId(editId, uid).orElse(form);
            if (form.getAsset() != null) assetId = form.getAsset().getId();
        }
        m.addAttribute("form", form);
        m.addAttribute("assets", svc.activeAssets(uid));
        m.addAttribute("records", upgradeRepo.findByAssetUserIdOrderByUpgradeDateDesc(uid));
        m.addAttribute("total", AssetService.nz(upgradeRepo.totalByUser(uid)));
        m.addAttribute("selectedAssetId", assetId);
        return "upgrades";
    }

    @PostMapping("/upgrades")
    public String saveUpgrade(@SessionAttribute("userId") Long uid, @RequestParam Long assetId,
                              @Valid @ModelAttribute("form") UpgradeRecord form, BindingResult br, RedirectAttributes ra) {
        if (br.hasErrors()) { ra.addFlashAttribute("error", firstError(br)); return "redirect:/upgrades?assetId=" + assetId; }
        svc.saveUpgrade(uid, assetId, form);
        ra.addFlashAttribute("msg", "Upgrade saved");
        return "redirect:/upgrades";
    }
}
