package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.Optional;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.context.ParseContext;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.annotationRefDataType;
import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifyAnnotationReference extends Identifier {

	public IdentifyAnnotationReference(ParseContext context) {
		super(context);
	}

	@Override
	public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<>();

        if (!node.isSchemaASTInstance(annotationRefDataType.class)) return ret;

        if (node.size() < 3) return ret;

        SchemaNode annotationIdentifier = node.get(2);

        Optional<Symbol> scope = CSTUtils.findScope(node);
        if (scope.isPresent()) {
            annotationIdentifier.setSymbol(SymbolType.ANNOTATION, context.fileURI(), scope.get());
        } else {
            annotationIdentifier.setSymbol(SymbolType.ANNOTATION, context.fileURI());
        }

        context.addUnresolvedAnnotationReferenceNode(annotationIdentifier);
        return ret;
	}
}
