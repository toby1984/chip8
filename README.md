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
  - step over subroutine call
  - unconditional breakpoints (just click on line in debugger window)
- Assembler
  - Syntax as described in http://devernay.free.fr/hacks/chip8/C8TECH10.HTM
  - TODO: Currently no support for initializing memory locations to specific values
- Disassembler
  - Syntax as described in http://devernay.free.fr/hacks/chip8/C8TECH10.HTM
