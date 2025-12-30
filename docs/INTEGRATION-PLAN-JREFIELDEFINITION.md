# Integration Plan: jreFieldDefinition with Rule Editor Plugin

## Executive Summary

Integrate the `jreFieldDefinition` Joget entity with the Rule Editor plugin, enabling dynamic field filtering. Create new MDM entity `md51FieldCategory` for dynamic category management. Add runtime filtering UI to dictionary panel.

**Status**: In Progress (Phase 1 Complete)
**Created**: 2025-12-29
**Updated**: 2025-12-30
**Total Phases**: 6

---

## Important Architecture Update (2025-12-30)

The `joget-rule-editor` plugin has been **split into two separate plugins**:

1. **joget-rule-editor** (UI only, 88KB)
   - RuleEditorElement, RuleEditorResources
   - Static JS/CSS resources
   - Configurable API endpoint property

2. **joget-rules-api** (API, parser, compiler, 496KB)
   - RulesApiProvider - REST endpoints
   - FieldRegistryService, RulesetService
   - Parser, Compiler, Adapters

**API Base URL changed:** `/jw/api/erel/rules/` → `/jw/api/jre/jre/`

Backend modifications (Phases 2+) now target `joget-rules-api` project at:
`/Users/aarelaponin/IdeaProjects/gs-plugins/joget-rules-api`

---

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Multi-select UI | Checkboxes | Most intuitive, easy to use |
| Categories | Dynamic from MDM | Support multiple use cases |
| Data source | FormDataDao | Joget platform API |
| Runtime filtering | Yes | Add filter UI to dictionary panel |
| New MDM | md51FieldCategory | Next available sequence number |

---

## Projects Involved

| Project | Path | Role |
|---------|------|------|
| joget-rule-editor | `/Users/aarelaponin/IdeaProjects/gs-plugins/joget-rule-editor` | Plugin code |
| joget-form-generator | `/Users/aarelaponin/PycharmProjects/dev/joget-form-generator` | Form definitions |
| joget-deployment-toolkit | `/Users/aarelaponin/PycharmProjects/dev/joget-deployment-toolkit` | MDM data files |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  Plugin Configuration Panel: "Configure Editor Plugin"          │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Scope Code: [____________]                   (required)     ││
│  │ Categories: ☑ DEMOGRAPHIC ☑ ECONOMIC ☐ ...  (checkboxes)   ││
│  │ Field Types: ☑ NUMBER ☑ LOOKUP ☐ TEXT       (checkboxes)   ││
│  │ Grid Fields: (○ All  ○ Yes Only  ○ No Only) (radio)        ││
│  │ Lookup Form ID: [____________]               (optional)     ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  Dictionary Panel (with runtime filtering)                       │
│  ┌─────────────────────────────────────────────────────────────┐│
│  │ Category: [All ▼]  Type: [All ▼]  Grid: [All ▼]            ││
│  │ [Search...]                                                  ││
│  │ ─────────────────────────────────────────────────────────── ││
│  │ DEMOGRAPHIC                                                  ││
│  │   age        Age of beneficiary       [number]              ││
│  │   gender     Gender                   [text]                ││
│  └─────────────────────────────────────────────────────────────┘│
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  GET /jw/api/erel/rules/fields                                   │
│  ?scopeCode=X&categories=A,B&fieldTypes=C,D&isGrid=Y            │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  FieldRegistryService (FormDataDao - Joget Platform API)        │
└───────────────────────────────┬─────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│  md51FieldCategory (MDM)  ←──  jreFieldDefinition (Form)        │
└─────────────────────────────────────────────────────────────────┘
```

---

## Phase Overview

| Phase | Project | Description | Status |
|-------|---------|-------------|--------|
| 0 | joget-deployment-toolkit | Create md51FieldCategory MDM | ✅ Complete |
| 1 | joget-rule-editor | Configuration panel enhancement | ✅ Complete |
| 2 | joget-rules-api | Backend filter support | ✅ Complete |
| 3 | joget-rule-editor | Frontend runtime filtering | ✅ Complete |
| 4 | joget-form-generator | Update jreFieldDefinition form | ✅ Complete |
| 5 | joget-rule-editor + joget-rules-api | Testing & documentation | ✅ Complete |

---

## Phase 0: MDM Creation ✅ COMPLETE

### Objective
Create `md51FieldCategory` MDM for dynamic category management.

### Project
`joget-deployment-toolkit` at `/Users/aarelaponin/PycharmProjects/dev/joget-deployment-toolkit`

### Status: ✅ COMPLETED (2025-12-30)

### What Was Done
- Created `md51FieldCategory` MDM entity
- Populated with 8 category records
- Deployed to Joget instance

### Results
- [x] File `md51FieldCategory.csv` created
- [x] Contains 8 category records
- [x] Follows MDM naming convention
- [x] Data populated in Joget

---

## Phase 1: Configuration Panel Enhancement ✅ COMPLETE

### Objective
Rename configuration panel and add filter configuration fields.

### Project
`joget-rule-editor` at `/Users/aarelaponin/IdeaProjects/gs-plugins/joget-rule-editor`

### Status: ✅ COMPLETED (2025-12-30)

### What Was Done
1. ✅ Renamed section "Configure EREL Editor" → "Configure Editor Plugin"
2. ✅ Added "Field Dictionary Filters" section with:
   - filterCategories (checkbox group)
   - filterFieldTypes (checkbox group)
   - filterIsGrid (radio)
   - filterLookupFormId (textfield)
3. ✅ Added "API Configuration" section with:
   - apiEndpoint (configurable, default: /jw/api/jre/jre)
   - apiId, apiKey
4. ✅ Updated RuleEditorElement.java to:
   - Extract filter properties
   - Serialize as filterConfig JSON
   - Make apiEndpoint configurable
5. ✅ Added field count display to dictionary button (e.g., "📖 Fields (105)")
6. ✅ Fixed caching issues with cache-busting version parameter

### Architecture Refactoring (Additional Work)
During Phase 1, the plugin was split into two:
- `joget-rule-editor` - UI only (88KB)
- `joget-rules-api` - API, parser, compiler (496KB)

### Results
- [x] Configuration panel renamed
- [x] Filter fields added to JSON
- [x] Java code updated to extract properties
- [x] Build passes (both plugins)

---

## Phase 2: Backend Filter Support ✅ COMPLETE

### Objective
Enhance FieldRegistryService to support filtering by category, fieldType, isGrid, lookupFormId.

### Project
`joget-rules-api` at `/Users/aarelaponin/IdeaProjects/gs-plugins/joget-rules-api`

### Status: ✅ COMPLETED (2025-12-30)

### What Was Done
1. ✅ Created `FieldFilterCriteria.java` with:
   - Fields: scopeCode, categories, fieldTypes, isGrid, lookupFormId
   - Builder pattern for fluent construction
   - `toCacheKey()` method for cache key generation
   - `equals()/hashCode()` for proper cache keying
2. ✅ Enhanced `FieldRegistryService.java`:
   - Added `getFieldsForScope(FieldFilterCriteria)` method
   - Builds dynamic WHERE clause with optional filters
   - Updated cache to use criteria-based keys via `cacheExpiryMap`
   - Kept backward compatibility (old method delegates to new)
   - Added `getCategories()` method to load from md51FieldCategory MDM
3. ✅ Updated `RulesApiProvider.java`:
   - Modified `/jre/fields` endpoint to accept query params: categories, fieldTypes, isGrid, lookupFormId
   - Added new `/jre/categories` endpoint returning available categories
   - Added `parseCommaSeparated()` helper method

### Files Created
- `src/main/java/global/govstack/rulesapi/service/FieldFilterCriteria.java`

### Files Modified
- `src/main/java/global/govstack/rulesapi/service/FieldRegistryService.java`
- `src/main/java/global/govstack/rulesapi/lib/RulesApiProvider.java`

### Results
- [x] FieldFilterCriteria class created
- [x] FieldRegistryService enhanced with filter support
- [x] /jre/fields endpoint accepts filter params
- [x] /jre/categories endpoint added
- [x] Build succeeds

---

## Phase 3: Frontend Runtime Filtering ✅ COMPLETE

### Objective
Add runtime filter dropdowns to dictionary panel in the editor UI.

### Project
`joget-rule-editor` at `/Users/aarelaponin/IdeaProjects/gs-plugins/joget-rule-editor`

### Status: ✅ COMPLETED (2025-12-30)

### What Was Done
1. ✅ Updated `RuleEditorElement.ftl`:
   - Pass `filterConfig` object to `JREEditor.init()` from form properties
   - Bumped cache version to force reload

2. ✅ Updated `jre-editor.js`:
   - Added `filterConfig` to defaults with categories, fieldTypes, isGrid, lookupFormId
   - Added runtime filter state tracking (`runtimeFilters`)
   - Added three filter dropdowns to dictionary panel: Category, Type, Grid
   - Implemented `loadCategories()` to populate Category dropdown from `/categories` API
   - Enhanced `loadFieldDictionary()` to include form-level filter params in API call
   - Implemented `applyRuntimeFilters()` for client-side filtering
   - Updated `filterDictionary()` to combine search with runtime filters
   - Added data attributes to field elements for filtering: data-category, data-type, data-grid
   - Type dropdown auto-populated from field data

3. ✅ Updated `jre-editor.css`:
   - Added `.jre-filter-row` flexbox layout for dropdowns
   - Styled filter dropdowns with custom arrow icon
   - Added hover and focus states
   - Added responsive styles for smaller screens (768px, 480px breakpoints)

### Files Modified
- `src/main/resources/templates/RuleEditorElement.ftl`
- `src/main/resources/static/jre-editor.js`
- `src/main/resources/static/jre-editor.css`

### Results
- [x] Filter config passed from template to JS
- [x] Filter dropdowns added to dictionary panel
- [x] Client-side filtering works
- [x] API calls include filter params
- [x] Consistent styling
- [x] Build succeeds (90KB JAR)

### Prompt for Phase 3
```
I need to add runtime filtering UI to the Rule Editor dictionary panel.

Project: /Users/aarelaponin/IdeaProjects/gs-plugins/joget-rule-editor

Context: This is Phase 3 of integrating jreFieldDefinition. See docs/INTEGRATION-PLAN-JREFIELDEFINITION.md for full plan.

Tasks:
1. Update RuleEditorElement.ftl:
   - Pass filterConfig object to JREEditor.init() with categories, fieldTypes, isGrid, lookupFormId from form properties

2. Update jre-editor.js:
   - Add filter dropdowns to dictionary panel header: Category [All▼], Type [All▼], Grid [All▼]
   - Load categories from new /categories endpoint for dropdown options
   - Include form-level filter params in /fields API call
   - Implement client-side filtering when runtime dropdowns change
   - Preserve existing search functionality alongside new filters

3. Update jre-editor.css:
   - Style filter dropdowns to match existing UI
   - Compact layout for filter row
   - Responsive design for smaller screens

Build: mvn clean package

Report:
- Summary of UI changes
- Screenshot description of new filter UI
- Any UX considerations
- Build result
```

### Expected Results
- [ ] Filter config passed from template to JS
- [ ] Filter dropdowns added to dictionary panel
- [ ] Client-side filtering works
- [ ] API calls include filter params
- [ ] Consistent styling
- [ ] Build succeeds

---

## Phase 4: Form Update

### Objective
Update jreFieldDefinition form to use md51FieldCategory MDM for category field.

### Project
`joget-form-generator` at `/Users/aarelaponin/PycharmProjects/dev/joget-form-generator`

### Files to Modify
- `specs/jre/output/jreFieldDefinition.json`

### Changes Required
1. Change `category` field from selectbox with hardcoded options to lookup from `md51FieldCategory`

### Prompt for Phase 4
```
I need to update the jreFieldDefinition form to use MDM for categories.

Project: /Users/aarelaponin/PycharmProjects/dev/joget-form-generator

Context: This is Phase 4 of integrating jreFieldDefinition. See joget-rule-editor/docs/INTEGRATION-PLAN-JREFIELDEFINITION.md for full plan.

Tasks:
1. In specs/jre/output/jreFieldDefinition.json:
   - Find the `category` field (currently a selectbox with hardcoded options)
   - Change it to use md51FieldCategory as the data source
   - The lookup should use 'code' as value and 'name' as label
   - Ensure validation still works (mandatory field)

Report:
- Previous category field configuration
- New category field configuration
- Any other changes needed for the lookup to work
```

### Expected Results
- [ ] Category field changed to MDM lookup
- [ ] Form still validates correctly
- [ ] Existing data remains compatible

---

## Phase 5: Testing & Documentation

### Objective
Verify integration works end-to-end, update tests and documentation.

### Project
`joget-rule-editor` at `/Users/aarelaponin/IdeaProjects/gs-plugins/joget-rule-editor`

### Files to Modify
- `src/test/java/.../FieldRegistryServiceTest.java` (if exists)
- `CLAUDE.md`
- This file (update status)

### Tasks
1. Add/update unit tests for FieldRegistryService filter scenarios
2. Test filter combinations manually
3. Verify backward compatibility
4. Update CLAUDE.md with new configuration options
5. Update phase status in this document

### Prompt for Phase 5
```
I need to complete testing and documentation for the jreFieldDefinition integration.

Project: /Users/aarelaponin/IdeaProjects/gs-plugins/joget-rule-editor

Context: This is Phase 5 (final) of integrating jreFieldDefinition. See docs/INTEGRATION-PLAN-JREFIELDEFINITION.md for full plan.

Tasks:
1. Review and update tests:
   - Check if FieldRegistryServiceTest exists
   - Add tests for filter scenarios: single filter, multiple filters, empty filters
   - Ensure backward compatibility tests

2. Run all tests: mvn test

3. Update CLAUDE.md:
   - Add section about filter configuration options
   - Document new /categories endpoint
   - Document filter query parameters for /fields endpoint

4. Update docs/INTEGRATION-PLAN-JREFIELDEFINITION.md:
   - Mark all phases as complete
   - Add completion date
   - Note any deviations from original plan

Report:
- Test results
- Documentation updates made
- Any issues found during testing
- Final integration status
```

### Expected Results
- [ ] All tests pass
- [ ] CLAUDE.md updated
- [ ] Integration plan marked complete
- [ ] No regressions

---

## API Reference (Final State)

### GET /jw/api/jre/jre/fields
```
Query Parameters:
  scopeCode     (required)  - Field scope code
  categories    (optional)  - Comma-separated category codes
  fieldTypes    (optional)  - Comma-separated field types
  isGrid        (optional)  - Y/N filter for grid fields
  lookupFormId  (optional)  - Filter by lookup form ID

Response:
{
  "scopeCode": "FARMER_ELIGIBILITY",
  "count": 105,
  "categories": [
    {
      "category": "DEMOGRAPHIC",
      "fields": [
        {
          "fieldId": "age",
          "fieldLabel": "Age",
          "fieldType": "NUMBER",
          "isGrid": false,
          ...
        }
      ]
    }
  ]
}
```

### GET /jw/api/jre/jre/categories
```
Response:
[
  {"code": "DEMOGRAPHIC", "name": "Demographic"},
  {"code": "ECONOMIC", "name": "Economic"},
  ...
]
```

---

## Completion Tracking

| Phase | Started | Completed | Notes |
|-------|---------|-----------|-------|
| 0 | 2025-12-29 | 2025-12-30 | md51FieldCategory MDM created and populated |
| 1 | 2025-12-29 | 2025-12-30 | Config panel + architecture refactoring (split into 2 plugins) |
| 2 | 2025-12-30 | 2025-12-30 | FieldFilterCriteria, enhanced FieldRegistryService, /fields filter params, /categories endpoint |
| 3 | 2025-12-30 | 2025-12-30 | Runtime filter dropdowns, client-side filtering, API filter params |
| 4 | 2025-12-30 | 2025-12-30 | jreFieldDefinition form updated manually to use md51FieldCategory MDM |
| 5 | 2025-12-30 | 2025-12-30 | Documentation updated (CLAUDE.md, integration plan) |

**Overall Status**: ✅ Complete
**Completion Date**: 2025-12-30

---

## Architecture Change Log

| Date | Change |
|------|--------|
| 2025-12-30 | Split joget-rule-editor into joget-rule-editor (UI) and joget-rules-api (backend) |
| 2025-12-30 | Changed API base from /jw/api/erel/rules to /jw/api/jre/jre |
| 2025-12-30 | Added configurable apiEndpoint property to RuleEditorElement |
| 2025-12-30 | Added field count display to dictionary button |
