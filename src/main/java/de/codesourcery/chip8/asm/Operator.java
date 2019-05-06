package de.codesourcery.chip8.asm;

import java.util.List;

public enum Operator
{
    PLUS(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return intValue( values.get(0) ) + intValue( values.get(1) );
        }
    },
    MINUS(2)
    {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return intValue( values.get(0) ) - intValue( values.get(1) );
        }
    },
    UNARY_MINUS(1) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return -intValue( values.get(0) );
        }
    },
    MULTIPLY(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return intValue( values.get(0) ) * intValue( values.get(1) );
        }
    },
    DIVIDE(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return intValue( values.get(0) ) / intValue( values.get(1) );
        }
    };

    private final int operandCount;

    private Operator(int operandCount) {
        this.operandCount = operandCount;
    }

    public static Operator parseOperator(String s)
    {
        switch( s ) {
            case "+":
                return PLUS;
            case "-":
                return MINUS;
            case "*":
                return MULTIPLY;
            case "/":
                return DIVIDE;
        }
        throw new IllegalArgumentException( "Not a valid operator: '"+s+"'" );
    }

    private static int intValue(Object object) {
        return ((Number) object).intValue();
    }

    public Object evaluate(List<Object> values)
    {
        assertOperandCount( values );
        assertAllNumericOperands( values );
        return doEvaluate( values );
    }

    protected abstract Object doEvaluate(List<Object> values);

    protected void assertOperandCount(List<Object> values) {
        assertOperandCount( values, operandCount );
    }

    protected void assertOperandCount(List<Object> values,int expected)
    {
        if ( values ==null || values.size() != expected ) {
            throw new IllegalArgumentException( "Operator "+this+" expected "+expected+" arguments" );
        }
    }

    protected void assertAllNumericOperands(List<Object> values)
    {
        for (int i = 0; i < values.size(); i++)
        {
            Object value = values.get( i );
            if ( !(value instanceof Number) )
            {
                throw new IllegalArgumentException( "Operator " + this + " supports only numeric arguments but " +
                                                    "argument no. "+(i+1)+" was "+value );
            }
        }
    }
}
