package edu.cit.escuzar.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory")
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    private String productId;

    @Column(name = "name")
    private String name;

    @Column(name = "stock")
    private int stock;

    protected InventoryItem() {
        // required by JPA
    }

    public InventoryItem(String productId, String name, int stock) {
        this.productId = productId;
        this.name = name;
        this.stock = stock;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }
}
