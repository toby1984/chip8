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
package de.codesourcery.chip8.asm.ast;

/**
 * Register literal AST node.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class RegisterNode extends ASTNode
{
    public final int regNum;

    public RegisterNode(int regNum,TextRegion region)
    {
        super(region);
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
