/**
 * Copyright 2012 Tobias Gierke <tobias.gierke@code-sourcery.de>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.codesourcery.chip8.asm.parser;

import de.codesourcery.chip8.asm.Assembler;
import de.codesourcery.chip8.asm.Identifier;
import de.codesourcery.chip8.asm.SymbolTable;
import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.CommentNode;
import de.codesourcery.chip8.asm.ast.InstructionNode;
import de.codesourcery.chip8.asm.ast.LabelNode;
import de.codesourcery.chip8.asm.ast.NumberNode;
import de.codesourcery.chip8.asm.ast.RegisterNode;
import de.codesourcery.chip8.asm.ast.TextNode;
import de.codesourcery.chip8.asm.ast.TextRegion;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Parser.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Parser
{
    private final Lexer lexer;

    public Parser(Lexer lexer)
    {
        this.lexer = lexer;
    }

    public ASTNode parse() {

        try {
            return internalParse();
        }
        catch(RuntimeException e)
        {
            printError( e.getMessage(), lexer.peek().offset );
            throw e;
        }
    }

    private ASTNode internalParse() {

        final ASTNode ast = new ASTNode();
        while ( ! lexer.eof() )
        {
            while ( lexer.peek().is(TokenType.NEWLINE) ) {
                lexer.next();
            }
            if ( ! lexer.eof() )
            {
                ASTNode stmt = parseStatement();
                if (stmt == null)
                {
                    throw new RuntimeException("Parse error");
                }
                ast.add(stmt);
            }
        }
        return ast;
    }

    private ASTNode parseStatement()
    {
        final ASTNode statement = new ASTNode();
        final ASTNode label = parseLabel();
        if ( label != null ) {
            statement.add( label );
        }
        final ASTNode insn = parseInstruction();
        if ( insn != null ) {
            statement.add( insn );
        }
        final ASTNode comment = parseComment();
        if ( comment != null ) {
            statement.add( comment );
        }
        if ( statement.children.isEmpty() )
        {
            throw new RuntimeException("Empty statement @ "+lexer.peek().offset);
        }
        return statement;
    }

    private ASTNode parseLabel()
    {
        if ( lexer.peek().is(TokenType.TEXT ) )
        {
            Token tok = lexer.next();
            if ( Identifier.isValid( tok.value) && lexer.peek().is(TokenType.COLON ) )
            {
                lexer.next();
                return new LabelNode( new Identifier( tok.value ) , tok.region() );
            }
            lexer.pushBack( tok );
        }
        return null;
    }

    private ASTNode parseInstruction()
    {
        final Token tok = lexer.peek();
        if ( tok.is(TokenType.TEXT) ) {
            Instruction match= Stream.of( Instruction.values() ).filter(  x->x.mnemonic.equalsIgnoreCase( tok.value) ).findFirst().orElse( null );
            if ( match != null )
            {
                lexer.next();
                final InstructionNode insn = new InstructionNode( match.mnemonic, tok.region() );
                ASTNode op = null;
                boolean required = false;
                while ( ( op = parseOperand() ) != null )
                {
                    required = false;
                    insn.add( op );
                    if ( lexer.peek().is(TokenType.COMMA ) ) {
                        lexer.next();
                        required = true;
                    }
                }
                if ( required ) {
                    throw new RuntimeException("Expected operand after comma @ "+lexer.peek().offset);
                }
                if ( insn.getInstruction() == null ) {
                    throw new RuntimeException("Unknown instruction @ "+tok.offset);
                }
                return insn;
            }
        }
        return null;
    }

    private ASTNode parseOperand()
    {
        Token tok = lexer.peek();
        switch( tok.type )
        {
            case HEX_NUMBER:
            case DECIMAL_NUMBER:
            case BINARY_NUMBER:
                final int value;
                switch( tok.type)
                {
                    case HEX_NUMBER:
                        value = Integer.parseInt( lexer.next().value.substring(2), 16 );
                        break;
                    case DECIMAL_NUMBER:
                        value = Integer.parseInt( lexer.next().value );
                        break;
                    case BINARY_NUMBER:
                        value = Integer.parseInt( lexer.next().value.substring(1) ,2 );
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached: "+tok.type);
                }
                return new NumberNode( value, tok.region());
            case REGISTER:
                final int regNum = Integer.parseInt( lexer.next().value.substring( 1 ) );
                return new RegisterNode( regNum, tok.region() );
            case TEXT:
                return new TextNode( lexer.next().value, tok.region() );
            default:
                // not a valid operand
        }
        return null;
    }

    private ASTNode parseComment()
    {
        final Token tok = lexer.peek();
        if ( tok.is(TokenType.SEMICOLON) )
        {
            lexer.next();
            lexer.setSkipWhitespace( false );
            TextRegion region = tok.region();
            final StringBuilder comment = new StringBuilder();
            try {
                while ( ! lexer.eof() && ! lexer.peek().is(TokenType.NEWLINE) )
                {
                    final Token tok2 = lexer.next();
                    region.merge(tok2.region());
                    comment.append( tok2.value );
                }
            } finally {
                lexer.setSkipWhitespace( true );
            }
            return new CommentNode(comment.toString(), region);
        }
        return null;
    }

    public enum OperandType
    {
        NONE,
        REGISTER, // Vx
        REGISTER_V0, // V0
        NIBBLE,// 4-bit value
        BYTE,// 8-bit value
        ADDRESS, // 12-bit value
        // -- literals
        PRESSED_KEY, // literal 'K'
        INDEX, // literal 'I'
        DELAY_TIMER, //  literal 'DT'
        FONT,// literal 'F'
        BCD, //  literal 'B'
        SOUND_TIMER, // literal 'ST'
        I_INDIRECT; // literal '[I]'

        public boolean matches(ASTNode node)
        {
            if ( node instanceof TextNode )
            {
                if ( this == ADDRESS ) {
                    // we'll assume this is actually an identifier
                    // and the symbol table lookup will succeed
                    return true;
                }
                switch( ((TextNode) node).value.toLowerCase() ) {
                    case "k":
                        return this == PRESSED_KEY;
                    case "i":
                        return this == INDEX;
                    case "dt":
                        return this == DELAY_TIMER;
                    case "st":
                        return this == SOUND_TIMER;
                    case "f":
                        return this == FONT;
                    case "b":
                        return this == BCD;
                    case "[i]":
                        return this == I_INDIRECT;
                }
            }
            if ( node instanceof RegisterNode)
            {
                final int regNum = ((RegisterNode) node).regNum;
                if ( regNum == 0 && this == REGISTER_V0 ) {
                    return true;
                }
                return this == REGISTER;
            }
            if ( node instanceof NumberNode )
            {
                final int value = ((NumberNode) node).value;
                if ( this == NIBBLE ) {
                    return value >= 0 && value <= 15;
                }
                if ( this == BYTE) {
                    return value >= 0 && value <= 255;
                }
                if ( this == ADDRESS) {
                    return value >= 0 && value <= 0xfff;
                }
            }
            return false;
        }
    }

    public enum Instruction
    {
        CLS("cls") {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                context.writeWord( this,0x00e0 );
            }
        },
        RET("ret") {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                context.writeWord( this, 0x00ee );
            }
        },
        JP("jp",OperandType.ADDRESS)
        {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                context.writeWord( this, 0x1000 | assertIn12BitRange( evaluate( instruction.child( 0 ), context ) ) );
            }
        },
        CALL("call",OperandType.ADDRESS) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                context.writeWord( this,0x2000 | assertIn12BitRange( evaluate( instruction.child( 0 ), context ) ) );
            }
        },
        SE("se",OperandType.REGISTER,OperandType.BYTE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 3xkk - SE Vx, byte
                int regNo = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x3000 | regNo << 8 | cnst );
            }
        },
        SNE("SNE",OperandType.REGISTER,OperandType.BYTE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 4xkk - SE Vx, byte
                int regNo = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x4000 | regNo << 8 | cnst );
            }
        },
        SE2("SE",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 5xy0 - SE Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x5000 | regNo0 << 8 | regNo1 << 4);
            }
        },
        LD("LD",OperandType.REGISTER,OperandType.BYTE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 6xkk - LD Vx, byte
                int regNo = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x6000 | regNo << 8 | cnst );
            }
        },
        ADD("ADD",OperandType.REGISTER,OperandType.BYTE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 7xkk - ADD Vx, byte
                int regNo = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x7000 | regNo << 8 | cnst );
            }
        },
        LD2("LD",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy0 - LD Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x8000 | regNo0 << 8 | regNo1 << 4);
            }
        },
        OR("OR",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy1 - OR Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x8001 | regNo0 << 8 | regNo1 << 4);
            }
        },
        AND("AND",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy2 - AND Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x8002 | regNo0 << 8 | regNo1 << 4);
            }
        },
        XOR("XOR",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy3 - XOR Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x8003 | regNo0 << 8 | regNo1 << 4);
            }
        },
        ADD2("ADD",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy4 - ADD Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x8004 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SUB("SUB",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy5 - SUB Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x8005 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SHR("SHR",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy6 - SHR Vx,Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x8006 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SUBN("SUN",OperandType.REGISTER,OperandType.REGISTER)  {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xy7 - SUBN Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x8007 | regNo0 << 8 | regNo1 << 4);
            }
        },
        SHL("SHL",OperandType.REGISTER)  {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 8xyE - SHL Vx,Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x800e | regNo0 << 8 | regNo1 << 4);
            }
        },
        SNE2("SNE",OperandType.REGISTER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // 9xy0 - SNE Vx, Vy
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int regNo1 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0x9000 | regNo0 << 8 | regNo1 << 4);
            }
        },
        LD3("LD",OperandType.INDEX,OperandType.ADDRESS) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Annn - LD I, addr
                int cnst = assertIn12BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xA000 | cnst );
            }
        },
        JP2("JP",OperandType.REGISTER_V0,OperandType.ADDRESS) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Bnnn - JP V0, addr
                int cnst = assertIn12BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xB000 | cnst );
            }
        },
        RND("RND",OperandType.REGISTER,OperandType.BYTE)  {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Cxkk - RND Vx, byte
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int cnst = assertIn8BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xc000 | regNo0 << 8 | cnst );
            }
        },
        DRW("DRW",OperandType.REGISTER,OperandType.REGISTER,OperandType.NIBBLE) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Dxyn - DRW Vx, Vy, nibble
                int x = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                int y = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                int h = assertIn4BitRange( evaluate( instruction.child(2), context ) );
                context.writeWord(this, 0xd000 | x << 8 | y << 4 | h);
            }
        },
        SKP("SKP",OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Ex9E - SKP Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                context.writeWord(this, 0xe09e | regNo0 << 8 );
            }
        },
        SKNP("SKNP",OperandType.REGISTER){
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // ExA1 - SKNP Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                context.writeWord(this, 0xe0a1 | regNo0 << 8 );
            }
        },
        LD4("LD",OperandType.REGISTER,OperandType.DELAY_TIMER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx07 - LD Vx, DT
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                context.writeWord(this, 0xf007 | regNo0 << 8 );
            }
        },
        LD5("LD",OperandType.REGISTER,OperandType.PRESSED_KEY ) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx0A - LD Vx, K
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                context.writeWord(this, 0xf00a | regNo0 << 8 );
            }
        },
        LD6("LD",OperandType.DELAY_TIMER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx15 - LD DT, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xf015 | regNo0 << 8 );
            }
        },
        LD7("LD",OperandType.SOUND_TIMER,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx18 - LD ST, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xf018 | regNo0 << 8 );
            }
        },
        ADD3("ADD",OperandType.INDEX,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx1E - ADD I, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xf01e | regNo0 << 8 );
            }
        },
        LD8("LD",OperandType.FONT,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx29 - LD F, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xf029 | regNo0 << 8 );
            }
        },
        LD9("LD",OperandType.BCD,OperandType.REGISTER) {
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx33 - LD B, Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xf033 | regNo0 << 8 );
            }
        },
        LD10("LD",OperandType.I_INDIRECT,OperandType.REGISTER){
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx55 - LD [I], Vx
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(1), context ) );
                context.writeWord(this, 0xf055 | regNo0 << 8 );
            }
        },
        LD11("LD",OperandType.REGISTER,OperandType.I_INDIRECT){
            @Override
            public void compile(InstructionNode instruction, Assembler.CompilationContext context)
            {
                // Fx65 - LD Vx, [I]
                int regNo0 = assertIn4BitRange( evaluate( instruction.child(0), context ) );
                context.writeWord(this, 0xf065 | regNo0 << 8 );
            }
        };

        private final String mnemonic;
        private final OperandType operand0;
        private final OperandType operand1;
        private final OperandType operand2;

        private Instruction(String mnemonic)
        {
            this(mnemonic,OperandType.NONE,OperandType.NONE);
        }

        public OperandType operandType(int no) {
            switch(no) {
                case 0:
                    return operand0;
                case 1:
                    return operand1;
                case 2:
                    return operand2;
            }
            throw new IllegalArgumentException( "Operand no. out-of-range: "+no );
        }

        protected int assertIn12BitRange(int value)
        {
            if ( value < 0 || value > 0xfff) {
                throw new IllegalArgumentException( "Value not in 12-bit range: 0x"+Integer.toHexString( value ) );
            }
            return value;
        }

        protected int assertIn8BitRange(int value)
        {
            if ( value < 0 || value > 0xff) {
                throw new IllegalArgumentException( "Value not in 8-bit range: 0x"+Integer.toHexString( value ) );
            }
            return value;
        }

        protected int assertIn4BitRange(int value)
        {
            if ( value < 0 || value > 0b1111) {
                throw new IllegalArgumentException( "Value not in 4-bit range: 0x"+Integer.toHexString( value ) );
            }
            return value;
        }

        public static Instruction findMatch(InstructionNode insn)
        {
            final List<Instruction> candidates =
                Stream.of( values() ).filter( x -> matchesOperandTypes( insn, x ) ).collect( Collectors.toList() );
            switch ( candidates.size() ) {
                case 0 :
                    return null;
                case 1:
                    return candidates.get(0);
                default:
                    throw new RuntimeException("Internal error, found multiple candidate instructions: "+candidates);
            }
        }

        protected int evaluate(ASTNode node, Assembler.CompilationContext ctx)
        {
            if ( node instanceof NumberNode)
            {
                return ((NumberNode) node).value;
            }
            if ( node instanceof TextNode && ((TextNode) node).isValidIdentifier() )
            {
                final Identifier id = new Identifier( ((TextNode) node).value );
                final SymbolTable.Symbol symbol = ctx.symbolTable.get( id );
                if ( symbol == null ) {
                    throw new RuntimeException("Unknown symbol '"+id.value+"'");
                }
                if ( !(symbol.value instanceof Number) ) {
                    throw new RuntimeException("Symbol '"+symbol.name+"' has non-numeric value "+symbol.value);
                }
                return ((Number) symbol.value).intValue();
            }
            if ( node instanceof RegisterNode ) {
                return ((RegisterNode) node).regNum;
            }
            throw new RuntimeException("Internal error, don't know how to evaluate "+node);
        }

        public abstract void compile(InstructionNode instruction, Assembler.CompilationContext context);

        private static boolean matchesOperandTypes(InstructionNode node,Instruction insn)
        {
            if ( node.childCount() != insn.operandCount() ) {
                return false;
            }
            if ( ! insn.mnemonic.equalsIgnoreCase( node.mnemonic ) ) {
                return false;
            }
            for ( int i = 0, len = insn.operandCount() ; i < len ; i++ )
            {
                OperandType expected = insn.operandType( i );
                if ( ! expected.matches( node.child(i) ) ) {
                    return false;
                }
            }
            return true;
        }

        private Instruction(String mnemonic,OperandType op0)
        {
            this(mnemonic,op0,OperandType.NONE);
        }

        private Instruction(String mnemonic,OperandType op0,OperandType op1)
        {
            this(mnemonic,op0,op1,OperandType.NONE);
        }

        private Instruction(String mnemonic,OperandType op0,OperandType op1,OperandType op2)
        {
            this.mnemonic = mnemonic;
            operand0 = op0;
            operand1 = op1;
            operand2 = op2;
        }

        public OperandType operand0() {
            return operand0;
        }

        public OperandType operand1() {
            return operand1;
        }

        public OperandType operand2() {
            return operand2;
        }

        public int operandCount()
        {
            int count = 0;
            if ( operand0 != OperandType.NONE ) {
                count++;
            }
            if ( operand1 != OperandType.NONE ) {
                count++;
            }
            if ( operand2 != OperandType.NONE ) {
                count++;
            }
            return count;
        }
    }

    private void printError(String msg,int offset)
    {
        final String text = lexer.getScanner().getText();
        final String[] lines = text.split("\n");
        int lineStartOffset = 0;
        int lineNo = 1;
        for ( String line : lines )
        {
            final int lineEnd = lineStartOffset + line.length();
//            System.err.println("Line "+lineNo+" ("+lineStartOffset+" - "+lineEnd+"): "+line);
            if ( lineStartOffset <= offset && offset <= lineEnd )
            {
                int regionStart = Math.max( lineStartOffset, offset-10);
                int regionEnd = Math.min( lineEnd, offset+10);
                final String errLine =
                    line.substring( regionStart-lineStartOffset, regionEnd-lineStartOffset );
                final int col = offset - lineStartOffset;
                System.err.println( "Error at line "+lineNo+", column "+col);
                System.err.println( errLine );
                final int indent = offset - regionStart;
                System.err.println( StringUtils.repeat(' ',indent)+"^ "+msg);
                return;
            }
            lineStartOffset = lineEnd+1;
            lineNo++;
        }
        if ( lines.length > 0 && offset == lineStartOffset)
        {
            final int col = offset - lineStartOffset;
            System.err.println( "Error at line "+lineNo+", column "+col);
            System.err.println( lines[lines.length - 1] );
        }
        else
        {
            System.err.println( "Failed to find src line for offset " + offset );
        }
        System.err.println("ERROR: "+msg);
    }
}