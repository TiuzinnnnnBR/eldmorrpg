package com.wendrew.eldmor;

import android.app.Activity;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Versão da Google Play: os diamantes são comprados pelo pagamento da Google Play.
// O jogo chama window.EldPlay.*, e as respostas voltam para o jogo em window.__gp*.
// Quem entrega os diamantes é o servidor, depois de confirmar a compra direto com o Google.
public class Billing {
    private static Activity act;
    private static WebView web;
    private static BillingClient client;
    private static boolean ready = false;
    private static final Map<String, ProductDetails> details = new HashMap<>();

    public static void attach(Activity a, WebView w) {
        act = a; web = w;
        w.addJavascriptInterface(new Bridge(), "EldPlay");
        client = BillingClient.newBuilder(a)
                .setListener((r, list) -> {
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) { for (Purchase p : list) deliver(p); }
                    else if (r.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED) js("__gpCancel()");
                    else js("__gpError(" + JSONObject.quote(msg(r)) + ")");
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
        connect();
    }

    public static void resume() { if (ready) restore(); }

    private static void connect() {
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                ready = r.getResponseCode() == BillingClient.BillingResponseCode.OK;
                js("__gpReady(" + ready + ")");
                if (ready) restore();
            }
            @Override public void onBillingServiceDisconnected() { ready = false; }
        });
    }

    private static String msg(BillingResult r) {
        switch (r.getResponseCode()) {
            case BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED: return "Você tem uma compra pendente desse pacote. Abra o jogo de novo para receber.";
            case BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE:
            case BillingClient.BillingResponseCode.SERVICE_DISCONNECTED: return "Sem conexão com a Google Play. Tente de novo.";
            case BillingClient.BillingResponseCode.BILLING_UNAVAILABLE: return "A Google Play não está disponível neste aparelho.";
            default: return "Não foi possível concluir a compra (" + r.getResponseCode() + ").";
        }
    }

    private static void deliver(Purchase p) {
        try {
            JSONObject o = new JSONObject();
            o.put("token", p.getPurchaseToken());
            o.put("product", p.getProducts().isEmpty() ? "" : p.getProducts().get(0));
            o.put("order", p.getOrderId() == null ? "" : p.getOrderId());
            o.put("state", p.getPurchaseState() == Purchase.PurchaseState.PURCHASED ? "ok" : "pending");
            js("__gpPurchase(" + o + ")");
        } catch (Exception e) { }
    }

    private static void restore() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(), (r, list) -> {
            if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null) for (Purchase p : list) if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) deliver(p);
        });
    }

    private static void js(String code) {
        if (act == null || web == null) return;
        act.runOnUiThread(() -> web.evaluateJavascript("try{" + code + "}catch(e){}", null));
    }

    static class Bridge {
        @JavascriptInterface public boolean ready() { return ready; }

        @JavascriptInterface public void prices(String csv) {
            if (!ready) { js("__gpPrices({})"); return; }
            List<QueryProductDetailsParams.Product> ps = new ArrayList<>();
            for (String id : csv.split(",")) if (!id.trim().isEmpty()) ps.add(QueryProductDetailsParams.Product.newBuilder().setProductId(id.trim()).setProductType(BillingClient.ProductType.INAPP).build());
            if (ps.isEmpty()) return;
            client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(ps).build(), (r, res) -> {
                JSONObject out = new JSONObject();
                try {
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && res != null) {
                        for (ProductDetails d : res.getProductDetailsList()) {
                            details.put(d.getProductId(), d);
                            ProductDetails.OneTimePurchaseOfferDetails of = d.getOneTimePurchaseOfferDetails();
                            out.put(d.getProductId(), of != null ? of.getFormattedPrice() : "");
                        }
                    }
                } catch (Exception e) { }
                js("__gpPrices(" + out + ")");
            });
        }

        @JavascriptInterface public void buy(String productId, String account) {
            act.runOnUiThread(() -> {
                ProductDetails d = details.get(productId);
                if (!ready || d == null) { js("__gpError(" + JSONObject.quote("A loja da Google Play ainda está carregando. Tente de novo em alguns segundos.") + ")"); if (!ready) connect(); return; }
                List<BillingFlowParams.ProductDetailsParams> l = new ArrayList<>();
                l.add(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build());
                BillingFlowParams.Builder b = BillingFlowParams.newBuilder().setProductDetailsParamsList(l);
                if (account != null && !account.isEmpty()) b.setObfuscatedAccountId(account);
                BillingResult r = client.launchBillingFlow(act, b.build());
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK && r.getResponseCode() != BillingClient.BillingResponseCode.USER_CANCELED) js("__gpError(" + JSONObject.quote(msg(r)) + ")");
            });
        }

        @JavascriptInterface public void restore() { if (ready) Billing.restore(); }
    }
}
