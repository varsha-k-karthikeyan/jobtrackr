package com.jobtrackr.jobtrackrbackend.auth.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Runs the Bean Validation annotations on RegisterRequest directly, without Spring or HTTP,
// to pin down exactly which passwords the policy accepts.
class RegisterRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Demo@1234", "S3cret@Pass", "Aa!aaaaa", "Pass_word", "Ünïcödé#Aa"})
    void acceptsPasswordsWithUpperLowerAndSpecialCharacter(String password) {
        assertThat(passwordViolations(password)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "lowercase@1",   // no uppercase
            "UPPERCASE@1",   // no lowercase
            "NoSpecial123",  // no special character
            "Has Space1",    // whitespace does not count as a special character
    })
    void rejectsPasswordsMissingACharacterClass(String password) {
        assertThat(passwordViolations(password))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly(PasswordPolicy.COMPOSITION_MESSAGE);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Ab@1", "Abcde@1",              // shorter than 8
            "Abcdefghij@klmnopqrst",        // 21 characters
    })
    void rejectsPasswordsOutsideEightToTwentyCharacters(String password) {
        assertThat(passwordViolations(password))
                .extracting(ConstraintViolation::getMessage)
                .containsExactly("size must be between 8 and 20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Abcdef@1", "Abcdefghij@klmnopqrs"})   // exactly 8 and exactly 20 characters
    void acceptsLengthsAtTheBoundaries(String password) {
        assertThat(passwordViolations(password)).isEmpty();
    }

    private Set<ConstraintViolation<RegisterRequest>> passwordViolations(String password) {
        return validator.validateProperty(new RegisterRequest("valid@example.com", password, null), "password");
    }
}
