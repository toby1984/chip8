package de.codesourcery.chip8.asm;

import java.util.List;
import java.util.Objects;

public enum Operator
{
    // logical operators
    EQUALS(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return Objects.equals( values.get(0) , values.get(1) );
        }
    },
    NOT_EQUALS(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return ! Objects.equals( values.get(0) , values.get(1) );
        }
    },
    LESS_THAN(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return intValue( values.get(0) ) < intValue( values.get(1) );
        }
    },
    GREATER_THAN(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return intValue( values.get(0) ) > intValue( values.get(1) );
        }
    },
    LOGICAL_AND(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return boolValue( values.get(0) ) && boolValue( values.get(1) );
        }

        @Override
        protected void checkArgumentTypes(List<Object> values) {
            assertAllBooleanOperands( values );
        }
    },
    LOGICAL_OR(2) {
        @Override
        public Object doEvaluate(List<Object> values)
        {
            return boolValue( values.get(0) ) || boolValue( values.get(1) );
        }

        @Override
        protected void checkArgumentTypes(List<Object> values) {
            assertAllBooleanOperands( values );
        }
    },
    // numeric operators
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
    SHIFT_LEFT(2) {
        protected Object doEvaluate(List<Object> values)
        {
            return intValue(values.get(0) ) << intValue( values.get(1) );
        }
    },
    SHIFT_RIGHT(2) {
        protected Object doEvaluate(List<Object> values)
        {
            return intValue(values.get(0) ) >> intValue( values.get(1) );
        }
    },
    BITWISE_AND(2) {
        protected Object doEvaluate(List<Object> values)
        {
            return intValue(values.get(0) ) & intValue( values.get(1) );
        }
    },
    BITWISE_OR(2) {
        protected Object doEvaluate(List<Object> values)
        {
            return intValue(values.get(0) ) | intValue( values.get(1) );
        }
    },
    BITWISE_XOR(2) {
        protected Object doEvaluate(List<Object> values)
        {
            return intValue(values.get(0) ) ^ intValue( values.get(1) );
        }
    },
    BITWISE_NEGATION(1) {
        @Override
        protected Object doEvaluate(List<Object> values)
        {
            return ~intValue( values.get(0) );
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
        switch( s )
        {
            // logical operators
            case "==":
                return EQUALS;
            case "!=":
                return NOT_EQUALS;
            case "<":
                return LESS_THAN;
            case ">":
                return GREATER_THAN;
            case "&&":
                return LOGICAL_AND;
            case "||":
                return LOGICAL_OR;
            // numeric operators
            case "&":
                return BITWISE_AND;
            case "|":
                return BITWISE_OR;
            case "^":
                return BITWISE_XOR;
            case "~":
                return BITWISE_NEGATION;
            case ">>":
                return SHIFT_RIGHT;
            case "<<":
                return SHIFT_LEFT;
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

    private static boolean boolValue(Object object) {
        return ((Boolean) object).booleanValue();
    }

    public Object evaluate(List<Object> values)
    {
        checkArgumentCount( values );
        checkArgumentTypes( values );
        return doEvaluate( values );
    }

    protected void checkArgumentCount(List<Object> values) {
        assertOperandCount( values );
    }

    protected void checkArgumentTypes(List<Object> values) {
        assertAllNumericOperands( values );
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

    protected void assertAllBooleanOperands(List<Object> values)
    {
        for (int i = 0; i < values.size(); i++)
        {
            Object value = values.get( i );
            if ( !(value instanceof Boolean) )
            {
                throw new IllegalArgumentException( "Operator " + this + " supports only boolean arguments but " +
                        "argument no. "+(i+1)+" was "+value );
            }
        }
    }
}
