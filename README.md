# chip8

A CHIP-8 IDE written in Java 11, complete with assembler,debugger,disassembler and emulator.

# Requirements

Maven 3.x, JDK 11+

# Running

    mvn clean package
    java -jar target/chip8.jar

![Screenshot](https://raw.githubusercontent.com/toby1984/chip8/master/screenshot.png)

# Features

- Emulator
  - Complete CHIP-8 emulation
- Debugger
  - single stepping
  - step over subroutine calls
  - unconditional breakpoints (just click on line in debugger window)
- Assembler
  - Instruction set as described in http://devernay.free.fr/hacks/chip8/C8TECH10.HTM
  - Syntax highlighting
  - Build-in help
  - Support for expressions with parentheses and the following operators: - + * / >> << | & ^
  - Support for hexadecimal (0x1234), decimal (1234) and binary (%101101) number literals
  - Support for global ( label: ) and local ( .label) labels
    Labels must not be a reserved word, start with a digit and otherwise contain only digits,letters or underscores
  - Support for .byte / .word / .reserve / .origin directives 
  - Support assigning alias names to registers (.alias) and clearing them (.clearAliases)
  - Support for parameterized macros (.macro) 
- Disassembler
  - Syntax as described in http://devernay.free.fr/hacks/chip8/C8TECH10.HTM
- Sprite viewer
