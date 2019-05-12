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

/**
 * Available token types.
 * @author tobias.gierke@code-sourcery.de
 */
public enum TokenType
{
    TEXT, // anything that's not one of the other tokens
    IDENTIFIER,
    ASSIGNMENT, // '='
    // numbers
    PARENS_OPEN, // (
    PARENS_CLOSE, // )
    HEX_NUMBER, // 0x1234
    DECIMAL_NUMBER, // 12345
    BINARY_NUMBER, // %1010110
    // v<NUMBER>
    REGISTER,
    CURLY_PARENS_OPEN, // {
    CURLY_PARENS_CLOSE, // }
    DOT, // '.'
    COMMA, // ','
    COLON, // ':'
    WHITESPACE, // tab or space
    NEWLINE, // 0x0a
    SEMICOLON, // ';'
    OPERATOR, // + - * /
    EOF // end of input
}
