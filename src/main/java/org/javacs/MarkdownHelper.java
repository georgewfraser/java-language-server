package org.javacs;

import com.sun.source.doctree.AuthorTree;
import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.IdentifierTree;
import com.sun.source.doctree.InheritDocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialDataTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.SerialTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.VersionTree;
import com.sun.source.util.DocTreeScanner;
import java.util.ArrayList;
import java.util.List;
import org.javacs.lsp.MarkupContent;
import org.javacs.lsp.MarkupKind;

public class MarkdownHelper {

    public static MarkupContent asMarkupContent(DocCommentTree comment) {
        var markdown = asMarkdown(comment);
        var content = new MarkupContent();
        content.kind = MarkupKind.Markdown;
        content.value = markdown;
        return content;
    }

    public static String asMarkdown(DocCommentTree comment) {
        var scanner = new JavadocToMarkdownScanner();
        scanner.scan(comment, null);
        return scanner.toString();
    }

    private static class JavadocToMarkdownScanner extends DocTreeScanner<Void, Void> {
        private final StringBuilder out = new StringBuilder();

        @Override
        public String toString() {
            return out.toString();
        }

        @Override
        public Void visitDocComment(DocCommentTree node, Void p) {
            scan(node.getFirstSentence(), null);
            if (!node.getBody().isEmpty() || !node.getBlockTags().isEmpty()) {
                out.append("\n\n");
                scan(node.getBody(), null);
                scan(node.getBlockTags(), null);
            }
            return null;
        }

        @Override
        public Void visitText(TextTree node, Void p) {
            // TODO: Avoid splitting paragraphs without <p>.
            // TODO: Escape special characters.
            var firstLine = true;
            for (var line : node.getBody().split("\n")) {
                if (firstLine) {
                    out.append(line);
                    firstLine = false;
                } else {
                    // Strip a leading space as it is quite common in comments.
                    if (line.startsWith(" ")) {
                        line = line.substring(1);
                    }
                    out.append("\n");
                    out.append(line);
                }
            }
            return null;
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void p) {
            out.append("`");
            out.append(node.getName().toString());
            out.append("` ");
            return null;
        }

        @Override
        public Void visitEntity(EntityTree node, Void p) {
            // TODO: Support converting HTML entities.
            out.append("&");
            out.append(node.getName());
            out.append(";");
            return null;
        }

        @Override
        public Void visitLiteral(LiteralTree node, Void p) {
            out.append("`");
            super.visitLiteral(node, p);
            out.append("`");
            return null;
        }

        @Override
        public Void visitLink(LinkTree node, Void p) {
            // TODO: Support @link properly.
            out.append("`");
            if (node.getLabel().isEmpty()) {
                out.append(node.getReference().getSignature());
            } else {
                scan(node.getLabel(), null);
            }
            out.append("`");
            return null;
        }

        @Override
        public Void visitSee(SeeTree node, Void p) {
            // TODO: Support @see properly.
            out.append("`");
            scan(node.getReference(), null);
            out.append("`");
            return null;
        }

        @Override
        public Void visitInheritDoc(InheritDocTree node, Void p) {
            // TODO: Support @inheritDoc.
            out.append("@inheritDoc");
            return null;
        }

        @Override
        public Void visitValue(ValueTree node, Void p) {
            // TODO: Support @value.
            out.append("{@value ");
            visitReference(node.getReference(), null);
            out.append("}");
            return null;
        }

        @Override
        public Void visitVersion(VersionTree node, Void p) {
            // TODO: Support @version.
            out.append("{@version ");
            scan(node.getBody(), null);
            out.append("}");
            return null;
        }

        @Override
        public Void visitUnknownInlineTag(UnknownInlineTagTree node, Void p) {
            out.append("{@");
            out.append(node.getTagName());
            out.append(" ");
            scan(node.getContent(), null);
            out.append("}");
            return null;
        }

        @Override
        public Void visitStartElement(StartElementTree node, Void p) {
            var name = node.getName();
            if (name.contentEquals("p")) {
                out.append("\n\n");
            } else if (name.contentEquals("ul") || name.contentEquals("ol")) {
                // TODO: Support <ol> properly. For now we're reluctant to
                // introduce states in this class for simplicity.
                out.append("\n\n");
            } else if (name.contentEquals("li")) {
                // TODO: Support nested lists.
                out.append("\n- ");
            } else if (name.contentEquals("pre")) {
                out.append("\n```\n");
            } else if (name.contentEquals("b")) {
                out.append("**");
            } else if (name.contentEquals("i")) {
                out.append("*");
            } else if (name.contentEquals("code")) {
                out.append("`");
            }
            return null;
        }

        @Override
        public Void visitEndElement(EndElementTree node, Void p) {
            var name = node.getName();
            if (name.contentEquals("pre")) {
                out.append("\n```\n");
            } else if (name.contentEquals("b")) {
                out.append("**");
            } else if (name.contentEquals("i")) {
                out.append("*");
            } else if (name.contentEquals("code")) {
                out.append("`");
            }
            return null;
        }

        @Override
        public Void visitAuthor(AuthorTree node, Void p) {
            return visitBlockTag(node, node.getName());
        }

        @Override
        public Void visitDeprecated(DeprecatedTree node, Void p) {
            return visitBlockTag(node, node.getBody());
        }

        @Override
        public Void visitSince(SinceTree node, Void p) {
            return visitBlockTag(node, node.getBody());
        }

        @Override
        public Void visitParam(ParamTree node, Void p) {
            var children = new ArrayList<DocTree>();
            if (node.getName() != null) {
                children.add(node.getName());
            }
            children.addAll(node.getDescription());
            return visitBlockTag(node, children);
        }

        @Override
        public Void visitReturn(ReturnTree node, Void p) {
            return visitBlockTag(node, node.getDescription());
        }

        @Override
        public Void visitThrows(ThrowsTree node, Void p) {
            var children = new ArrayList<DocTree>();
            if (node.getExceptionName() != null) {
                children.add(node.getExceptionName());
            }
            children.addAll(node.getDescription());
            return visitBlockTag(node, children);
        }

        @Override
        public Void visitSerial(SerialTree node, Void p) {
            return visitBlockTag(node, node.getDescription());
        }

        @Override
        public Void visitSerialData(SerialDataTree node, Void p) {
            return visitBlockTag(node, node.getDescription());
        }

        @Override
        public Void visitSerialField(SerialFieldTree node, Void p) {
            var children = new ArrayList<DocTree>();
            if (node.getName() != null) {
                children.add(node.getName());
            }
            if (node.getType() != null) {
                children.add(node.getType());
            }
            children.addAll(node.getDescription());
            return visitBlockTag(node, children);
        }

        @Override
        public Void visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
            return visitBlockTag(node, node.getContent());
        }

        @Override
        public Void visitErroneous(ErroneousTree node, Void p) {
            return visitText(node, p);
        }

        private Void visitBlockTag(BlockTagTree node, List<? extends DocTree> children) {
            out.append("\n\n*@");
            out.append(node.getTagName());
            out.append("* ");
            scan(children, null);
            out.append("\n\n");
            return null;
        }
    }
}
