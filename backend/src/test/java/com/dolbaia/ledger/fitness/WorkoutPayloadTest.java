package com.dolbaia.ledger.fitness;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.fitness.FitnessController.SetForm;
import com.dolbaia.ledger.fitness.FitnessController.WrapUpForm;

/** Port of fitness.test.ts "workout payload schemas". */
class WorkoutPayloadTest {

    final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsALoggedSetAndRejectsImpossibleWork() {
        assertThat(validator.validate(new SetForm(new BigDecimal("62.5"), 7, true))).isEmpty();
        assertThat(validator.validate(new SetForm(new BigDecimal("62.5"), 0, true))).isNotEmpty();
        assertThat(validator.validate(new SetForm(new BigDecimal("2000.01"), 5, true))).isNotEmpty();
        assertThat(validator.validate(new SetForm(new BigDecimal("-1"), 5, true))).isNotEmpty();
    }

    @Test
    void requiresAHardnessRatingAndCompleteNextTargetsToWrapUp() {
        assertThat(validator.validate(new WrapUpForm(2, new BigDecimal("65"), 8, 3))).isEmpty();
        assertThat(validator.validate(new WrapUpForm(4, new BigDecimal("65"), 8, 3))).isNotEmpty();
        assertThat(validator.validate(new WrapUpForm(2, new BigDecimal("65"), 8, 0))).isNotEmpty();
        assertThat(validator.validate(new WrapUpForm(null, new BigDecimal("65"), 8, 3))).isNotEmpty();
    }
}
