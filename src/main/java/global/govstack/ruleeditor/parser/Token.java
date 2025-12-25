package global.govstack.ruleeditor.parser;

/**
 * Token types for the Rules Script language.
 *
 * Rules Script grammar supports:
 * - Rule definitions: RULE, TYPE, CATEGORY, MANDATORY, ORDER, WHEN
 * - Conditions: field operator value
 * - Logical operators: AND, OR, NOT
 * - Comparison operators: =, !=, >, >=, <, <=, BETWEEN, IN, NOT IN
 * - String operators: CONTAINS, STARTS WITH, ENDS WITH
 * - Null checks: IS EMPTY, IS NOT EMPTY
 * - Aggregations: COUNT, SUM, AVG, MIN, MAX
 * - Grid checks: HAS_ANY, HAS_ALL, HAS_NONE
 * - Scoring: SCORE, WEIGHT
 * - Messages: PASS MESSAGE, FAIL MESSAGE
 */
public enum Token {
    // === Keywords ===
    RULE,           // RULE "name"
    TYPE,           // TYPE:
    CATEGORY,       // CATEGORY:
    MANDATORY,      // MANDATORY:
    ORDER,          // ORDER:
    WHEN,           // WHEN condition
    SCORE,          // SCORE:
    WEIGHT,         // WEIGHT:
    PASS_MESSAGE,   // PASS MESSAGE:
    FAIL_MESSAGE,   // FAIL MESSAGE:
    
    // === Rule Types ===
    INCLUSION,      // TYPE: INCLUSION
    EXCLUSION,      // TYPE: EXCLUSION
    PRIORITY,       // TYPE: PRIORITY
    BONUS,          // TYPE: BONUS
    
    // === Boolean Values ===
    YES,            // YES, Y, TRUE, 1
    NO,             // NO, N, FALSE, 0
    TRUE,
    FALSE,
    
    // === Logical Operators ===
    AND,            // AND
    OR,             // OR
    NOT,            // NOT
    
    // === Comparison Operators ===
    EQ,             // =
    NEQ,            // !=
    GT,             // >
    GTE,            // >=
    LT,             // <
    LTE,            // <=
    BETWEEN,        // BETWEEN x AND y
    IN,             // IN (...)
    NOT_IN,         // NOT IN (...)
    
    // === String Operators ===
    CONTAINS,       // CONTAINS
    STARTS_WITH,    // STARTS WITH
    ENDS_WITH,      // ENDS WITH
    
    // === Null Checks ===
    IS_EMPTY,       // IS EMPTY
    IS_NOT_EMPTY,   // IS NOT EMPTY
    
    // === Aggregation Functions ===
    COUNT,          // COUNT(grid)
    SUM,            // SUM(grid.field)
    AVG,            // AVG(grid.field)
    MIN,            // MIN(grid.field)
    MAX,            // MAX(grid.field)
    
    // === Grid Check Functions ===
    HAS_ANY,        // HAS_ANY(grid.field, value)
    HAS_ALL,        // HAS_ALL(grid.field, value1, value2, ...)
    HAS_NONE,       // HAS_NONE(grid.field, value)
    
    // === Literals ===
    STRING,         // "text" or 'text'
    NUMBER,         // 123, 123.45, -123
    IDENTIFIER,     // fieldName, grid.fieldName
    
    // === Punctuation ===
    LPAREN,         // (
    RPAREN,         // )
    COMMA,          // ,
    COLON,          // :
    DOT,            // .
    PLUS,           // +
    MINUS,          // -
    
    // === Special ===
    COMMENT,        // # comment
    NEWLINE,        // End of line
    EOF,            // End of file
    UNKNOWN;        // Unrecognized token
    
    /**
     * Check if this token is a rule type (INCLUSION, EXCLUSION, etc.)
     */
    public boolean isRuleType() {
        return this == INCLUSION || this == EXCLUSION || 
               this == PRIORITY || this == BONUS;
    }
    
    /**
     * Check if this token is a comparison operator
     */
    public boolean isComparisonOperator() {
        return this == EQ || this == NEQ || this == GT || this == GTE ||
               this == LT || this == LTE || this == BETWEEN ||
               this == IN || this == NOT_IN ||
               this == CONTAINS || this == STARTS_WITH || this == ENDS_WITH ||
               this == IS_EMPTY || this == IS_NOT_EMPTY;
    }
    
    /**
     * Check if this token is a logical operator
     */
    public boolean isLogicalOperator() {
        return this == AND || this == OR || this == NOT;
    }
    
    /**
     * Check if this token is an aggregation function
     */
    public boolean isAggregationFunction() {
        return this == COUNT || this == SUM || this == AVG ||
               this == MIN || this == MAX;
    }
    
    /**
     * Check if this token is a grid check function
     */
    public boolean isGridCheckFunction() {
        return this == HAS_ANY || this == HAS_ALL || this == HAS_NONE;
    }
    
    /**
     * Check if this token is a boolean value
     */
    public boolean isBooleanValue() {
        return this == YES || this == NO || this == TRUE || this == FALSE;
    }
}
