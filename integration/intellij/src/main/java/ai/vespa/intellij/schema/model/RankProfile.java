// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.intellij.schema.model;

import ai.vespa.intellij.schema.SdUtil;
import ai.vespa.intellij.schema.psi.SdRankProfileDefinition;
import ai.vespa.intellij.schema.psi.SdTypes;
import com.intellij.lang.ASTNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A rank profile
 *
 * @author bratseth
 */
public class RankProfile {

    private final SdRankProfileDefinition definition;

    private final Schema owner;

    public RankProfile(SdRankProfileDefinition definition, Schema owner) {
        this.definition = Objects.requireNonNull(definition);
        this.owner = owner;
    }

    public String name() { return definition.getName(); }

    public SdRankProfileDefinition definition() { return definition; }

    /**
     * Returns the profiles inherited by this.
     *
     * @return the profiles this inherits from, empty if none
     */
    public List<RankProfile> findInherited() {
        ASTNode inheritsNode = definition.getNode().findChildByType(SdTypes.INHERITS);
        if (inheritsNode == null) return List.of();
        return inherits().stream()
                         .map(parentIdentifierAST -> parentIdentifierAST.getPsi().getReference())
                         .filter(reference -> reference != null)
                         .map(reference -> owner.rankProfile(reference.getCanonicalText()))
                         .flatMap(r -> r.stream())
                         .collect(Collectors.toList());
    }

    /** Returns the nodes following "inherits" in the definition of this */
    private List<ASTNode> inherits() {
        SdUtil.Tokens tokens = SdUtil.Tokens.of(definition);
        tokens.require(SdTypes.RANK_PROFILE);
        tokens.requireWhitespace();
        tokens.require(SdTypes.IDENTIFIER_VAL, SdTypes.IDENTIFIER_WITH_DASH_VAL);
        if ( ! tokens.skipWhitespace()) return List.of();
        if ( ! tokens.skip(SdTypes.INHERITS)) return List.of();
        tokens.requireWhitespace();
        List<ASTNode> inherited = new ArrayList<>();
        do {
            inherited.add(tokens.require(SdTypes.IDENTIFIER_VAL, SdTypes.IDENTIFIER_WITH_DASH_VAL));
            tokens.skipWhitespace();
            if ( ! tokens.skip(SdTypes.COMMA)) break;
            tokens.skipWhitespace();
        } while (true);
        return inherited;
    }

}
