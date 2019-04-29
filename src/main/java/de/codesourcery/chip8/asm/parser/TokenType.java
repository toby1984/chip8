package de.codesourcery.chip8.asm.parser;

public enum TokenType
{
    TEXT,
    HEX_NUMBER,
    DECIMAL_NUMBER,
    BINARY_NUMBER,
    REGISTER,
    COMMA,
    COLON,
    WHITESPACE,
    NEWLINE,
    SEMICOLON,
    EOF
}
