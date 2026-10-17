package com.dolbaia.ledger.fitness;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.dolbaia.ledger.common.Ids;
import com.dolbaia.ledger.common.Text;
import com.dolbaia.ledger.fitness.FitnessViews.ActiveWorkoutView;
import com.dolbaia.ledger.fitness.FitnessViews.FitnessPage;

/** The training workspace and the guided workout runner. */
@RestController
@RequestMapping("/api/fitness")
public class FitnessController {

    // Limits shared with the runner UI: 0-2000 kg, 1-200 reps, 1-20 sets, effort 1-3.
    public record ExerciseForm(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 80) String muscleGroup,
            @Size(max = 2000) String note,
            @DecimalMin("0") @DecimalMax("2000") @Digits(integer = 4, fraction = 2) BigDecimal recommendedWeight,
            @Min(1) @Max(200) Integer recommendedReps,
            @Min(1) @Max(20) Integer recommendedSets) {}

    public record NewVariationForm(@NotBlank String exerciseId, @NotBlank @Size(max = 120) String name,
                                   @Size(max = 500) String note) {}

    public record VariationForm(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String note,
            @DecimalMin("0") @DecimalMax("2000") @Digits(integer = 4, fraction = 2) BigDecimal recommendedWeight,
            @Min(1) @Max(200) Integer recommendedReps,
            @Min(1) @Max(20) Integer recommendedSets) {}

    public record PlanForm(@NotBlank @Size(max = 120) String name, @Size(max = 500) String description) {}

    public record DayForm(@NotBlank String planId, @NotBlank @Size(max = 120) String name) {}

    public record SlotForm(@NotBlank String planDayId, @NotBlank String exerciseId,
                           @Min(1) @Max(20) Integer setCount, @Size(max = 500) String note,
                           Boolean supersetWithPrevious) {}

    public record StartForm(@NotBlank String planDayId) {}

    public record SetForm(
            @NotNull @DecimalMin("0") @DecimalMax("2000") @Digits(integer = 4, fraction = 2) BigDecimal weight,
            @NotNull @Min(1) @Max(200) Integer reps,
            @NotNull Boolean completed) {}

    public record VariationChoiceForm(String variationId) {}

    /** The end-of-exercise step; every field is entered by hand. */
    public record WrapUpForm(
            @NotNull @Min(1) @Max(3) Integer perceivedEffort,
            @NotNull @DecimalMin("0") @DecimalMax("2000") @Digits(integer = 4, fraction = 2) BigDecimal nextWeight,
            @NotNull @Min(1) @Max(200) Integer nextReps,
            @NotNull @Min(1) @Max(20) Integer nextSets) {}

    public record NoteForm(@Size(max = 2000) String note) {}

    private final FitnessService fitness;
    private final WorkoutService workouts;

    public FitnessController(FitnessService fitness, WorkoutService workouts) {
        this.fitness = fitness;
        this.workouts = workouts;
    }

    @GetMapping
    FitnessPage page() {
        return fitness.page();
    }

    // ---- Library -------------------------------------------------------------------------

    @PostMapping("/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createExercise(@Valid @RequestBody ExerciseForm form) {
        return Map.of("id", fitness.createExercise(exercise(form)));
    }

    @PutMapping("/exercises/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateExercise(@PathVariable UUID id, @Valid @RequestBody ExerciseForm form) {
        fitness.updateExercise(id, exercise(form));
    }

    @PostMapping("/exercises/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveExercise(@PathVariable UUID id) {
        fitness.archiveExercise(id);
    }

    @PostMapping("/variations")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createVariation(@Valid @RequestBody NewVariationForm form) {
        return Map.of("id", fitness.createVariation(Ids.required(form.exerciseId()), form.name().trim(),
                Text.clean(form.note())));
    }

    @PutMapping("/variations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void updateVariation(@PathVariable UUID id, @Valid @RequestBody VariationForm form) {
        fitness.updateVariation(id, new FitnessService.VariationForm(form.name().trim(), Text.clean(form.note()),
                weight(form.recommendedWeight()), reps(form.recommendedReps()), form.recommendedSets()));
    }

    @PostMapping("/variations/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archiveVariation(@PathVariable UUID id) {
        fitness.archiveVariation(id);
    }

    // ---- Plans -----------------------------------------------------------------------------

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createPlan(@Valid @RequestBody PlanForm form) {
        return Map.of("id", fitness.createPlan(form.name().trim(), Text.clean(form.description())));
    }

    @PostMapping("/plans/{id}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void archivePlan(@PathVariable UUID id) {
        fitness.archivePlan(id);
    }

    @PostMapping("/days")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> createDay(@Valid @RequestBody DayForm form) {
        return Map.of("id", fitness.createDay(Ids.required(form.planId()), form.name().trim()));
    }

    @PostMapping("/plan-exercises")
    @ResponseStatus(HttpStatus.CREATED)
    Map<String, UUID> addSlot(@Valid @RequestBody SlotForm form) {
        return Map.of("id", fitness.addSlot(Ids.required(form.planDayId()), Ids.required(form.exerciseId()),
                form.setCount() == null ? 3 : form.setCount(), Text.clean(form.note()),
                Boolean.TRUE.equals(form.supersetWithPrevious())));
    }

    @PostMapping("/plan-exercises/{id}/superset")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void toggleSuperset(@PathVariable UUID id) {
        fitness.toggleSuperset(id);
    }

    @DeleteMapping("/plan-exercises/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void removeSlot(@PathVariable UUID id) {
        fitness.removeSlot(id);
    }

    // ---- Workouts ----------------------------------------------------------------------------

    /** 201 with a new workout, or 200 with the one already in progress for that session. */
    @PostMapping("/workouts")
    ResponseEntity<WorkoutService.Started> start(@Valid @RequestBody StartForm form) {
        WorkoutService.Started started = workouts.start(Ids.required(form.planDayId()));
        return ResponseEntity.status(started.resumed() ? HttpStatus.OK : HttpStatus.CREATED).body(started);
    }

    @GetMapping("/workouts/{id}")
    ActiveWorkoutView workout(@PathVariable UUID id) {
        return workouts.view(id);
    }

    @DeleteMapping("/workouts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void discard(@PathVariable UUID id) {
        workouts.discard(id);
    }

    @PutMapping("/workouts/{id}/note")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void saveNote(@PathVariable UUID id, @Valid @RequestBody NoteForm form) {
        workouts.saveNote(id, Text.clean(form.note()));
    }

    /** Files the workout into history; returns the history entry's id. */
    @PostMapping("/workouts/{id}/complete")
    Map<String, UUID> complete(@PathVariable UUID id) {
        return Map.of("id", workouts.complete(id));
    }

    @PutMapping("/active-sets/{id}")
    Map<String, Boolean> saveSet(@PathVariable UUID id, @Valid @RequestBody SetForm form) {
        workouts.saveSet(id, form.weight(), form.reps(), form.completed());
        return Map.of("ok", true);
    }

    @PutMapping("/active-exercises/{id}/variation")
    Map<String, ActiveWorkoutView> selectVariation(@PathVariable UUID id, @RequestBody VariationChoiceForm form) {
        return Map.of("workout", workouts.selectVariation(id, Ids.optional(form.variationId())));
    }

    @PutMapping("/active-exercises/{id}/wrap-up")
    Map<String, Boolean> wrapUp(@PathVariable UUID id, @Valid @RequestBody WrapUpForm form) {
        workouts.wrapUp(id, form.perceivedEffort(), form.nextWeight(), form.nextReps(), form.nextSets());
        return Map.of("ok", true);
    }

    private static FitnessService.ExerciseForm exercise(ExerciseForm form) {
        return new FitnessService.ExerciseForm(form.name().trim(), Text.clean(form.muscleGroup()),
                Text.clean(form.note()), weight(form.recommendedWeight()), reps(form.recommendedReps()),
                form.recommendedSets());
    }

    private static BigDecimal weight(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int reps(Integer value) {
        return value == null ? 10 : value;
    }
}
