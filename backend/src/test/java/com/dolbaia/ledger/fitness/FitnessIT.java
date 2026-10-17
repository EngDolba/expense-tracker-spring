package com.dolbaia.ledger.fitness;

import static com.dolbaia.ledger.support.Api.body;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.jayway.jsonpath.JsonPath;

import org.junit.jupiter.api.Test;

import com.dolbaia.ledger.support.Api;
import com.dolbaia.ledger.support.DatabaseTest;

class FitnessIT extends DatabaseTest {

    record Setup(String plan, String day, String press, String flat, String incline, String row) {}

    /** A plan with one session: chest press (flat/incline variations) supersetted with a row. */
    Setup setup(Api api) throws Exception {
        String press = api.create("/api/fitness/exercises", body("name", "Chest press", "muscleGroup", "Chest",
                "recommendedWeight", "60", "recommendedReps", 8), "$.id");
        String row = api.create("/api/fitness/exercises", body("name", "Row", "recommendedWeight", 50), "$.id");
        String flat = api.create("/api/fitness/variations", body("exerciseId", press, "name", "Flat"), "$.id");
        String incline = api.create("/api/fitness/variations", body("exerciseId", press, "name", "Incline"), "$.id");
        String plan = api.create("/api/fitness/plans", body("name", "Gym", "description", "A/B"), "$.id");
        String day = api.create("/api/fitness/days", body("planId", plan, "name", "Routine A"), "$.id");
        api.post("/api/fitness/plan-exercises", body("planDayId", day, "exerciseId", press, "setCount", 2))
                .andExpect(status().isCreated());
        api.post("/api/fitness/plan-exercises", body("planDayId", day, "exerciseId", row, "setCount", 2,
                "supersetWithPrevious", true)).andExpect(status().isCreated());
        return new Setup(plan, day, press, flat, incline, row);
    }

    @Test
    void buildsTheLibraryAndPlans() throws Exception {
        Api api = api();
        Setup setup = setup(api);

        api.get("/api/fitness")
                .andExpect(jsonPath("$.exercises", hasSize(2)))
                .andExpect(jsonPath("$.exercises[0].name").value("Chest press"))
                .andExpect(jsonPath("$.exercises[0].variations[1].name").value("Incline"))
                .andExpect(jsonPath("$.exercises[0].variations[1].recommendedWeight").value(60))
                .andExpect(jsonPath("$.plans[0].days[0].planExercises", hasSize(2)))
                .andExpect(jsonPath("$.plans[0].days[0].planExercises[0].supersetGroup").value(1))
                .andExpect(jsonPath("$.plans[0].days[0].planExercises[1].supersetGroup").value(1))
                .andExpect(jsonPath("$.plans[0].days[0].planExercises[0].target.setCount").value(2))
                .andExpect(jsonPath("$.nextPlanDayId").value(setup.day()));

        String rowSlot = JsonPath.read(api.get("/api/fitness").andReturn().getResponse().getContentAsString(),
                "$.plans[0].days[0].planExercises[1].id");
        api.post("/api/fitness/plan-exercises/" + rowSlot + "/superset").andExpect(status().isNoContent());
        api.get("/api/fitness").andExpect(jsonPath("$.plans[0].days[0].planExercises[0].supersetGroup").value(nullValue()));
    }

    @Test
    void runsAWorkoutFromStartToHistory() throws Exception {
        Api api = api();
        Setup setup = setup(api);

        String workout = api.read(api.post("/api/fitness/workouts", body("planDayId", setup.day()))
                .andExpect(status().isCreated()), "$.id");
        api.post("/api/fitness/workouts", body("planDayId", setup.day()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumed").value(true))
                .andExpect(jsonPath("$.id").value(workout));

        String view = api.get("/api/fitness/workouts/" + workout)
                .andExpect(jsonPath("$.planDayName").value("Routine A"))
                .andExpect(jsonPath("$.exercises", hasSize(2)))
                .andExpect(jsonPath("$.exercises[0].variationId").value(setup.flat()))
                .andExpect(jsonPath("$.exercises[0].sets", hasSize(2)))
                .andExpect(jsonPath("$.exercises[0].sets[0].weight").value(60))
                .andReturn().getResponse().getContentAsString();
        String pressEntry = JsonPath.read(view, "$.exercises[0].id");
        String rowEntry = JsonPath.read(view, "$.exercises[1].id");
        List<String> pressSets = JsonPath.read(view, "$.exercises[0].sets[*].id");

        // Switch to incline before logging anything: sets re-seed from the incline target.
        api.put("/api/fitness/active-exercises/" + pressEntry + "/variation", body("variationId", setup.incline()))
                .andExpect(jsonPath("$.workout.exercises[0].variationId").value(setup.incline()));
        String reseeded = api.get("/api/fitness/workouts/" + workout).andReturn().getResponse().getContentAsString();
        pressSets = JsonPath.read(reseeded, "$.exercises[0].sets[*].id");

        api.put("/api/fitness/active-sets/" + pressSets.get(0), body("weight", "62.5", "reps", 7, "completed", true))
                .andExpect(jsonPath("$.ok").value(true));
        api.post("/api/fitness/workouts/" + workout + "/complete")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("exercise_not_rated"));

        api.put("/api/fitness/active-exercises/" + pressEntry + "/wrap-up",
                body("perceivedEffort", 2, "nextWeight", 65, "nextReps", 8, "nextSets", 3)).andExpect(status().isOk());
        api.put("/api/fitness/workouts/" + workout + "/note", body("note", "Felt strong")).andExpect(status().isNoContent());

        String history = api.read(api.post("/api/fitness/workouts/" + workout + "/complete")
                .andExpect(status().isOk()), "$.id");

        // Only performed work is filed: one exercise (the row was untouched), one set.
        api.get("/api/fitness")
                .andExpect(jsonPath("$.activeWorkouts", hasSize(0)))
                .andExpect(jsonPath("$.workouts[0].id").value(history))
                .andExpect(jsonPath("$.workouts[0].note").value("Felt strong"))
                .andExpect(jsonPath("$.workouts[0].exercises", hasSize(1)))
                .andExpect(jsonPath("$.workouts[0].exercises[0].variationName").value("Incline"))
                .andExpect(jsonPath("$.workouts[0].exercises[0].supersetGroup").value(nullValue()))
                .andExpect(jsonPath("$.workouts[0].exercises[0].sets", hasSize(1)))
                .andExpect(jsonPath("$.workouts[0].volume").value(437.5))
                .andExpect(jsonPath("$.workouts[0].planDay.planName").value("Gym"))
                // The incline variation carries the hand-entered next target; flat is untouched.
                .andExpect(jsonPath("$.exercises[0].variations[1].recommendedWeight").value(65))
                .andExpect(jsonPath("$.exercises[0].variations[1].recommendedSets").value(3))
                .andExpect(jsonPath("$.exercises[0].variations[0].recommendedWeight").value(60));

        assertThat(jdbc.queryForObject("select count(*) from active_workout_set", Integer.class)).isZero();

        // Next time the slot starts on the variation after the one used: flat again.
        String next = api.create("/api/fitness/workouts", body("planDayId", setup.day()), "$.id");
        api.get("/api/fitness/workouts/" + next).andExpect(jsonPath("$.exercises[0].variationId").value(setup.flat()));
    }

    @Test
    void cannotFinishWithoutLoggingAndCanDiscard() throws Exception {
        Api api = api();
        Setup setup = setup(api);
        String workout = api.create("/api/fitness/workouts", body("planDayId", setup.day()), "$.id");

        api.post("/api/fitness/workouts/" + workout + "/complete")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("nothing_logged"));
        api.delete("/api/fitness/workouts/" + workout).andExpect(status().isNoContent());
        api.get("/api/fitness/workouts/" + workout).andExpect(status().isNotFound());
    }

    @Test
    void removingASlotDissolvesItsSuperset() throws Exception {
        Api api = api();
        Setup setup = setup(api);
        String pressSlot = JsonPath.read(api.get("/api/fitness").andReturn().getResponse().getContentAsString(),
                "$.plans[0].days[0].planExercises[0].id");

        api.delete("/api/fitness/plan-exercises/" + pressSlot).andExpect(status().isNoContent());
        api.get("/api/fitness")
                .andExpect(jsonPath("$.plans[0].days[0].planExercises", hasSize(1)))
                .andExpect(jsonPath("$.plans[0].days[0].planExercises[0].supersetGroup").value(nullValue()));
        api.post("/api/fitness/plan-exercises", body("planDayId", setup.day(), "exerciseId", setup.row()))
                .andExpect(status().isConflict());
    }
}
