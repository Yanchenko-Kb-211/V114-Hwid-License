package com.example.chestdropper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import java.security.SecureRandom;

public final class LicenseProofClient {
    private static final String K0 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAx6r9wCTAAqzov3E1DOeQY9iyIaJri9xkaP+L3BLEmySDaeii";
    private static final String K1 = "CePHWoO292UNcBhdemCKHqiq7fCYBV/fZYhJGk9rFVgHQiB/zSWu1/A6yvp1ynxFVwtOPKByJctxr3Vq2hj3B0ciUuQEYA37K38jv5wwapJcByXlNw7dG2uj5rIvMDxL32xDmuPPtmeRkqBAKu8E5l1nmPYfATYNYjmGfyGpdVvbJVAWa0F/1K4h1Ji/zkSk9044yZRDGQNmf48cH50GXISquT0e2IKSk+1eziLM3EuQklQqee6wBpxCzeIx+hjUDQUBegqh9ERJbtN+0A772n9dwcyZoKbB42E1LwIDAQAB";

    private static final SecureRandom RNG = new SecureRandom();
    private static final Object LOCK = new Object();
    private static volatile boolean valid;
    private static volatile long expiresAt;
    private static volatile String lastUrl = "";
    private static volatile String lastHwid = "";
    private static volatile boolean verifierStarted;

    private LicenseProofClient() {}

    public static boolean check(String url, String hwid) throws Exception {
        String nonce = randomNonce();
        String body = "{"hwid":"" + escapeJson(hwid) + "","nonce":"" + nonce + ""}";

        HttpURLConnection c = (HttpURLConnection) URI.create(url).toURL().openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(7000);
        c.setReadTimeout(7000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        c.setRequestProperty("Cache-Control", "no-store");
        c.setRequestProperty("User-Agent", "AutoSnake-License/2");

        byte[] request = body.getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(request.length);
        try (var out = c.getOutputStream()) {
            out.write(request);
        }

        int code = c.getResponseCode();
        String response = readAll(code >= 200 && code < 400 ? c.getInputStream() : c.getErrorStream());
        if (code != 200) {
            invalidateIfKnown(hwid);
            return false;
        }

        boolean ok = "true".equalsIgnoreCase(readBool(response, "ok"));
        if (!ok) {
            invalidateIfKnown(hwid);
            return false;
        }

        long issued = readLong(response, "issued_at");
        long expires = readLong(response, "expires_at");
        String serverNonce = readString(response, "nonce");
        String name = readString(response, "license_name");
        String signatureB64 = readString(response, "signature");

        long now = System.currentTimeMillis() / 1000L;
        if (!nonce.equals(serverNonce) || issued <= 0 || expires <= issued || expires < now || expires > now + 90000L) {
            invalidateIfKnown(hwid);
            return false;
        }

        String message = "v1|" + hwid + "|" + nonce + "|" + issued + "|" + expires + "|" + name;
        if (!verify(message.getBytes(StandardCharsets.UTF_8), Base64.getDecoder().decode(signatureB64))) {
            invalidateIfKnown(hwid);
            return false;
        }

        synchronized (LOCK) {
            valid = true;
            expiresAt = expires;
            lastUrl = url;
            lastHwid = hwid;
            startVerifierThread();
        }
        return true;
    }

    public static boolean isValid() {
        if (!valid) return false;
        return System.currentTimeMillis() / 1000L < expiresAt;
    }

    private static void startVerifierThread() {
        if (verifierStarted) return;
        verifierStarted = true;
        Thread t = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(120_000L);
                    String u = lastUrl;
                    String h = lastHwid;
                    if (u.isEmpty() || h.isEmpty()) continue;
                    check(u, h);
                } catch (Throwable ignored) {
                }
            }
        }, "AutoSnake-License-Recheck");
        t.setDaemon(true);
        t.start();
    }

    private static void invalidateIfKnown(String hwid) {
        if (!lastHwid.isEmpty() && lastHwid.equals(hwid)) valid = false;
    }

    private static String randomNonce() {
        byte[] b = new byte[32];
        RNG.nextBytes(b);
        return bytesToHex(b);
    }

    private static boolean verify(byte[] message, byte[] sigBytes) throws Exception {
        PublicKey key = publicKey();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(key);
        verifier.update(message);
        return verifier.verify(sigBytes);
    }

    private static PublicKey publicKey() throws Exception {
        byte[] der = Base64.getDecoder().decode(K0 + K1);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = input.read(buf)) >= 0) out.write(buf, 0, n);
            return out.toString(StandardCharsets.UTF_8);
        }
    }

    private static String readString(String json, String key) {
        String needle = """ + key + """;
        int p = json.indexOf(needle);
        if (p < 0) throw new IllegalStateException("Missing field");
        p = json.indexOf(':', p + needle.length());
        if (p < 0) throw new IllegalStateException("Missing field");
        p++;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        if (p >= json.length() || json.charAt(p) != '"') throw new IllegalStateException("Invalid field");
        p++;
        StringBuilder s = new StringBuilder();
        boolean esc = false;
        for (; p < json.length(); p++) {
            char ch = json.charAt(p);
            if (esc) {
                if (ch == 'n') s.append('\n');
                else if (ch == 'r') s.append('\r');
                else if (ch == 't') s.append('\t');
                else s.append(ch);
                esc = false;
            } else if (ch == '\\') esc = true;
            else if (ch == '"') return s.toString();
            else s.append(ch);
        }
        throw new IllegalStateException("Unterminated field");
    }

    private static String readBool(String json, String key) {
        String needle = """ + key + """;
        int p = json.indexOf(needle);
        if (p < 0) return "false";
        p = json.indexOf(':', p + needle.length());
        if (p < 0) return "false";
        p++;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        int q = p;
        while (q < json.length() && Character.isLetter(json.charAt(q))) q++;
        return json.substring(p, q);
    }

    private static long readLong(String json, String key) {
        String needle = """ + key + """;
        int p = json.indexOf(needle);
        if (p < 0) throw new IllegalStateException("Missing field");
        p = json.indexOf(':', p + needle.length());
        if (p < 0) throw new IllegalStateException("Missing field");
        p++;
        while (p < json.length() && Character.isWhitespace(json.charAt(p))) p++;
        int q = p;
        while (q < json.length() && (json.charAt(q) == '-' || Character.isDigit(json.charAt(q)))) q++;
        return Long.parseLong(json.substring(p, q));
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace(""", "\\"");
    }

    private static String bytesToHex(byte[] data) {
        char[] chars = "0123456789abcdef".toCharArray();
        char[] out = new char[data.length * 2];
        for (int i = 0; i < data.length; i++) {
            int v = data[i] & 0xff;
            out[i * 2] = chars[v >>> 4];
            out[i * 2 + 1] = chars[v & 0x0f];
        }
        return new String(out).toLowerCase(Locale.ROOT);
    }
}
