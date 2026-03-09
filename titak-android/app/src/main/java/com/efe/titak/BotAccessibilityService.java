package com.efe.titak;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * BotAccessibilityService
 *
 * TikTok uygulamasını izler, ekranda sandık / treasure chest
 * elementleri belirdiğinde otomatik olarak tıklar.
 *
 * İki tıklama yöntemi:
 *   1. Accessibility tree node tıklama (performAction)
 *   2. Gesture-based koordinat tıklama (dispatchGesture) - fallback
 */
public class BotAccessibilityService extends AccessibilityService {

    // TikTok paket adları (farklı ülkeler için farklı paketler)
    private static final String[] TIKTOK_PACKAGES = {
        "com.zhiliaoapp.musically",   // TikTok (Global)
        "com.ss.android.ugc.trill",   // TikTok (bazı bölgeler)
        "com.ss.android.ugc.aweme",   // Douyin (Çin)
        "com.zhiliaoapp.musically.go" // TikTok Lite
    };

    // Sandık/ödül içeriği için arama kalıpları (küçük harf)
    private static final String[] TREASURE_KEYWORDS = {
        "treasure", "chest", "lucky", "gift box", "lucky box",
        "rose", "open", "collect", "claim", "reward",
        "haziney", "sandık", "ödül", "topla", "aç",
        "free", "bonus", "prize"
    };

    // Claim butonu kelimeleri
    private static final String[] CLAIM_KEYWORDS = {
        "open", "collect", "claim", "receive", "grab",
        "aç", "topla", "al", "ödülü al", "hemen al",
        "tap to open", "tap to collect"
    };

    private long lastClickTime = 0;
    private static final long CLICK_COOLDOWN_MS = 3000; // 3 saniyede bir en fazla tıkla

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        BotEngine.accessibilityService = this;
        BotEngine.get().log("✅ Erişilebilirlik servisi bağlandı");
    }

    @Override
    public void onInterrupt() {
        BotEngine.accessibilityService = null;
        BotEngine.get().log("⚠️ Erişilebilirlik servisi kesildi");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        BotEngine.accessibilityService = null;
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!BotEngine.get().isActive()) return;

        // Sadece TikTok'ta çalış
        CharSequence pkg = event.getPackageName();
        if (pkg == null || !isTikTokPackage(pkg.toString())) return;

        // Pencere değişimi veya içerik değişimini dinle
        int type = event.getEventType();
        if (type == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            type == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            type == AccessibilityEvent.TYPE_VIEW_SCROLLED) {

            // Cooldown kontrolü
            long now = System.currentTimeMillis();
            if (now - lastClickTime < CLICK_COOLDOWN_MS) return;

            scanAndClick();
        }
    }

    /**
     * Ekranı tara, sandık/claim butonunu bul ve tıkla
     */
    private void scanAndClick() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        BotEngine.get().updateStatus("📺 TikTok taranıyor...");

        // Önce claim butonlarını ara (modal açıksa)
        AccessibilityNodeInfo claimNode = findClaimButton(root);
        if (claimNode != null) {
            BotEngine.get().log("🎯 Claim butonu bulundu, tıklanıyor...");
            performNodeClick(claimNode, "claim-button");
            root.recycle();
            return;
        }

        // Treasure chest ara
        AccessibilityNodeInfo treasureNode = findTreasureChest(root);
        if (treasureNode != null) {
            BotEngine.get().log("🎁 Treasure chest bulundu, tıklanıyor...");
            performNodeClick(treasureNode, "treasure-chest");
        }

        root.recycle();
    }

    /**
     * Treasure chest node'unu bul
     */
    private AccessibilityNodeInfo findTreasureChest(AccessibilityNodeInfo root) {
        return findNodeByKeywords(root, TREASURE_KEYWORDS, true);
    }

    /**
     * Claim butonu node'unu bul (modal içinde)
     */
    private AccessibilityNodeInfo findClaimButton(AccessibilityNodeInfo root) {
        // Button veya tıklanabilir node'lar içinde ara
        return findNodeByKeywords(root, CLAIM_KEYWORDS, false);
    }

    /**
     * Verilen anahtar kelimelerle node ağacını tara
     * @param onlySmall: true = sadece küçük boyutlu elementler (sandık boyutu)
     */
    private AccessibilityNodeInfo findNodeByKeywords(
            AccessibilityNodeInfo node,
            String[] keywords,
            boolean onlySmall) {

        if (node == null) return null;

        // Bu node'un metinlerini kontrol et
        String contentDesc = node.getContentDescription() != null
            ? node.getContentDescription().toString().toLowerCase() : "";
        String text = node.getText() != null
            ? node.getText().toString().toLowerCase() : "";
        String viewId = node.getViewIdResourceName() != null
            ? node.getViewIdResourceName().toLowerCase() : "";
        String combined = contentDesc + " " + text + " " + viewId;

        for (String kw : keywords) {
            if (combined.contains(kw)) {
                // Tıklanabilir mi?
                if (node.isClickable() && node.isVisibleToUser()) {
                    return node;
                }
                // Parent'ı tıklanabilir mi?
                AccessibilityNodeInfo parent = node.getParent();
                if (parent != null && parent.isClickable() && parent.isVisibleToUser()) {
                    return parent;
                }
            }
        }

        // Alt elementleri tara
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            AccessibilityNodeInfo result = findNodeByKeywords(child, keywords, onlySmall);
            if (result != null) return result;
            if (child != null) child.recycle();
        }

        return null;
    }

    /**
     * Node'a tıkla
     */
    private void performNodeClick(AccessibilityNodeInfo node, String source) {
        try {
            boolean clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK);
            if (clicked) {
                lastClickTime = System.currentTimeMillis();
                BotEngine.get().onCoinCollected(source);
            } else {
                // Node tıklanamadı — koordinat bazlı gesture dene
                android.graphics.Rect bounds = new android.graphics.Rect();
                node.getBoundsInScreen(bounds);
                float cx = bounds.exactCenterX();
                float cy = bounds.exactCenterY();
                performTapGesture(cx, cy, source);
            }
        } catch (Exception e) {
            BotEngine.get().log("❌ Tıklama hatası: " + e.getMessage());
        }
    }

    /**
     * Koordinat bazlı dokunma simülasyonu (API 24+)
     */
    public void performTapGesture(float x, float y, String source) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return;

        Path path = new Path();
        path.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
            new GestureDescription.StrokeDescription(path, 0, 100);

        GestureDescription gesture = new GestureDescription.Builder()
            .addStroke(stroke)
            .build();

        dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                lastClickTime = System.currentTimeMillis();
                BotEngine.get().onCoinCollected(source + "-gesture");
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                BotEngine.get().log("⚠️ Gesture iptal edildi");
            }
        }, null);
    }

    /**
     * TikTok paketi mi?
     */
    private boolean isTikTokPackage(String pkg) {
        for (String p : TIKTOK_PACKAGES) {
            if (pkg.equals(p)) return true;
        }
        return false;
    }
}
