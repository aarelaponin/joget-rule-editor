package global.govstack.ruleeditor.parser;

import global.govstack.ruleeditor.model.Condition;
import global.govstack.ruleeditor.model.Condition.ConditionType;
import global.govstack.ruleeditor.model.Condition.FunctionType;
import global.govstack.ruleeditor.model.Condition.Operator;
import global.govstack.ruleeditor.model.Rule;
import global.govstack.ruleeditor.model.Rule.RuleType;
import global.govstack.ruleeditor.model.ValidationResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser for Rules Script.
 *
 * Implements a recursive descent parser that converts tokens into rule objects.
 *
 * Grammar (simplified):
 * <pre>
 * script     := rule*
 * rule       := RULE STRING ruleBody
 * ruleBody   := (TYPE COLON ruleType)?
 *               (CATEGORY COLON IDENTIFIER)?
 *               (MANDATORY COLON boolValue)?
 *               (ORDER COLON NUMBER)?
 *               (WHEN condition)?
 *               (SCORE COLON NUMBER)?
 *               (PASS_MESSAGE COLON STRING)?
 *               (FAIL_MESSAGE COLON STRING)?
 * ruleType   := INCLUSION | EXCLUSION | PRIORITY | BONUS
 * boolValue  := YES | NO | TRUE | FALSE
 * condition  := orExpr
 * orExpr     := andExpr (OR andExpr)*
 * andExpr    := unaryExpr (AND unaryExpr)*
 * unaryExpr  := NOT? primaryExpr
 * primaryExpr := LPAREN condition RPAREN | comparison | functionExpr
 * comparison := IDENTIFIER operator value
 *             | IDENTIFIER BETWEEN value AND value
 *             | IDENTIFIER IN LPAREN valueList RPAREN
 *             | IDENTIFIER IS_EMPTY
 *             | IDENTIFIER IS_NOT_EMPTY
 * operator   := EQ | NEQ | GT | GTE | LT | LTE | CONTAINS | STARTS_WITH | ENDS_WITH
 * functionExpr := functionName LPAREN IDENTIFIER (COMMA valueList)? RPAREN (operator value)?
 * functionName := COUNT | SUM | AVG | MIN | MAX | HAS_ANY | HAS_ALL | HAS_NONE
 * value      := STRING | NUMBER | boolValue | IDENTIFIER
 * valueList  := value (COMMA value)*
 * </pre>
 */
public class RuleScriptParser {

    private List<TokenInstance> tokens;
    private int current = 0;
    private ValidationResult result;

    public RuleScriptParser() {
        this.result = new ValidationResult();
    }

    /**
     * Parse a Rules Script and return the validation result with parsed rules.
     */
    public ValidationResult parse(String script) {
        result = new ValidationResult();

        if (script == null || script.trim().isEmpty()) {
            result.setValid(true);
            result.setRuleCount(0);
            return result;
        }

        try {
            // Tokenize
            RuleScriptLexer lexer = new RuleScriptLexer(script);
            tokens = lexer.tokenize();
            current = 0;

            // Parse rules
            List<Rule> rules = parseRules();

            result.setRuleCount(rules.size());
            result.setValid(result.getErrors().isEmpty());

            // Add rules to result
            for (Rule rule : rules) {
                result.addRule(rule);

                int conditionCount = countConditions(rule.getCondition());
                result.addRuleSummary(
                    rule.getName(),
                    rule.getCode(),
                    rule.getType().name(),
                    conditionCount
                );
            }

        } catch (ParseException e) {
            result.addError(e.getLine(), e.getColumn(), e.getMessage(), "ERROR");
            result.setValid(false);
        } catch (Exception e) {
            result.addError(1, 1, "Unexpected error: " + e.getMessage(), "ERROR");
            result.setValid(false);
        }

        return result;
    }

    /**
     * Parse all rules in the script
     */
    private List<Rule> parseRules() throws ParseException {
        List<Rule> rules = new ArrayList<>();

        while (!isAtEnd()) {
            skipNewlines();

            if (isAtEnd()) break;

            if (check(Token.RULE)) {
                rules.add(parseRule());
            } else {
                // Skip unknown tokens
                TokenInstance token = advance();
                if (token.getType() != Token.NEWLINE && token.getType() != Token.EOF) {
                    result.addWarning(token.getLine(), token.getColumn(),
                        "Unexpected token outside rule: " + token.getValue());
                }
            }
        }

        return rules;
    }

    /**
     * Parse a single rule
     */
    private Rule parseRule() throws ParseException {
        TokenInstance ruleToken = consume(Token.RULE, "Expected RULE keyword");
        Rule rule = new Rule();
        rule.setStartLine(ruleToken.getLine());

        // Rule name (string)
        TokenInstance nameToken = consume(Token.STRING, "Expected rule name after RULE");
        rule.setName(nameToken.getStringValue());

        skipNewlines();

        // Parse rule body (optional clauses in any order)
        while (!isAtEnd() && !check(Token.RULE)) {
            skipNewlines();
            if (isAtEnd() || check(Token.RULE)) break;

            if (check(Token.TYPE)) {
                advance();
                consume(Token.COLON, "Expected ':' after TYPE");
                rule.setType(parseRuleType());
            } else if (check(Token.CATEGORY)) {
                advance();
                consume(Token.COLON, "Expected ':' after CATEGORY");
                TokenInstance catToken = consume(Token.IDENTIFIER, "Expected category identifier");
                rule.setCategory(catToken.getValue());
            } else if (check(Token.MANDATORY)) {
                advance();
                consume(Token.COLON, "Expected ':' after MANDATORY");
                rule.setMandatory(parseBoolValue());
            } else if (check(Token.ORDER)) {
                advance();
                consume(Token.COLON, "Expected ':' after ORDER");
                TokenInstance orderToken = consume(Token.NUMBER, "Expected number after ORDER:");
                rule.setOrder((int) orderToken.getNumericValue());
            } else if (check(Token.WHEN)) {
                advance();
                rule.setCondition(parseCondition());
            } else if (check(Token.SCORE)) {
                advance();
                consume(Token.COLON, "Expected ':' after SCORE");
                // Handle optional + or - sign
                int sign = 1;
                if (check(Token.PLUS)) {
                    advance();
                } else if (check(Token.MINUS)) {
                    advance();
                    sign = -1;
                }
                TokenInstance scoreToken = consume(Token.NUMBER, "Expected number after SCORE:");
                rule.setScore(sign * (int) scoreToken.getNumericValue());
            } else if (check(Token.WEIGHT)) {
                advance();
                consume(Token.COLON, "Expected ':' after WEIGHT");
                TokenInstance weightToken = consume(Token.NUMBER, "Expected number after WEIGHT:");
                rule.setWeight((int) weightToken.getNumericValue());
            } else if (check(Token.PASS_MESSAGE)) {
                advance();
                consume(Token.COLON, "Expected ':' after PASS MESSAGE");
                TokenInstance msgToken = consume(Token.STRING, "Expected string after PASS MESSAGE:");
                rule.setPassMessage(msgToken.getStringValue());
            } else if (check(Token.FAIL_MESSAGE)) {
                advance();
                consume(Token.COLON, "Expected ':' after FAIL MESSAGE");
                TokenInstance msgToken = consume(Token.STRING, "Expected string after FAIL MESSAGE:");
                rule.setFailMessage(msgToken.getStringValue());
            } else if (check(Token.NEWLINE)) {
                advance();
            } else {
                // Unknown token in rule body - break to next rule
                break;
            }
        }

        rule.setEndLine(previous().getLine());

        // Validate rule has a condition
        if (rule.getCondition() == null) {
            result.addWarning(rule.getStartLine(), 1,
                "Rule '" + rule.getName() + "' has no WHEN condition");
        }

        return rule;
    }

    /**
     * Parse rule type
     */
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
        } else {
            throw error(peek(), "Expected rule type (INCLUSION, EXCLUSION, PRIORITY, or BONUS)");
        }
    }

    /**
     * Parse boolean value
     */
    private boolean parseBoolValue() throws ParseException {
        if (check(Token.YES) || check(Token.TRUE)) {
            advance();
            return true;
        } else if (check(Token.NO) || check(Token.FALSE)) {
            advance();
            return false;
        } else if (check(Token.NUMBER)) {
            TokenInstance t = advance();
            return t.getNumericValue() != 0;
        } else {
            throw error(peek(), "Expected boolean value (YES, NO, TRUE, FALSE, 1, 0)");
        }
    }

    /**
     * Parse a condition (OR expression)
     */
    private Condition parseCondition() throws ParseException {
        return parseOrExpr();
    }

    /**
     * Parse OR expression: andExpr (OR andExpr)*
     */
    private Condition parseOrExpr() throws ParseException {
        Condition left = parseAndExpr();

        while (check(Token.OR)) {
            advance();
            skipNewlines();
            Condition right = parseAndExpr();
            left = Condition.or(left, right);
        }

        return left;
    }

    /**
     * Parse AND expression: unaryExpr (AND unaryExpr)*
     */
    private Condition parseAndExpr() throws ParseException {
        Condition left = parseUnaryExpr();

        while (check(Token.AND)) {
            advance();
            skipNewlines();
            Condition right = parseUnaryExpr();
            left = Condition.and(left, right);
        }

        return left;
    }

    /**
     * Parse unary expression: NOT? primaryExpr
     */
    private Condition parseUnaryExpr() throws ParseException {
        if (check(Token.NOT)) {
            advance();
            skipNewlines();
            Condition inner = parsePrimaryExpr();
            return Condition.not(inner);
        }

        return parsePrimaryExpr();
    }

    /**
     * Parse primary expression: grouped, comparison, or function
     */
    private Condition parsePrimaryExpr() throws ParseException {
        skipNewlines();

        // Grouped expression: (condition)
        if (check(Token.LPAREN)) {
            advance();
            skipNewlines();
            Condition inner = parseCondition();
            skipNewlines();
            consume(Token.RPAREN, "Expected ')' after grouped expression");
            return Condition.group(inner);
        }

        // Function call: COUNT(...), HAS_ANY(...), etc.
        if (peek().getType().isAggregationFunction() || peek().getType().isGridCheckFunction()) {
            return parseFunctionExpr();
        }

        // Comparison: field operator value
        if (check(Token.IDENTIFIER)) {
            return parseComparison();
        }

        throw error(peek(), "Expected condition (field, function, or grouped expression)");
    }

    /**
     * Parse function expression: COUNT(field) > 0, HAS_ANY(field, value), etc.
     */
    private Condition parseFunctionExpr() throws ParseException {
        TokenInstance funcToken = advance();
        FunctionType funcType = tokenToFunctionType(funcToken.getType());

        consume(Token.LPAREN, "Expected '(' after function name");

        // First argument: field identifier
        TokenInstance argToken = consume(Token.IDENTIFIER, "Expected field identifier in function");
        String arg = argToken.getValue();

        Condition funcCond = Condition.functionCall(funcType, arg);

        // For HAS_ANY, HAS_ALL, HAS_NONE - parse additional value arguments
        if (funcType == FunctionType.HAS_ANY || funcType == FunctionType.HAS_ALL || funcType == FunctionType.HAS_NONE) {
            if (check(Token.COMMA)) {
                advance();
                List<Object> values = parseValueList();
                funcCond.setFunctionValues(values);
            }
        }

        consume(Token.RPAREN, "Expected ')' after function arguments");

        // For aggregation functions, there may be a comparison: COUNT(x) > 0
        if (funcType.ordinal() <= FunctionType.MAX.ordinal()) {
            if (isComparisonOperator()) {
                Operator op = parseOperator();
                Object value = parseValue();

                // Store comparison in the function condition itself
                funcCond.setFunctionOperator(op);
                funcCond.setFunctionCompareValue(value);
            }
        }

        return funcCond;
    }

    /**
     * Parse comparison: field operator value
     */
    private Condition parseComparison() throws ParseException {
        TokenInstance fieldToken = consume(Token.IDENTIFIER, "Expected field identifier");
        String fieldId = fieldToken.getValue();
        int line = fieldToken.getLine();
        int column = fieldToken.getColumn();

        // IS EMPTY / IS NOT EMPTY
        if (check(Token.IS_EMPTY)) {
            advance();
            Condition cond = Condition.isEmpty(fieldId);
            cond.setLine(line);
            cond.setColumn(column);
            return cond;
        }

        if (check(Token.IS_NOT_EMPTY)) {
            advance();
            Condition cond = Condition.isNotEmpty(fieldId);
            cond.setLine(line);
            cond.setColumn(column);
            return cond;
        }

        // BETWEEN x AND y
        if (check(Token.BETWEEN)) {
            advance();
            Object min = parseValue();
            consume(Token.AND, "Expected AND in BETWEEN expression");
            Object max = parseValue();
            Condition cond = Condition.between(fieldId, min, max);
            cond.setLine(line);
            cond.setColumn(column);
            return cond;
        }

        // IN (value1, value2, ...)
        if (check(Token.IN)) {
            advance();
            consume(Token.LPAREN, "Expected '(' after IN");
            List<Object> values = parseValueList();
            consume(Token.RPAREN, "Expected ')' after IN values");
            Condition cond = Condition.in(fieldId, values);
            cond.setLine(line);
            cond.setColumn(column);
            return cond;
        }

        // NOT IN (value1, value2, ...)
        if (check(Token.NOT_IN)) {
            advance();
            consume(Token.LPAREN, "Expected '(' after NOT IN");
            List<Object> values = parseValueList();
            consume(Token.RPAREN, "Expected ')' after NOT IN values");
            // Create IN condition and wrap in NOT
            Condition inCond = Condition.in(fieldId, values);
            Condition cond = Condition.not(inCond);
            cond.setLine(line);
            cond.setColumn(column);
            return cond;
        }

        // Standard comparison: field op value
        if (!isComparisonOperator()) {
            throw error(peek(), "Expected comparison operator");
        }

        Operator op = parseOperator();
        Object value = parseValue();

        Condition cond = Condition.comparison(fieldId, op, value);
        cond.setLine(line);
        cond.setColumn(column);
        return cond;
    }

    /**
     * Check if current token is a comparison operator
     */
    private boolean isComparisonOperator() {
        Token t = peek().getType();
        return t == Token.EQ || t == Token.NEQ ||
               t == Token.GT || t == Token.GTE ||
               t == Token.LT || t == Token.LTE ||
               t == Token.CONTAINS || t == Token.STARTS_WITH || t == Token.ENDS_WITH;
    }

    /**
     * Parse comparison operator
     */
    private Operator parseOperator() throws ParseException {
        TokenInstance t = advance();
        switch (t.getType()) {
            case EQ: return Operator.EQ;
            case NEQ: return Operator.NEQ;
            case GT: return Operator.GT;
            case GTE: return Operator.GTE;
            case LT: return Operator.LT;
            case LTE: return Operator.LTE;
            case CONTAINS: return Operator.CONTAINS;
            case STARTS_WITH: return Operator.STARTS_WITH;
            case ENDS_WITH: return Operator.ENDS_WITH;
            default:
                throw error(t, "Expected comparison operator");
        }
    }

    /**
     * Parse a value (string, number, boolean, or identifier)
     */
    private Object parseValue() throws ParseException {
        if (check(Token.STRING)) {
            return advance().getStringValue();
        }
        if (check(Token.NUMBER)) {
            return advance().getNumericValue();
        }
        if (check(Token.YES) || check(Token.TRUE)) {
            advance();
            return true;
        }
        if (check(Token.NO) || check(Token.FALSE)) {
            advance();
            return false;
        }
        if (check(Token.IDENTIFIER)) {
            return advance().getValue();
        }

        throw error(peek(), "Expected value (string, number, boolean, or identifier)");
    }

    /**
     * Parse a list of values
     */
    private List<Object> parseValueList() throws ParseException {
        List<Object> values = new ArrayList<>();
        values.add(parseValue());

        while (check(Token.COMMA)) {
            advance();
            values.add(parseValue());
        }

        return values;
    }

    /**
     * Convert token type to function type
     */
    private FunctionType tokenToFunctionType(Token token) {
        switch (token) {
            case COUNT: return FunctionType.COUNT;
            case SUM: return FunctionType.SUM;
            case AVG: return FunctionType.AVG;
            case MIN: return FunctionType.MIN;
            case MAX: return FunctionType.MAX;
            case HAS_ANY: return FunctionType.HAS_ANY;
            case HAS_ALL: return FunctionType.HAS_ALL;
            case HAS_NONE: return FunctionType.HAS_NONE;
            default: return null;
        }
    }

    /**
     * Count conditions in a condition tree
     */
    private int countConditions(Condition cond) {
        if (cond == null) return 0;

        switch (cond.getType()) {
            case AND:
            case OR:
                return countConditions(cond.getLeft()) + countConditions(cond.getRight());
            case NOT:
            case GROUP:
                return countConditions(cond.getInner());
            default:
                return 1;
        }
    }

    // === Token helpers ===

    private boolean check(Token type) {
        if (isAtEnd()) return false;
        return peek().getType() == type;
    }

    private TokenInstance advance() {
        if (!isAtEnd()) current++;
        return previous();
    }

    private boolean isAtEnd() {
        return peek().getType() == Token.EOF;
    }

    private TokenInstance peek() {
        return tokens.get(current);
    }

    private TokenInstance previous() {
        return tokens.get(current - 1);
    }

    private TokenInstance consume(Token type, String message) throws ParseException {
        if (check(type)) return advance();
        throw error(peek(), message);
    }

    private void skipNewlines() {
        while (check(Token.NEWLINE)) {
            advance();
        }
    }

    private ParseException error(TokenInstance token, String message) {
        return new ParseException(message, token.getLine(), token.getColumn());
    }

    /**
     * Parser exception with location information
     */
    public static class ParseException extends Exception {
        private final int line;
        private final int column;

        public ParseException(String message, int line, int column) {
            super(message);
            this.line = line;
            this.column = column;
        }

        public int getLine() {
            return line;
        }

        public int getColumn() {
            return column;
        }
    }
}
