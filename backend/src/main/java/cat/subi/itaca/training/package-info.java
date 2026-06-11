/**
 * Training bounded context: exercises, routines, workouts and sets.
 * Publishes events such as WorkoutCompleted; no direct references to other contexts.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Training")
package cat.subi.itaca.training;
