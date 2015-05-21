package com.shapesecurity.csp.directives;

import com.shapesecurity.csp.sources.AncestorSource;

import javax.annotation.Nonnull;
import java.util.List;

public class FrameAncestorsDirective extends AncestorSourceListDirective {
    @Nonnull
    private static final String name = "frame-ancestors";

    public FrameAncestorsDirective(@Nonnull List<AncestorSource> ancestorSources) {
        super(FrameAncestorsDirective.name, ancestorSources);
    }
}