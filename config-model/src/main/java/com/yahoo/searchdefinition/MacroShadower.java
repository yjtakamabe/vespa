// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.searchlib.rankingexpression.RankingExpression;
import com.yahoo.searchlib.rankingexpression.rule.*;
import com.yahoo.searchlib.rankingexpression.transform.ExpressionTransformer;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Transforms function nodes to reference nodes if a macro shadows a built-in function.
 * This has the effect of allowing macros to redefine built-in functions.
 * Another effect is that we can more or less add built-in functions over time
 * without fear of breaking existing users' macros with the same name.
 *
 * However, there is a (largish) caveat. If a user has a macro with a certain number
 * of arguments, and we add in a built-in function with a different arity,
 * this will cause parse errors as the Java parser gives precedence to
 * built-in functions.
 *
 * @author lesters
 */
class MacroShadower extends ExpressionTransformer {

    private static final DeployLogger logger = new BaseDeployLogger();

    private final Map<String, RankProfile.Macro> macros;

    public MacroShadower(Map<String, RankProfile.Macro> macros) {
        this.macros = macros;
    }

    @Override
    public RankingExpression transform(RankingExpression expression) {
        String name = expression.getName();
        ExpressionNode node = expression.getRoot();
        ExpressionNode result = transform(node);
        return new RankingExpression(name, result);
    }

    @Override
    public ExpressionNode transform(ExpressionNode node) {
        if (node instanceof FunctionNode)
            return transformFunctionNode((FunctionNode) node);
        if (node instanceof CompositeNode)
            return transformChildren((CompositeNode)node);
        return node;
    }

    protected ExpressionNode transformFunctionNode(FunctionNode function) {
        String name = function.getFunction().toString();
        RankProfile.Macro macro = macros.get(name);
        if (macro == null) {
            return transformChildren(function);
        }

        int functionArity = function.getFunction().arity();
        int macroArity = macro.getFormalParams() != null ? macro.getFormalParams().size() : 0;
        if (functionArity != macroArity) {
            logger.log(Level.WARNING, "Macro \"" + name + "\" has the same name as a built-in function. Due to different number of arguments, the built-in function will be used.");
            return transformChildren(function);
        }

        logger.log(Level.WARNING, "Macro \"" + name + "\" shadows the built-in function with the same name.");
        ReferenceNode node = new ReferenceNode(name, function.children(), null);
        return transformChildren(node);
    }

}
