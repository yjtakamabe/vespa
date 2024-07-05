package ai.vespa.schemals.context;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;


import ai.vespa.schemals.SchemaDiagnosticsHandler;
import ai.vespa.schemals.context.parser.Identifier;
import ai.vespa.schemals.context.parser.IdentifyDeprecatedToken;
import ai.vespa.schemals.context.parser.IdentifyDirtyNodes;
import ai.vespa.schemals.context.parser.IdentifySymbolDefinition;
import ai.vespa.schemals.context.parser.IdentifySymbolReferences;
import ai.vespa.schemals.context.parser.IdentifyType;
import ai.vespa.schemals.parser.SchemaParser;
import ai.vespa.schemals.parser.ParseException;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.Token;

import ai.vespa.schemals.parser.indexinglanguage.IndexingParser;
import ai.vespa.schemals.parser.rankingexpression.RankingExpressionParser;

import com.yahoo.schema.parser.ParsedSchema;
import com.yahoo.searchlib.rankingexpression.rule.ReferenceNode;
import com.yahoo.tensor.functions.Diag;
import com.yahoo.schema.parser.ParsedBlock;

import com.yahoo.vespa.indexinglanguage.expressions.Expression;

import ai.vespa.schemals.tree.CSTUtils;
import ai.vespa.schemals.tree.SchemaNode;
import ai.vespa.schemals.tree.indexinglanguage.ILUtils;
import ai.vespa.schemals.tree.rankingexpression.RankingExpressionUtils;

public class SchemaDocumentParser {

    private PrintStream logger;
    private SchemaDiagnosticsHandler diagnosticsHandler;
    private SchemaIndex schemaIndex;

    private ArrayList<Identifier> parseIdentifiers;

    private String fileURI = "";
    private String content = "";
    
    private SchemaNode CST;
    private boolean faultySchema = true;

    public SchemaDocumentLexer lexer = new SchemaDocumentLexer();

    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI) {
        this(logger, diagnosticsHandler, schemaIndex, fileURI, null);
    }
    
    public SchemaDocumentParser(PrintStream logger, SchemaDiagnosticsHandler diagnosticsHandler, SchemaIndex schemaIndex, String fileURI, String content) {
        this.logger = logger;
        this.diagnosticsHandler = diagnosticsHandler;
        this.schemaIndex = schemaIndex;
        this.fileURI = fileURI;

        SchemaDocumentParser self = this;

        this.parseIdentifiers = new ArrayList<Identifier>() {{
            add(new IdentifySymbolDefinition(logger, self, schemaIndex));
            add(new IdentifyType(logger));
            add(new IdentifySymbolReferences(logger, self, schemaIndex));
            add(new IdentifyDeprecatedToken(logger));
            add(new IdentifyDirtyNodes(logger));
        }};

        if (content != null) {
            this.content = content;
            parseContent();
        };
    }

    public void updateFileContent(String content) {
        this.content = content;
        parseContent();

        CSTUtils.printTree(logger, CST);
    }

    public String getFileURI() {
        return fileURI;
    }

    public String getFileName() {
        Integer splitPos = fileURI.lastIndexOf('/');
        return fileURI.substring(splitPos + 1);
    }


    public boolean isFaulty() {
        return faultySchema;
    }

    public Position getPreviousStartOfWord(Position pos) {
        int offset = positionToOffset(pos);

        // Skip whitespace
        // But not newline because newline is a token
        while (offset >= 0 && Character.isWhitespace(content.charAt(offset)))offset--;

        for (int i = offset; i >= 0; i--) {
            if (Character.isWhitespace(content.charAt(i)))return offsetToPosition(i + 1);
        }

        return null;
    }

    public boolean isInsideComment(Position pos) {
        int offset = positionToOffset(pos);

        if (content.charAt(offset) == '\n')offset--;

        for (int i = offset; i >= 0; i--) {
            if (content.charAt(i) == '\n')break;
            if (content.charAt(i) == '#')return true;
        }
        return false;
    }

    public SchemaNode getRootNode() {
        return CST;
    }

    public SchemaNode getNodeAtOrBeforePosition(Position pos) {
        return getNodeAtPosition(CST, pos, false, true);
    }

    public SchemaNode getLeafNodeAtPosition(Position pos) {
        return getNodeAtPosition(CST, pos, true, false);
    }

    public SchemaNode getNodeAtPosition(Position pos) {
        return getNodeAtPosition(CST, pos, false, false);
    }


    private SchemaNode getNodeAtPosition(SchemaNode node, Position pos, boolean onlyLeaf, boolean findNearest) {
        if (node.isLeaf() && CSTUtils.positionInRange(node.getRange(), pos)) {
            return node;
        }

        if (!CSTUtils.positionInRange(node.getRange(), pos)) {
            if (findNearest && !onlyLeaf)return node;
            return null;
        }

        for (int i = 0; i < node.size(); i++) {
            SchemaNode child = node.get(i);

            if (CSTUtils.positionInRange(child.getRange(), pos)) {
                return getNodeAtPosition(child, pos, onlyLeaf, findNearest);
            }
        }

        if (onlyLeaf)return null;

        return node;
    }

    private class ParserPayload {
        int numOfLeadingChars = 0;
        String source;

        ParserPayload(String source) {

            this.source = source;

            int firstSplit = source.indexOf(':') + 1;
            if (firstSplit == 0) {
                firstSplit = source.indexOf('{') + 1;
            }

            String sourceSubStr = source.substring(firstSplit);
            int leadingChars = sourceSubStr.length() - sourceSubStr.stripLeading().length();

            numOfLeadingChars = firstSplit + leadingChars;
        }

        String getLeadingChars() {
            return source.substring(0, numOfLeadingChars);
        }

        Position getContentPosition() {
            String leadingChars = getLeadingChars();
            long numOfNewLines = leadingChars.chars().filter(ch -> ch == '\n').count();

            int lastNewLine = leadingChars.lastIndexOf('\n');

            return new Position((int)numOfNewLines, leadingChars.length() - lastNewLine - 1);
        }

    }

    private ArrayList<Diagnostic> parseContent() {
        CharSequence sequence = content;

        logger.println("Parsing document: " + fileURI);

        SchemaParser parserStrict = new SchemaParser(logger, getFileName(), sequence);
        parserStrict.setParserTolerant(false);

        ArrayList<Diagnostic> errors = new ArrayList<Diagnostic>();

        try {

            parserStrict.Root();
            faultySchema = false;
        } catch (ParseException e) {
            faultySchema = true;

            Node.TerminalNode node = e.getToken();

            Range range = CSTUtils.getNodeRange(node);
            String message = e.getMessage();

            
            Throwable cause = e.getCause();
            if (
                cause != null &&
                cause instanceof com.yahoo.searchlib.rankingexpression.parser.ParseException &&
                node instanceof Token
            ) {
                var parseCause = (com.yahoo.searchlib.rankingexpression.parser.ParseException) cause;
                var tok = parseCause.currentToken.next;

                Position relativeStartPosition = new Position(
                    tok.beginLine - 1,
                    tok.beginColumn - 1
                );

                Position relativeEndPosition = new Position(
                    tok.endLine - 1,
                    tok.endColumn - 1
                );

                if (relativeStartPosition.equals(relativeEndPosition)) {
                    relativeEndPosition.setCharacter(relativeEndPosition.getCharacter() + 1);
                }

                logger.println(tok.beginColumn);
                logger.println(tok.endColumn);

                Token rootToken = (Token) node;
                String source = rootToken.getSource();
                
                ParserPayload parserPayload = new ParserPayload(source);

                Position parsingStartPosition = CSTUtils.addPositions(range.getStart(), parserPayload.getContentPosition());

                Position absoluteStartPosition = CSTUtils.addPositions(parsingStartPosition, relativeStartPosition);
                Position absoluteEndPosition = CSTUtils.addPositions(parsingStartPosition, relativeEndPosition);

                range = new Range(
                    absoluteStartPosition,
                    absoluteEndPosition
                );

                message = parseCause.getMessage();

            }

            if (
                cause != null &&
                cause instanceof com.yahoo.schema.parser.ParseException
            ) {
                logger.println(e);
                logger.println(cause);
            }

            errors.add(new Diagnostic(range, message));


        } catch (IllegalArgumentException e) {
            // Complex error, invalidate the whole document

            errors.add(
                new Diagnostic(
                    new Range(
                        new Position(0, 0),
                        new Position((int)content.lines().count() - 1, 0)
                    ),
                    e.getMessage())
                );

            diagnosticsHandler.publishDiagnostics(fileURI, errors);
            
            return errors;
        }

        SchemaParser parserFaultTolerant = new SchemaParser(getFileName(), sequence);
        try {
            parserFaultTolerant.Root();
        } catch (ParseException e) {
            // Ignore
        } catch (IllegalArgumentException e) {
            // Ignore
        }

        Node node = parserFaultTolerant.rootNode();
        errors.addAll(parseCST(node));

        // CSTUtils.printTree(logger, CST);

        diagnosticsHandler.publishDiagnostics(fileURI, errors);

        lexer.setCST(CST);

        return errors;
    }

    private ArrayList<Diagnostic> traverseCST(SchemaNode node) {

        ArrayList<Diagnostic> ret = new ArrayList<>();
        
        for (Identifier identifier : parseIdentifiers) {
            ret.addAll(identifier.identify(node));
        }

        if (node.isIndexingElm()) {
            Range nodeRange = node.getRange();
            var indexingNode = parseIndexingScript(node.getILScript(), nodeRange.getStart(), ret);

            if (indexingNode != null) {
                node.setIndexingNode(indexingNode);
            }
        }

        if (node.isFeatureListElm()) {
            Range nodeRange = node.getRange();
            String nodeString = node.get(0).get(0).toString();
            Position featureListStart = CSTUtils.addPositions(nodeRange.getStart(), new Position(0, nodeString.length()));
            var featureListNode = parseFeatureList(node.getFeatureListString(), featureListStart, ret);

            if (featureListNode != null) {
                node.setFeatureListNode(featureListNode);
            }
        }

        for (int i = 0; i < node.size(); i++) {
            ret.addAll(traverseCST(node.get(i)));
        }

        return ret;
    }

    private ArrayList<Diagnostic> parseCST(Node node) {
        schemaIndex.clearDocument(fileURI);
        CST = new SchemaNode(node);
        if (node == null) {
            return new ArrayList<Diagnostic>();
        }
        return traverseCST(CST);
    }

    private ai.vespa.schemals.parser.indexinglanguage.Node parseIndexingScript(String script, Position scriptStart, ArrayList<Diagnostic> diagnostics) {
        if (script == null) return null;

        CharSequence sequence = script;
        IndexingParser parser = new IndexingParser(logger, getFileName(), sequence);
        parser.setParserTolerant(false);

        try {
            logger.println("Trying to parse indexing script: " + script);
            Expression indexingExpression = parser.root();
            // TODO: Verify expression
            return parser.rootNode();
        } catch(ai.vespa.schemals.parser.indexinglanguage.ParseException pe) {
            logger.println("Encountered parsing error in parsing feature list");
            Range range = ILUtils.getNodeRange(pe.getToken());
            range.setStart(CSTUtils.addPositions(scriptStart, range.getStart()));
            range.setEnd(CSTUtils.addPositions(scriptStart, range.getEnd()));

            diagnostics.add(new Diagnostic(range, pe.getMessage()));
        } catch(IllegalArgumentException ex) {
            logger.println("Encountered unknown error in parsing ILScript: " + ex.getMessage());
        }

        return null;
    }

    private ai.vespa.schemals.parser.rankingexpression.Node parseFeatureList(String featureListString, Position listStart, ArrayList<Diagnostic> diagnostics) {
        if (featureListString == null)return null;
        CharSequence sequence = featureListString;

        RankingExpressionParser parser = new RankingExpressionParser(logger, getFileName(), sequence);
        parser.setParserTolerant(false);

        try {
            logger.println("Trying to parse feature list: " + featureListString);
            List<ReferenceNode> features = parser.featureList();
            return parser.rootNode();
        } catch(ai.vespa.schemals.parser.rankingexpression.ParseException pe) {
            logger.println("Encountered parsing error in parsing ILScript");
            Range range = RankingExpressionUtils.getNodeRange(pe.getToken());
            range.setStart(CSTUtils.addPositions(listStart, range.getStart()));
            range.setEnd(CSTUtils.addPositions(listStart, range.getEnd()));

            diagnostics.add(new Diagnostic(range, pe.getMessage()));
        } catch(IllegalArgumentException ex) {
            logger.println("Encountered unknown error in parsing ILScript: " + ex.getMessage());
        }


        return null;
    }


    /*
     * If necessary, the following methods can be sped up by
     * selecting an appropriate data structure.
     * */
    private int positionToOffset(Position pos) {
        List<String> lines = content.lines().toList();
        if (pos.getLine() >= lines.size())throw new IllegalArgumentException("Line " + pos.getLine() + " out of range for document " + fileURI);

        int lineCounter = 0;
        int offset = 0;
        for (String line : lines) {
            if (lineCounter == pos.getLine())break;
            offset += line.length() + 1; // +1 for line terminator
            lineCounter += 1;
        }

        if (pos.getCharacter() > lines.get(pos.getLine()).length())throw new IllegalArgumentException("Character " + pos.getCharacter() + " out of range for line " + pos.getLine());

        offset += pos.getCharacter();

        return offset;
    }

    private Position offsetToPosition(int offset) {
        List<String> lines = content.lines().toList();
        int lineCounter = 0;
        for (String line : lines) {
            int lengthIncludingTerminator = line.length() + 1;
            if (offset < lengthIncludingTerminator) {
                return new Position(lineCounter, offset);
            }
            offset -= lengthIncludingTerminator;
            lineCounter += 1;
        }
        return null;
    }
}
