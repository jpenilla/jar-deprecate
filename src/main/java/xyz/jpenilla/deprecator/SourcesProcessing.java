package xyz.jpenilla.deprecator;

import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.RewriteContext;
import org.cadixdev.mercury.SourceRewriter;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.EnumConstantDeclaration;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.Javadoc;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.RecordDeclaration;
import org.eclipse.jdt.core.dom.TagElement;
import org.eclipse.jdt.core.dom.TextElement;
import org.eclipse.jdt.core.dom.TypeDeclaration;

@DefaultQualifier(NonNull.class)
public final class SourcesProcessing {
  private SourcesProcessing() {
  }

  public static void process(final Path input, final Path output, final String deprecationMessage) throws IOException {
    final Path extracted = input.resolveSibling(input.getFileName() + ".dir");
    final Path out = output.resolveSibling(output.getFileName() + ".dir");
    Util.deleteRecursively(extracted);
    Util.deleteRecursively(out);
    Files.deleteIfExists(output);

    try (final FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + input.toUri()), new HashMap<>())) {
      Files.createDirectories(extracted);
      Util.copyRecursively(fs.getPath("/"), extracted);
    }

    final Mercury mercury = new Mercury();
    mercury.setGracefulClasspathChecks(true);
    mercury.setSourceCompatibility(JavaCore.VERSION_16);
    mercury.getProcessors().add(new DeprecatingProcessor(deprecationMessage));
    try {
      mercury.rewrite(extracted, out);
    } catch (final Exception e) {
      Util.rethrow(e);
    }

    final HashMap<String, String> options = new HashMap<>();
    options.put("create", "true");
    try (final FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + output.toUri()), options)) {
      Files.createDirectories(out);
      Util.copyRecursively(out, fs.getPath("/"));
    }

    Util.deleteRecursively(extracted);
    Util.deleteRecursively(out);
  }

  private record DeprecatingProcessor(String deprecationMessage) implements SourceRewriter {
    @Override
    public void rewrite(final RewriteContext context) {
      context.getCompilationUnit().accept(new DeprecatingVisitor(context, this.deprecationMessage));
    }
  }

  private static final class DeprecatingVisitor extends ASTVisitor {
    private final RewriteContext context;
    private final String deprecationMessage;

    public DeprecatingVisitor(final RewriteContext context, final String deprecationMessage) {
      this.context = context;
      this.deprecationMessage = deprecationMessage;
    }

    @Override
    public boolean visit(final EnumDeclaration node) {
      if (deprecated(node.modifiers())) {
        return true;
      }

      final Javadoc javadoc = javadoc(node);
      this.context.createASTRewrite()
        .set(node, EnumDeclaration.JAVADOC_PROPERTY, javadoc, null);

      this.context.createASTRewrite()
        .getListRewrite(node, EnumDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);

      return true;
    }

    @Override
    public boolean visit(final RecordDeclaration node) {
      if (deprecated(node.modifiers())) {
        return true;
      }

      final Javadoc javadoc = javadoc(node);
      this.context.createASTRewrite()
        .set(node, RecordDeclaration.JAVADOC_PROPERTY, javadoc, null);

      this.context.createASTRewrite()
        .getListRewrite(node, RecordDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);

      return true;
    }

    @Override
    public boolean visit(final AnnotationTypeDeclaration node) {
      if (deprecated(node.modifiers())) {
        return true;
      }

      final Javadoc javadoc = javadoc(node);
      this.context.createASTRewrite()
        .set(node, AnnotationTypeDeclaration.JAVADOC_PROPERTY, javadoc, null);

      this.context.createASTRewrite()
        .getListRewrite(node, AnnotationTypeDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);

      return true;
    }

    @Override
    public boolean visit(final TypeDeclaration node) {
      if (deprecated(node.modifiers())) {
        return true;
      }

      final Javadoc javadoc = javadoc(node);
      this.context.createASTRewrite()
        .set(node, TypeDeclaration.JAVADOC_PROPERTY, javadoc, null);

      this.context.createASTRewrite()
        .getListRewrite(node, TypeDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);

      return true;
    }

    @Override
    public boolean visit(final MethodDeclaration node) {
      if (deprecated(node.modifiers())) {
        return false;
      }

      final Javadoc javadoc = javadoc(node);
      this.context.createASTRewrite()
        .set(node, MethodDeclaration.JAVADOC_PROPERTY, javadoc, null);

      this.context.createASTRewrite()
        .getListRewrite(node, MethodDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);

      return false;
    }

    @Override
    public boolean visit(final EnumConstantDeclaration node) {
      if (deprecated(node.modifiers())) {
        return false;
      }

      final Javadoc javadoc = javadoc(node);
      this.context.createASTRewrite()
        .set(node, EnumConstantDeclaration.JAVADOC_PROPERTY, javadoc, null);

      this.context.createASTRewrite()
        .getListRewrite(node, EnumConstantDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);

      return false;
    }

    @Override
    public boolean visit(final FieldDeclaration node) {
      if (deprecated(node.modifiers())) {
        return false;
      }

      final Javadoc javadoc = javadoc(node);
      this.context.createASTRewrite()
        .set(node, FieldDeclaration.JAVADOC_PROPERTY, javadoc, null);

      this.context.createASTRewrite()
        .getListRewrite(node, FieldDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);

      return false;
    }

    @SuppressWarnings("unchecked")
    private Javadoc javadoc(final BodyDeclaration node) {
      final Javadoc javadoc = (Javadoc) node.getAST().createInstance(Javadoc.class);
      if (node.getJavadoc() != null) {
        javadoc.tags().addAll((List<TagElement>) ASTNode.copySubtrees(node.getAST(), node.getJavadoc().tags()));
      }
      final TagElement tag = node.getAST().newTagElement();
      tag.setTagName(TagElement.TAG_DEPRECATED);
      final TextElement text = node.getAST().newTextElement();
      text.setText(this.deprecationMessage);
      tag.fragments().add(text);
      javadoc.tags().add(tag);
      return javadoc;
    }

    private static boolean deprecated(final List<?> modifiers) {
      for (final Object modifier : modifiers) {
        if (modifier instanceof Annotation annotation) {
          final String name = annotation.getTypeName().getFullyQualifiedName();
          if (name.equals("Deprecated") || name.equals("java.lang.Deprecated")) {
            return true;
          }
        }
      }
      return false;
    }

    private static MarkerAnnotation deprecated(final AST ast) {
      final MarkerAnnotation annotation = (MarkerAnnotation) ast.createInstance(MarkerAnnotation.class);
      annotation.setTypeName(ast.newName("java.lang.Deprecated"));
      return annotation;
    }
  }
}
