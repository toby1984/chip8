package de.codesourcery.chip8.asm;

public class RegisterNode extends ASTNode
{
    public final int regNum;

    public RegisterNode(int regNum) {
        if ( regNum < 0 || regNum > 15 ) {
            throw new IllegalArgumentException( "Register number of out range: "+regNum );
        }
        this.regNum = regNum;
    }

    @Override
    public String toString()
    {
        return "RegisterNode[ "+regNum+" ]";
    }
}
