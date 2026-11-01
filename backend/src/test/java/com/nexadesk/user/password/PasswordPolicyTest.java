package com.nexadesk.user.password;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Demo@Admin2026", "Str0ng!Passw", "Àbcdefgh1$"})
    void acceptsStrongPasswords(String password) {
        assertThat(StrongPasswordValidator.isStrong(password)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Short1!a",          // too short
            "alllowercase1!",    // no uppercase
            "ALLUPPERCASE1!",    // no lowercase
            "NoDigitsHere!!",    // no digit
            "NoSpecial12345",    // no special character
            "With Space 1a"      // whitespace is not a special character, and none other present
    })
    void rejectsWeakPasswords(String password) {
        assertThat(StrongPasswordValidator.isStrong(password)).isFalse();
    }

    @Test
    void generatedTemporaryPasswordsAreStrongAndUnique() {
        TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String password = generator.generate();
            assertThat(StrongPasswordValidator.isStrong(password)).as(password).isTrue();
            assertThat(password).doesNotContainAnyWhitespaces().doesNotContain("0", "O", "1", "l", "I");
            seen.add(password);
        }
        assertThat(seen).hasSize(500);
    }
}
