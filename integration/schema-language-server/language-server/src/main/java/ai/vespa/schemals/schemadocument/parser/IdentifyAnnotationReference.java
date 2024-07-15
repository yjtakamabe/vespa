package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.ast.annotationRefDataType;
import ai.vespa.schemals.schemadocument.ParseContext;
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

        annotationIdentifier.setSymbol(SymbolType.ANNOTATION, context.fileURI());

        context.addUnresolvedAnnotationReferenceNode(annotationIdentifier);
        return ret;
	}
}
