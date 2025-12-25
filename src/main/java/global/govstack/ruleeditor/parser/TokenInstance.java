package global.govstack.ruleeditor.parser;

/**
 * Represents a single token instance with its type, value, and position.
 */
public class TokenInstance {
    
    private final Token type;
    private final String value;
    private final int line;
    private final int column;
    
    public TokenInstance(Token type, String value, int line, int column) {
        this.type = type;
        this.value = value;
        this.line = line;
        this.column = column;
    }
    
    public Token getType() {
        return type;
    }
    
    public String getValue() {
        return value;
    }
    
    public int getLine() {
        return line;
    }
    
    public int getColumn() {
        return column;
    }
    
    /**
     * Check if this token matches the given type
     */
    public boolean is(Token type) {
        return this.type == type;
    }
    
    /**
     * Check if this token matches any of the given types
     */
    public boolean isAny(Token... types) {
        for (Token t : types) {
            if (this.type == t) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the numeric value if this is a NUMBER token
     */
    public double getNumericValue() {
        if (type != Token.NUMBER) {
            throw new IllegalStateException("Token is not a NUMBER: " + type);
        }
        return Double.parseDouble(value);
    }
    
    /**
     * Get the string value without quotes if this is a STRING token
     */
    public String getStringValue() {
        if (type != Token.STRING) {
            return value;
        }
        // Remove surrounding quotes
        if (value.length() >= 2) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
    
    @Override
    public String toString() {
        return String.format("%s('%s') at %d:%d", type, value, line, column);
    }
}
