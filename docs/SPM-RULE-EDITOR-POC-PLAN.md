# SPM Rule Editor Integration - End-to-End PoC Plan

**Version:** 3.0 (REVISED - 6-Project Ecosystem)  
**Date:** 2025-01-28  
**Objective:** Complete end-to-end proof-of-concept for Rule Editor integration with Subsidy Program Management

---

## The Context

**Project:** Farmers' Registry System (FRS) on Joget DX Enterprise Edition

**Architecture Reference:** This document contains the complete 6-project ecosystem architecture in Section 1.

**The 6 Projects:**

| Project | Type | Location | Primary Responsibility |
|---------|------|----------|------------------------|
| `joget-form-generator` | Python | `PycharmProjects/dev/joget-form-generator` | Form specs (YAML), master data (CSV), form generation |
| `joget-deployment-toolkit` | Python | `PycharmProjects/dev/joget-deployment-toolkit` | Deployment, data sync, field dictionary generation |
| `joget-instance-manager` | Python | `PycharmProjects/dev/joget-instance-manager` | Instance setup, reset, verification, health checks |
| `form-creator-api` | Java/Joget | `IdeaProjects/gs-plugins/form-creator-api` | REST API for programmatic form creation |
| `joget-rule-editor` | Java/Joget | `IdeaProjects/gs-plugins/joget-rule-editor` | Joget plugin (UI, REST API, services) |
| `rules-grammar` | Java | `IdeaProjects/gs-plugins/rules-grammar` | Rule language grammar, parser (ANTLR4) ✅ COMPLETE |

**Key Principle:** Single source of truth
- Form specs: `joget-form-generator/specs/*/input/*.yaml`
- Master data: `joget-form-generator/specs/*/data/*.csv`
- Grammar: `rules-grammar/` (✅ Already implemented with ANTLR4)
- Instance config: `~/.joget/instances.yaml`

**Key Directories:**
- Form Generator Specs: `/Users/aarelaponin/PycharmProjects/dev/joget-form-generator/specs/`
- Deployment Toolkit: `/Users/aarelaponin/PycharmProjects/dev/joget-deployment-toolkit/components/`
- Instance Manager: `/Users/aarelaponin/PycharmProjects/dev/joget-instance-manager/`
- Form Creator API: `/Users/aarelaponin/IdeaProjects/gs-plugins/form-creator-api/`
- Rule Editor Plugin: `/Users/aarelaponin/IdeaProjects/gs-plugins/joget-rule-editor/`
- Rules Grammar: `/Users/aarelaponin/IdeaProjects/gs-plugins/rules-grammar/`

**Project Knowledge Documents:**
- `farmer-form-metadata.docx` - Farmer form field definitions
- `uc3-desc.docx` - Eligibility criteria design with rule examples
- `validation-rules.yaml` - Field types and validations
- `f01-main.json` through `f01_07.json` - Actual farmer form definitions

---

## 1. Ecosystem Architecture

### 1.1 The 6-Project Ecosystem

#### Project 1: joget-form-generator (Python)
**Location:** `/Users/aarelaponin/PycharmProjects/dev/joget-form-generator`

**Responsibility:** 
- **Sole authority** for generating Joget form JSON definitions
- Source of truth for YAML form specifications
- Source of truth for CSV master data files
- Quality and correctness of all Joget forms

**Key Directories:**
```
specs/
├── jre/                        # JRE-specific specs
│   ├── input/*.yaml            # Form YAML definitions (SOURCE OF TRUTH)
│   ├── data/*.csv              # Master data (SOURCE OF TRUTH)
│   └── output/*.json           # Generated form JSONs
├── spm/                        # SPM module specs
├── existing-farmer/            # Farmer form specs
└── archive/erel/               # Archived EREL specs
```

**Workflow:** YAML specs → joget-form-generator → JSON forms

---

#### Project 2: joget-deployment-toolkit (Python)
**Location:** `/Users/aarelaponin/PycharmProjects/dev/joget-deployment-toolkit`

**Responsibility:**
- Deploy forms and data to Joget instances
- **Field dictionary generation** (transforms farmer metadata → structured field definitions)
- Connection to Joget instances (read/write)
- Deployment artifacts storage
- **Uses form-creator-api plugin** for REST-based form deployment

**Note:** This project handles data transformation tasks that bridge specifications and deployment.

**Key Directories:**
```
components/
├── jre/                        # JRE deployment artifacts
│   ├── forms/*.json            # Copied from specs/jre/output/
│   ├── data/*.csv              # Copied from specs/jre/data/
│   ├── scripts/                # Deployment + generation scripts
│   │   ├── deploy_jre_forms.py
│   │   ├── populate_field_dictionary.py
│   │   └── generate_field_dictionary.py  # Generates from farmer metadata
│   └── archive/erel/           # Archived EREL components
├── spm/                        # SPM deployment artifacts
├── farmer/                     # Farmer form artifacts
└── mdm/                        # Master data artifacts
```

---

#### Project 3: joget-instance-manager (Python)
**Location:** `/Users/aarelaponin/PycharmProjects/dev/joget-instance-manager`

**Responsibility:**
- **Instance Configuration Layer** - Low-level Joget instance management
- Tomcat ports, datasource files, Glowroot APM configuration
- MySQL database creation, user management, schema import
- Instance lifecycle: setup, reset, verification, health checks
- Multi-instance coordination for development environments
- **Manages `~/.joget/instances.yaml`** - Single source of truth for instance configuration

**Key Tools:**
- `joget_instance_manager.py` - Primary tool for setup/reset/configure
- `bootstrap_joget_instance.py` - Bootstrap formCreator for API deployments
- `health_check.py` - Instance health monitoring

**Instance Architecture:**
```
Instance   Port    Purpose
────────   ────    ───────
jdx1       8081    Production
jdx2       8082    Staging
jdx3       8083    Development
jdx4       8084    Client Alpha (SPM PoC target)
jdx5       8085    Client Beta
jdx6       8086    Sandbox
```

**Relationship to Other Projects:**
- Writes/reads `~/.joget/instances.yaml` (shared configuration)
- `joget-deployment-toolkit` reads this config to know where to deploy

---

#### Project 4: form-creator-api (Java/Joget Plugin)
**Location:** `/Users/aarelaponin/IdeaProjects/gs-plugins/form-creator-api`

**Responsibility:**
- **REST API for programmatic form creation** in Joget
- Creates forms, API endpoints, and CRUD interfaces via HTTP
- **Provides services to joget-deployment-toolkit**
- Solves bootstrap paradox (formCreator form needed for API deployments)

**Package:** `global.govstack.formcreator`

**Key API:**
```
POST /jw/api/formcreator/formcreator/forms
  - formId, formName, tableName, formDefinition
  - createApiEndpoint: true/false
  - createCrud: true/false
```

**Services:**
- `FormCreationService` - Main orchestrator
- `FormDatabaseService` - Form registration with cache invalidation
- `ApiBuilderService` - Creates API endpoints
- `CrudService` - Creates datalist + userview

---

#### Project 5: joget-rule-editor (Java/Joget Plugin)
**Location:** `/Users/aarelaponin/IdeaProjects/gs-plugins/joget-rule-editor`

**Responsibility:**
- Joget plugin implementation (form element, REST API)
- UI components (CodeMirror editor, field dictionary panel)
- Service layer (FieldRegistryService, RulesetService)
- SQL Compiler (transforms AST to SQL WHERE clauses)
- **Depends on** `rules-grammar` library for parsing

**Package:** `global.govstack.ruleeditor`

**Key Structure:**
```
src/main/java/global/govstack/ruleeditor/
├── element/                    # Joget form element
│   ├── RuleEditorElement.java
│   └── RuleEditorResources.java
├── lib/                        # REST API
│   └── RulesServiceProvider.java
├── service/                    # Business logic
│   ├── FieldRegistryService.java
│   └── RulesetService.java
├── adapter/                    # Integration with grammar project
├── compiler/                   # SQL compilation (uses grammar)
└── model/                      # Data models

src/main/resources/
├── static/                     # JS/CSS assets
│   ├── jre-editor.js
│   ├── jre-editor.css
│   ├── jre-mode.js             # CodeMirror syntax highlighting
│   └── codemirror.min.*
└── templates/
    └── RuleEditorElement.ftl

docs/
├── SPM-RULE-EDITOR-POC-PLAN.md # THIS DOCUMENT
└── PHASE-*-COMPLETE.md
```

---

#### Project 6: rules-grammar (Java Library) ✅ COMPLETE
**Location:** `/Users/aarelaponin/IdeaProjects/gs-plugins/rules-grammar`

**Status:** ✅ **IMPLEMENTED** with comprehensive test coverage

**Responsibility:**
- ANTLR4 grammar definition for Rules Script DSL
- Lexer and Parser implementation
- AST (Abstract Syntax Tree) models
- **Independent from business projects** - enables grammar evolution without affecting consumers

**Package:** `global.govstack.rules.grammar`

**Structure:**
```
rules-grammar/
├── src/main/antlr4/global/govstack/rules/grammar/
│   ├── RulesScriptLexer.g4       # Lexer grammar
│   └── RulesScriptParser.g4      # Parser grammar
├── src/main/java/global/govstack/rules/grammar/
│   ├── RulesScript.java          # Main entry point
│   ├── RulesScriptAstBuilder.java # AST construction
│   ├── ParseResult.java          # Parse result wrapper
│   ├── ParseError.java           # Error representation
│   └── model/                    # AST node types
│       ├── Script.java           # Root AST node
│       ├── Rule.java             # Rule definition
│       ├── Condition.java        # Condition variants (sealed)
│       ├── FieldRef.java         # Field reference
│       ├── Value.java            # Value types (sealed)
│       ├── RuleType.java         # INCLUSION/EXCLUSION/PRIORITY/BONUS
│       └── ComparisonOperator.java
├── src/test/java/.../            # Comprehensive test suite
├── CHANGELOG.md
├── DEVELOPER.md
├── README.md
└── pom.xml
```

**Key API:**
```java
// Main parsing API
ParseResult<Script> result = RulesScript.parse(scriptText);

if (result.isSuccess()) {
    Script script = result.getValue();
    // Process AST
} else {
    List<ParseError> errors = result.getErrors();
    // Handle errors
}
```

**AST Model Classes:**
- `Condition` - Sealed interface with variants: SimpleComparison, Between, In, NotIn, IsEmpty, And, Or, Not, GridCheck, Aggregation
- `Value` - Sealed interface with variants: StringValue, NumberValue, BooleanValue, IdentifierValue

---

### 1.2 Complete Data Flow Architecture

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           COMPLETE ECOSYSTEM                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │               SPECIFICATIONS (Source of Truth)                          │ │
│  │                 joget-form-generator/specs/                             │ │
│  ├────────────────────────────────────────────────────────────────────────┤ │
│  │  jre/input/*.yaml     →  Form definitions                               │ │
│  │  jre/data/*.csv       →  Master data (SINGLE SOURCE)                    │ │
│  │  jre/output/*.json    →  Generated forms                                │ │
│  └───────────────────────────────┬────────────────────────────────────────┘ │
│                                  │                                           │
│                                  │ joget-form-generator                      │
│                                  │ (generates JSON from YAML)                │
│                                  ▼                                           │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │               DEPLOYMENT ARTIFACTS                                      │ │
│  │            joget-deployment-toolkit/components/                         │ │
│  ├────────────────────────────────────────────────────────────────────────┤ │
│  │  jre/forms/*.json     ←  Copied from specs/jre/output/                  │ │
│  │  jre/data/*.csv       ←  Copied from specs/jre/data/                    │ │
│  │  jre/scripts/         →  Deployment & generation utilities              │ │
│  │                                                                          │ │
│  │  generate_field_dictionary.py:                                          │ │
│  │    - Reads: farmer form metadata, validation-rules.yaml                 │ │
│  │    - Writes: specs/jre/data/jreFieldDefinition.csv                      │ │
│  └───────────────────────────────┬────────────────────────────────────────┘ │
│                                  │                                           │
│                                  │ deployment scripts                        │
│                                  │ (uses form-creator-api REST endpoints)    │
│                                  ▼                                           │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    INSTANCE LAYER                                       │ │
│  │                 joget-instance-manager                                  │ │
│  ├────────────────────────────────────────────────────────────────────────┤ │
│  │  ~/.joget/instances.yaml  →  Instance configuration (shared)            │ │
│  │  Scripts: setup, reset, verify, health-check                            │ │
│  │  Bootstrap: formCreator form for API deployments                        │ │
│  └───────────────────────────────┬────────────────────────────────────────┘ │
│                                  │                                           │
│                                  │ configures                                │
│                                  ▼                                           │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    JOGET INSTANCE (jdx4)                                │ │
│  ├────────────────────────────────────────────────────────────────────────┤ │
│  │  Database Tables:                                                        │ │
│  │    app_fd_jreFieldScope       →  Field scope configuration              │ │
│  │    app_fd_jreFieldDefinition  →  Field dictionary (~80 fields)          │ │
│  │    app_fd_jreRuleset          →  Stored rule scripts                    │ │
│  │                                                                          │ │
│  │  Deployed Plugins:                                                       │ │
│  │    form-creator-api           →  REST API for form deployment           │ │
│  │    joget-rule-editor          →  Rule editor form element               │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
│                                                                              │
│  ┌────────────────────────────────────────────────────────────────────────┐ │
│  │                    RUNTIME COMPONENTS                                   │ │
│  ├────────────────────────────────────────────────────────────────────────┤ │
│  │                                                                          │ │
│  │  ┌─────────────────────┐      ┌─────────────────────┐                   │ │
│  │  │   rules-grammar     │      │  joget-rule-editor  │                   │ │
│  │  │   (Java Library)    │◄─────│     (Joget Plugin)  │                   │ │
│  │  ├─────────────────────┤      ├─────────────────────┤                   │ │
│  │  │ • ANTLR4 Grammar    │      │ • Form Element UI   │                   │ │
│  │  │ • Lexer/Parser      │      │ • REST API          │                   │ │
│  │  │ • AST models        │      │ • Services          │                   │ │
│  │  │ • ParseResult API   │      │ • SQL Compiler      │                   │ │
│  │  └─────────────────────┘      └─────────────────────┘                   │ │
│  │                                                                          │ │
│  │  ┌─────────────────────┐                                                │ │
│  │  │  form-creator-api   │                                                │ │
│  │  │   (Joget Plugin)    │                                                │ │
│  │  ├─────────────────────┤                                                │ │
│  │  │ • Form creation API │                                                │ │
│  │  │ • CRUD generation   │                                                │ │
│  │  │ • API endpoint gen  │                                                │ │
│  │  └─────────────────────┘                                                │ │
│  │                                                                          │ │
│  └────────────────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### 1.3 Project Dependencies Summary

```
┌─────────────────────────────────────────────────────────────────────┐
│                     PROJECT DEPENDENCIES                             │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  Python Projects:                                                    │
│  ───────────────                                                     │
│  joget-form-generator                                                │
│       │                                                              │
│       │ generates forms for                                          │
│       ▼                                                              │
│  joget-deployment-toolkit ──────► form-creator-api (REST)            │
│       │                                                              │
│       │ reads instance config from                                   │
│       ▼                                                              │
│  joget-instance-manager                                              │
│       │                                                              │
│       │ manages                                                      │
│       ▼                                                              │
│  ~/.joget/instances.yaml                                             │
│                                                                      │
│  Java Projects:                                                      │
│  ─────────────                                                       │
│  rules-grammar (library) ✅ COMPLETE                                 │
│       │                                                              │
│       │ provides parsing for                                         │
│       ▼                                                              │
│  joget-rule-editor (plugin)                                          │
│                                                                      │
│  form-creator-api (plugin) ◄────── joget-deployment-toolkit          │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 2. Executive Summary

This plan defines the steps to complete the integration of the **Joget Rule Editor** plugin with the **Subsidy Program Management (SPM)** module. The PoC will demonstrate:

1. Program officer creates a subsidy program
2. Defines eligibility rules using the Rule Editor
3. Rules validate against the farmer field dictionary
4. Rules are saved with the program
5. Rules can be tested against farmer data

---

## 3. Current State Assessment

### 3.1 What Exists ✅

| Component | Location | Status |
|-----------|----------|--------|
| **Rules Grammar Library** | `gs-plugins/rules-grammar/` | ✅ Complete with tests |
| ANTLR4 Grammar | `RulesScriptLexer.g4`, `RulesScriptParser.g4` | ✅ Complete |
| AST Models | `model/` package | ✅ Complete (sealed classes) |
| Parse API | `RulesScript.parse()` → `ParseResult<Script>` | ✅ Complete |
| **Rule Editor Plugin** | `gs-plugins/joget-rule-editor/` | Working |
| Maven Dependency | pom.xml → rules-grammar | ✅ Complete |
| Adapter Layer | `adapter/` package | ✅ Complete (RuleAdapter, ConditionAdapter, ValueAdapter) |
| Parser Integration | `RuleScriptParser.java` uses `RulesScript.parse()` | ✅ Complete |
| Form Element | `RuleEditorElement.java` | Working |
| REST API | `RulesServiceProvider.java` | Working |
| Editor UI | `jre-editor.js`, `jre-editor.css` | Working |
| CodeMirror Mode | `jre-mode.js` | Working |
| **Form Generator Specs** | `joget-form-generator/specs/jre/` | Created (Phase 0) |
| YAML specs | `specs/jre/input/jre-*.yaml` | Complete |
| Sample field data | `specs/jre/data/jreFieldScope.csv` | Created |
| **Deployment Toolkit** | `joget-deployment-toolkit/components/jre/` | Structure created |
| **Instance Manager** | `joget-instance-manager/` | Available |
| **Form Creator API** | `gs-plugins/form-creator-api/` | Available |

### 3.2 What's Missing ❌

| Gap | Description | Responsible Project |
|-----|-------------|---------------------|
| **Form JSON generation** | YAMLs exist but JSONs not generated | joget-form-generator |
| **Field dictionary** | Only scope CSV, need ~80 field definitions | joget-deployment-toolkit |
| **Database tables** | Forms not deployed to Joget instance | joget-deployment-toolkit |
| **SPM integration** | Rule Editor not embedded in Program Design form | joget-rule-editor |
| **Test functionality** | Tab 3 "Test & Validate" not implemented | joget-rule-editor |

### 3.3 Key Design Decisions (Already Made)

1. **Plugin handles all technical complexity** - SPM just configures and uses
2. **Script as source of truth** - Raw text stored, parsed at runtime
3. **Full field IDs** - No mapping, `householdMembers.disability` used directly
4. **Metadata-driven** - Field dictionary configurable per scope
5. **Grammar is independent** - Can evolve without changing business projects
6. **rules-grammar provides parsing** - ✅ Integration complete, joget-rule-editor uses it as a dependency
7. **Adapter pattern for compatibility** - ✅ Grammar AST adapted to existing model classes

---

## 4. PoC Scope Definition

### 4.1 In Scope

| Feature | Responsible Project |
|---------|---------------------|
| Integrate with rules-grammar library | joget-rule-editor |
| Generate JRE form JSONs | joget-form-generator |
| Generate field dictionary (~80 fields) | joget-deployment-toolkit |
| Deploy forms and data to Joget | joget-deployment-toolkit |
| Update plugin services to use database | joget-rule-editor |
| Embed Rule Editor in SPM form | joget-rule-editor |
| Basic rule testing | joget-rule-editor |

### 4.2 Out of Scope (v1)

| Feature | Reason |
|---------|--------|
| Rule versioning/publishing | PoC uses DRAFT only |
| Filtered aggregations (`COUNT WHERE`) | v1 grammar feature |
| Row access (`grid[N]`) | v1 grammar feature |
| Full eligibility engine | Separate module |
| Bulk farmer evaluation | Performance optimization phase |

### 4.3 Success Criteria

| # | Criterion | Verification |
|---|-----------|--------------|
| 1 | Rule Editor displays in Program Design form | Visual inspection |
| 2 | Field dictionary shows all farmer fields | Count ≥ 80 fields |
| 3 | All uc3-desc.docx example rules validate | No syntax errors |
| 4 | Rules save with program | Database record exists |
| 5 | Rules load when editing program | Editor pre-populated |
| 6 | Invalid field shows error | Unknown field warning |
| 7 | Test returns farmer count | Number displayed |

---

## 5. Step-by-Step Implementation Plan

### Phase 0: Preparation & Cleanup ✅ COMPLETE

**Status:** Completed 2025-01-27  
**Documentation:** `PHASE-0-COMPLETE.md`

**Projects Involved:** All

**Completed:**
- [x] Created JRE directory structure in joget-form-generator
- [x] Created JRE directory structure in joget-deployment-toolkit
- [x] Archived old EREL content
- [x] Created YAML form specs (jre-field-scope.yaml, etc.)
- [x] Created jreFieldScope.csv in specs/jre/data/

**Structure Verified:**
- `specs/jre/data/` contains source CSVs (single source of truth)
- `components/jre/data/` is empty (will receive copies during deployment)
- `components/jre/forms/` is empty (will receive generated JSONs)

---

### Phase 1: Field Dictionary Generation (2-3 hours)

**Responsible Project:** `joget-deployment-toolkit`

**Objective:** Generate comprehensive field dictionary (~80 fields) from farmer form metadata.

#### Step 1.1: Analyze Farmer Form Metadata

**Input Sources (from Project Knowledge):**
- `farmer-form-metadata.docx` - Field definitions by form
- `validation-rules.yaml` - Field types and validations
- `f01-main.json` through `f01_07.json` - Actual form definitions

**Target Categories:**
| Category | Source Forms | Expected Fields |
|----------|--------------|-----------------|
| DEMOGRAPHIC | farmerBasicInfo | ~8 fields |
| LOCATION | farmerLocation | ~6 fields |
| AGRICULTURAL | farmerAgriculture, farmerCropsLivestock | ~15 fields |
| HOUSEHOLD | farmerHousehold, householdMemberForm | ~12 fields (incl. grid) |
| ECONOMIC | farmerEconomic | ~8 fields |
| CROPS | cropManagementForm | ~8 fields (grid) |
| LIVESTOCK | livestockDetailsForm | ~6 fields (grid) |
| DERIVED | spFarmerDerived (computed) | ~15 fields |
| PROGRAM | programParticipation | ~5 fields |

#### Step 1.2: Create Field Dictionary Generation Script

**Location:** `joget-deployment-toolkit/components/jre/scripts/generate_field_dictionary.py`

**Inputs:** 
- Project Knowledge: farmer-form-metadata.docx, validation-rules.yaml, f01-*.json

**Output:**
- `joget-form-generator/specs/jre/data/jreFieldDefinition.csv` (SOURCE OF TRUTH)

**Note:** The generated CSV goes to `specs/jre/data/` because that's the source of truth for all master data. It will be copied to `components/jre/data/` during deployment sync.

#### Step 1.3: Define Field Dictionary Schema

CSV columns (matching jre-field-definition.yaml):
```
id,scopeCode,fieldId,fieldLabel,category,fieldType,applicableOperators,isGrid,gridParentField,aggregationFunctions,lookupFormId,lookupValues,helpText,displayOrder,isActive
```

#### Step 1.4: Generate Field Dictionary

```bash
cd joget-deployment-toolkit
python -m components.jre.scripts.generate_field_dictionary \
    --metadata-doc /path/to/farmer-form-metadata.docx \
    --forms-dir components/farmer/ \
    --output ~/PycharmProjects/dev/joget-form-generator/specs/jre/data/jreFieldDefinition.csv
```

#### Step 1.5: Review and Adjust

Manual review of generated CSV:
- Verify all categories present
- Check operator assignments
- Confirm grid relationships
- Add any missing derived fields

**Deliverables:**
- [ ] `generate_field_dictionary.py` script in joget-deployment-toolkit
- [ ] `jreFieldDefinition.csv` (~80 fields) in joget-form-generator/specs/jre/data/
- [ ] `PHASE-1-COMPLETE.md` documentation

---

### Phase 2: Form Definition Updates & Generation (1-2 hours)

**Responsible Project:** `joget-form-generator`

**Objective:** Generate Joget form JSONs from YAML specifications.

#### Step 2.1: Review/Update YAML Specs

**Location:** `joget-form-generator/specs/jre/input/`

Files to verify:
- `jre-field-scope.yaml`
- `jre-field-definition.yaml`
- `jre-ruleset.yaml`

#### Step 2.2: Generate JRE Forms

```bash
cd joget-form-generator
python -m src.generate specs/jre/input/jre-field-scope.yaml -o specs/jre/output/
python -m src.generate specs/jre/input/jre-field-definition.yaml -o specs/jre/output/
python -m src.generate specs/jre/input/jre-ruleset.yaml -o specs/jre/output/
```

#### Step 2.3: Verify Generated Forms

Check that generated JSONs have correct:
- Form IDs: `jreFieldScope`, `jreFieldDefinition`, `jreRuleset`
- Table names: `jreFieldScope`, `jreFieldDefinition`, `jreRuleset`
- Field mappings

#### Step 2.4: Sync to Deployment Toolkit

```bash
cd joget-deployment-toolkit
python -m src.sync_artifacts \
    --source ~/PycharmProjects/dev/joget-form-generator/specs/jre/output \
    --dest components/jre/forms

python -m src.sync_artifacts \
    --source ~/PycharmProjects/dev/joget-form-generator/specs/jre/data \
    --dest components/jre/data
```

**Deliverables:**
- [ ] Updated YAML specs (if needed)
- [ ] `jreFieldScope.json` in specs/jre/output/
- [ ] `jreFieldDefinition.json` in specs/jre/output/
- [ ] `jreRuleset.json` in specs/jre/output/
- [ ] Forms and data synced to deployment toolkit
- [ ] `PHASE-2-COMPLETE.md` documentation

---

### Phase 3: Grammar Integration Review ✅ COMPLETE

**Responsible Project:** `rules-grammar` + `joget-rule-editor`

**Status:** ✅ **ALREADY COMPLETE**

**What Was Done:**
- ✅ `rules-grammar` project complete with ANTLR4 grammar and comprehensive tests
- ✅ Maven dependency added to `joget-rule-editor/pom.xml`
- ✅ OSGi bundle embedding configured for `rules-grammar` and `antlr4-runtime`
- ✅ Adapter layer created: `RuleAdapter`, `ConditionAdapter`, `ValueAdapter`
- ✅ `RuleScriptParser.java` updated to use `RulesScript.parse()` API
- ✅ Backwards compatibility maintained with existing model classes

**Integration Details:**

The `joget-rule-editor` plugin now uses `rules-grammar` for all parsing:

```java
// In RuleScriptParser.java
ParseResult parseResult = RulesScript.parse(script);

// Convert to existing models via adapters
for (global.govstack.rules.grammar.model.Rule grammarRule : parseResult.script().rules()) {
    Rule oldRule = RuleAdapter.toOldModel(grammarRule);
    result.addRule(oldRule);
}
```

**Remaining Work (Documentation Only):**
- [ ] Create `GRAMMAR-REFERENCE.md` documenting the DSL syntax
- [ ] Document available operators, functions, and examples

**Deliverables:**
- [x] Maven dependency added
- [x] Adapter layer created
- [x] Parser integration complete
- [ ] `GRAMMAR-REFERENCE.md` documentation (optional)
- [ ] `PHASE-3-COMPLETE.md` documentation

---

### Phase 4: Database Deployment (1-2 hours)

**Responsible Projects:** `joget-deployment-toolkit` + `joget-instance-manager`

**Objective:** Deploy JRE forms and data to Joget instance.

#### Step 4.1: Verify Instance Configuration

**Check:** `~/.joget/instances.yaml`

Ensure jdx4 configuration exists:
```yaml
instances:
  jdx4:
    port: 8084
    app: spmRegistry
    api_id: API-xxx
    api_key: xxx
```

**If needed, use joget-instance-manager:**
```bash
cd joget-instance-manager
python joget_instance_manager.py setup jdx4
```

#### Step 4.2: Ensure form-creator-api Plugin Deployed

**Check:** Plugin is installed in jdx4 instance

**If needed, bootstrap:**
```bash
cd joget-instance-manager
python bootstrap_joget_instance.py jdx4
```

#### Step 4.3: Deploy Forms via form-creator-api

**Location:** `joget-deployment-toolkit/components/jre/scripts/`

```bash
cd joget-deployment-toolkit
python -m components.jre.scripts.deploy_jre_forms \
    --instance jdx4 \
    --app spmRegistry
```

**Expected Results:**
- 3 forms created in App Composer
- 3 database tables (`app_fd_jre*`)

#### Step 4.4: Populate Field Scope Data

```bash
python -m components.jre.scripts.populate_field_dictionary \
    --instance jdx4 \
    --app spmRegistry \
    --form jreFieldScope \
    --data components/jre/data/jreFieldScope.csv
```

#### Step 4.5: Populate Field Dictionary

```bash
python -m components.jre.scripts.populate_field_dictionary \
    --instance jdx4 \
    --app spmRegistry \
    --form jreFieldDefinition \
    --data components/jre/data/jreFieldDefinition.csv
```

#### Step 4.6: Verify Deployment

**Checklist:**
- [ ] Forms visible in App Composer
- [ ] Tables exist in database
- [ ] Field scope record exists: `FARMER_ELIGIBILITY`
- [ ] ~80 field definitions loaded
- [ ] API returns 200:
  ```bash
  curl http://localhost:8084/jw/api/app/spmRegistry/form/jreFieldScope \
    -H "api_id: API-xxx" -H "api_key: xxx"
  ```

**Deliverables:**
- [ ] Instance verified (jdx4)
- [ ] Forms deployed to Joget
- [ ] Data populated
- [ ] Verification passed
- [ ] `PHASE-4-COMPLETE.md` documentation

---

### Phase 5: Plugin API Updates (1-2 hours)

**Responsible Project:** `joget-rule-editor`

**Objective:** Update plugin services to load field dictionary and rulesets from Joget database.

**Note:** Grammar integration is already complete (Phase 3). This phase focuses on database integration only.

#### Step 5.1: Update FieldRegistryService (REQUIRED)

**File:** `joget-rule-editor/src/.../service/FieldRegistryService.java`

**Current:** Returns hardcoded demo fields  
**Target:** Load from `jreFieldDefinition` form via FormDataDao

```java
public Map<String, List<FieldDefinition>> getFieldsByCategory(String scopeCode) {
    FormDataDao formDataDao = (FormDataDao) AppUtil.getApplicationContext()
        .getBean("formDataDao");
    
    String condition = "scopeCode = ? AND isActive = 'Y'";
    FormRowSet rows = formDataDao.find("jreFieldDefinition", condition, 
        new Object[]{scopeCode}, "category, displayOrder", null, null, null);
    
    // Group by category and convert to FieldDefinition objects
    ...
}
```

#### Step 5.2: Update RulesetService (REQUIRED)

**File:** `joget-rule-editor/src/.../service/RulesetService.java`

**Current:** Simplified storage  
**Target:** Proper CRUD via `jreRuleset` form

#### Step 5.3: Update RulesServiceProvider API (REQUIRED)

**File:** `joget-rule-editor/src/.../lib/RulesServiceProvider.java`

**Updates:**
- `/rules/fields` - Use FieldRegistryService with database
- `/rules/validate` - Already uses rules-grammar via RuleScriptParser ✅
- `/rules/saveRuleset` - Use RulesetService with database
- `/rules/loadRuleset` - Use RulesetService with database

#### Step 5.4: Verify SQL Compiler Works with Grammar AST

**Location:** `joget-rule-editor/src/.../compiler/`

The SQL compiler should already work with the adapted models. Verify it compiles correctly.

#### Step 5.5: Build and Deploy Plugin

```bash
cd joget-rule-editor
mvn clean package -DskipTests

# Deploy JAR to jdx4
cp target/joget-rule-editor-*.jar /path/to/jdx4/plugins/
```

#### Step 5.6: Verify API Endpoints

```bash
# Test field dictionary
curl http://localhost:8084/jw/api/erel/rules/fields?scopeCode=FARMER_ELIGIBILITY \
  -H "api_id: API-xxx" -H "api_key: xxx"

# Should return 80+ fields grouped by category
```

**Deliverables:**
- [x] Maven dependency added (Phase 3) ✅
- [x] Adapter layer created (Phase 3) ✅
- [ ] FieldRegistryService loads from database
- [ ] RulesetService uses FormDataDao
- [ ] API endpoints working with database
- [ ] Plugin deployed to Joget
- [ ] `PHASE-5-COMPLETE.md` documentation

---

### Phase 6: SPM Form Integration (2-3 hours)

**Responsible Projects:** `joget-form-generator` + `joget-rule-editor`

**Objective:** Embed Rule Editor in SPM Program Design form.

#### Step 6.1: Update SPM Program Form YAML

**File:** `joget-form-generator/specs/spm/input/spProgram.yaml`

Add Rule Editor element to Tab 2:

```yaml
# In the eligibility tab section
- id: eligibilityScript
  label: Eligibility Rules
  type: custom
  className: global.govstack.ruleeditor.element.RuleEditorElement
  properties:
    scopeCode: FARMER_ELIGIBILITY
    contextType: ELIGIBILITY
    height: 500px
    showDictionary: true
    showSaveButton: false
```

#### Step 6.2: Regenerate SPM Form

```bash
cd joget-form-generator
python -m src.generate specs/spm/input/spProgram.yaml -o specs/spm/output/
```

#### Step 6.3: Sync and Deploy Updated Form

```bash
cd joget-deployment-toolkit
python -m src.sync_artifacts \
    --source ~/PycharmProjects/dev/joget-form-generator/specs/spm/output \
    --dest components/spm/forms

python -m components.spm.scripts.deploy_forms \
    --instance jdx4 \
    --app spmRegistry
```

#### Step 6.4: Configure Context Code Binding

The `contextCode` should be the program code from Tab 1.

**Option:** Bean Shell Load Binder to set contextCode from program record.

#### Step 6.5: Verify Integration

- [ ] Rule Editor displays in Tab 2
- [ ] Field dictionary panel shows categories
- [ ] Can write and validate rules
- [ ] Rules save with program

**Deliverables:**
- [ ] SPM form YAML updated
- [ ] Form regenerated and deployed
- [ ] Rule Editor displays correctly
- [ ] `PHASE-6-COMPLETE.md` documentation

---

### Phase 7: Test & Validate Tab (2-3 hours)

**Responsible Project:** `joget-rule-editor`

**Objective:** Implement rule testing functionality.

#### Step 7.1: Add Test API Endpoint

**Endpoint:** `POST /jw/api/erel/rules/test`

**Request:**
```json
{
  "script": "RULE ...",
  "scopeCode": "FARMER_ELIGIBILITY",
  "farmerId": "FARMER-2025-001"    // Optional
}
```

**Response:**
```json
{
  "totalFarmers": 15234,
  "eligibleCount": 3456,
  "ineligibleCount": 11778,
  "ruleResults": [...]
}
```

#### Step 7.2: Implement SQL-Based Test Logic

Uses `SqlCompiler` to generate SQL WHERE clause from parsed AST:

```java
// Parse script using rules-grammar
ParseResult<Script> parseResult = RulesScript.parse(script);
if (!parseResult.isSuccess()) {
    return errorResponse(parseResult.getErrors());
}

// Compile to SQL
String whereClause = sqlCompiler.compile(parseResult.getValue(), fieldMapping);
String sql = "SELECT COUNT(*) FROM app_fd_farmer WHERE " + whereClause;
```

#### Step 7.3: Add Test UI to Tab 3

Update form element or add custom HTML for test interface.

**Deliverables:**
- [ ] Test API endpoint implemented
- [ ] SQL-based evaluation working
- [ ] Test UI in form (Tab 3)
- [ ] `PHASE-7-COMPLETE.md` documentation

---

### Phase 8: End-to-End Testing (2-3 hours)

**Responsible:** All projects

**Objective:** Verify complete PoC functionality.

#### Step 8.1: Test Scenarios

| Scenario | Steps | Expected |
|----------|-------|----------|
| Create program with rules | Fill form, write rules, save | Rules persisted |
| Edit existing program | Open program, modify rules, save | Changes persisted |
| Test rules | Run test, check counts | Results displayed |
| Invalid rules | Write bad syntax | Error shown |

#### Step 8.2: Test Rule Examples from uc3-desc.docx

```
RULE "Adult Farmer"
  TYPE: INCLUSION
  MANDATORY: YES
  WHEN age BETWEEN 18 AND 70
  FAIL MESSAGE: "Must be between 18 and 70 years old"
```

#### Step 8.3: Document Results

**Deliverables:**
- [ ] All scenarios pass
- [ ] Test results documented
- [ ] `PHASE-8-COMPLETE.md` - Final PoC report

---

## 6. Implementation Order Summary

| Phase | Duration | Responsible Project(s) | Deliverables | Status |
|-------|----------|------------------------|--------------|--------|
| **0: Preparation** | ✅ Done | All | Directory structure | ✅ Complete |
| **1: Field Dictionary** | 2-3 hrs | joget-deployment-toolkit | `jreFieldDefinition.csv` (~80 fields) | Pending |
| **2: Form Generation** | 1-2 hrs | joget-form-generator | Form JSONs | Pending |
| **3: Grammar Integration** | ✅ Done | rules-grammar + joget-rule-editor | Parser uses rules-grammar | ✅ Complete |
| **4: Database Deploy** | 1-2 hrs | joget-deployment-toolkit + joget-instance-manager | Forms + data in Joget | Pending |
| **5: Plugin API** | 1-2 hrs | joget-rule-editor | Database-backed services | Pending |
| **6: SPM Integration** | 2-3 hrs | joget-form-generator + joget-rule-editor | Rule Editor in form | Pending |
| **7: Test Tab** | 2-3 hrs | joget-rule-editor | Test functionality | Pending |
| **8: E2E Testing** | 2-3 hrs | All | Verified PoC | Pending |
| **Total Remaining** | **~12-18 hrs** | | |

---

## 7. Cross-Project Dependencies

```
Phase 0 (All) ✅ COMPLETE
    │
    ▼
Phase 1 (joget-deployment-toolkit)
    │
    │ generates field dictionary CSV
    ▼
Phase 2 (joget-form-generator)
    │
    │ generates form JSONs, syncs to deployment toolkit
    │
    ├─────────────────────────────────┐
    ▼                                 │
Phase 3 (rules-grammar ✅ COMPLETE    │
         + joget-rule-editor)         │
    │                                 │
    │ grammar integration done        │
    │                                 │
    ├─────────────────────────────────┤
    ▼                                 │
Phase 4 (joget-deployment-toolkit     │
         + joget-instance-manager)    │
    │                                 │
    │ deploys forms & data            │
    │ uses form-creator-api           │
    ▼                                 │
Phase 5 (joget-rule-editor) ◄─────────┘
    │
    │ database integration (services)
    ▼
Phase 6 (joget-form-generator + joget-rule-editor)
    │
    │ embeds Rule Editor in SPM form
    ▼
Phase 7 (joget-rule-editor)
    │
    │ implements test functionality
    ▼
Phase 8 (All)
    │
    │ end-to-end verification
    ▼
    COMPLETE
```

---

## 8. Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Field dictionary incomplete | Generate from actual form JSONs, manual review |
| Grammar integration issues | rules-grammar is complete with tests; create adapter layer |
| Performance on large farmer data | SQL-based evaluation ensures performance |
| Form generation issues | Use existing proven joget-form-generator |
| Cross-project sync issues | Establish clear sync scripts and procedures |
| Instance not ready | Use joget-instance-manager to setup/reset |
| form-creator-api not deployed | Use bootstrap script from joget-instance-manager |

---

## 9. Infrastructure Services

### 9.1 Instance Manager

**Usage:** Instance setup, reset, verification

```bash
cd joget-instance-manager

# Setup new instance
python joget_instance_manager.py setup jdx4

# Reset instance (clean slate)
python joget_instance_manager.py reset jdx4

# Verify instance health
python health_check.py jdx4

# Bootstrap formCreator for API deployments
python bootstrap_joget_instance.py jdx4
```

### 9.2 Shared Configuration

**File:** `~/.joget/instances.yaml`

```yaml
instances:
  jdx4:
    name: "Client Alpha"
    port: 8084
    tomcat_home: "/opt/joget/jdx4"
    database:
      host: localhost
      port: 3306
      name: jwdb_jdx4
      user: jwdb_jdx4_user
    api:
      api_id: "API-xxx"
      api_key: "xxx"
```

### 9.3 Form Creator API

**REST Endpoint:** `POST /jw/api/formcreator/formcreator/forms`

**Usage from joget-deployment-toolkit:**
```python
def deploy_form(instance, form_json):
    config = load_instance_config(instance)
    response = requests.post(
        f"http://localhost:{config['port']}/jw/api/formcreator/formcreator/forms",
        json={
            "formId": form_json["id"],
            "formName": form_json["name"],
            "tableName": form_json["tableName"],
            "formDefinition": json.dumps(form_json),
            "createApiEndpoint": True,
            "createCrud": False
        },
        headers={"api_id": config["api_id"], "api_key": config["api_key"]}
    )
    return response.json()
```

---

## 10. Related Documents

| Document | Location | Description |
|----------|----------|-------------|
| Phase 0 Complete | `joget-rule-editor/docs/PHASE-0-COMPLETE.md` | Phase 0 results |
| Grammar Reference | `joget-rule-editor/docs/GRAMMAR-REFERENCE.md` | DSL syntax (Phase 3) |
| Rules Grammar README | `rules-grammar/README.md` | Grammar project documentation |
| Rules Grammar Developer | `rules-grammar/DEVELOPER.md` | Development guide |

---

## Appendix A: File Locations by Project

### joget-form-generator (Source of Truth)

```
specs/jre/
├── input/
│   ├── jre-field-scope.yaml         # Form spec
│   ├── jre-field-definition.yaml    # Form spec
│   └── jre-ruleset.yaml             # Form spec
├── data/
│   ├── jreFieldScope.csv            # Master data (SOURCE)
│   └── jreFieldDefinition.csv       # Field dictionary (SOURCE, generated)
└── output/
    ├── jreFieldScope.json           # Generated
    ├── jreFieldDefinition.json      # Generated
    └── jreRuleset.json              # Generated
```

### joget-deployment-toolkit (Deployment)

```
components/jre/
├── README.md
├── forms/                           # COPIED from form-generator
├── data/                            # COPIED from form-generator
├── scripts/
│   ├── deploy_jre_forms.py
│   ├── populate_field_dictionary.py
│   └── generate_field_dictionary.py # Generates to specs/jre/data/
└── archive/erel/                    # Old EREL reference
```

### joget-instance-manager (Infrastructure)

```
joget-instance-manager/
├── joget_instance_manager.py        # Main tool
├── bootstrap_joget_instance.py      # Bootstrap formCreator
├── health_check.py                  # Instance health
└── config/
    └── defaults.yaml                # Default configurations
```

### form-creator-api (Plugin)

```
form-creator-api/
├── src/main/java/global/govstack/formcreator/
│   ├── api/
│   │   └── FormCreatorApi.java      # REST endpoint
│   ├── service/
│   │   ├── FormCreationService.java
│   │   ├── FormDatabaseService.java
│   │   ├── ApiBuilderService.java
│   │   └── CrudService.java
│   └── Activator.java
└── pom.xml
```

### joget-rule-editor (Plugin)

```
src/main/java/global/govstack/ruleeditor/
├── element/
│   └── RuleEditorElement.java
├── lib/
│   └── RulesServiceProvider.java
├── service/
│   ├── FieldRegistryService.java
│   └── RulesetService.java
├── adapter/
│   └── GrammarAdapter.java          # Bridge to rules-grammar
└── compiler/
    └── SqlCompiler.java             # Uses AST from rules-grammar
docs/
├── SPM-RULE-EDITOR-POC-PLAN.md
├── GRAMMAR-REFERENCE.md
└── PHASE-*-COMPLETE.md
```

### rules-grammar (Grammar Library) ✅ COMPLETE

```
src/main/
├── antlr4/global/govstack/rules/grammar/
│   ├── RulesScriptLexer.g4
│   └── RulesScriptParser.g4
└── java/global/govstack/rules/grammar/
    ├── RulesScript.java             # Main API
    ├── ParseResult.java
    ├── ParseError.java
    └── model/
        ├── Script.java
        ├── Rule.java
        ├── Condition.java           # Sealed interface
        ├── Value.java               # Sealed interface
        └── ...
```

---

## Appendix B: Instance Architecture

```
Instance   Port    Purpose                    Status
────────   ────    ───────                    ──────
jdx1       8081    Production                 Active
jdx2       8082    Staging                    Active
jdx3       8083    Development                Active
jdx4       8084    Client Alpha (SPM PoC)     Target for PoC
jdx5       8085    Client Beta                Available
jdx6       8086    Sandbox                    Available
```

**Target Instance:** jdx4 (Client Alpha)

---

## Appendix C: rules-grammar Usage Examples

### Basic Parsing

```java
import global.govstack.rules.grammar.RulesScript;
import global.govstack.rules.grammar.ParseResult;
import global.govstack.rules.grammar.model.Script;

String script = """
    RULE "Adult Farmer"
      TYPE: INCLUSION
      MANDATORY: YES
      WHEN age BETWEEN 18 AND 70
      FAIL MESSAGE: "Must be between 18 and 70"
    """;

ParseResult<Script> result = RulesScript.parse(script);

if (result.isSuccess()) {
    Script ast = result.getValue();
    // Process AST
    for (Rule rule : ast.getRules()) {
        System.out.println("Rule: " + rule.getName());
        System.out.println("Type: " + rule.getType());
        // Process conditions...
    }
} else {
    // Handle errors
    for (ParseError error : result.getErrors()) {
        System.err.println("Error at line " + error.getLine() + ": " + error.getMessage());
    }
}
```

### Working with Conditions

```java
import global.govstack.rules.grammar.model.*;

// Conditions are a sealed interface with pattern matching
void processCondition(Condition condition) {
    switch (condition) {
        case Condition.SimpleComparison sc -> {
            System.out.println(sc.field() + " " + sc.operator() + " " + sc.value());
        }
        case Condition.Between b -> {
            System.out.println(b.field() + " BETWEEN " + b.low() + " AND " + b.high());
        }
        case Condition.In in -> {
            System.out.println(in.field() + " IN " + in.values());
        }
        case Condition.And and -> {
            processCondition(and.left());
            processCondition(and.right());
        }
        // ... other variants
    }
}
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2025-01-27 | Initial plan |
| 2.0 | 2025-01-27 | Added 4-project ecosystem |
| 3.0 | 2025-01-28 | Expanded to 6-project ecosystem, corrected grammar integration |
