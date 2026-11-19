package com.wl.orders;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Reads the QR codes made by the QR Maker (qr/api.php). Pure Java, no Android classes.
 *
 * A QR holds one link:   https://wosux.com/qr/p/WS01-11-4?d=TOKEN
 *   id    = last part of the path   ->  SKU - number - billId
 *   TOKEN = base64url( IV[12] + ciphertext + tag[16] ),  AES-256-GCM, associated data = the id
 *   plain = 1 | name | buyPrice | minPrice | maxPrice | YYYY-MM-DD | categoryId | storeId
 */
public final class QrCrypto {

    /** The message is written for the person holding the phone, so it can be shown as it is. */
    public static final class QrException extends Exception {
        public QrException(String message) { super(message); }
    }

    private static final String MSG_NOT_OURS = "This is not a Wosux product QR.";
    private static final String MSG_NO_DATA  = "This QR has no product data.";
    private static final String MSG_BAD      = "Can't open this QR. It may be damaged, edited, or made with a different key.";
    private static final String MSG_FORMAT   = "This QR uses a newer format. Please update the app.";
    private static final String MSG_KEY      = "The secret key inside the app is not set correctly.";

    // Same rule as api.php / p.php:  SKU (letters, digits, _) - number - bill id
    private static final Pattern ID = Pattern.compile("^([A-Z0-9_]{1,20})-(\\d{1,5})-(\\d{1,10})$");

    private QrCrypto() {}

    /**
     * @param scanned the text the camera read from the QR
     * @param keyHex  secret_key_hex from the QR Maker's config.php (64 hex characters)
     */
    public static QrItem parse(String scanned, String keyHex) throws QrException {
        if (scanned == null) throw new QrException(MSG_NOT_OURS);

        String s = scanned.trim();
        int hash = s.indexOf('#');
        if (hash >= 0) s = s.substring(0, hash);
        int q = s.indexOf('?');
        String path = q >= 0 ? s.substring(0, q) : s;
        String query = q >= 0 ? s.substring(q + 1) : "";

        // Normally the id is the last part of the path. p.php also accepts ?c=ID, so do the same.
        String id = queryParam(query, "c");
        if (id == null || id.isEmpty()) {
            while (path.endsWith("/")) path = path.substring(0, path.length() - 1);
            id = path.substring(path.lastIndexOf('/') + 1);
        }
        id = id.trim().toUpperCase(Locale.ROOT);   // the maker only writes upper case, like p.php we are forgiving
        Matcher m = ID.matcher(id);
        if (!m.matches()) throw new QrException(MSG_NOT_OURS);

        String token = queryParam(query, "d");
        if (token == null || token.isEmpty()) throw new QrException(MSG_NO_DATA);

        byte[] key = hexToBytes(keyHex);
        if (key == null || key.length != 32) throw new QrException(MSG_KEY);

        byte[] raw = b64urlDecode(token);
        if (raw == null || raw.length < 12 + 16 + 1) throw new QrException(MSG_BAD);

        String plain;
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, raw, 0, 12));
            c.updateAAD(id.getBytes(StandardCharsets.UTF_8));
            plain = new String(c.doFinal(raw, 12, raw.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) {
            // wrong key, edited link, or a link from somebody else's QR maker
            throw new QrException(MSG_BAD);
        }

        String[] f = plain.split("\\|", -1);
        if (f.length < 8 || !"1".equals(f[0])) throw new QrException(MSG_FORMAT);

        return new QrItem(id, m.group(1), m.group(2), m.group(3),
                f[1], f[2], f[3], f[4], f[5], f[6], f[7]);
    }

    // ---- helpers ------------------------------------------------------------------------------

    private static String queryParam(String query, String name) {
        if (query == null || query.isEmpty()) return null;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq > 0 && part.substring(0, eq).equals(name)) return part.substring(eq + 1);
        }
        return null;
    }

    private static byte[] hexToBytes(String hex) {
        if (hex == null) return null;
        String h = hex.trim();
        if (h.length() % 2 != 0) return null;
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(h.charAt(2 * i), 16);
            int lo = Character.digit(h.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) return null;
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** base64url without padding (also tolerates '=' padding and the normal + / alphabet). No android.util.Base64, so it works on API 24+ and in plain JVM tests. */
    private static byte[] b64urlDecode(String s) {
        int len = s.length();
        while (len > 0 && s.charAt(len - 1) == '=') len--;
        if (len % 4 == 1) return null;
        byte[] out = new byte[len * 6 / 8];
        int buf = 0, bits = 0, o = 0;
        for (int i = 0; i < len; i++) {
            int v = b64Value(s.charAt(i));
            if (v < 0) return null;
            buf = (buf << 6) | v;
            bits += 6;
            if (bits >= 8) {
                bits -= 8;
                out[o++] = (byte) ((buf >> bits) & 0xFF);
                buf &= (1 << bits) - 1;
            }
        }
        return out;
    }

    private static int b64Value(char c) {
        if (c >= 'A' && c <= 'Z') return c - 'A';
        if (c >= 'a' && c <= 'z') return c - 'a' + 26;
        if (c >= '0' && c <= '9') return c - '0' + 52;
        if (c == '-' || c == '+') return 62;
        if (c == '_' || c == '/') return 63;
        return -1;
    }
}
