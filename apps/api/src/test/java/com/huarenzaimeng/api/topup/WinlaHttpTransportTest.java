package com.huarenzaimeng.api.topup;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WinlaHttpTransportTest {
    private static final char[] KEY="0123456789abcdef".toCharArray();

    @Test void signatureUsesAsciiSortedNonEmptyFieldsAndAppendsApiKey(){
        Map<String,String> fields=new LinkedHashMap<>();fields.put("user_order_no","ORDER1ABCDEFGHIJKLMNOP");fields.put("uid","test");fields.put("recharge_no","01712345678");fields.put("product_code","123");fields.put("price","99.54");fields.put("empty","");
        assertThat(WinlaHttpTransport.sign(fields,KEY)).isEqualTo("56948dc591328339660a91e983919efc");
    }

    @Test void submitUsesCnyYuanTrustedCostAndLocalPhoneWithoutLeakingKey(){
        FakeHttp http=new FakeHttp("{\"result\":{\"order_no\":\"P100\",\"user_order_no\":\"ORDER1\",\"price\":99.54},\"code\":10000,\"message\":\"ok\"}");WinlaHttpTransport transport=transport(http);
        var result=transport.submit(new TopupProviderPort.Command("ORDER1","REQ1","123","8801712345678",9954,"CNY","a".repeat(64)));
        assertThat(result.providerAmountRaw()).isEqualTo("99.54");assertThat(result.amountUnit()).isEqualTo(WinlaTopupAdapter.AmountUnit.MAJOR);assertThat(result.providerCurrencyRaw()).isEqualTo("CNY");
        assertThat(http.url).isEqualTo(WinlaHttpTransport.SUBMIT_URL);assertThat(http.body).contains("recharge_no=01712345678","price=99.54","product_code=123","user_order_no=ORDER1","sign=").doesNotContain(new String(KEY));
    }

    @Test void queryMapsOnlyDocumentedTerminalStatesAndKeepsPriceInYuan(){
        FakeHttp http=new FakeHttp("{\"result\":{\"order_no\":\"P100\",\"user_order_no\":\"ORDER1\",\"price\":99.54,\"state\":2},\"code\":10000}");var result=transport(http).query("P100","ORDER1");
        assertThat(result.stateRaw()).isEqualTo("DELIVERED");assertThat(result.providerAmountRaw()).isEqualTo("99.54");assertThat(result.amountUnit()).isEqualTo(WinlaTopupAdapter.AmountUnit.MAJOR);
    }

    @Test void callbackRequiresProviderSignatureAndTreatsOrderMoneyAsCnyCents(){
        Map<String,String> fields=new LinkedHashMap<>();fields.put("order_no","P100");fields.put("user_order_no","ORDER1");fields.put("status","200");fields.put("order_money","9954");String sign=WinlaHttpTransport.sign(fields,KEY);
        String body="order_no=P100&user_order_no=ORDER1&status=200&order_money=9954&sign="+sign;var callback=transport(new FakeHttp("{}")).verifyCallback(new TopupProviderPort.CallbackEnvelope("ignored","","","",body));
        assertThat(callback.stateRaw()).isEqualTo("DELIVERED");assertThat(callback.providerAmountRaw()).isEqualTo("9954");assertThat(callback.amountUnit()).isEqualTo(WinlaTopupAdapter.AmountUnit.MINOR);assertThat(callback.providerSku()).isNull();assertThat(callback.recipientRaw()).isNull();
        assertThatThrownBy(()->transport(new FakeHttp("{}")).verifyCallback(new TopupProviderPort.CallbackEnvelope("ignored","","","",body.replace(sign,"0".repeat(32))))).hasMessage("WINLA_CALLBACK_SIGNATURE_INVALID");
    }

    @Test void malformedResponseForbiddenSkuAndWrongPhoneFailClosedWithoutRawDetails(){
        assertThatThrownBy(()->transport(new FakeHttp("not-json")).submit(new TopupProviderPort.Command("ORDER1","REQ1","123","8801712345678",9954,"CNY","a".repeat(64)))).hasMessage("WINLA_RESPONSE_INVALID");
        assertThatThrownBy(()->transport(new FakeHttp("{}")).submit(new TopupProviderPort.Command("ORDER1","REQ1","SKU-X","8801712345678",9954,"CNY","a".repeat(64)))).hasMessage("WINLA_PRODUCT_CODE_INVALID");
        assertThatThrownBy(()->WinlaHttpTransport.providerPhone("880181234567")).hasMessage("RECIPIENT_INVALID");
    }

    private static WinlaHttpTransport transport(FakeHttp http){return new WinlaHttpTransport("test",KEY,http,new ObjectMapper());}
    static final class FakeHttp implements WinlaHttpTransport.HttpExchange{final byte[] response;String url,body;FakeHttp(String response){this.response=response.getBytes(StandardCharsets.UTF_8);}@Override public byte[] post(String url,String body){this.url=url;this.body=body;return response;}}
}
