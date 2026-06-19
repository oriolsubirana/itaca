/**
 * Profile bounded context: single-user anthropometrics (weight, height, age, sex,
 * activity, goal) and the pure calorie-target calculation (Mifflin-St Jeor -> TDEE).
 * The day's target (+ exercise, flare adjustment) is composed at the edge, not here.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Profile")
package cat.subi.itaca.profile;
