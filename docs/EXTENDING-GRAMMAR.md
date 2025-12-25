# Extending the Rules Script Grammar

This guide explains how to extend the Rules Script language with new keywords, operators, functions, and rule types. The parser uses a **hand-written recursive descent approach**, making extensions straightforward.

## Table of Contents

1. [Understanding the Parser Pipeline](#understanding-the-parser-pipeline)
2. [Adding a New Keyword](#adding-a-new-keyword)
3. [Adding a New Operator](#adding-a-new-operator)
4. [Adding a New Function](#adding-a-new-function)
5. [Adding a New Rule Type](#adding-a-new-rule-type)
6. [Adding a New Rule Clause](#adding-a-new-rule-clause)
7. [Use Case: Subsidy Program Rules](#use-case-subsidy-program-rules)
8. [Updating the UI](#updating-the-ui)

---

## Understanding the Parser Pipeline

```
Rules Script (text)
       │
       ▼
┌──────────────┐
│  RuleScriptLexer   │  Tokenization: text → tokens
│  Token.java  │  Defines all token types
└──────────────┘
       │
       ▼
┌──────────────┐
│  RuleScriptParser  │  Parsing: tokens → AST (Abstract Syntax Tree)
│              │  Implements grammar rules
└──────────────┘
       │
       ▼
┌──────────────────┐
│  Condition   │  Model: Condition tree structure
│  Rule        │  Model: Rule structure
└──────────────────┘
       │
       ▼
┌──────────────┐
│  RuleScriptCompiler│  Compilation: AST → SQL
└──────────────┘
```

### Files to Modify for Grammar Changes

| Change Type | Token.java | RuleScriptLexer.java | RuleScriptParser.java | Condition.java | RuleScriptCompiler.java | UI Files |
|-------------|:----------:|:--------------:|:---------------:|:------------------:|:-----------------:|:--------:|
| New Keyword | ✓ | ✓ | ✓ | | | ✓ |
| New Operator | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| New Function | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| New Rule Type | ✓ | ✓ | ✓ | | ✓ | ✓ |
| New Rule Clause | ✓ | ✓ | ✓ | | | ✓ |

---

## Adding a New Keyword

**Example**: Add `DEPENDS ON "RuleName"` clause for rule dependencies.

### Step 1: Add Token Type (Token.java)

```java
public enum Token {
    // ... existing tokens ...

    // === New Keywords ===
    DEPENDS,        // DEPENDS ON "rule-name"
    ON,             // (part of DEPENDS ON)

    // ... rest of enum ...
}
```

### Step 2: Register Keyword (RuleScriptLexer.java)

Add to the `KEYWORDS` map:

```java
static {
    // ... existing keywords ...

    // New keywords
    KEYWORDS.put("DEPENDS", Token.DEPENDS);
    KEYWORDS.put("ON", Token.ON);
}
```

### Step 3: Parse the Clause (RuleScriptParser.java)

In the `parseRule()` method, add handling for the new clause:

```java
private Rule parseRule() throws ParseException {
    // ... existing code ...

    while (!isAtEnd() && !check(Token.RULE)) {
        // ... existing clause handling ...

        } else if (check(Token.DEPENDS)) {
            advance();  // consume DEPENDS
            consume(Token.ON, "Expected 'ON' after DEPENDS");
            TokenInstance depToken = consume(Token.STRING, "Expected rule name after DEPENDS ON");
            rule.addDependency(depToken.getStringValue());

        } else if (check(Token.NEWLINE)) {
            advance();
        } else {
            break;
        }
    }

    return rule;
}
```

### Step 4: Update Model (Rule.java)

```java
public class Rule {
    // ... existing fields ...

    private List<String> dependencies = new ArrayList<>();

    public void addDependency(String ruleName) {
        dependencies.add(ruleName);
    }

    public List<String> getDependencies() {
        return dependencies;
    }
}
```

### Step 5: Update UI Highlighting (jre-mode.js)

```javascript
// Keywords (blue, bold)
var keywords = new RegExp("^(RULE|WHEN|AND|OR|NOT|DEPENDS|ON|...)\\b", "i");
```

---

## Adding a New Operator

**Example**: Add `MATCHES` operator for regex matching.

### Step 1: Add Token Type (Token.java)

```java
public enum Token {
    // ... existing tokens ...

    // === String Operators ===
    CONTAINS,       // CONTAINS
    STARTS_WITH,    // STARTS WITH
    ENDS_WITH,      // ENDS WITH
    MATCHES,        // MATCHES (new regex operator)

    // Update helper method
    public boolean isComparisonOperator() {
        return this == EQ || this == NEQ || this == GT || this == GTE ||
               this == LT || this == LTE || this == BETWEEN ||
               this == IN || this == NOT_IN ||
               this == CONTAINS || this == STARTS_WITH || this == ENDS_WITH ||
               this == MATCHES ||  // Add new operator
               this == IS_EMPTY || this == IS_NOT_EMPTY;
    }
}
```

### Step 2: Register Keyword (RuleScriptLexer.java)

```java
static {
    // ... existing keywords ...

    KEYWORDS.put("MATCHES", Token.MATCHES);
}
```

### Step 3: Add to Condition Model (Condition.java)

```java
public enum Operator {
    EQ("="),
    NEQ("!="),
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<="),
    CONTAINS("CONTAINS"),
    STARTS_WITH("STARTS WITH"),
    ENDS_WITH("ENDS WITH"),
    MATCHES("MATCHES");  // New operator

    // ... rest of enum ...
}
```

### Step 4: Handle in Parser (RuleScriptParser.java)

In `isComparisonOperator()`:

```java
private boolean isComparisonOperator() {
    Token t = peek().getType();
    return t == Token.EQ || t == Token.NEQ ||
           t == Token.GT || t == Token.GTE ||
           t == Token.LT || t == Token.LTE ||
           t == Token.CONTAINS || t == Token.STARTS_WITH || t == Token.ENDS_WITH ||
           t == Token.MATCHES;  // Add new operator
}
```

In `parseOperator()`:

```java
private Operator parseOperator() throws ParseException {
    TokenInstance t = advance();
    switch (t.getType()) {
        // ... existing cases ...
        case MATCHES: return Operator.MATCHES;
        default:
            throw error(t, "Expected comparison operator");
    }
}
```

### Step 5: Compile to SQL (RuleScriptCompiler.java)

In `operatorToSql()`:

```java
private String operatorToSql(Condition.Operator op) {
    switch (op) {
        // ... existing cases ...
        case MATCHES: return "REGEXP";  // MySQL syntax
        default: return "=";
    }
}
```

For `MATCHES`, you may need special handling in `compileComparison()`:

```java
private String compileComparison(Condition condition, CompiledRule compiled) {
    // ... existing code ...

    if (condition.getOperator() == Operator.MATCHES) {
        // Special handling for regex
        return sqlRef + " REGEXP " + sqlValue;
    }

    return sqlRef + " " + sqlOp + " " + sqlValue;
}
```

---

## Adding a New Function

**Example**: Add `LATEST(grid.field)` to get the most recent value from a grid.

### Step 1: Add Token Type (Token.java)

```java
public enum Token {
    // ... existing tokens ...

    // === Aggregation Functions ===
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX,
    LATEST,  // New function

    // Update helper
    public boolean isAggregationFunction() {
        return this == COUNT || this == SUM || this == AVG ||
               this == MIN || this == MAX || this == LATEST;
    }
}
```

### Step 2: Register Keyword (RuleScriptLexer.java)

```java
static {
    // ... existing keywords ...

    KEYWORDS.put("LATEST", Token.LATEST);
}
```

### Step 3: Add to Condition Model (Condition.java)

```java
public enum FunctionType {
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX,
    LATEST,  // New function
    HAS_ANY,
    HAS_ALL,
    HAS_NONE
}
```

### Step 4: Handle in Parser (RuleScriptParser.java)

In `tokenToFunctionType()`:

```java
private FunctionType tokenToFunctionType(Token token) {
    switch (token) {
        case COUNT: return FunctionType.COUNT;
        case SUM: return FunctionType.SUM;
        case AVG: return FunctionType.AVG;
        case MIN: return FunctionType.MIN;
        case MAX: return FunctionType.MAX;
        case LATEST: return FunctionType.LATEST;  // New function
        case HAS_ANY: return FunctionType.HAS_ANY;
        case HAS_ALL: return FunctionType.HAS_ALL;
        case HAS_NONE: return FunctionType.HAS_NONE;
        default: return null;
    }
}
```

### Step 5: Compile to SQL (RuleScriptCompiler.java)

In `compileFunctionCall()`:

```java
private String compileFunctionCall(Condition condition, CompiledRule compiled) {
    // ... existing code ...

    switch (funcType) {
        // ... existing cases ...

        case LATEST:
            functionSql = compileLatest(functionArg, compiled);
            break;

        // ... rest of switch ...
    }
}

/**
 * Compile LATEST(grid.field) - get most recent value
 */
private String compileLatest(String fieldPath, CompiledRule compiled) {
    if (!fieldPath.contains(".")) {
        output.addWarning("LATEST requires grid.field notation: " + fieldPath);
        return "NULL";
    }

    String gridName = fieldPath.substring(0, fieldPath.indexOf('.'));
    String fieldName = fieldPath.substring(fieldPath.indexOf('.') + 1);

    GridInfo grid = fieldMapping.getGrid(gridName);
    if (grid == null) {
        output.addWarning("Unknown grid: " + gridName);
        return "NULL";
    }

    compiled.addUsedField(fieldPath);

    // Subquery to get latest value (assuming dateCreated column)
    return String.format(
        "(SELECT %s.c_%s FROM %s %s WHERE %s ORDER BY %s.dateCreated DESC LIMIT 1)",
        grid.getTableAlias(),
        fieldName,
        grid.getTableName(),
        grid.getTableAlias(),
        grid.getCorrelation(),
        grid.getTableAlias()
    );
}
```

---

## Adding a New Rule Type

**Example**: Add `SCORING_TIER` rule type for tiered scoring systems.

### Step 1: Add Token Type (Token.java)

```java
public enum Token {
    // ... existing tokens ...

    // === Rule Types ===
    INCLUSION,
    EXCLUSION,
    PRIORITY,
    BONUS,
    SCORING_TIER,  // New rule type

    public boolean isRuleType() {
        return this == INCLUSION || this == EXCLUSION ||
               this == PRIORITY || this == BONUS || this == SCORING_TIER;
    }
}
```

### Step 2: Register Keyword (RuleScriptLexer.java)

```java
static {
    // ... existing keywords ...

    KEYWORDS.put("SCORING_TIER", Token.SCORING_TIER);
}
```

### Step 3: Update Rule Model (Rule.java)

```java
public class Rule {
    public enum RuleType {
        INCLUSION,
        EXCLUSION,
        PRIORITY,
        BONUS,
        SCORING_TIER  // New type
    }

    // ... rest of class ...
}
```

### Step 4: Handle in Parser (RuleScriptParser.java)

In `parseRuleType()`:

```java
private RuleType parseRuleType() throws ParseException {
    if (check(Token.INCLUSION)) {
        advance();
        return RuleType.INCLUSION;
    } else if (check(Token.EXCLUSION)) {
        advance();
        return RuleType.EXCLUSION;
    } else if (check(Token.PRIORITY)) {
        advance();
        return RuleType.PRIORITY;
    } else if (check(Token.BONUS)) {
        advance();
        return RuleType.BONUS;
    } else if (check(Token.SCORING_TIER)) {
        advance();
        return RuleType.SCORING_TIER;
    } else {
        throw error(peek(), "Expected rule type");
    }
}
```

### Step 5: Handle in Compiler (RuleScriptCompiler.java)

In `compile()` method, handle the new type:

```java
for (Rule rule : rules) {
    CompiledRule compiled = compileRule(rule);
    output.addCompiledRule(compiled);

    switch (rule.getType()) {
        case INCLUSION:
            // ... existing ...
            break;
        case EXCLUSION:
            // ... existing ...
            break;
        case SCORING_TIER:
            // Custom handling for tiered scoring
            compileScoringTier(rule, compiled);
            break;
        // ... other cases ...
    }
}
```

---

## Adding a New Rule Clause

**Example**: Add `EFFECTIVE FROM date TO date` for time-bound rules.

### Step 1: Add Token Types (Token.java)

```java
public enum Token {
    // ... existing tokens ...

    EFFECTIVE,      // EFFECTIVE FROM ... TO ...
    FROM,
    TO,
}
```

### Step 2: Handle Multi-Word Keyword (RuleScriptLexer.java)

```java
static {
    // ... existing keywords ...

    KEYWORDS.put("EFFECTIVE", Token.EFFECTIVE);
    KEYWORDS.put("FROM", Token.FROM);
    KEYWORDS.put("TO", Token.TO);
}
```

### Step 3: Update Rule Model (Rule.java)

```java
public class Rule {
    // ... existing fields ...

    private String effectiveFrom;
    private String effectiveTo;

    public void setEffectiveFrom(String date) {
        this.effectiveFrom = date;
    }

    public String getEffectiveFrom() {
        return effectiveFrom;
    }

    public void setEffectiveTo(String date) {
        this.effectiveTo = date;
    }

    public String getEffectiveTo() {
        return effectiveTo;
    }
}
```

### Step 4: Parse the Clause (RuleScriptParser.java)

```java
private Rule parseRule() throws ParseException {
    // ... existing code ...

    while (!isAtEnd() && !check(Token.RULE)) {
        // ... existing clause handling ...

        } else if (check(Token.EFFECTIVE)) {
            advance();  // consume EFFECTIVE
            consume(Token.FROM, "Expected 'FROM' after EFFECTIVE");
            TokenInstance fromToken = consume(Token.STRING, "Expected date after FROM");
            rule.setEffectiveFrom(fromToken.getStringValue());

            consume(Token.TO, "Expected 'TO' after date");
            TokenInstance toToken = consume(Token.STRING, "Expected date after TO");
            rule.setEffectiveTo(toToken.getStringValue());

        } else if (check(Token.NEWLINE)) {
            advance();
        } else {
            break;
        }
    }

    return rule;
}
```

### Step 5: Handle in Compiler (RuleScriptCompiler.java)

```java
private CompiledRule compileRule(Rule rule) {
    // ... existing code ...

    // Add date range condition if specified
    if (rule.getEffectiveFrom() != null && rule.getEffectiveTo() != null) {
        String dateClause = String.format(
            "(CURRENT_DATE BETWEEN '%s' AND '%s')",
            rule.getEffectiveFrom(),
            rule.getEffectiveTo()
        );
        // Combine with existing WHERE clause
        if (whereClause != null && !whereClause.isEmpty()) {
            whereClause = "(" + whereClause + ") AND " + dateClause;
        } else {
            whereClause = dateClause;
        }
        compiled.setWhereClause(whereClause);
    }

    return compiled;
}
```

---

## Use Case: Subsidy Program Rules

Here's a complete example of extending EREL for a subsidy program with:
- Benefit tiers
- Program-specific categories
- Eligibility windows

### New Syntax

```text
RULE "Small Farmer Subsidy"
  TYPE: SUBSIDY_TIER
  PROGRAM: "AGRICULTURE_SUPPORT_2025"
  TIER: 1
  BENEFIT: 5000
  CATEGORY: AGRICULTURE
  WHEN farmSize < 5 AND isRegisteredFarmer = true
  EFFECTIVE FROM "2025-01-01" TO "2025-12-31"
  FAIL MESSAGE: "Does not qualify for small farmer subsidy"
```

### Implementation Steps

1. **Add new tokens**: `SUBSIDY_TIER`, `PROGRAM`, `TIER`, `BENEFIT`
2. **Add to lexer**: Register in KEYWORDS map
3. **Update Rule**: Add `programCode`, `tier`, `benefitAmount` fields
4. **Update parser**: Handle new clauses in `parseRule()`
5. **Update compiler**: Generate appropriate SQL with benefit calculations
6. **Update UI**: Add syntax highlighting for new keywords

### SQL Output Example

```sql
SELECT
    f.id,
    f.farmer_name,
    CASE
        WHEN f.c_farmSize < 5 AND f.c_isRegisteredFarmer = 'true'
             AND CURRENT_DATE BETWEEN '2025-01-01' AND '2025-12-31'
        THEN 5000
        ELSE 0
    END AS tier_1_benefit
FROM app_fd_farmer f
WHERE f.c_programCode = 'AGRICULTURE_SUPPORT_2025'
```

---

## Updating the UI

After modifying the grammar, update these files:

### jre-mode.js (Syntax Highlighting)

```javascript
// Add new keywords to appropriate category
var keywords = new RegExp("^(RULE|WHEN|AND|OR|NOT|EFFECTIVE|FROM|TO|DEPENDS|ON|...)\\b", "i");
var clauses = new RegExp("^(TYPE|CATEGORY|MANDATORY|PROGRAM|TIER|BENEFIT|...)\\b", "i");
var types = new RegExp("^(INCLUSION|EXCLUSION|SUBSIDY_TIER|...)\\b", "i");
```

### jre-editor.js (Help Panel)

Update `buildHTML()` to include new syntax in the help panel:

```javascript
'<li><code>EFFECTIVE FROM</code> date <code>TO</code> date</li>\
<li><code>DEPENDS ON</code> "rule-name"</li>\
<li><code>PROGRAM:</code> "program-code"</li>'
```

### jre-editor.js (Sample Script)

Update `getSampleScript()` with examples of new syntax:

```javascript
function getSampleScript() {
    return '\
RULE "Subsidy Tier 1"\n\
  TYPE: SUBSIDY_TIER\n\
  PROGRAM: "AGRI_2025"\n\
  TIER: 1\n\
  BENEFIT: 5000\n\
  WHEN farmSize < 5\n\
  EFFECTIVE FROM "2025-01-01" TO "2025-12-31"';
}
```

---

## Testing Your Extensions

### Unit Test Template

```java
@Test
public void testNewKeyword() {
    String script = """
        RULE "Test Dependency"
          TYPE: INCLUSION
          DEPENDS ON "Parent Rule"
          WHEN age >= 18
        """;

    RuleScriptParser parser = new RuleScriptParser();
    ValidationResult result = parser.parse(script);

    assertTrue(result.isValid());
    assertEquals(1, result.getRuleCount());

    Rule rule = result.getRules().get(0);
    assertEquals(1, rule.getDependencies().size());
    assertEquals("Parent Rule", rule.getDependencies().get(0));
}
```

### Manual Testing

1. Build: `mvn clean package`
2. Deploy to Joget
3. Test in browser console:

```javascript
// Validate new syntax
fetch('/jw/api/erel/rules/validate', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'api_id': 'YOUR_API_ID',
        'api_key': 'YOUR_API_KEY'
    },
    body: JSON.stringify({
        script: 'RULE "Test" TYPE: INCLUSION DEPENDS ON "Other" WHEN age >= 18'
    })
}).then(r => r.json()).then(console.log);
```

---

## Next Steps

- [UI-CUSTOMIZATION.md](./UI-CUSTOMIZATION.md) - Styling the editor
- [INTEGRATION.md](./INTEGRATION.md) - Integrating with other systems
- [DEVELOPER.md](./DEVELOPER.md) - General developer guide
