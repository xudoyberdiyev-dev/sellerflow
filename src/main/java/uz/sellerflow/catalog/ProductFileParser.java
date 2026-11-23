package uz.sellerflow.catalog;

import uz.sellerflow.calculation.Money;
import uz.sellerflow.catalog.ProductImportService.Row;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * V2: mahsulotlar faylini o'qish — CSV (UTF-8 yoki Windows-1251, ; , yoki tab) va XLSX (birinchi varaq).
 * Ustunlar sarlavha bo'yicha avtomatik aniqlanadi (o'zbek/rus/ingliz). Tashqi kutubxonasiz; zip-bomb va hajm chegaralari bor.
 */
public final class ProductFileParser {

    public static final long MAX_FILE_BYTES = 5L * 1024 * 1024;
    static final long MAX_UNZIPPED_BYTES = 40L * 1024 * 1024;
    static final int MAX_ROWS = ProductImportService.MAX_ROWS;

    public static final class ParseException extends RuntimeException {
        public ParseException(String code) { super(code); }
    }

    public record Parsed(List<Row> rows, Map<String, String> columns, int dataRows) {}

    enum Col { NAME, SKU, BARCODE, COST, PRICE }

    // Tartib muhim: «закупочная цена» avval COST ga tushishi kerak, keyin PRICE tekshiriladi
    private static final Map<Col, String[]> KEYS = new java.util.LinkedHashMap<>();
    static {
        KEYS.put(Col.COST, new String[]{"tannarx", "tan narx", "себестоим", "закуп", "purchase", "cost"});
        KEYS.put(Col.BARCODE, new String[]{"barcode", "shtrix", "штрих", "баркод", "ean"});
        KEYS.put(Col.SKU, new String[]{"sku", "artikul", "артикул", "seller item", "selleritemcode", "код товара"});
        KEYS.put(Col.PRICE, new String[]{"sotish narx", "narx", "цена", "стоимость", "price"});
        KEYS.put(Col.NAME, new String[]{"nomi", "nom", "mahsulot", "наименован", "назван", "товар", "title", "name"});
    }

    private ProductFileParser() {}

    public static Parsed parse(String fileName, byte[] data) {
        if (data == null || data.length == 0) throw new ParseException("EMPTY");
        if (data.length > MAX_FILE_BYTES) throw new ParseException("FILE_TOO_LARGE");
        String n = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        boolean zip = data.length > 4 && data[0] == 'P' && data[1] == 'K';
        List<List<String>> table;
        if (n.endsWith(".xlsx") || zip) table = readXlsx(data);
        else if (n.endsWith(".xls")) throw new ParseException("XLS_UNSUPPORTED");
        else table = readCsv(data);
        return toRows(table);
    }

    // ------------------------------------------------------------------ mapping

    static Parsed toRows(List<List<String>> table) {
        // Sarlavha qatori: birinchi 10 qator ichida kamida nom va (tannarx yoki narx yoki SKU) bor qator
        int headerIdx = -1;
        Map<Col, Integer> map = null;
        for (int i = 0; i < Math.min(10, table.size()); i++) {
            Map<Col, Integer> m = detect(table.get(i));
            if (m.containsKey(Col.NAME) && (m.containsKey(Col.COST) || m.containsKey(Col.PRICE) || m.containsKey(Col.SKU))) {
                headerIdx = i;
                map = m;
                break;
            }
        }
        if (map == null) throw new ParseException("NO_HEADER");
        List<String> header = table.get(headerIdx);
        Map<String, String> columns = new java.util.LinkedHashMap<>();
        for (var e : map.entrySet()) columns.put(e.getKey().name(), header.get(e.getValue()));
        List<Row> rows = new ArrayList<>();
        int data = 0;
        for (int i = headerIdx + 1; i < table.size(); i++) {
            List<String> r = table.get(i);
            if (r.stream().allMatch(c -> c == null || c.isBlank())) continue;
            if (++data > MAX_ROWS) throw new ParseException("TOO_MANY_ROWS");
            rows.add(new Row(null, null, null, cell(r, map.get(Col.NAME)), cell(r, map.get(Col.SKU)),
                    cell(r, map.get(Col.BARCODE)), num(cell(r, map.get(Col.COST))), num(cell(r, map.get(Col.PRICE))),
                    null, null, null, null));
        }
        if (rows.isEmpty()) throw new ParseException("NO_ROWS");
        return new Parsed(rows, columns, data);
    }

    private static Map<Col, Integer> detect(List<String> header) {
        Map<Col, Integer> m = new HashMap<>();
        for (int i = 0; i < header.size(); i++) {
            String h = header.get(i) == null ? "" : header.get(i).toLowerCase(Locale.ROOT).replace('ʻ', '\'').replace('‘', '\'').strip();
            if (h.isEmpty()) continue;
            for (var e : KEYS.entrySet()) {
                if (m.containsKey(e.getKey())) continue;
                boolean hit = false;
                for (String k : e.getValue()) {
                    if (h.contains(k)) {
                        hit = true;
                        break;
                    }
                }
                if (hit) {
                    m.put(e.getKey(), i);
                    break; // bitta ustun — bitta ma'no
                }
            }
        }
        return m;
    }

    private static String cell(List<String> r, Integer idx) {
        if (idx == null || idx >= r.size()) return null;
        String v = r.get(idx);
        return v == null || v.isBlank() ? null : v.strip();
    }

    static BigDecimal num(String raw) {
        if (raw == null) return null;
        String t = raw.replace(" ", " ").replaceAll("(?i)(so'm|so‘m|сум|sum|uzs)", "").strip();
        if (t.isEmpty()) return null;
        var p = Money.parse(t);
        if (p.isPresent()) return p.get();
        // Excel'dan kelgan 120000.5 kabi ko'p kasrli qiymatlar
        try {
            BigDecimal b = new BigDecimal(t.replace(" ", "").replace(',', '.'));
            return b.signum() < 0 ? null : b;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ CSV

    static List<List<String>> readCsv(byte[] data) {
        String text = decode(data);
        if (!text.isEmpty() && text.charAt(0) == '﻿') text = text.substring(1);
        String first = text.lines().findFirst().orElse("");
        char sep = ';';
        int best = count(first, ';');
        if (count(first, ',') > best) { sep = ','; best = count(first, ','); }
        if (count(first, '\t') > best) sep = '\t';
        List<List<String>> out = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean q = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (q) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else q = false;
                } else cur.append(c);
            } else if (c == '"') q = true;
            else if (c == sep) {
                row.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(cur.toString());
                cur.setLength(0);
                out.add(row);
                row = new ArrayList<>();
                if (out.size() > MAX_ROWS + 20) throw new ParseException("TOO_MANY_ROWS");
            } else cur.append(c);
        }
        if (cur.length() > 0 || !row.isEmpty()) {
            row.add(cur.toString());
            out.add(row);
        }
        // eksportimizdagi «'» himoya belgisini olib tashlash
        for (List<String> r : out) r.replaceAll(v -> v != null && v.startsWith("'") && v.length() > 1 && "=+-@".indexOf(v.charAt(1)) >= 0 ? v.substring(1) : v);
        return out;
    }

    private static int count(String s, char c) {
        int n = 0;
        for (int i = 0; i < s.length(); i++) if (s.charAt(i) == c) n++;
        return n;
    }

    private static String decode(byte[] data) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(data)).toString();
        } catch (CharacterCodingException e) {
            return new String(data, Charset.forName("windows-1251"));
        }
    }

    // ------------------------------------------------------------------ XLSX (minimal: shared strings + birinchi varaq)

    static List<List<String>> readXlsx(byte[] data) {
        Map<String, byte[]> parts = unzip(data);
        List<String> shared = parts.containsKey("xl/sharedStrings.xml") ? sharedStrings(parts.get("xl/sharedStrings.xml")) : List.of();
        String sheet = firstSheetPath(parts);
        byte[] xml = parts.get(sheet);
        if (xml == null) throw new ParseException("XLSX_INVALID");
        return sheet(xml, shared);
    }

    private static Map<String, byte[]> unzip(byte[] data) {
        Map<String, byte[]> out = new HashMap<>();
        long total = 0;
        try (ZipInputStream z = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            int entries = 0;
            while ((e = z.getNextEntry()) != null) {
                if (++entries > 500) throw new ParseException("XLSX_INVALID");
                String name = e.getName();
                if (e.isDirectory() || !(name.startsWith("xl/") && name.endsWith(".xml") || name.endsWith(".rels"))) continue;
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r;
                while ((r = z.read(buf)) > 0) {
                    total += r;
                    if (total > MAX_UNZIPPED_BYTES) throw new ParseException("FILE_TOO_LARGE");
                    bo.write(buf, 0, r);
                }
                out.put(name, bo.toByteArray());
            }
        } catch (IOException ex) {
            throw new ParseException("XLSX_INVALID");
        }
        return out;
    }

    private static XMLStreamReader xml(byte[] b) throws XMLStreamException {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        return f.createXMLStreamReader(new ByteArrayInputStream(b));
    }

    private static String firstSheetPath(Map<String, byte[]> parts) {
        // workbook.xml dagi birinchi <sheet r:id> → workbook.xml.rels dagi Target
        try {
            byte[] wb = parts.get("xl/workbook.xml");
            byte[] rels = parts.get("xl/_rels/workbook.xml.rels");
            if (wb != null && rels != null) {
                String rid = null;
                XMLStreamReader r = xml(wb);
                while (r.hasNext() && rid == null) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(r.getLocalName())) {
                        for (int i = 0; i < r.getAttributeCount(); i++) {
                            if ("id".equals(r.getAttributeLocalName(i))) rid = r.getAttributeValue(i);
                        }
                    }
                }
                if (rid != null) {
                    XMLStreamReader rr = xml(rels);
                    while (rr.hasNext()) {
                        if (rr.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(rr.getLocalName())
                                && rid.equals(rr.getAttributeValue(null, "Id"))) {
                            String t = rr.getAttributeValue(null, "Target");
                            if (t != null) {
                                t = t.startsWith("/") ? t.substring(1) : "xl/" + t;
                                if (!t.contains("..")) return t;
                            }
                        }
                    }
                }
            }
        } catch (XMLStreamException ignored) {
            // standart yo'lga tushamiz
        }
        return "xl/worksheets/sheet1.xml";
    }

    private static List<String> sharedStrings(byte[] b) {
        List<String> out = new ArrayList<>();
        try {
            XMLStreamReader r = xml(b);
            StringBuilder cur = null;
            boolean inT = false;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    if ("si".equals(r.getLocalName())) cur = new StringBuilder();
                    else if ("t".equals(r.getLocalName())) inT = true;
                } else if (ev == XMLStreamConstants.CHARACTERS && inT && cur != null) {
                    cur.append(r.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if ("t".equals(r.getLocalName())) inT = false;
                    else if ("si".equals(r.getLocalName()) && cur != null) {
                        out.add(cur.toString());
                        cur = null;
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new ParseException("XLSX_INVALID");
        }
        return out;
    }

    private static List<List<String>> sheet(byte[] b, List<String> shared) {
        List<List<String>> out = new ArrayList<>();
        try {
            XMLStreamReader r = xml(b);
            List<String> row = null;
            String type = null;
            int col = 0;
            StringBuilder val = null;
            while (r.hasNext()) {
                int ev = r.next();
                if (ev == XMLStreamConstants.START_ELEMENT) {
                    switch (r.getLocalName()) {
                        case "row" -> row = new ArrayList<>();
                        case "c" -> {
                            type = r.getAttributeValue(null, "t");
                            String ref = r.getAttributeValue(null, "r");
                            col = ref == null ? (row == null ? 0 : row.size()) : colIndex(ref);
                            val = new StringBuilder();
                        }
                        case "v", "t" -> { if (val == null) val = new StringBuilder(); }
                        default -> { }
                    }
                } else if (ev == XMLStreamConstants.CHARACTERS && val != null) {
                    val.append(r.getText());
                } else if (ev == XMLStreamConstants.END_ELEMENT) {
                    if ("c".equals(r.getLocalName()) && row != null && val != null) {
                        String v = val.toString();
                        if ("s".equals(type)) {
                            try {
                                int i = Integer.parseInt(v.strip());
                                v = i >= 0 && i < shared.size() ? shared.get(i) : "";
                            } catch (NumberFormatException e) {
                                v = "";
                            }
                        }
                        if (col > 200) col = 200;
                        while (row.size() < col) row.add("");
                        row.add(v);
                        val = null;
                    } else if ("row".equals(r.getLocalName()) && row != null) {
                        out.add(row);
                        row = null;
                        if (out.size() > MAX_ROWS + 20) throw new ParseException("TOO_MANY_ROWS");
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new ParseException("XLSX_INVALID");
        }
        return out;
    }

    private static int colIndex(String ref) {
        int n = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (c < 'A' || c > 'Z') break;
            n = n * 26 + (c - 'A' + 1);
        }
        return Math.max(0, n - 1);
    }

}
