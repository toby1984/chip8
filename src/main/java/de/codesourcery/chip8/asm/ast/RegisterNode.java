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

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

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
    public ASTNode copyThisNode()
    {
        return new RegisterNode(this.regNum, getRegionCopy());
    }

    @Override
    public String toString()
    {
        return "RegisterNode[ "+regNum+" ]";
    }

    public static int parseRegisterNum(String expr) throws IllegalArgumentException
    {
        String expression = expr == null ? expr : expr.trim();
        if (StringUtils.isNotBlank(expression) &&
                expression.length() >= 2 &&
                Character.toLowerCase(expr.charAt(0) ) == 'v')
        {
            boolean allDigits = true;
            for (int i = 1; allDigits && i < expression.length(); i++)
            {
                if (!Character.isDigit(expression.charAt(i)))
                {
                    allDigits = false;
                }
            }
            if (allDigits)
            {
                return Integer.parseInt(expression.substring(1));
            }
        }
        throw new IllegalArgumentException("Not a valid register expression: "+expression);
    }
}
