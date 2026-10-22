package com.coderzclub.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionSecretValidatorTest {
    @Test
    void rejectsBlankAndPlaceholderSecrets() {
        assertThrows(IllegalStateException.class, () -> ProductionSecretValidator.requireSecureJwt(""));
        assertThrows(IllegalStateException.class, () -> ProductionSecretValidator.requireSecureJwt("replace-me"));
        assertThrows(IllegalStateException.class, () -> ProductionSecretValidator.requireSecureJwt("secret"));
    }

    @Test
    void acceptsLongNonPlaceholderSecret() {
        String secret = "a".repeat(64);
        assertDoesNotThrow(() -> ProductionSecretValidator.requireSecureJwt(secret));
    }

    @Test
    void validatorIsNoOpWhenNotRequired() {
        ProductionSecretValidator validator = new ProductionSecretValidator(false, "secret");
        assertDoesNotThrow(validator::validate);
    }
}
