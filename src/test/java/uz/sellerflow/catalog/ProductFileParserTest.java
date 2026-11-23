package uz.sellerflow.catalog;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductFileParserTest {

    @Test
    void csv_russianHeaders_semicolon_cp1251_costBeforePrice() {
        String csv = "Артикул;Наименование товара;Закупочная цена;Цена продажи\r\n"
                + "A-1;Футболка белая;45 000;120 000\r\n"
                + "A-2;\"Кружка \"\"Big\"\"\";12000,50;\r\n\r\n";
        var p = ProductFileParser.parse("tovar.csv", csv.getBytes(Charset.forName("windows-1251")));
        assertThat(p.rows()).hasSize(2);
        assertThat(p.columns()).containsEntry("COST", "Закупочная цена").containsEntry("PRICE", "Цена продажи")
                .containsEntry("SKU", "Артикул").containsEntry("NAME", "Наименование товара");
        var r = p.rows().get(0);
        assertThat(r.name()).isEqualTo("Футболка белая");
        assertThat(r.unitCost()).isEqualByComparingTo("45000");
        assertThat(r.price()).isEqualByComparingTo("120000");
        assertThat(p.rows().get(1).name()).isEqualTo("Кружка \"Big\"");
        assertThat(p.rows().get(1).unitCost()).isEqualByComparingTo("12000.50");
        assertThat(p.rows().get(1).price()).isNull();
    }

    @Test
    void csv_uzbekHeaders_comma_utf8Bom() {
        String csv = "﻿Mahsulot nomi,Tannarx,Sotish narxi\nChoynak,30000,59000\n";
        var p = ProductFileParser.parse("a.csv", csv.getBytes(StandardCharsets.UTF_8));
        assertThat(p.rows()).singleElement().satisfies(r -> {
            assertThat(r.name()).isEqualTo("Choynak");
            assertThat(r.unitCost()).isEqualByComparingTo("30000");
            assertThat(r.price()).isEqualByComparingTo("59000");
        });
    }

    @Test
    void xlsx_sharedStringsAndNumbers() throws Exception {
        byte[] x = xlsx("""
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>
                <row r="1"><c r="A1" t="s"><v>0</v></c><c r="C1" t="s"><v>1</v></c></row>
                <row r="2"><c r="A2" t="s"><v>2</v></c><c r="C2"><v>15000.5</v></c></row>
                <row r="3"><c r="A3" t="inlineStr"><is><t>Lampa</t></is></c><c r="C3"><v>7000</v></c></row>
                </sheetData></worksheet>""",
                "<sst><si><t>Nomi</t></si><si><t>Tannarx</t></si><si><t>Stol</t></si></sst>");
        var p = ProductFileParser.parse("f.xlsx", x);
        assertThat(p.rows()).hasSize(2);
        assertThat(p.rows().get(0).name()).isEqualTo("Stol");
        assertThat(p.rows().get(0).unitCost()).isEqualByComparingTo("15000.5");
        assertThat(p.rows().get(1).name()).isEqualTo("Lampa");
    }

    @Test
    void errors() {
        assertThatThrownBy(() -> ProductFileParser.parse("a.csv", "foo;bar\n1;2\n".getBytes())).hasMessage("NO_HEADER");
        assertThatThrownBy(() -> ProductFileParser.parse("a.xls", new byte[]{1, 2, 3})).hasMessage("XLS_UNSUPPORTED");
        assertThatThrownBy(() -> ProductFileParser.parse("a.xlsx", "PKgarbage".getBytes())).hasMessageStartingWith("XLSX");
        assertThatThrownBy(() -> ProductFileParser.parse("a.csv", new byte[0])).hasMessage("EMPTY");
    }

    @Test
    void xlsx_xxePayloadIsNotResolved() throws Exception {
        byte[] x = xlsx("""
                <?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]>
                <worksheet><sheetData><row><c t="inlineStr"><is><t>Nomi</t></is></c><c t="inlineStr"><is><t>Narx</t></is></c></row>
                <row><c t="inlineStr"><is><t>&e;</t></is></c><c><v>5</v></c></row></sheetData></worksheet>""", null);
        assertThatThrownBy(() -> ProductFileParser.parse("f.xlsx", x)).isInstanceOf(ProductFileParser.ParseException.class);
    }

    static byte[] xlsx(String sheet, String shared) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bo)) {
            z.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            z.write(sheet.getBytes(StandardCharsets.UTF_8));
            if (shared != null) {
                z.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
                z.write(shared.getBytes(StandardCharsets.UTF_8));
            }
        }
        return bo.toByteArray();
    }
}
