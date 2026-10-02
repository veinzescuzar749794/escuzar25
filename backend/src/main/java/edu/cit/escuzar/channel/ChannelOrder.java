package edu.cit.escuzar.channel;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tiangge_orders")
class ChannelOrder {
    @Id @Column(name = "marketplace_order_id", length = 80) private String marketplaceOrderId;
    @Column(name = "shop_order_id") private Long shopOrderId;
    @Column(nullable = false, length = 30) private String status;
    @Column(name = "lines_json", nullable = false, columnDefinition = "text") private String linesJson;
    protected ChannelOrder() {}
    ChannelOrder(String id, Long shopOrderId, String status, String linesJson) {
        this.marketplaceOrderId = id; this.shopOrderId = shopOrderId; this.status = status; this.linesJson = linesJson;
    }
    String getMarketplaceOrderId() { return marketplaceOrderId; }
    Long getShopOrderId() { return shopOrderId; }
    void setShopOrderId(Long shopOrderId) { this.shopOrderId = shopOrderId; }
    String getStatus() { return status; }
    void setStatus(String status) { this.status = status; }
    String getLinesJson() { return linesJson; }
}
