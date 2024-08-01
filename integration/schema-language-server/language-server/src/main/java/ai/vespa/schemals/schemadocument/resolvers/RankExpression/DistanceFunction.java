package ai.vespa.schemals.schemadocument.resolvers.RankExpression;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;

import ai.vespa.schemals.common.SchemaDiagnostic;
import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.rankingexpression.RankNode;

public class DistanceFunction implements FunctionHandler {


    @Override
    public List<Diagnostic> handleArgumentList(ParseContext context, RankNode node) {
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<RankNode> arguments = node.getChildren();

        if (arguments.size() != 2) {
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(node.getRange())
                .setMessage("The distance function takes two argument (dimension, name)")
                .setSeverity(DiagnosticSeverity.Error)
                .build());
            return diagnostics;
        }

        SchemaNode firstArgument = arguments.get(0).getSchemaNode();
        while (!firstArgument.hasSymbol() && firstArgument.size() > 0) {
            firstArgument = firstArgument.get(0);
        }

        if (firstArgument.hasSymbol()) {
            firstArgument.removeSymbol();
        }

        boolean isField = firstArgument.getText().equals("field");
        boolean isLabel = firstArgument.getText().equals("label");

        if (!isField && !isLabel) {
            diagnostics.add(new SchemaDiagnostic.Builder()
                .setRange(firstArgument.getRange())
                .setMessage("The first argument must be field or label")
                .setSeverity(DiagnosticSeverity.Error)
                .build());
            return diagnostics;
        }

        SchemaNode secondArgument = arguments.get(1).getSchemaNode();
        while (!secondArgument.hasSymbol() && secondArgument.size() > 0) {
            secondArgument = secondArgument.get(0);
        }

        if (!secondArgument.hasSymbol()) {
            return diagnostics;
        }

        if (secondArgument.getSymbol().getStatus() == SymbolStatus.REFERENCE) {
            secondArgument.getSymbol().setStatus(SymbolStatus.UNRESOLVED);
            context.schemaIndex().deleteSymbolReference(secondArgument.getSymbol());
        }

        SymbolType newType = isField ? SymbolType.FIELD : SymbolType.LABEL;
        secondArgument.setSymbolType(newType);

        if (isLabel) {
            secondArgument.setSymbolStatus(SymbolStatus.BUILTIN_REFERENCE);
        }


        return diagnostics;
    }
}
