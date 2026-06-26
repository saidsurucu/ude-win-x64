package com.udewin.fileassoc;

/**
 * .udf dosyasina cift-tiklayinca uygulamanin acilmasini saglayan arguman normalizasyonu.
 *
 * KOK NEDEN: WPAppManager argumanlarda "getNewWPInstance"/"null"/null kontrol-jetonu
 * GORMEZSE ve en az bir arguman varsa hicbir sey acmadan return eder. jpackage
 * --file-associations ile cift-tikta launcher "app.exe \"C:/yol/dosya.udf\"" cagirir;
 * jpackage --arguments (getNewWPInstance EDITOR_TYPE_DOCUMENT) yalnizca VARSAYILANDIR
 * ve CLI argumani verilince devre disi kalir -> args=["...udf"] -> erken return.
 *
 * Bu yardimci, kontrol-jetonu olmayan (yani yalniz dosya yolu/yollari iceren) cagrilarin
 * basina "getNewWPInstance" ekler. EDITOR_TYPE_DOCUMENT eklenmez (default mod=2 zaten dogru).
 * Idempotent: zaten jeton iceren normal baslatmalari aynen birakir.
 */
public final class ArgFix {
    private ArgFix() {}

    public static String[] normalize(String[] args) {
        if (args == null || args.length == 0) return args;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null || "getNewWPInstance".equals(a) || "null".equals(a)) {
                return args; // normal baslatma (kontrol-jetonu var) -> dokunma
            }
        }
        // Kontrol-jetonu yok -> dosya-iliskilendirme cift-tiki (yalniz yol). Basa jeton ekle.
        String[] out = new String[args.length + 1];
        out[0] = "getNewWPInstance";
        System.arraycopy(args, 0, out, 1, args.length);
        return out;
    }
}
