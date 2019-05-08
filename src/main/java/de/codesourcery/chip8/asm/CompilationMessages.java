package de.codesourcery.chip8.asm;

import de.codesourcery.chip8.asm.ast.ASTNode;
import de.codesourcery.chip8.asm.ast.TextRegion;
import de.codesourcery.chip8.asm.parser.Token;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public class CompilationMessages
{
    public static final class CompilationMessage
    {
        public enum Severity {
            INFO(1),
            WARNING(2),
            ERROR(3);

            public final int level;

            Severity(int value)
            {
                this.level = value;
            }
        }

        public final ZonedDateTime timestamp = ZonedDateTime.now();
        public final Severity severity;
        public final int offset;
        public final int len;
        public final String message;

        public CompilationMessage(String message, Severity severity, int offset) {
            this(message, severity, offset, -1 );
        }

        public CompilationMessage(String message, Severity severity, ASTNode node)
        {
            this(message, severity, node.getRegion() );
        }

        public CompilationMessage(String message, Severity severity, Token token)
        {
            this(message, severity, token.region() );
        }

        public CompilationMessage(String message, Severity severity, TextRegion region)
        {
            this(message, severity, region.getStartingOffset(), region.getLength() );
        }

        public CompilationMessage(String message, Severity severity, int offset, int len)
        {
            this.severity = severity;
            this.offset = offset;
            this.len = len;
            this.message = message;
        }

        public boolean isError() { return severity == Severity.ERROR; };
        public boolean isWarning() { return severity == Severity.WARNING; };
        public boolean isInfo() { return severity == Severity.INFO; };
    }

    public final List<CompilationMessage> messages = new ArrayList<>();

    public List<CompilationMessage> getSorted()
    {
        final List<CompilationMessage> result = new ArrayList<>(this.messages);
        result.sort(
                Comparator.comparingInt( (CompilationMessage a) -> a.offset )
                .thenComparingInt( a -> a.severity.level ) );
        return result;
    }

    public void info(String message, TextRegion region) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.INFO, region ) );
    }

    public void info(String message, ASTNode node) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.INFO, node) );
    }

    public void info(String message, Token token) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.INFO, token) );
    }

    public void info(String message, int offset) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.INFO, offset) );
    }

    public void warn(String message, int offset) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.WARNING, offset) );
    }

    public void warn(String message,TextRegion region) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.WARNING, region ) );
    }

    public void warn(String message,ASTNode node) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.WARNING, node) );
    }

    public void warn(String message,Token token) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.WARNING, token) );
    }

    public void error(String message, int offset) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.ERROR, offset) );
    }

    public void error(String message,TextRegion region) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.ERROR, region ) );
    }

    public void error(String message,ASTNode node) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.ERROR, node) );
    }

    public void error(String message,Token token) {
        messages.add( new CompilationMessage( message, CompilationMessage.Severity.ERROR, token) );
    }

    public boolean hasErrors()
    {
        return messages.stream().anyMatch( CompilationMessage::isError );
    }

    public Stream<CompilationMessage> stream() {
        return messages.stream();
    }
}
