package de.codesourcery.chip8.asm.parser;

public final class Scanner
{
    private final String input;
    private int index;

    public Scanner(String input) {
        this.input = input;
    }

    public String getText() {
        return input;
    }

    private void assertNotEOF()
    {
        if ( eof() ) {
            throw new IllegalStateException("Already at EOF");
        }
    }

    public boolean eof() {
        return index >= input.length();
    }

    public char next()
    {
        assertNotEOF();
        return input.charAt(index++);
    }

    public char peek() {
        assertNotEOF();
        return input.charAt(index);
    }

    public int offset() {
        return index;
    }

    public void setOffset(int offset) {
        this.index=offset;
    }
}
