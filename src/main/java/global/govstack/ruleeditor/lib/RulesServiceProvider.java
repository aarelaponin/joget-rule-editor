package global.govstack.ruleeditor.lib;

import global.govstack.ruleeditor.model.Rule;
import global.govstack.ruleeditor.model.ValidationResult;
import global.govstack.ruleeditor.parser.RuleScriptParser;
import global.govstack.ruleeditor.compiler.CompiledRuleset;
import global.govstack.ruleeditor.compiler.CompiledRuleset.CompiledRule;
import global.govstack.ruleeditor.compiler.RuleScriptCompiler;
import global.govstack.ruleeditor.compiler.FieldMapping;
import global.govstack.ruleeditor.service.FieldRegistryService;
import global.govstack.ruleeditor.service.FieldRegistryService.FieldDefinition;
import global.govstack.ruleeditor.service.RulesetService;
import global.govstack.ruleeditor.service.RulesetService.RulesetData;
import org.joget.api.annotations.Operation;
import org.joget.api.annotations.Param;
import org.joget.api.annotations.Response;
import org.joget.api.annotations.Responses;
import org.joget.api.model.ApiPluginAbstract;
import org.joget.api.model.ApiResponse;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.LogUtil;
import org.joget.plugin.property.model.PropertyEditable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.Map;

/**
 * Rules Service Provider - API Plugin for validating, compiling and managing Rules Scripts.
 *
 * Endpoints:
 * - POST /rules/validate - Validate script without saving
 * - POST /rules/compile - Compile script to SQL
 * - GET  /rules/fields - Get available field definitions
 * - POST /rules/fields/refresh - Refresh field cache
 * - POST /rules/saveRuleset - Save a ruleset
 * - GET  /rules/loadRuleset - Load a ruleset by code or context
 * - POST /rules/publishRuleset - Publish a ruleset
 *
 * Base URL: /jw/api/erel/rules/...
 */
public class RulesServiceProvider extends ApiPluginAbstract implements PropertyEditable {

    private static final String CLASS_NAME = "global.govstack.ruleeditor.lib.RulesServiceProvider";

    // Default scope code
    private static final String DEFAULT_SCOPE = "FARMER_ELIGIBILITY";

    // Services
    private FieldRegistryService fieldRegistryService;
    private RulesetService rulesetService;

    @Override
    public String getName() {
        return "rules";
    }

    @Override
    public String getVersion() {
        return "8.1-SNAPSHOT";
    }

    @Override
    public String getDescription() {
        return "Rules Script API - Validate, compile and manage Rules Script definitions";
    }

    @Override
    public String getTag() {
        return "rules";
    }

    @Override
    public String getIcon() {
        return "<i class=\"fa fa-check-square-o\"></i>";
    }

    @Override
    public String getLabel() {
        return "Rules Script API";
    }

    @Override
    public String getClassName() {
        return CLASS_NAME;
    }

    @Override
    public String getPropertyOptions() {
        return AppUtil.readPluginResource(
            getClass().getName(),
            "/properties/RulesServiceProvider.json",
            null,
            true,
            null
        );
    }

    /**
     * Get the field registry service (lazy initialization).
     */
    private FieldRegistryService getFieldRegistry() {
        if (fieldRegistryService == null) {
            fieldRegistryService = new FieldRegistryService();
        }
        return fieldRegistryService;
    }

    /**
     * Get the ruleset service (lazy initialization).
     */
    private RulesetService getRulesetService() {
        if (rulesetService == null) {
            rulesetService = new RulesetService();
        }
        return rulesetService;
    }

    // ==========================================================================
    // VALIDATION ENDPOINTS
    // ==========================================================================

    /**
     * Validate a Rules Script without saving.
     *
     * Endpoint: POST /jw/api/erel/rules/validate
     */
    @Operation(
        path = "/rules/validate",
        type = Operation.MethodType.POST,
        summary = "Validate a Rules Script",
        description = "Parses and validates a Rules Script, returning any errors or warnings. " +
                      "Does not save the rules to the database."
    )
    @Responses({
        @Response(responseCode = 200, description = "Validation completed"),
        @Response(responseCode = 400, description = "Invalid request format"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse validate(
        @Param(value = "body", required = true) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Rules Validate Request ===");

        try {
            JSONObject request = new JSONObject(requestBody);
            String script = request.optString("script", "");
            String scopeCode = request.optString("scopeCode", DEFAULT_SCOPE);

            LogUtil.info(CLASS_NAME, "Script length: " + script.length() + ", scope: " + scopeCode);

            // Parse
            RuleScriptParser parser = new RuleScriptParser();
            ValidationResult result = parser.parse(script);

            // Build response
            JSONObject response = new JSONObject();
            response.put("valid", result.isValid());
            response.put("ruleCount", result.getRuleCount());
            response.put("rules", new JSONArray(result.getRulesSummary()));
            response.put("errors", new JSONArray(result.getErrors()));
            response.put("warnings", new JSONArray(result.getWarnings()));

            LogUtil.info(CLASS_NAME, "Validation: valid=" + result.isValid() +
                         ", rules=" + result.getRuleCount());

            return new ApiResponse(200, response.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error validating Rules Script");

            JSONObject error = new JSONObject();
            error.put("valid", false);
            error.put("error", e.getMessage());
            error.put("errorType", "PARSE_ERROR");

            return new ApiResponse(400, error.toString());
        }
    }

    // ==========================================================================
    // COMPILATION ENDPOINTS
    // ==========================================================================

    /**
     * Compile a Rules Script to SQL.
     *
     * Endpoint: POST /jw/api/erel/rules/compile
     *
     * Request body:
     * {
     *   "script": "RULE ...",
     *   "scopeCode": "FARMER_ELIGIBILITY",
     *   "rulesetCode": "RS-..."  // optional
     * }
     *
     * OR load from saved ruleset:
     * {
     *   "rulesetCode": "RS-ELIG-251223-89CA"
     * }
     */
    @Operation(
        path = "/rules/compile",
        type = Operation.MethodType.POST,
        summary = "Compile Rules Script to SQL",
        description = "Parses and compiles a Rules Script to SQL queries for eligibility checking and scoring."
    )
    @Responses({
        @Response(responseCode = 200, description = "Compilation successful"),
        @Response(responseCode = 400, description = "Parse/validation error"),
        @Response(responseCode = 404, description = "Ruleset not found"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse compile(
        @Param(value = "body", required = true) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Rules Compile Request ===");

        try {
            JSONObject request = new JSONObject(requestBody);

            String script = request.optString("script", "");
            String scopeCode = request.optString("scopeCode", DEFAULT_SCOPE);
            String rulesetCode = request.optString("rulesetCode", "");

            // If no script provided but rulesetCode given, load from database
            if (script.isEmpty() && !rulesetCode.isEmpty()) {
                RulesetData ruleset = getRulesetService().loadRuleset(rulesetCode);
                if (ruleset == null) {
                    return new ApiResponse(404, errorJson("Ruleset not found: " + rulesetCode));
                }
                script = ruleset.getScript();
                scopeCode = ruleset.getFieldScopeCode();
                LogUtil.info(CLASS_NAME, "Loaded ruleset: " + rulesetCode);
            }

            if (script.isEmpty()) {
                return new ApiResponse(400, errorJson("Script is required"));
            }

            LogUtil.info(CLASS_NAME, "Compiling script, length=" + script.length() + ", scope=" + scopeCode);

            // Parse first
            RuleScriptParser parser = new RuleScriptParser();
            ValidationResult parseResult = parser.parse(script);

            if (!parseResult.isValid()) {
                JSONObject response = new JSONObject();
                response.put("success", false);
                response.put("message", "Parse errors");
                response.put("errors", new JSONArray(parseResult.getErrors()));
                return new ApiResponse(400, response.toString());
            }

            List<Rule> rules = parseResult.getRules();

            // Compile to SQL
            FieldMapping fieldMapping = FieldMapping.createFarmerEligibilityMapping();
            RuleScriptCompiler compiler = new RuleScriptCompiler(fieldMapping);
            CompiledRuleset compiled = compiler.compile(rules, rulesetCode, scopeCode);

            // Build response
            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("rulesetCode", rulesetCode);
            response.put("scopeCode", scopeCode);

            // Statistics
            JSONObject stats = new JSONObject();
            stats.put("totalRules", compiled.getTotalRules());
            stats.put("inclusionRules", compiled.getInclusionRules());
            stats.put("exclusionRules", compiled.getExclusionRules());
            stats.put("bonusRules", compiled.getBonusRules());
            response.put("statistics", stats);

            // SQL outputs
            JSONObject sql = new JSONObject();
            sql.put("eligibilityWhereClause", compiled.getEligibilityWhereClause());
            sql.put("exclusionWhereClause", compiled.getExclusionWhereClause());
            sql.put("eligibilityCheckQuery", compiled.getEligibilityCheckQuery());
            sql.put("scoringQuery", compiled.getScoringQuery());
            sql.put("fullEligibilityQuery", compiled.getFullEligibilityQuery());
            response.put("sql", sql);

            // Individual compiled rules
            JSONArray rulesArray = new JSONArray();
            for (CompiledRule rule : compiled.getCompiledRules()) {
                JSONObject ruleJson = new JSONObject();
                ruleJson.put("ruleName", rule.getRuleName());
                ruleJson.put("ruleCode", rule.getRuleCode());
                ruleJson.put("ruleType", rule.getRuleType());
                ruleJson.put("mandatory", rule.isMandatory());
                if (rule.getScore() != null) {
                    ruleJson.put("score", rule.getScore());
                }
                ruleJson.put("whereClause", rule.getWhereClause());
                ruleJson.put("selectExpression", rule.getSelectExpression());
                ruleJson.put("usedFields", new JSONArray(rule.getUsedFields()));
                rulesArray.put(ruleJson);
            }
            response.put("compiledRules", rulesArray);

            // Warnings
            if (compiled.hasWarnings()) {
                response.put("warnings", new JSONArray(compiled.getCompilationWarnings()));
            }

            LogUtil.info(CLASS_NAME, "Compilation successful: " + compiled.getTotalRules() + " rules");

            return new ApiResponse(200, response.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error compiling Rules Script");
            return new ApiResponse(500, errorJson(e.getMessage()));
        }
    }

    // ==========================================================================
    // FIELD ENDPOINTS
    // ==========================================================================

    /**
     * Get available field definitions for autocomplete.
     *
     * Endpoint: GET /jw/api/erel/rules/fields?scopeCode=FARMER_ELIGIBILITY
     */
    @Operation(
        path = "/rules/fields",
        type = Operation.MethodType.GET,
        summary = "Get available field definitions",
        description = "Returns fields that can be used in rule conditions, grouped by category."
    )
    @Responses({
        @Response(responseCode = 200, description = "Field definitions returned"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse getFields(
        @Param(value = "scopeCode", required = false) String scopeCode
    ) {
        LogUtil.info(CLASS_NAME, "=== Rules Get Fields Request ===");

        try {
            if (scopeCode == null || scopeCode.isEmpty()) {
                scopeCode = DEFAULT_SCOPE;
            }

            Map<String, List<FieldDefinition>> fieldsByCategory =
                getFieldRegistry().getFieldsByCategory(scopeCode);

            JSONArray categories = new JSONArray();
            int totalCount = 0;

            for (Map.Entry<String, List<FieldDefinition>> entry : fieldsByCategory.entrySet()) {
                JSONObject category = new JSONObject();
                category.put("category", entry.getKey());

                JSONArray fields = new JSONArray();
                for (FieldDefinition field : entry.getValue()) {
                    fields.put(fieldToJson(field));
                    totalCount++;
                }
                category.put("fields", fields);
                categories.put(category);
            }

            JSONObject response = new JSONObject();
            response.put("scopeCode", scopeCode);
            response.put("categories", categories);
            response.put("count", totalCount);

            return new ApiResponse(200, response.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error getting field definitions");
            return new ApiResponse(500, errorJson(e.getMessage()));
        }
    }

    /**
     * Refresh the field cache.
     *
     * Endpoint: POST /jw/api/erel/rules/fields/refresh
     */
    @Operation(
        path = "/rules/fields/refresh",
        type = Operation.MethodType.POST,
        summary = "Refresh field cache",
        description = "Clears the field definition cache."
    )
    @Responses({
        @Response(responseCode = 200, description = "Cache refreshed")
    })
    public ApiResponse refreshFieldCache() {
        LogUtil.info(CLASS_NAME, "=== Rules Refresh Field Cache ===");

        try {
            getFieldRegistry().clearCache();

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("message", "Field cache cleared");

            return new ApiResponse(200, response.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error refreshing field cache");
            return new ApiResponse(500, errorJson(e.getMessage()));
        }
    }

    // ==========================================================================
    // RULESET ENDPOINTS
    // ==========================================================================

    /**
     * Save a ruleset.
     *
     * Endpoint: POST /jw/api/erel/rules/saveRuleset
     */
    @Operation(
        path = "/rules/saveRuleset",
        type = Operation.MethodType.POST,
        summary = "Save a ruleset",
        description = "Saves or updates a ruleset. Validates the script before saving."
    )
    @Responses({
        @Response(responseCode = 200, description = "Ruleset saved"),
        @Response(responseCode = 400, description = "Validation failed"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse saveRuleset(
        @Param(value = "body", required = true) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Rules Save Ruleset Request ===");

        try {
            JSONObject request = new JSONObject(requestBody);

            String rulesetCode = request.optString("rulesetCode", null);
            if (rulesetCode != null && rulesetCode.isEmpty()) {
                rulesetCode = null;
            }
            String rulesetName = request.optString("rulesetName", "");
            String script = request.optString("script", "");
            String contextType = request.optString("contextType", "ELIGIBILITY");
            String contextCode = request.optString("contextCode", "");
            String fieldScopeCode = request.optString("fieldScopeCode", DEFAULT_SCOPE);
            String notes = request.optString("notes", "");
            boolean validateFirst = request.optBoolean("validate", true);

            LogUtil.info(CLASS_NAME, "Saving ruleset: code=" + rulesetCode +
                         ", name=" + rulesetName + ", contextCode=" + contextCode);

            // Validate first (unless skipped)
            if (validateFirst && !script.isEmpty()) {
                RuleScriptParser parser = new RuleScriptParser();
                ValidationResult result = parser.parse(script);

                if (!result.isValid()) {
                    JSONObject response = new JSONObject();
                    response.put("success", false);
                    response.put("message", "Validation failed");
                    response.put("errors", new JSONArray(result.getErrors()));
                    return new ApiResponse(400, response.toString());
                }
            }

            // Save
            String savedCode = getRulesetService().saveRuleset(
                rulesetCode, rulesetName, script,
                contextType, contextCode, fieldScopeCode, notes
            );

            // Load saved data
            RulesetData saved = getRulesetService().loadRuleset(savedCode);

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("rulesetCode", savedCode);
            response.put("version", saved != null ? saved.getVersion() : "1");
            response.put("status", saved != null ? saved.getStatus() : "DRAFT");
            response.put("message", "Ruleset saved successfully");

            LogUtil.info(CLASS_NAME, "Saved ruleset: " + savedCode);

            return new ApiResponse(200, response.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error saving ruleset");
            return new ApiResponse(500, errorJson(e.getMessage()));
        }
    }

    /**
     * Load a ruleset.
     *
     * Endpoint: GET /jw/api/erel/rules/loadRuleset?rulesetCode=RS-...
     */
    @Operation(
        path = "/rules/loadRuleset",
        type = Operation.MethodType.GET,
        summary = "Load a ruleset",
        description = "Loads a ruleset by code or by context (type + code)."
    )
    @Responses({
        @Response(responseCode = 200, description = "Ruleset loaded"),
        @Response(responseCode = 404, description = "Ruleset not found"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse loadRuleset(
        @Param(value = "rulesetCode", required = false) String rulesetCode,
        @Param(value = "contextType", required = false) String contextType,
        @Param(value = "contextCode", required = false) String contextCode
    ) {
        LogUtil.info(CLASS_NAME, "=== Rules Load Ruleset Request ===");
        LogUtil.info(CLASS_NAME, "rulesetCode=" + rulesetCode +
                     ", contextType=" + contextType + ", contextCode=" + contextCode);

        try {
            RulesetData ruleset = null;

            if (rulesetCode != null && !rulesetCode.isEmpty()) {
                ruleset = getRulesetService().loadRuleset(rulesetCode);
            } else if (contextType != null && contextCode != null &&
                       !contextType.isEmpty() && !contextCode.isEmpty()) {
                ruleset = getRulesetService().loadRulesetByContext(contextType, contextCode);
            } else {
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", "Either rulesetCode or (contextType + contextCode) required");
                return new ApiResponse(400, error.toString());
            }

            if (ruleset == null) {
                JSONObject error = new JSONObject();
                error.put("success", false);
                error.put("error", "Ruleset not found");
                return new ApiResponse(404, error.toString());
            }

            JSONObject response = rulesetToJson(ruleset);
            response.put("success", true);

            return new ApiResponse(200, response.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading ruleset");
            return new ApiResponse(500, errorJson(e.getMessage()));
        }
    }

    /**
     * Publish a ruleset.
     *
     * Endpoint: POST /jw/api/erel/rules/publishRuleset
     */
    @Operation(
        path = "/rules/publishRuleset",
        type = Operation.MethodType.POST,
        summary = "Publish a ruleset",
        description = "Changes ruleset status to PUBLISHED."
    )
    @Responses({
        @Response(responseCode = 200, description = "Ruleset published"),
        @Response(responseCode = 404, description = "Ruleset not found"),
        @Response(responseCode = 500, description = "Server error")
    })
    public ApiResponse publishRuleset(
        @Param(value = "body", required = true) String requestBody
    ) {
        LogUtil.info(CLASS_NAME, "=== Rules Publish Ruleset Request ===");

        try {
            JSONObject request = new JSONObject(requestBody);
            String rulesetCode = request.optString("rulesetCode", "");

            if (rulesetCode.isEmpty()) {
                return new ApiResponse(400, errorJson("rulesetCode is required"));
            }

            getRulesetService().updateStatus(rulesetCode, "PUBLISHED");

            JSONObject response = new JSONObject();
            response.put("success", true);
            response.put("rulesetCode", rulesetCode);
            response.put("status", "PUBLISHED");
            response.put("message", "Ruleset published successfully");

            return new ApiResponse(200, response.toString());

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error publishing ruleset");
            return new ApiResponse(500, errorJson(e.getMessage()));
        }
    }

    // ==========================================================================
    // HELPER METHODS
    // ==========================================================================

    private JSONObject fieldToJson(FieldDefinition field) {
        JSONObject json = new JSONObject();

        json.put("fieldId", field.getFieldId());
        json.put("fieldLabel", field.getFieldLabel());
        json.put("fieldType", field.getFieldType());
        json.put("category", field.getCategory());
        json.put("isGrid", field.isGrid());

        if (field.getGridParentField() != null && !field.getGridParentField().isEmpty()) {
            json.put("gridParentField", field.getGridParentField());
        }

        if (!field.getApplicableOperators().isEmpty()) {
            json.put("operators", new JSONArray(field.getApplicableOperators()));
        }

        if (!field.getAggregationFunctions().isEmpty()) {
            json.put("aggregations", new JSONArray(field.getAggregationFunctions()));
        }

        if (!field.getLookupValues().isEmpty()) {
            json.put("lookupValues", new JSONArray(field.getLookupValues()));
        }

        if (field.getLookupFormId() != null && !field.getLookupFormId().isEmpty()) {
            json.put("lookupFormId", field.getLookupFormId());
        }

        if (field.getHelpText() != null && !field.getHelpText().isEmpty()) {
            json.put("helpText", field.getHelpText());
        }

        json.put("displayOrder", field.getDisplayOrder());

        return json;
    }

    private JSONObject rulesetToJson(RulesetData ruleset) {
        JSONObject json = new JSONObject();

        json.put("rulesetCode", ruleset.getRulesetCode());
        json.put("rulesetName", ruleset.getRulesetName());
        json.put("script", ruleset.getScript());
        json.put("status", ruleset.getStatus());
        json.put("version", ruleset.getVersion());
        json.put("contextType", ruleset.getContextType());
        json.put("contextCode", ruleset.getContextCode());
        json.put("fieldScopeCode", ruleset.getFieldScopeCode());

        if (ruleset.getEffectiveFrom() != null) {
            json.put("effectiveFrom", ruleset.getEffectiveFrom());
        }
        if (ruleset.getEffectiveTo() != null) {
            json.put("effectiveTo", ruleset.getEffectiveTo());
        }
        if (ruleset.getNotes() != null) {
            json.put("notes", ruleset.getNotes());
        }

        return json;
    }

    private String errorJson(String message) {
        JSONObject error = new JSONObject();
        error.put("success", false);
        error.put("error", message);
        return error.toString();
    }
}
