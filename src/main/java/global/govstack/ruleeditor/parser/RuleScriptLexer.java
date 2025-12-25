package global.govstack.ruleeditor.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lexer (Tokenizer) for Rules Script.
 *
 * Converts raw Rules Script text into a stream of tokens.
 * Handles:
 * - Keywords (RULE, WHEN, TYPE, etc.)
 * - Multi-word keywords (IS EMPTY, PASS MESSAGE, etc.)
 * - Operators (=, !=, >=, etc.)
 * - Strings (double or single quoted)
 * - Numbers (integers and decimals, positive and negative)
 * - Identifiers (field names, including dot notation)
 * - Comments (# single line)
 */
public class RuleScriptLexer {

    private final String input;
    private int pos = 0;
    private int line = 1;
    private int column = 1;

    // Keywords mapping
    private static final Map<String, Token> KEYWORDS = new HashMap<>();

    static {
        // Rule structure keywords
        KEYWORDS.put("RULE", Token.RULE);
        KEYWORDS.put("TYPE", Token.TYPE);
        KEYWORDS.put("CATEGORY", Token.CATEGORY);
        KEYWORDS.put("MANDATORY", Token.MANDATORY);
        KEYWORDS.put("ORDER", Token.ORDER);
        KEYWORDS.put("WHEN", Token.WHEN);
        KEYWORDS.put("SCORE", Token.SCORE);
        KEYWORDS.put("WEIGHT", Token.WEIGHT);

        // Rule types
        KEYWORDS.put("INCLUSION", Token.INCLUSION);
        KEYWORDS.put("EXCLUSION", Token.EXCLUSION);
        KEYWORDS.put("PRIORITY", Token.PRIORITY);
        KEYWORDS.put("BONUS", Token.BONUS);

        // Boolean values
        KEYWORDS.put("YES", Token.YES);
        KEYWORDS.put("NO", Token.NO);
        KEYWORDS.put("TRUE", Token.TRUE);
        KEYWORDS.put("FALSE", Token.FALSE);
        KEYWORDS.put("Y", Token.YES);
        KEYWORDS.put("N", Token.NO);

        // Logical operators
        KEYWORDS.put("AND", Token.AND);
        KEYWORDS.put("OR", Token.OR);
        KEYWORDS.put("NOT", Token.NOT);

        // Comparison operators (single word)
        KEYWORDS.put("BETWEEN", Token.BETWEEN);
        KEYWORDS.put("IN", Token.IN);
        KEYWORDS.put("CONTAINS", Token.CONTAINS);

        // Aggregation functions
        KEYWORDS.put("COUNT", Token.COUNT);
        KEYWORDS.put("SUM", Token.SUM);
        KEYWORDS.put("AVG", Token.AVG);
        KEYWORDS.put("MIN", Token.MIN);
        KEYWORDS.put("MAX", Token.MAX);

        // Grid check functions
        KEYWORDS.put("HAS_ANY", Token.HAS_ANY);
        KEYWORDS.put("HAS_ALL", Token.HAS_ALL);
        KEYWORDS.put("HAS_NONE", Token.HAS_NONE);
    }

    public RuleScriptLexer(String input) {
        this.input = input != null ? input : "";
    }

    /**
     * Tokenize the entire input and return list of tokens.
     */
    public List<TokenInstance> tokenize() {
        List<TokenInstance> tokens = new ArrayList<>();

        while (!isAtEnd()) {
            TokenInstance token = nextToken();
            if (token != null && token.getType() != Token.COMMENT) {
                tokens.add(token);
            }
        }

        // Add EOF token
        tokens.add(new TokenInstance(Token.EOF, "", line, column));

        return tokens;
    }

    /**
     * Get the next token from the input.
     */
    public TokenInstance nextToken() {
        skipWhitespace();

        if (isAtEnd()) {
            return new TokenInstance(Token.EOF, "", line, column);
        }

        int startLine = line;
        int startColumn = column;
        char c = peek();

        // Comments
        if (c == '#') {
            return scanComment(startLine, startColumn);
        }

        // Strings
        if (c == '"' || c == '\'') {
            return scanString(startLine, startColumn);
        }

        // Numbers (including negative)
        if (Character.isDigit(c) || (c == '-' && Character.isDigit(peekNext()))) {
            return scanNumber(startLine, startColumn);
        }

        // Identifiers and keywords
        if (Character.isLetter(c) || c == '_') {
            return scanIdentifierOrKeyword(startLine, startColumn);
        }

        // Operators and punctuation
        return scanOperator(startLine, startColumn);
    }

    /**
     * Scan a comment (from # to end of line)
     */
    private TokenInstance scanComment(int startLine, int startColumn) {
        StringBuilder sb = new StringBuilder();
        advance(); // skip #

        while (!isAtEnd() && peek() != '\n') {
            sb.append(advance());
        }

        return new TokenInstance(Token.COMMENT, sb.toString().trim(), startLine, startColumn);
    }

    /**
     * Scan a string literal (double or single quoted)
     */
    private TokenInstance scanString(int startLine, int startColumn) {
        char quote = advance();
        StringBuilder sb = new StringBuilder();
        sb.append(quote);

        while (!isAtEnd() && peek() != quote) {
            if (peek() == '\n') {
                // Unterminated string
                break;
            }
            if (peek() == '\\' && peekNext() == quote) {
                // Escaped quote
                sb.append(advance());
            }
            sb.append(advance());
        }

        if (!isAtEnd() && peek() == quote) {
            sb.append(advance()); // closing quote
        }

        return new TokenInstance(Token.STRING, sb.toString(), startLine, startColumn);
    }

    /**
     * Scan a number (integer or decimal)
     */
    private TokenInstance scanNumber(int startLine, int startColumn) {
        StringBuilder sb = new StringBuilder();

        // Handle negative sign
        if (peek() == '-') {
            sb.append(advance());
        }

        // Integer part
        while (!isAtEnd() && Character.isDigit(peek())) {
            sb.append(advance());
        }

        // Decimal part
        if (!isAtEnd() && peek() == '.' && Character.isDigit(peekNext())) {
            sb.append(advance()); // the dot
            while (!isAtEnd() && Character.isDigit(peek())) {
                sb.append(advance());
            }
        }

        return new TokenInstance(Token.NUMBER, sb.toString(), startLine, startColumn);
    }

    /**
     * Scan an identifier or keyword.
     * Handles multi-word keywords like "IS EMPTY", "NOT IN", "PASS MESSAGE".
     */
    private TokenInstance scanIdentifierOrKeyword(int startLine, int startColumn) {
        StringBuilder sb = new StringBuilder();

        // First word
        while (!isAtEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            sb.append(advance());
        }

        String word = sb.toString().toUpperCase();

        // Check for multi-word keywords
        skipSpacesOnly();

        // IS EMPTY / IS NOT EMPTY
        if (word.equals("IS")) {
            String nextWord = peekWord();
            if (nextWord.equals("EMPTY")) {
                consumeWord();
                return new TokenInstance(Token.IS_EMPTY, "IS EMPTY", startLine, startColumn);
            }
            if (nextWord.equals("NOT")) {
                consumeWord();
                skipSpacesOnly();
                if (peekWord().equals("EMPTY")) {
                    consumeWord();
                    return new TokenInstance(Token.IS_NOT_EMPTY, "IS NOT EMPTY", startLine, startColumn);
                }
            }
        }

        // NOT IN
        if (word.equals("NOT")) {
            String nextWord = peekWord();
            if (nextWord.equals("IN")) {
                consumeWord();
                return new TokenInstance(Token.NOT_IN, "NOT IN", startLine, startColumn);
            }
        }

        // STARTS WITH / ENDS WITH
        if (word.equals("STARTS") || word.equals("ENDS")) {
            String nextWord = peekWord();
            if (nextWord.equals("WITH")) {
                consumeWord();
                if (word.equals("STARTS")) {
                    return new TokenInstance(Token.STARTS_WITH, "STARTS WITH", startLine, startColumn);
                } else {
                    return new TokenInstance(Token.ENDS_WITH, "ENDS WITH", startLine, startColumn);
                }
            }
        }

        // PASS MESSAGE / FAIL MESSAGE
        if (word.equals("PASS") || word.equals("FAIL")) {
            String nextWord = peekWord();
            if (nextWord.equals("MESSAGE")) {
                consumeWord();
                if (word.equals("PASS")) {
                    return new TokenInstance(Token.PASS_MESSAGE, "PASS MESSAGE", startLine, startColumn);
                } else {
                    return new TokenInstance(Token.FAIL_MESSAGE, "FAIL MESSAGE", startLine, startColumn);
                }
            }
        }

        // Check if it's a keyword
        Token keywordToken = KEYWORDS.get(word);
        if (keywordToken != null) {
            return new TokenInstance(keywordToken, sb.toString(), startLine, startColumn);
        }

        // Check for dot notation (field.subfield)
        // Return to original case for identifiers
        String identifier = sb.toString();
        while (!isAtEnd() && peek() == '.') {
            sb.append(advance()); // the dot
            while (!isAtEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
                sb.append(advance());
            }
            identifier = sb.toString();
        }

        return new TokenInstance(Token.IDENTIFIER, identifier, startLine, startColumn);
    }

    /**
     * Scan operators and punctuation
     */
    private TokenInstance scanOperator(int startLine, int startColumn) {
        char c = advance();

        switch (c) {
            case '(':
                return new TokenInstance(Token.LPAREN, "(", startLine, startColumn);
            case ')':
                return new TokenInstance(Token.RPAREN, ")", startLine, startColumn);
            case ',':
                return new TokenInstance(Token.COMMA, ",", startLine, startColumn);
            case ':':
                return new TokenInstance(Token.COLON, ":", startLine, startColumn);
            case '.':
                return new TokenInstance(Token.DOT, ".", startLine, startColumn);
            case '+':
                return new TokenInstance(Token.PLUS, "+", startLine, startColumn);
            case '-':
                return new TokenInstance(Token.MINUS, "-", startLine, startColumn);
            case '=':
                return new TokenInstance(Token.EQ, "=", startLine, startColumn);
            case '!':
                if (peek() == '=') {
                    advance();
                    return new TokenInstance(Token.NEQ, "!=", startLine, startColumn);
                }
                return new TokenInstance(Token.NOT, "!", startLine, startColumn);
            case '>':
                if (peek() == '=') {
                    advance();
                    return new TokenInstance(Token.GTE, ">=", startLine, startColumn);
                }
                return new TokenInstance(Token.GT, ">", startLine, startColumn);
            case '<':
                if (peek() == '=') {
                    advance();
                    return new TokenInstance(Token.LTE, "<=", startLine, startColumn);
                }
                return new TokenInstance(Token.LT, "<", startLine, startColumn);
            case '\n':
                line++;
                column = 1;
                return new TokenInstance(Token.NEWLINE, "\\n", startLine, startColumn);
            default:
                return new TokenInstance(Token.UNKNOWN, String.valueOf(c), startLine, startColumn);
        }
    }

    // === Helper methods ===

    private boolean isAtEnd() {
        return pos >= input.length();
    }

    private char peek() {
        if (isAtEnd()) return '\0';
        return input.charAt(pos);
    }

    private char peekNext() {
        if (pos + 1 >= input.length()) return '\0';
        return input.charAt(pos + 1);
    }

    private char advance() {
        char c = input.charAt(pos);
        pos++;
        column++;
        return c;
    }

    private void skipWhitespace() {
        while (!isAtEnd()) {
            char c = peek();
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '\n') {
                advance();
                line++;
                column = 1;
            } else {
                break;
            }
        }
    }

    private void skipSpacesOnly() {
        while (!isAtEnd() && (peek() == ' ' || peek() == '\t')) {
            advance();
        }
    }

    /**
     * Peek at the next word without consuming it
     */
    private String peekWord() {
        int savedPos = pos;
        int savedColumn = column;

        StringBuilder sb = new StringBuilder();
        while (!isAtEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            sb.append(advance());
        }

        // Restore position
        pos = savedPos;
        column = savedColumn;

        return sb.toString().toUpperCase();
    }

    /**
     * Consume (skip) the next word
     */
    private void consumeWord() {
        while (!isAtEnd() && (Character.isLetterOrDigit(peek()) || peek() == '_')) {
            advance();
        }
    }
}
