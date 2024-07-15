package ai.vespa.schemals.schemadocument.parser;

import java.util.ArrayList;
import java.util.HashMap;

import org.eclipse.lsp4j.Diagnostic;

import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.ast.annotationElm;
import ai.vespa.schemals.parser.ast.annotationOutside;
import ai.vespa.schemals.parser.ast.documentElm;
import ai.vespa.schemals.parser.ast.documentSummary;
import ai.vespa.schemals.parser.ast.fieldElm;
import ai.vespa.schemals.parser.ast.fieldSetElm;
import ai.vespa.schemals.parser.ast.functionElm;
import ai.vespa.schemals.parser.ast.identifierStr;
import ai.vespa.schemals.parser.ast.identifierWithDashStr;
import ai.vespa.schemals.parser.ast.namedDocument;
import ai.vespa.schemals.parser.ast.rankProfile;
import ai.vespa.schemals.parser.ast.rootSchema;
import ai.vespa.schemals.parser.ast.structDefinitionElm;
import ai.vespa.schemals.parser.ast.structFieldDefinition;
import ai.vespa.schemals.parser.ast.structFieldElm;
import ai.vespa.schemals.parser.ast.summaryInDocument;
import ai.vespa.schemals.schemadocument.ParseContext;
import ai.vespa.schemals.tree.SchemaNode;

public class IdentifySymbolDefinition extends Identifier {

    public IdentifySymbolDefinition(ParseContext context) {
		super(context);
	}

    private static final HashMap<Class<? extends Node>, SymbolType> identifierTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(annotationElm.class, SymbolType.ANNOTATION);
        put(annotationOutside.class, SymbolType.ANNOTATION);
        put(rootSchema.class, SymbolType.SCHEMA);
        put(documentElm.class, SymbolType.DOCUMENT);
        put(namedDocument.class, SymbolType.DOCUMENT);
        put(fieldElm.class, SymbolType.FIELD);
        put(structFieldDefinition.class, SymbolType.FIELD);
        put(fieldSetElm.class, SymbolType.FIELDSET);
        put(structDefinitionElm.class, SymbolType.STRUCT);
        put(structFieldElm.class, SymbolType.STRUCT_FIELD);
        put(functionElm.class, SymbolType.FUNCTION);
    }};

    private static final HashMap<Class<? extends Node>, SymbolType> identifierWithDashTypeMap = new HashMap<Class<? extends Node>, SymbolType>() {{
        put(rankProfile.class, SymbolType.RANK_PROFILE);
        put(documentSummary.class, SymbolType.DOCUMENT_SUMMARY);
        put(summaryInDocument.class, SymbolType.SUMMARY);
    }};

    public ArrayList<Diagnostic> identify(SchemaNode node) {
        ArrayList<Diagnostic> ret = new ArrayList<Diagnostic>();

        boolean isIdentifier = node.isSchemaASTInstance(identifierStr.class);
        boolean isIdentifierWithDash = node.isSchemaASTInstance(identifierWithDashStr.class);

        if (!isIdentifier && !isIdentifierWithDash) return ret;

        SchemaNode parent = node.getParent();
        if (parent == null || parent.size() <= 1) return ret;

        // Prevent inheritance from beeing marked as a definition
        if (parent.indexOf(node) >= 3) return ret;

        HashMap<Class<? extends Node>, SymbolType> searchMap = isIdentifier ? identifierTypeMap : identifierWithDashTypeMap;
        SymbolType symbolType = searchMap.get(parent.getASTClass());
        if (symbolType == null) return ret;

        node.setSymbol(symbolType, context.fileURI());

        if (context.schemaIndex().findSymbolInFile(context.fileURI(), symbolType, node.getText()) == null) {
            node.setSymbolStatus(SymbolStatus.DEFINITION);
            context.schemaIndex().insertSymbolDefinition(node.getSymbol());
        } else {
            node.setSymbolStatus(SymbolStatus.INVALID);
        }

        return ret;
    }
}
