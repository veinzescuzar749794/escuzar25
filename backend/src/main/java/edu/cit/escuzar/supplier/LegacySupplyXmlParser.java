package edu.cit.escuzar.supplier;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Package-private XML parser and serializer for LegacySupply XML schemas.
 * Standard Java DOM parsing with XXE protection.
 */
class LegacySupplyXmlParser {

    record AuthResponseDto(String sessionToken, String issuedAt) {}
    record PurchaseOrderAckDto(String poNumber, int statusCode, String supplierSku, int qty, String uom, String buyerRef, String createdAt) {}
    record PurchaseOrderStatusDto(String poNumber, int statusCode, String supplierSku, int qty, String uom, String buyerRef, String createdAt, String checkedAt) {}
    record PurchaseOrderListDto(int count, List<PurchaseOrderStatusDto> orders) {}
    record LSErrorDto(String code, String message) {}

    private static DocumentBuilder newSafeDocumentBuilder() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder();
    }

    public static String buildAuthRequest(String clientId, String apiKey) {
        return "<AuthRequest>"
                + "<ClientId>" + escapeXml(clientId) + "</ClientId>"
                + "<ApiKey>" + escapeXml(apiKey) + "</ApiKey>"
                + "</AuthRequest>";
    }

    public static String buildPurchaseOrder(String supplierSku, int qty, String buyerRef) {
        return "<PurchaseOrder>"
                + "<SupplierSku>" + escapeXml(supplierSku) + "</SupplierSku>"
                + "<Qty>" + qty + "</Qty>"
                + "<BuyerRef>" + escapeXml(buyerRef) + "</BuyerRef>"
                + "</PurchaseOrder>";
    }

    public static AuthResponseDto parseAuthResponse(String xml) {
        try {
            Document doc = parseXml(xml);
            String token = getTagValue(doc.getDocumentElement(), "SessionToken");
            String issuedAt = getTagValue(doc.getDocumentElement(), "IssuedAt");
            return new AuthResponseDto(token, issuedAt);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse AuthResponse XML: " + xml, e);
        }
    }

    public static PurchaseOrderAckDto parsePurchaseOrderAck(String xml) {
        try {
            Document doc = parseXml(xml);
            Element root = doc.getDocumentElement();
            String poNumber = getTagValue(root, "PoNumber");
            int statusCode = Integer.parseInt(getTagValue(root, "StatusCode"));
            String sku = getTagValue(root, "SupplierSku");
            int qty = Integer.parseInt(getTagValue(root, "Qty"));
            String uom = getTagValue(root, "Uom");
            String buyerRef = getTagValue(root, "BuyerRef");
            String createdAt = getTagValue(root, "CreatedAt");
            return new PurchaseOrderAckDto(poNumber, statusCode, sku, qty, uom, buyerRef, createdAt);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse PurchaseOrderAck XML: " + xml, e);
        }
    }

    public static PurchaseOrderStatusDto parsePurchaseOrderStatus(String xml) {
        try {
            Document doc = parseXml(xml);
            Element root = doc.getDocumentElement();
            String poNumber = getTagValue(root, "PoNumber");
            int statusCode = parseStatusCode(getTagValue(root, "StatusCode"));
            String sku = getTagValue(root, "SupplierSku");
            int qty = Integer.parseInt(getTagValue(root, "Qty"));
            String uom = getTagValue(root, "Uom");
            String buyerRef = getTagValue(root, "BuyerRef");
            String createdAt = getTagValue(root, "CreatedAt");
            String checkedAt = getTagValue(root, "CheckedAt");
            return new PurchaseOrderStatusDto(poNumber, statusCode, sku, qty, uom, buyerRef, createdAt, checkedAt);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse PurchaseOrderStatus XML: " + xml, e);
        }
    }

    public static PurchaseOrderListDto parsePurchaseOrderList(String xml) {
        try {
            Document doc = parseXml(xml);
            Element root = doc.getDocumentElement();
            int count = 0;
            String countStr = getTagValue(root, "Count");
            if (countStr != null && !countStr.isBlank()) {
                count = Integer.parseInt(countStr.trim());
            }
            List<PurchaseOrderStatusDto> orders = new ArrayList<>();
            NodeList list = root.getElementsByTagName("PurchaseOrder");
            for (int i = 0; i < list.getLength(); i++) {
                Element el = (Element) list.item(i);
                String poNumber = getTagValue(el, "PoNumber");
                int statusCode = parseStatusCode(getTagValue(el, "StatusCode"));
                String sku = getTagValue(el, "SupplierSku");
                int qty = Integer.parseInt(getTagValue(el, "Qty"));
                String uom = getTagValue(el, "Uom");
                String buyerRef = getTagValue(el, "BuyerRef");
                String createdAt = getTagValue(el, "CreatedAt");
                orders.add(new PurchaseOrderStatusDto(poNumber, statusCode, sku, qty, uom, buyerRef, createdAt, null));
            }
            return new PurchaseOrderListDto(count, orders);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse PurchaseOrderList XML: " + xml, e);
        }
    }

    public static LSErrorDto parseLSError(String xml) {
        try {
            Document doc = parseXml(xml);
            Element root = doc.getDocumentElement();
            String code = getTagValue(root, "Code");
            String message = getTagValue(root, "Message");
            return new LSErrorDto(code, message);
        } catch (Exception e) {
            return new LSErrorDto("E-UNKNOWN", xml);
        }
    }

    private static Document parseXml(String xml) throws Exception {
        DocumentBuilder builder = newSafeDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml.trim())));
    }

    private static String getTagValue(Element parent, String tagName) {
        NodeList nl = parent.getElementsByTagName(tagName);
        if (nl != null && nl.getLength() > 0) {
            return nl.item(0).getTextContent().trim();
        }
        return null;
    }

    private static int parseStatusCode(String codeStr) {
        if (codeStr == null || codeStr.isBlank()) {
            return -1;
        }
        try {
            return Integer.parseInt(codeStr.trim());
        } catch (NumberFormatException e) {
            // In case of non-numeric status code, return -1
            return -1;
        }
    }

    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
