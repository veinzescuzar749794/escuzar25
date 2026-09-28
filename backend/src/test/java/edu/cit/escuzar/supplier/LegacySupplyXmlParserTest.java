package edu.cit.escuzar.supplier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacySupplyXmlParserTest {

    @Test
    void buildsAuthRequestXml() {
        String xml = LegacySupplyXmlParser.buildAuthRequest("22-1382-413", "KEY123");
        assertTrue(xml.contains("<ClientId>22-1382-413</ClientId>"));
        assertTrue(xml.contains("<ApiKey>KEY123</ApiKey>"));
    }

    @Test
    void buildsPurchaseOrderXml() {
        String xml = LegacySupplyXmlParser.buildPurchaseOrder("WQC-6004", 2, "RO-1");
        assertTrue(xml.contains("<SupplierSku>WQC-6004</SupplierSku>"));
        assertTrue(xml.contains("<Qty>2</Qty>"));
        assertTrue(xml.contains("<BuyerRef>RO-1</BuyerRef>"));
    }

    @Test
    void parsesAuthResponse() {
        String xml = "<AuthResponse><SessionToken>tok-abc-123</SessionToken><IssuedAt>2026-09-28T10:00:00Z</IssuedAt></AuthResponse>";
        LegacySupplyXmlParser.AuthResponseDto auth = LegacySupplyXmlParser.parseAuthResponse(xml);
        assertEquals("tok-abc-123", auth.sessionToken());
        assertEquals("2026-09-28T10:00:00Z", auth.issuedAt());
    }

    @Test
    void parsesPurchaseOrderAck() {
        String xml = "<PurchaseOrderAck><PoNumber>PO-100231</PoNumber><StatusCode>10</StatusCode><SupplierSku>WQC-6004</SupplierSku><Qty>2</Qty><Uom>CS</Uom><BuyerRef>RO-1</BuyerRef><CreatedAt>2026-09-24T01:16:02.000Z</CreatedAt></PurchaseOrderAck>";
        LegacySupplyXmlParser.PurchaseOrderAckDto ack = LegacySupplyXmlParser.parsePurchaseOrderAck(xml);
        assertEquals("PO-100231", ack.poNumber());
        assertEquals(10, ack.statusCode());
        assertEquals("WQC-6004", ack.supplierSku());
        assertEquals(2, ack.qty());
        assertEquals("CS", ack.uom());
        assertEquals("RO-1", ack.buyerRef());
    }

    @Test
    void parsesPurchaseOrderStatus() {
        String xml = "<PurchaseOrderStatus><PoNumber>PO-100231</PoNumber><StatusCode>40</StatusCode><SupplierSku>WQC-6004</SupplierSku><Qty>2</Qty><Uom>CS</Uom><BuyerRef>RO-1</BuyerRef><CreatedAt>2026-09-24T01:16:02.000Z</CreatedAt><CheckedAt>2026-09-24T01:25:00.000Z</CheckedAt></PurchaseOrderStatus>";
        LegacySupplyXmlParser.PurchaseOrderStatusDto status = LegacySupplyXmlParser.parsePurchaseOrderStatus(xml);
        assertEquals("PO-100231", status.poNumber());
        assertEquals(40, status.statusCode());
        assertEquals("2026-09-24T01:25:00.000Z", status.checkedAt());
    }

    @Test
    void parsesPurchaseOrderList() {
        String xml = "<PurchaseOrderList><Count>1</Count><PurchaseOrder><PoNumber>PO-100231</PoNumber><StatusCode>20</StatusCode><SupplierSku>WQC-6004</SupplierSku><Qty>1</Qty><Uom>CS</Uom><BuyerRef>RO-1</BuyerRef><CreatedAt>2026-09-24T01:16:02.000Z</CreatedAt></PurchaseOrder></PurchaseOrderList>";
        LegacySupplyXmlParser.PurchaseOrderListDto list = LegacySupplyXmlParser.parsePurchaseOrderList(xml);
        assertEquals(1, list.count());
        assertEquals(1, list.orders().size());
        assertEquals("PO-100231", list.orders().get(0).poNumber());
        assertEquals(20, list.orders().get(0).statusCode());
    }

    @Test
    void parsesLSError() {
        String xml = "<LSError><Code>E-QTY-11</Code><Message>Quantity invalid.</Message></LSError>";
        LegacySupplyXmlParser.LSErrorDto err = LegacySupplyXmlParser.parseLSError(xml);
        assertEquals("E-QTY-11", err.code());
        assertEquals("Quantity invalid.", err.message());
    }
}
