package com.chatapp.util;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Base64;
import java.util.Arrays;
import java.security.MessageDigest;

public class PasswordUtils {
    private static final int ITER = 200_000;
    private static final int KEYLEN = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    public static String hashPassword(char[] password) {
        try {
            byte[] salt = new byte[16];
            RANDOM.nextBytes(salt);
            KeySpec spec = new PBEKeySpec(password, salt, ITER, KEYLEN);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] dk = f.generateSecret(spec).getEncoded();
            String sSalt = Base64.getEncoder().encodeToString(salt);
            String sDk = Base64.getEncoder().encodeToString(dk);
            return sSalt + ":" + sDk;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            Arrays.fill(password, '\0');
        }
    }

    public static boolean verifyPassword(String stored, char[] attempt) {
        try {
            String[] parts = stored.split(":");
            if (parts.length != 2) return false;
            byte[] salt = Base64.getDecoder().decode(parts[0]);
            byte[] dkStored = Base64.getDecoder().decode(parts[1]);
            KeySpec spec = new PBEKeySpec(attempt, salt, ITER, KEYLEN);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] dk = f.generateSecret(spec).getEncoded();
            boolean ok = MessageDigest.isEqual(dkStored, dk);
            return ok;
        } catch (Exception e) {
            return false;
        } finally {
            Arrays.fill(attempt, '\0');
        }
    }
}
