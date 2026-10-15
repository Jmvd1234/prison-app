package com.example.myapplication;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProfilePasswordTest {
    @Test public void saltedHashesMatchOnlyTheExactPassword() throws Exception {
        String password = "  my ñ password ☃  ";
        String first = ProfilePassword.hash(password), second = ProfilePassword.hash(password);
        assertNotEquals(first, second);
        assertTrue(first.startsWith("v2$SHA256$"));
        assertFalse(ProfilePassword.needsUpdate(first));
        assertFalse(first.contains(password));
        assertTrue(ProfilePassword.matches(password, first));
        assertFalse(ProfilePassword.matches(password.trim(), first));
        assertFalse(ProfilePassword.matches("wrong", first));
    }

    @Test public void malformedHashesDoNotAuthenticateOrRequestUnboundedWork() throws Exception {
        assertFalse(ProfilePassword.matches("pw", ""));
        assertFalse(ProfilePassword.matches("pw", "v1$unknown$600000$00$00"));
        assertFalse(ProfilePassword.matches("pw", "v1$PBKDF2WithHmacSHA256$2147483647$00$00"));
        assertFalse(ProfilePassword.matches("pw", "v1$PBKDF2WithHmacSHA256$600000$zz$zz"));
        assertFalse(ProfilePassword.matches("pw", "v2$SHA256$00$00"));
        assertFalse(ProfilePassword.matches("pw", "v2$SHA512$00$00"));
        assertFalse(ProfilePassword.matches("pw", ProfilePassword.hash("pw").replace("v2$", "v3$")));
        String good = ProfilePassword.hash("pw");
        assertFalse(ProfilePassword.matches("pw", good.substring(0, good.length()-1)+"z"));
        assertFalse(ProfilePassword.matches(null, ProfilePassword.hash("pw")));
    }

    @Test public void oldPasswordsAreComparedWithoutTrimming() {
        assertTrue(ProfilePassword.matchesLegacy(" old ", " old "));
        assertFalse(ProfilePassword.matchesLegacy("old", " old "));
        assertFalse(ProfilePassword.matchesLegacy(null, null));
    }

    @Test public void existingPbkdf2HashesStillVerifyBeforeTheirOneTimeUpdate() throws Exception {
        // Independent fixture produced with Python hashlib.pbkdf2_hmac.
        String old = "v1$PBKDF2WithHmacSHA256$600000$000102030405060708090a0b0c0d0e0f$"
                + "a625b99416f6c9a479adebc00728b0e922b3197df417c3eef0671b067f78dc38";
        assertTrue(ProfilePassword.needsUpdate(old));
        assertTrue(ProfilePassword.matches("old PBKDF2 password", old));
        assertFalse(ProfilePassword.matches("wrong", old));
    }
}
