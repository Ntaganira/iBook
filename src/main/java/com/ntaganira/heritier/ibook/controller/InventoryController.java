/**
 * <pre>
 * - Project   : Ebook Online - Cloud Accounting Platform
 * - Package   : com.ntaganira.heritier.ibook.controller
 * - File      : InventoryController.java
 * - Date      : 2026. 09. 18.
 * - User      : Hntaganira
 * - Desc      : Inventory web controller
 * </pre>
 */
package com.ntaganira.heritier.ibook.controller;

import com.ntaganira.heritier.ibook.dto.CategoryForm;
import com.ntaganira.heritier.ibook.dto.ProductForm;
import com.ntaganira.heritier.ibook.dto.StockAdjustmentForm;
import com.ntaganira.heritier.ibook.dto.WarehouseForm;
import com.ntaganira.heritier.ibook.entity.Company;
import com.ntaganira.heritier.ibook.entity.Product;
import com.ntaganira.heritier.ibook.entity.ProductCategory;
import com.ntaganira.heritier.ibook.entity.Warehouse;
import com.ntaganira.heritier.ibook.enums.MovementType;
import com.ntaganira.heritier.ibook.enums.ProductType;
import com.ntaganira.heritier.ibook.repository.CompanyRepository;
import com.ntaganira.heritier.ibook.service.AuditService;
import com.ntaganira.heritier.ibook.service.InventoryService;
import com.ntaganira.heritier.ibook.service.TaxRateService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Controller
@RequestMapping("/inventory")
public class InventoryController {

    private static final int PAGE_SIZE = 25;

    private final InventoryService inventoryService;
    private final TaxRateService taxRateService;
    private final CompanyRepository companyRepository;
    private final MessageSource messageSource;

    public InventoryController(InventoryService inventoryService,
                               TaxRateService taxRateService,
                               CompanyRepository companyRepository,
                               MessageSource messageSource) {
        this.inventoryService = inventoryService;
        this.taxRateService = taxRateService;
        this.companyRepository = companyRepository;
        this.messageSource = messageSource;
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }

    private Map<String, String> flash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "success");
    }

    private Map<String, String> errorFlash(String key, String detail) {
        return Map.of("title", msg(key), "detail", detail == null ? "" : detail, "type", "error");
    }

    private String baseCurrency() {
        Company company = companyRepository.findFirstByOrderByIdAsc().orElse(null);
        return company != null && company.getCurrencyCode() != null ? company.getCurrencyCode() : "RWF";
    }

    private Map<String, String> typeLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ProductType t : ProductType.values()) {
            m.put(t.name(), msg("inv.prod.type." + t.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    private Map<String, String> movementLabels() {
        Map<String, String> m = new LinkedHashMap<>();
        for (MovementType t : MovementType.values()) {
            m.put(t.name(), msg("inv.mv.type." + t.name().toLowerCase(Locale.ROOT)));
        }
        return m;
    }

    // ----- Products -----

    @GetMapping({"", "/products"})
    public String products(@RequestParam(value = "q", required = false) String q,
                           @RequestParam(value = "category", required = false) Long categoryId,
                           @RequestParam(value = "type", required = false) String type,
                           @RequestParam(value = "sort", defaultValue = "name") String sort,
                           @RequestParam(value = "dir", defaultValue = "asc") String dir,
                           @RequestParam(value = "page", defaultValue = "0") int page,
                           Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "name", "name", "sku", "categoryName",
                "costPrice", "sellingPrice", "active");
        model.addAttribute("products",
                inventoryService.listProducts(q, categoryId, type, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("onHand", inventoryService.onHandByProduct(LocalDate.now()));
        model.addAttribute("summary", inventoryService.summary());
        model.addAttribute("categories", inventoryService.listActiveCategories());
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("q", q);
        model.addAttribute("category", categoryId);
        model.addAttribute("type", type == null ? "" : type);
        StringBuilder fq = new StringBuilder();
        if (q != null && !q.isBlank()) {
            fq.append("q=").append(UriUtils.encodeQueryParam(q, StandardCharsets.UTF_8));
        }
        if (categoryId != null) {
            if (!fq.isEmpty()) {
                fq.append('&');
            }
            fq.append("category=").append(categoryId);
        }
        SortSpec.addListContext(model, "/inventory/products", fq.isEmpty() ? "" : "?" + fq, sp);
        return "inventory/products";
    }

    @GetMapping("/products/new")
    public String newProduct(Model model) {
        addProductContext(model, "create", null);
        model.addAttribute("form", ProductForm.empty());
        return "inventory/product-form";
    }

    @GetMapping("/products/{id}/edit")
    public String editProduct(@PathVariable Long id, Model model, RedirectAttributes ra) {
        Product p = inventoryService.getProduct(id);
        if (p == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.prod.notFound", null));
            return "redirect:/inventory/products";
        }
        addProductContext(model, "edit", id);
        model.addAttribute("form", new ProductForm(p.getSku(), p.getName(), p.getDescription(),
                p.getType().name(), p.getCategoryId(), p.getBrand(), p.getUnit(),
                p.getCostPrice(), p.getSellingPrice(), p.getTaxRateId(), p.getReorderLevel(),
                p.isTrackStock(), p.isActive()));
        model.addAttribute("onHandQty", inventoryService.onHand(id));
        return "inventory/product-form";
    }

    private void addProductContext(Model model, String mode, Long editingId) {
        model.addAttribute("mode", mode);
        model.addAttribute("editingId", editingId);
        model.addAttribute("categories", inventoryService.listActiveCategories());
        model.addAttribute("taxRates", taxRateService.listActive());
        model.addAttribute("typeLabels", typeLabels());
        model.addAttribute("baseCurrency", baseCurrency());
    }

    @PostMapping("/products")
    public String createProduct(@Valid @ModelAttribute("form") ProductForm form,
                                BindingResult br, Model model, RedirectAttributes ra) {
        if (inventoryService.skuExists(form.sku(), null)) {
            br.rejectValue("sku", "inv.prod.skuExists");
        }
        if (br.hasErrors()) {
            addProductContext(model, "create", null);
            return "inventory/product-form";
        }
        Product saved = inventoryService.saveProduct(form, null);
        ra.addFlashAttribute("flashMessage", flash("inv.prod.saved", saved.getName()));
        return "redirect:/inventory/products";
    }

    @PostMapping("/products/{id}")
    public String updateProduct(@PathVariable Long id, @Valid @ModelAttribute("form") ProductForm form,
                                BindingResult br, Model model, RedirectAttributes ra) {
        if (inventoryService.getProduct(id) == null) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.prod.notFound", null));
            return "redirect:/inventory/products";
        }
        if (inventoryService.skuExists(form.sku(), id)) {
            br.rejectValue("sku", "inv.prod.skuExists");
        }
        if (br.hasErrors()) {
            addProductContext(model, "edit", id);
            return "inventory/product-form";
        }
        Product saved = inventoryService.saveProduct(form, id);
        ra.addFlashAttribute("flashMessage", flash("inv.prod.saved", saved.getName()));
        return "redirect:/inventory/products";
    }

    @PostMapping("/products/{id}/toggle")
    public String toggleProduct(@PathVariable Long id, RedirectAttributes ra) {
        inventoryService.toggleProduct(id);
        ra.addFlashAttribute("flashMessage", flash("inv.prod.saved", null));
        return "redirect:/inventory/products";
    }

    @PostMapping("/products/{id}/delete")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes ra) {
        try {
            inventoryService.deleteProduct(id);
            ra.addFlashAttribute("flashMessage", flash("inv.prod.deleted", null));
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.prod.hasMovements", null));
        }
        return "redirect:/inventory/products";
    }

    // ----- Categories -----

    @GetMapping("/categories")
    public String categories(Model model) {
        model.addAttribute("categories", inventoryService.listCategories());
        model.addAttribute("form", new CategoryForm("", "", null, true));
        model.addAttribute("counts", countsByCategory());
        return "inventory/categories";
    }

    private Map<Long, Long> countsByCategory() {
        Map<Long, Long> counts = new LinkedHashMap<>();
        for (ProductCategory c : inventoryService.listCategories()) {
            counts.put(c.getId(), inventoryService.productsInCategory(c.getId()));
        }
        return counts;
    }

    @PostMapping("/categories")
    public String createCategory(@Valid @ModelAttribute("form") CategoryForm form,
                                 BindingResult br, Model model, RedirectAttributes ra) {
        if (inventoryService.categoryCodeExists(form.code(), null)) {
            br.rejectValue("code", "inv.cat.codeExists");
        }
        if (br.hasErrors()) {
            model.addAttribute("categories", inventoryService.listCategories());
            model.addAttribute("counts", countsByCategory());
            return "inventory/categories";
        }
        inventoryService.saveCategory(form, null);
        ra.addFlashAttribute("flashMessage", flash("inv.cat.saved", form.name()));
        return "redirect:/inventory/categories";
    }

    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable Long id, @Valid @ModelAttribute("form") CategoryForm form,
                                 BindingResult br, Model model, RedirectAttributes ra) {
        if (inventoryService.categoryCodeExists(form.code(), id)) {
            br.rejectValue("code", "inv.cat.codeExists");
        }
        if (br.hasErrors()) {
            model.addAttribute("categories", inventoryService.listCategories());
            model.addAttribute("counts", countsByCategory());
            return "inventory/categories";
        }
        inventoryService.saveCategory(form, id);
        ra.addFlashAttribute("flashMessage", flash("inv.cat.saved", form.name()));
        return "redirect:/inventory/categories";
    }

    @PostMapping("/categories/{id}/delete")
    public String deleteCategory(@PathVariable Long id, RedirectAttributes ra) {
        try {
            inventoryService.deleteCategory(id);
            ra.addFlashAttribute("flashMessage", flash("inv.cat.deleted", null));
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.cat.inUse", null));
        }
        return "redirect:/inventory/categories";
    }

    // ----- Warehouses -----

    @GetMapping("/warehouses")
    public String warehouses(Model model) {
        model.addAttribute("warehouses", inventoryService.listWarehouses());
        model.addAttribute("form", new WarehouseForm("", "", null, false, true));
        return "inventory/warehouses";
    }

    @PostMapping("/warehouses")
    public String createWarehouse(@Valid @ModelAttribute("form") WarehouseForm form,
                                  BindingResult br, Model model, RedirectAttributes ra) {
        if (inventoryService.warehouseCodeExists(form.code(), null)) {
            br.rejectValue("code", "inv.wh.codeExists");
        }
        if (br.hasErrors()) {
            model.addAttribute("warehouses", inventoryService.listWarehouses());
            return "inventory/warehouses";
        }
        Warehouse saved = inventoryService.saveWarehouse(form, null);
        ra.addFlashAttribute("flashMessage", flash("inv.wh.saved", saved.getName()));
        return "redirect:/inventory/warehouses";
    }

    @PostMapping("/warehouses/{id}")
    public String updateWarehouse(@PathVariable Long id, @Valid @ModelAttribute("form") WarehouseForm form,
                                  BindingResult br, Model model, RedirectAttributes ra) {
        if (inventoryService.warehouseCodeExists(form.code(), id)) {
            br.rejectValue("code", "inv.wh.codeExists");
        }
        if (br.hasErrors()) {
            model.addAttribute("warehouses", inventoryService.listWarehouses());
            return "inventory/warehouses";
        }
        Warehouse saved = inventoryService.saveWarehouse(form, id);
        ra.addFlashAttribute("flashMessage", flash("inv.wh.saved", saved.getName()));
        return "redirect:/inventory/warehouses";
    }

    // ----- Movements -----

    @GetMapping("/movements")
    public String movements(@RequestParam(value = "product", required = false) Long productId,
                            @RequestParam(value = "warehouse", required = false) Long warehouseId,
                            @RequestParam(value = "type", required = false) String type,
                            @RequestParam(value = "from", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(value = "to", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(value = "sort", defaultValue = "movementDate") String sort,
                            @RequestParam(value = "dir", defaultValue = "desc") String dir,
                            @RequestParam(value = "page", defaultValue = "0") int page,
                            Model model) {
        SortSpec sp = SortSpec.resolve(sort, dir, "movementDate", "movementDate", "productName",
                "movementType", "quantity");
        model.addAttribute("movements", inventoryService.listMovements(productId, warehouseId, type,
                from, to, PageRequest.of(page, PAGE_SIZE, sp.sort())));
        model.addAttribute("products", inventoryService.listStockedProducts());
        model.addAttribute("warehouses", inventoryService.listActiveWarehouses());
        model.addAttribute("movementLabels", movementLabels());
        model.addAttribute("selectedProduct", productId);
        model.addAttribute("selectedWarehouse", warehouseId);
        model.addAttribute("type", type == null ? "" : type);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("baseCurrency", baseCurrency());
        SortSpec.addListContext(model, "/inventory/movements", "", sp);
        return "inventory/movements";
    }

    // ----- Adjustments -----

    @GetMapping("/adjustments")
    public String adjustments(Model model) {
        model.addAttribute("products", inventoryService.listStockedProducts());
        model.addAttribute("warehouses", inventoryService.listActiveWarehouses());
        model.addAttribute("onHand", inventoryService.onHandByProduct(LocalDate.now()));
        model.addAttribute("movementLabels", movementLabels());
        model.addAttribute("recent", inventoryService.listMovements(null, null, null, null, null,
                PageRequest.of(0, 10, SortSpec.resolve("movementDate", "desc", "movementDate",
                        "movementDate").sort())));
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("today", LocalDate.now());
        return "inventory/adjustments";
    }

    @PostMapping("/adjustments")
    public String adjust(@ModelAttribute StockAdjustmentForm form, RedirectAttributes ra) {
        try {
            inventoryService.adjust(form, AuditService.currentUsername());
            ra.addFlashAttribute("flashMessage", flash("inv.adj.recorded", null));
        } catch (RuntimeException ex) {
            ra.addFlashAttribute("flashMessage", errorFlash("inv.adj.failed", ex.getMessage()));
        }
        return "redirect:/inventory/adjustments";
    }

    // ----- Valuation -----

    @GetMapping("/valuation")
    public String valuation(@RequestParam(value = "asOf", required = false)
                            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
                            Model model) {
        LocalDate date = asOf != null ? asOf : LocalDate.now();
        model.addAttribute("asOf", date);
        model.addAttribute("report", inventoryService.valuation(date));
        model.addAttribute("baseCurrency", baseCurrency());
        model.addAttribute("company", companyRepository.findFirstByOrderByIdAsc().orElse(null));
        return "inventory/valuation";
    }
}
