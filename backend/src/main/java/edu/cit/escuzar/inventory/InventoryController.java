package edu.cit.escuzar.inventory;

import edu.cit.escuzar.inventory.dto.InventoryItemView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
@CrossOrigin(origins = "${app.cors.allowed-origin:http://localhost:5173}")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public ResponseEntity<List<InventoryItemView>> getInventory() {
        List<InventoryItemView> items = inventoryService.getAllItems();
        return ResponseEntity.ok(items);
    }
}

