package global.govstack.ruleeditor.service;

import org.joget.apps.app.dao.FormDefinitionDao;
import org.joget.apps.app.model.AppDefinition;
import org.joget.apps.app.service.AppService;
import org.joget.apps.app.service.AppUtil;
import org.joget.apps.form.dao.FormDataDao;
import org.joget.apps.form.model.FormRow;
import org.joget.apps.form.model.FormRowSet;
import org.joget.commons.util.LogUtil;

import java.util.*;

/**
 * Service for loading field definitions from the ruleFieldDefinition form.
 *
 * Uses Joget's FormDataDao to query field data from the database.
 */
public class FieldRegistryService {

    private static final String CLASS_NAME = FieldRegistryService.class.getName();

    // Form/table names
    private static final String FIELD_DEFINITION_FORM = "ruleFieldDefinition";
    private static final String FIELD_DEFINITION_TABLE = "ruleFieldDefinition";
    private static final String FIELD_SCOPE_FORM = "ruleFieldScope";
    private static final String FIELD_SCOPE_TABLE = "ruleFieldScope";

    // Cache for field definitions (scope -> fields)
    private Map<String, List<FieldDefinition>> fieldCache = new HashMap<>();
    private long cacheExpiry = 0;
    private static final long CACHE_TTL = 60000; // 1 minute

    /**
     * Get all active field definitions for a given scope.
     *
     * @param scopeCode The scope code (e.g., "FARMER_ELIGIBILITY")
     * @return List of field definitions
     */
    public List<FieldDefinition> getFieldsForScope(String scopeCode) {
        // Check cache
        if (System.currentTimeMillis() < cacheExpiry && fieldCache.containsKey(scopeCode)) {
            return fieldCache.get(scopeCode);
        }

        List<FieldDefinition> fields = loadFieldsFromDatabase(scopeCode);

        // Update cache
        fieldCache.put(scopeCode, fields);
        cacheExpiry = System.currentTimeMillis() + CACHE_TTL;

        return fields;
    }

    /**
     * Get all active field definitions across all scopes.
     *
     * @return List of field definitions
     */
    public List<FieldDefinition> getAllFields() {
        return loadFieldsFromDatabase(null);
    }

    /**
     * Check if a field exists in the given scope.
     *
     * @param scopeCode The scope code
     * @param fieldId The field ID (e.g., "age" or "householdMembers.sex")
     * @return true if field exists and is active
     */
    public boolean fieldExists(String scopeCode, String fieldId) {
        List<FieldDefinition> fields = getFieldsForScope(scopeCode);
        return fields.stream().anyMatch(f -> f.getFieldId().equals(fieldId));
    }

    /**
     * Get a specific field definition.
     *
     * @param scopeCode The scope code
     * @param fieldId The field ID
     * @return FieldDefinition or null if not found
     */
    public FieldDefinition getField(String scopeCode, String fieldId) {
        List<FieldDefinition> fields = getFieldsForScope(scopeCode);
        return fields.stream()
            .filter(f -> f.getFieldId().equals(fieldId))
            .findFirst()
            .orElse(null);
    }

    /**
     * Get fields grouped by category.
     *
     * @param scopeCode The scope code
     * @return Map of category -> list of fields
     */
    public Map<String, List<FieldDefinition>> getFieldsByCategory(String scopeCode) {
        List<FieldDefinition> fields = getFieldsForScope(scopeCode);
        Map<String, List<FieldDefinition>> grouped = new LinkedHashMap<>();

        for (FieldDefinition field : fields) {
            String category = field.getCategory();
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(field);
        }

        return grouped;
    }

    /**
     * Clear the field cache.
     */
    public void clearCache() {
        fieldCache.clear();
        cacheExpiry = 0;
    }

    /**
     * Load fields from database using FormDataDao.
     */
    private List<FieldDefinition> loadFieldsFromDatabase(String scopeCode) {
        List<FieldDefinition> fields = new ArrayList<>();

        try {
            FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
                .getBean("formDataDao");

            // Build condition
            String condition = "WHERE c_isActive = ?";
            Object[] params;

            if (scopeCode != null && !scopeCode.isEmpty()) {
                condition += " AND c_scopeCode = ?";
                params = new Object[]{"Y", scopeCode};
            } else {
                params = new Object[]{"Y"};
            }

            condition += " ORDER BY c_category, c_displayOrder";

            // Query
            FormRowSet rowSet = formDataDao.find(
                FIELD_DEFINITION_FORM,
                FIELD_DEFINITION_TABLE,
                condition,
                params,
                null,  // sort
                null,  // desc
                null,  // start
                null   // rows
            );

            if (rowSet != null) {
                for (FormRow row : rowSet) {
                    FieldDefinition field = rowToFieldDefinition(row);
                    if (field != null) {
                        fields.add(field);
                    }
                }
            }

            LogUtil.info(CLASS_NAME, "Loaded " + fields.size() + " fields" +
                (scopeCode != null ? " for scope " + scopeCode : ""));

        } catch (Exception e) {
            LogUtil.error(CLASS_NAME, e, "Error loading field definitions from database");
        }

        return fields;
    }

    /**
     * Convert a FormRow to FieldDefinition.
     */
    private FieldDefinition rowToFieldDefinition(FormRow row) {
        try {
            FieldDefinition field = new FieldDefinition();

            field.setId(row.getId());
            field.setScopeCode(getProperty(row, "scopeCode"));
            field.setFieldId(getProperty(row, "fieldId"));
            field.setFieldLabel(getProperty(row, "fieldLabel"));
            field.setCategory(getProperty(row, "category"));
            field.setFieldType(getProperty(row, "fieldType"));
            field.setApplicableOperators(parseList(getProperty(row, "applicableOperators")));
            field.setGrid("Y".equals(getProperty(row, "isGrid")));
            field.setGridParentField(getProperty(row, "gridParentField"));
            field.setAggregationFunctions(parseList(getProperty(row, "aggregationFunctions")));
            field.setLookupFormId(getProperty(row, "lookupFormId"));
            field.setLookupValues(parseList(getProperty(row, "lookupValues")));
            field.setHelpText(getProperty(row, "helpText"));

            String displayOrder = getProperty(row, "displayOrder");
            if (displayOrder != null && !displayOrder.isEmpty()) {
                try {
                    field.setDisplayOrder(Integer.parseInt(displayOrder));
                } catch (NumberFormatException e) {
                    field.setDisplayOrder(100);
                }
            }

            return field;

        } catch (Exception e) {
            LogUtil.warn(CLASS_NAME, "Error converting row to FieldDefinition: " + e.getMessage());
            return null;
        }
    }

    /**
     * Get property from FormRow, handling null.
     */
    private String getProperty(FormRow row, String name) {
        Object value = row.getProperty(name);
        return value != null ? value.toString() : null;
    }

    /**
     * Parse a semicolon or newline separated list.
     */
    private List<String> parseList(String value) {
        if (value == null || value.isEmpty()) {
            return new ArrayList<>();
        }

        // Handle JSON array format
        if (value.startsWith("[")) {
            value = value.substring(1);
        }
        if (value.endsWith("]")) {
            value = value.substring(0, value.length() - 1);
        }

        // Split by semicolon, newline, or comma
        String[] parts = value.split("[;,\\n]+");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            String trimmed = part.trim().replace("\"", "");
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * Field definition data class.
     */
    public static class FieldDefinition {
        private String id;
        private String scopeCode;
        private String fieldId;
        private String fieldLabel;
        private String category;
        private String fieldType;
        private List<String> applicableOperators = new ArrayList<>();
        private boolean isGrid;
        private String gridParentField;
        private List<String> aggregationFunctions = new ArrayList<>();
        private String lookupFormId;
        private List<String> lookupValues = new ArrayList<>();
        private String helpText;
        private int displayOrder = 100;

        // Getters and setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }

        public String getScopeCode() { return scopeCode; }
        public void setScopeCode(String scopeCode) { this.scopeCode = scopeCode; }

        public String getFieldId() { return fieldId; }
        public void setFieldId(String fieldId) { this.fieldId = fieldId; }

        public String getFieldLabel() { return fieldLabel; }
        public void setFieldLabel(String fieldLabel) { this.fieldLabel = fieldLabel; }

        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }

        public String getFieldType() { return fieldType; }
        public void setFieldType(String fieldType) { this.fieldType = fieldType; }

        public List<String> getApplicableOperators() { return applicableOperators; }
        public void setApplicableOperators(List<String> applicableOperators) {
            this.applicableOperators = applicableOperators;
        }

        public boolean isGrid() { return isGrid; }
        public void setGrid(boolean grid) { isGrid = grid; }

        public String getGridParentField() { return gridParentField; }
        public void setGridParentField(String gridParentField) { this.gridParentField = gridParentField; }

        public List<String> getAggregationFunctions() { return aggregationFunctions; }
        public void setAggregationFunctions(List<String> aggregationFunctions) {
            this.aggregationFunctions = aggregationFunctions;
        }

        public String getLookupFormId() { return lookupFormId; }
        public void setLookupFormId(String lookupFormId) { this.lookupFormId = lookupFormId; }

        public List<String> getLookupValues() { return lookupValues; }
        public void setLookupValues(List<String> lookupValues) { this.lookupValues = lookupValues; }

        public String getHelpText() { return helpText; }
        public void setHelpText(String helpText) { this.helpText = helpText; }

        public int getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }

        /**
         * Check if this is a grid child field (has dot notation).
         */
        public boolean isGridChildField() {
            return fieldId != null && fieldId.contains(".");
        }

        /**
         * Get the parent grid field ID for a grid child field.
         */
        public String getParentGridFieldId() {
            if (!isGridChildField()) return null;
            return fieldId.substring(0, fieldId.indexOf('.'));
        }
    }
}
