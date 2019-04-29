package de.codesourcery.chip8.asm.parser;

public final class Token
{
    public final TokenType type;
    public final String value;
    public final int offset;

    public Token(TokenType type, char value, int offset) {
        this(type,Character.toString(value),offset);
    }

    public Token(TokenType type, String value, int offset)
    {
        this.type = type;
        this.value = value;
        this.offset = offset;
    }

    public boolean is(TokenType type) {
        return type.equals( this.type );
    }

    @Override
    public String toString()
    {
        return "Token{" +
               "type=" + type +
               ", value='" + value + '\'' +
               ", offset=" + offset +
               '}';
    }
}
