/**
 * Health bounded context (IBD): diary, meals, flares and lab results.
 * Publishes events such as LabResultsImported, FlareStarted/Ended.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Health")
package cat.subi.itaca.health;
