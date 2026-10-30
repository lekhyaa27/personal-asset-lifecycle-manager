package com.assetmanager.model;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Holds the values typed into the Add/Edit Asset form, with validation rules. */
@Getter @Setter
public class AssetForm {
    @NotBlank(message = "Asset name is required") @Size(max = 150) private String name;
    @NotBlank(message = "Category is required") private String category;
    private String brand;
    private String model;
    private String serialNumber;
    @NotNull(message = "Purchase price is required")
    @DecimalMin(value = "0.01", message = "Purchase price must be greater than 0")
    private BigDecimal purchasePrice;
    @NotNull(message = "Purchase date is required")
    @PastOrPresent(message = "Purchase date cannot be in the future")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate purchaseDate;
    private String purchaseLocation;
    private String assetCondition = "GOOD";
    @Min(value = 1, message = "Useful life must be at least 1 year") @Max(value = 100, message = "Useful life is too large")
    private Integer usefulLifeYears;
    @DecimalMin(value = "0.00", message = "Salvage value cannot be negative") private BigDecimal salvageValue;
    @Size(max = 1000) private String description;
    @Size(max = 1000) private String notes;

    private String warrantyProvider;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate warrantyStartDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate warrantyEndDate;
    private String insuranceProvider;
    private String insurancePolicyNumber;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate insuranceStartDate;
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) private LocalDate insuranceEndDate;

    public static AssetForm from(Asset a) {
        AssetForm f = new AssetForm();
        f.name = a.getName(); f.category = a.getCategory(); f.brand = a.getBrand(); f.model = a.getModel();
        f.serialNumber = a.getSerialNumber(); f.purchasePrice = a.getPurchasePrice(); f.purchaseDate = a.getPurchaseDate();
        f.purchaseLocation = a.getPurchaseLocation(); f.assetCondition = a.getAssetCondition();
        f.usefulLifeYears = a.getUsefulLifeYears(); f.salvageValue = a.getSalvageValue();
        f.description = a.getDescription(); f.notes = a.getNotes();
        return f;
    }
}
