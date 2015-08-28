package com.shapesecurity.csp;

import com.shapesecurity.csp.Tokeniser.TokeniserException;
import com.shapesecurity.csp.data.*;
import com.shapesecurity.csp.directiveValues.*;
import com.shapesecurity.csp.directives.*;
import com.shapesecurity.csp.interfaces.Show;
import com.shapesecurity.csp.tokens.DirectiveNameToken;
import com.shapesecurity.csp.tokens.DirectiveValueToken;
import com.shapesecurity.csp.tokens.Token;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;

public class Parser {

    @Nonnull
    private final Origin origin;

    @Nonnull
    public static Policy parse(@Nonnull String sourceText, @Nonnull Origin origin) throws ParseException, TokeniserException {
        return new Parser(Tokeniser.tokenise(sourceText), origin, null).parsePrivate();
    }

    @Nonnull
    public static Policy parse(@Nonnull String sourceText, @Nonnull Origin origin, @Nonnull Collection<Warning> warningsOut) throws ParseException, TokeniserException {
        return new Parser(Tokeniser.tokenise(sourceText), origin, warningsOut).parsePrivate();
    }

    @Nonnull
    public static Policy parse(@Nonnull String sourceText, @Nonnull String origin) throws ParseException, TokeniserException {
        return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), null).parsePrivate();
    }

    @Nonnull
    public static Policy parse(@Nonnull String sourceText, @Nonnull String origin, @Nonnull Collection<Warning> warningsOut) throws ParseException, TokeniserException {
        return new Parser(Tokeniser.tokenise(sourceText), URI.parse(origin), warningsOut).parsePrivate();
    }

    @Nonnull
    protected final Token[] tokens;
    protected int index = 0;

    @Nullable
    protected Collection<Warning> warningsOut;

    protected Parser(@Nonnull Token[] tokens, @Nonnull Origin origin, @Nullable Collection<Warning> warningsOut) {
        this.origin = origin;
        this.tokens = tokens;
        this.warningsOut = warningsOut;
    }

    @Nonnull
    protected Warning createWarning(@Nonnull String message) {
        return new Warning(message);
    }

    private void warn(@Nonnull String message) {
        if (this.warningsOut != null) {
            this.warningsOut.add(this.createWarning(message));
        }
    }

    @Nonnull
    private Token advance() {
        return this.tokens[this.index++];
    }

    protected boolean hasNext() {
        return this.index < this.tokens.length;
    }

    private boolean hasNext(@Nonnull String value) {
        return this.hasNext() && value.equals(this.tokens[this.index].value);
    }

    private boolean eat(@Nonnull String token) {
        if (this.hasNext(token)) {
            this.advance();
            return true;
        }
        return false;
    }

    @Nonnull
    protected ParseException createUnexpectedEOF(@Nonnull String message) {
        return this.createError(message);
    }

    @Nonnull
    protected ParseException createError(@Nonnull String message) {
        return new ParseException(message);
    }

    @Nonnull
    protected Policy parsePrivate() throws ParseException {
        Policy policy = new Policy(this.origin);
        while (this.hasNext()) {
            if (this.eat(";")) continue;
            policy.addDirective(this.parseDirective());
            if (!this.eat(";")) {
                if (this.hasNext()) {
                    throw this.createError("expecting semicolon or end of policy but found " + this.advance().value);
                } else {
                    break;
                }
            }
        }
        return policy;
    }

    @Nonnull
    private Directive<?> parseDirective() throws ParseException {
        Token token = this.advance();
        if (token instanceof DirectiveNameToken) {
            switch (((DirectiveNameToken) token).subtype) {
                case BaseUri: return new BaseUriDirective(this.parseSourceList());
                case ChildSrc: return new ChildSrcDirective(this.parseSourceList());
                case ConnectSrc: return new ConnectSrcDirective(this.parseSourceList());
                case DefaultSrc: return new DefaultSrcDirective(this.parseSourceList());
                case FontSrc: return new FontSrcDirective(this.parseSourceList());
                case FormAction: return new FormActionDirective(this.parseSourceList());
                case FrameAncestors: return new FrameAncestorsDirective(this.parseAncestorSourceList());
                case FrameSrc:
                    this.warn("The frame-src directive is deprecated as of CSP version 1.1. Authors who wish to govern nested browsing contexts SHOULD use the child-src directive instead.");
                    return new FrameSrcDirective(this.parseSourceList());
                case ImgSrc: return new ImgSrcDirective(this.parseSourceList());
                case MediaSrc: return new MediaSrcDirective(this.parseSourceList());
                case ObjectSrc: return new ObjectSrcDirective(this.parseSourceList());
                case PluginTypes: return new PluginTypesDirective(this.parseMediaTypeList());
                case ReportUri: return new ReportUriDirective(this.parseUriList());
                case Sandbox: return new SandboxDirective(this.parseSandboxTokenList());
                case ScriptSrc: return new ScriptSrcDirective(this.parseSourceList());
                case StyleSrc: return new StyleSrcDirective(this.parseSourceList());
                case Referrer:
                case UpgradeInsecureRequests:
                    throw this.createError("The " + ((DirectiveNameToken) token).value + " directive is not in the CSP specification yet.");
                case Allow:
                    throw this.createError("The allow directive has been replaced with default-src and is not in the CSP specification.");
                case Options:
                    throw this.createError("The options directive has been replaced with 'unsafe-inline' and 'unsafe-eval' and is not in the CSP specification.");
            }
        }
        throw this.createError("expecting directive-name but found " + token.value);
    }

    @Nonnull
    private Set<MediaType> parseMediaTypeList() throws ParseException {
        Set<MediaType> mediaTypes = new LinkedHashSet<>();
        if (this.hasNext(";")) {
            throw this.createError("media-type-list must contain at least one media-type");
        }
        if (!this.hasNext()) {
            throw this.createUnexpectedEOF("media-type-list must contain at least one media-type");
        }
        mediaTypes.add(this.parseMediaType());
        while (this.hasNext() && !this.hasNext(";")) {
            mediaTypes.add(this.parseMediaType());
        }
        return mediaTypes;
    }

    @Nonnull
    private MediaType parseMediaType() throws ParseException {
        Token token = this.advance();
        Matcher matcher = Constants.mediaTypePattern.matcher(token.value);
        if (matcher.find()) {
            return new MediaType(matcher.group("type"), matcher.group("subtype"));
        }
        throw this.createError("expecting media-type but found " + token.value);
    }

    @Nonnull
    private Set<SourceExpression> parseSourceList() throws ParseException {
        Set<SourceExpression> sourceExpressions = new LinkedHashSet<>();
        if (this.eat("'none'")) {
            sourceExpressions.add(None.INSTANCE);
            return sourceExpressions;
        }
        while (this.hasNext() && !this.hasNext(";")) {
            sourceExpressions.add(this.parseSourceExpression());
        }
        return sourceExpressions;
    }

    @Nonnull
    private SourceExpression parseSourceExpression() throws ParseException {
        Token token = this.advance();
        if (token instanceof DirectiveValueToken) {
            switch (token.value) {
                case "'self'":
                    return KeywordSource.Self;
                case "'unsafe-inline'":
                    return KeywordSource.UnsafeInline;
                case "'unsafe-eval'":
                    return KeywordSource.UnsafeEval;
                case "'unsafe-redirect'":
                    this.warn("'unsafe-redirect' has been removed from CSP as of version 2.0");
                    return KeywordSource.UnsafeRedirect;
                default:
                    if (token.value.startsWith("'nonce-")) {
                        Base64Value b;
                        try {
                            b = new Base64Value(token.value.substring(7, token.value.length() - 1));
                        } catch (IllegalArgumentException | StringIndexOutOfBoundsException e) {
                            throw this.createError(e.getMessage());
                        }
                        return new NonceSource(b);
                    } else if (token.value.startsWith("'sha")) {
                        HashSource.HashAlgorithm algo;
                        switch (token.value.substring(4, 7)) {
                            case "256":
                                algo = HashSource.HashAlgorithm.SHA256;
                                break;
                            case "384":
                                algo = HashSource.HashAlgorithm.SHA384;
                                break;
                            case "512":
                                algo = HashSource.HashAlgorithm.SHA512;
                                break;
                            default:
                                throw this.createError("unrecognised hash algorithm " + token.value.substring(1, 7));
                        }
                        Base64Value b;
                        try {
                            b = new Base64Value(token.value.substring(8, token.value.length() - 1));
                        } catch (IllegalArgumentException e) {
                            throw this.createError(e.getMessage());
                        }
                        return new HashSource(algo, b);
                    } else if (token.value.matches("^" + Constants.schemePart + ":$")) {
                        return new SchemeSource(token.value.substring(0, token.value.length() - 1));
                    } else {
                        Matcher matcher = Constants.hostSourcePattern.matcher(token.value);
                        if (matcher.find()) {
                            String scheme = matcher.group("scheme");
                            if (scheme != null) scheme = scheme.substring(0, scheme.length() - 3);
                            String port = matcher.group("port");
                            port = port == null ? "" : port.substring(1, port.length());
                            String host = matcher.group("host");
                            String path = matcher.group("path");
                            return new HostSource(scheme, host, port, path);
                        }
                    }
            }
        }
        throw this.createError("expecting source-expression but found " + token.value);
    }

    @Nonnull
    private Set<AncestorSource> parseAncestorSourceList() throws ParseException {
        Set<AncestorSource> ancestorSources = new LinkedHashSet<>();
        if (this.hasNext("'none'")) {
            this.advance();
            ancestorSources.add(None.INSTANCE);
            return ancestorSources;
        }
        while (this.hasNext() && !this.hasNext(";")) {
            ancestorSources.add(this.parseAncestorSource());
        }
        return ancestorSources;
    }

    @Nonnull
    private AncestorSource parseAncestorSource() throws ParseException {
        Token token = this.advance();
        if (token.value.matches("^" + Constants.schemePart + ":$")) {
            return new SchemeSource(token.value.substring(0, token.value.length() - 1));
        } else {
            Matcher matcher = Constants.hostSourcePattern.matcher(token.value);
            if (matcher.find()) {
                String scheme = matcher.group("scheme");
                if (scheme != null) scheme = scheme.substring(0, scheme.length() - 3);
                String port = matcher.group("port");
                port = port == null ? "" : port.substring(1, port.length());
                String host = matcher.group("host");
                String path = matcher.group("path");
                return new HostSource(scheme, host, port, path);
            }
        }
        throw this.createError("expecting ancestor-source but found " + token.value);
    }

    @Nonnull
    private Set<SandboxValue> parseSandboxTokenList() throws ParseException {
        Set<SandboxValue> sandboxTokens = new LinkedHashSet<>();
        while (this.hasNext() && !this.hasNext(";")) {
            sandboxTokens.add(this.parseSandboxToken());
        }
        return sandboxTokens;
    }

    @Nonnull
    private SandboxValue parseSandboxToken() throws ParseException {
        Token token = this.advance();
        Matcher matcher = Constants.sandboxTokenPattern.matcher(token.value);
        if (matcher.find()) {
            return new SandboxValue(token.value);
        }
        throw this.createError("expecting sandbox-token but found " + token.value);
    }

    @Nonnull
    private Set<URI> parseUriList() throws ParseException {
        Set<URI> uriList = new LinkedHashSet<>();
        while (this.hasNext() && !this.hasNext(";")) {
            uriList.add(this.parseUri());
        }
        if (uriList.isEmpty()) {
            if (!this.hasNext()) {
                throw this.createUnexpectedEOF("report-uri must contain at least one uri-reference");
            }
            throw this.createError("report-uri must contain at least one uri-reference");
        }
        return uriList;
    }

    @Nonnull
    private URI parseUri() throws ParseException {
        Token token = this.advance();
        try {
            return URI.parseWithOrigin(this.origin, token.value);
        } catch (IllegalArgumentException ignored) {}
        throw this.createError("expecting uri-reference but found " + token.value);
    }

    public static class ParseException extends Exception {
        @Nullable
        Location startLocation;
        @Nullable
        Location endLocation;

        private ParseException(@Nonnull String message) {
            super(message);
        }

        @Nonnull
        @Override
        public String getMessage() {
            if (startLocation == null) {
                return super.getMessage();
            }
            return startLocation.show() + ": " + super.getMessage();
        }
    }
}
