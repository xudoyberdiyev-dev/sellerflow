package uz.sellerflow.marketplace;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UzumServiceMappingTest {

    @Test
    void productCard_toRowPerSku_skipsArchived_andZeroPurchasePrice() throws Exception {
        var p = new ObjectMapper().readTree("""
                {"productId": 77, "title": "Futbolka", "status": {"value": "IN_STOCK"},
                 "commissionDto": {"minCommission": 10, "maxCommission": 15},
                 "skuList": [
                   {"skuId": 1, "skuTitle": "Oq / M", "barcode": "478001", "sellerItemCode": "FT-W-M", "price": 120000,
                    "purchasePrice": 0, "commission": 12, "quantityActive": 5, "quantityFbs": 2, "quantitySold": 40, "quantityReturned": 3},
                   {"skuId": 2, "skuTitle": "Qora / L", "price": 125000, "purchasePrice": 50000, "archived": false},
                   {"skuId": 3, "skuTitle": "Eski", "archived": true}
                 ]}""");
        var rows = UzumService.toRows(9, p);
        assertThat(rows).hasSize(2);
        var a = rows.get(0);
        assertThat(a.externalSkuId()).isEqualTo(1L);
        assertThat(a.externalProductId()).isEqualTo(77L);
        assertThat(a.shopId()).isEqualTo(9L);
        assertThat(a.name()).isEqualTo("Futbolka — Oq / M");
        assertThat(a.sku()).isEqualTo("FT-W-M");
        assertThat(a.unitCost()).isNull();
        assertThat(a.price()).isEqualByComparingTo("120000");
        assertThat(a.commissionPct()).isEqualByComparingTo("12");
        assertThat(a.stock()).isEqualTo(7);
        assertThat(a.sold()).isEqualTo(40);
        assertThat(rows.get(1).unitCost()).isEqualByComparingTo("50000");
        assertThat(rows.get(1).commissionPct()).isEqualByComparingTo("15");
    }

    @Test
    void archivedProduct_isSkipped() throws Exception {
        var p = new ObjectMapper().readTree("{\"productId\":1,\"title\":\"X\",\"status\":{\"value\":\"ARCHIVED\"},\"skuList\":[{\"skuId\":5}]}");
        assertThat(UzumService.toRows(1, p)).isEmpty();
    }
}
