package ai.vespa.schemals.tree;

import java.util.ArrayList;
import java.util.Iterator;

import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import ai.vespa.schemals.parser.Token;
import ai.vespa.schemals.parser.Token.TokenType;
import ai.vespa.schemals.parser.TokenSource;
import ai.vespa.schemals.parser.Token.ParseExceptionSource;
import ai.vespa.schemals.index.Symbol;
import ai.vespa.schemals.index.Symbol.SymbolStatus;
import ai.vespa.schemals.index.Symbol.SymbolType;
import ai.vespa.schemals.parser.Node;
import ai.vespa.schemals.parser.SubLanguageData;
import ai.vespa.schemals.parser.ast.indexingElm;
import ai.vespa.schemals.parser.ast.featureListElm;
import ai.vespa.schemals.parser.ast.identifierStr;

public class SchemaNode implements Iterable<SchemaNode> {

    private TokenType type;
    private String identifierString;
    private SchemaNode parent;
    private Symbol symbolAtNode;

    private Node originalNode;

    private ai.vespa.schemals.parser.indexinglanguage.Node indexingNode;
    private ai.vespa.schemals.parser.rankingexpression.Node featureListNode;

    // This array has to be in order, without overlapping elements
    private ArrayList<SchemaNode> children = new ArrayList<SchemaNode>();

    private Range range;

    public SchemaNode(Node node) {
        this(node, null);
    }
    
    public SchemaNode(Node node, SchemaNode parent) {
        this.parent = parent;
        originalNode = node;
        Node.NodeType originalType = node.getType();
        type = (node.isDirty() || !(originalType instanceof TokenType)) ? null : (TokenType) originalType;

        identifierString = node.getClass().getName();
        range = CSTUtils.getNodeRange(node);

        for (Node child : node) {
            children.add(new SchemaNode(child, this));
        }
    }

    public TokenType getType() {
        return type;
    }

    // Return token type (if the node is a token), even if the node is dirty
    public TokenType getDirtyType() {
        Node.NodeType originalType = originalNode.getType();
        if (originalType instanceof TokenType)return (TokenType)originalType;
        return null;
    }

    public TokenType setType(TokenType type) {
        this.type = type;
        return type;
    }

    public void setSymbol(SymbolType type, String fileURI) {
        this.symbolAtNode = new Symbol(this, type, fileURI);
    }

    public void setSymbol(SymbolType type, String fileURI, Symbol scope) {
        this.symbolAtNode = new Symbol(this, type, fileURI, scope);
    }

    public void setSymbolType(SymbolType newType) {
        if (!this.hasSymbol()) return;
        this.symbolAtNode.setType(newType);
    }

    public void setSymbolStatus(SymbolStatus newStatus) {
        if (!this.hasSymbol()) return;
        this.symbolAtNode.setStatus(newStatus);
    }

    public boolean hasSymbol() {
        return this.symbolAtNode != null;
    }

    public Symbol getSymbol() {
        if (!hasSymbol()) throw new IllegalArgumentException("getSymbol called on node without a symbol!");
        return this.symbolAtNode;
    }

    public boolean isIndexingElm() {
        return (originalNode instanceof indexingElm);
    }

    public boolean isFeatureListElm() {
        return (originalNode instanceof featureListElm);
    }

    public SubLanguageData getILScript() {
        if (!isIndexingElm())return null;
        indexingElm elmNode = (indexingElm)originalNode;
        return elmNode.getILScript();
    }

    public String getFeatureListString() {
        if (!isFeatureListElm()) return null;
        featureListElm elmNode = (featureListElm)originalNode;
        return elmNode.getFeatureListString();
    }

    public boolean hasIndexingNode() {
        return this.indexingNode != null;
    }

    public boolean hasFeatureListNode() {
        return this.featureListNode != null;
    }

    public ai.vespa.schemals.parser.indexinglanguage.Node getIndexingNode() {
        return this.indexingNode;
    }

    public ai.vespa.schemals.parser.rankingexpression.Node getFeatureListNode() {
        return this.featureListNode;
    }

    public void setIndexingNode(ai.vespa.schemals.parser.indexinglanguage.Node node) {
        this.indexingNode = node;
    }

    public void setFeatureListNode(ai.vespa.schemals.parser.rankingexpression.Node node) {
        this.featureListNode = node;
    }

    public boolean isASTInstance(Class<? extends Node> astClass) {
        return astClass.isInstance(originalNode);
    }

    public Class<? extends Node> getASTClass() {
        return originalNode.getClass();
    }

    public String getIdentifierString() {
        return identifierString;
    }

    public Node getOriginalNode() {
        return originalNode;
    }

    public void setNewStartCharacter(int startCharacter) {
        int currentOffset = originalNode.getBeginOffset();
        int characterDelta = startCharacter - range.getStart().getCharacter();

        originalNode.setBeginOffset(currentOffset + characterDelta);
        this.range = CSTUtils.getNodeRange(originalNode);
    }

    public void setNewEndCharacter(int endCharacter) {
        int currentOffset = originalNode.getEndOffset();
        int characterDelta = endCharacter - range.getEnd().getCharacter();

        originalNode.setEndOffset(currentOffset + characterDelta);
        this.range = CSTUtils.getNodeRange(originalNode);
    }

    public String getClassLeafIdentifierString() {
        int lastIndex = identifierString.lastIndexOf('.');
        return identifierString.substring(lastIndex + 1);
    }

    public Range getRange() {
        return range;
    }

    public SchemaNode getParent(int levels) {
        if (levels == 0) {
            return this;
        }

        if (parent == null) {
            return null;
        }

        return parent.getParent(levels - 1);
    }

    public SchemaNode getParent() {
        return getParent(1);
    }

    public void insertChildAfter(int index, SchemaNode child) {
        this.children.add(index+1, child);
    }

    public SchemaNode getPrevious() {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1)return null; // invalid setup

        if (parentIndex == 0)return parent;
        return parent.get(parentIndex - 1);
    }

    public SchemaNode getNext() {
        if (parent == null) return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1) return null;
        
        if (parentIndex == parent.size() - 1) return parent.getNext();

        return parent.get(parentIndex + 1);
    }

    private SchemaNode getSibling(int relativeIndex) {
        if (parent == null)return null;

        int parentIndex = parent.indexOf(this);

        if (parentIndex == -1) return null; // invalid setup

        int siblingIndex = parentIndex + relativeIndex;
        if (siblingIndex < 0 || siblingIndex >= parent.size()) return null;
        
        return parent.get(siblingIndex);
    }

    public SchemaNode getPreviousSibling() {
        return getSibling(-1);
    }

    public SchemaNode getNextSibling() {
        return getSibling(1);
    }

    public int indexOf(SchemaNode child) {
        return this.children.indexOf(child);
    }

    public int size() {
        return children.size();
    }

    public SchemaNode get(int i) {
        return children.get(i);
    }

    public String getText() {
        return originalNode.getSource();
    }

    public boolean isLeaf() {
        return children.size() == 0;
    }

    public SchemaNode findFirstLeaf() {
        SchemaNode ret = this;
        while (ret.size() > 0) {
            ret = ret.get(0);
        }
        return ret;
    }

    public boolean isDirty() {
        return originalNode.isDirty();
    }

    public IllegalArgumentException getIllegalArgumentException() {
        if (originalNode instanceof Token) {
            return ((Token)originalNode).getIllegalArguemntException();
        }
        return null;
    }

    public ParseExceptionSource getParseExceptionSource() {
        if (originalNode instanceof Token) {
            return ((Token)originalNode).getParseExceptionSource();
        }
        return null;
    }

    public TokenSource getTokenSource() { return originalNode.getTokenSource(); }

    public String toString() {
        Position pos = getRange().getStart();
        return getText() + "[" + getType() + "] at " + pos.getLine() + ":" + pos.getCharacter();
    }

	@Override
	public Iterator<SchemaNode> iterator() {
        return new Iterator<SchemaNode>() {
            int currentIndex = 0;

			@Override
			public boolean hasNext() {
                return currentIndex < children.size();
			}

			@Override
			public SchemaNode next() {
                return children.get(currentIndex++);
			}
        };
	}
}
