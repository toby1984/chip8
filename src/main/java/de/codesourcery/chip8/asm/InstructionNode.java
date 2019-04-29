package de.codesourcery.chip8.asm;

public class InstructionNode extends ASTNode
{
    public final String mnemonic;

    public InstructionNode(String mnemonic)
    {
        this.mnemonic = mnemonic;
    }

    public Parser.Instruction getInstruction()
    {
        return Parser.Instruction.findMatch( this );
    }

    @Override
    public String toString()
    {
        return "InstructionNode[ "+mnemonic+" ]";
    }
}
