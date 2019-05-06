package de.codesourcery.chip8.asm.ast;

public class DirectiveNode
{
    public enum Type
    {
        INITIALIZED_MEMORY,
        UNINITIALIZED_MEMORY,
        ALIAS
    }
}
