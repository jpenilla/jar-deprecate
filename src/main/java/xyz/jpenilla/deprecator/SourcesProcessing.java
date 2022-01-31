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
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MarkerAnnotation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

@DefaultQualifier(NonNull.class)
public final class SourcesProcessing {
  private SourcesProcessing() {
  }

  public static void process(final Path input, final Path output) throws IOException {
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
    mercury.getProcessors().add(new DeprecatingProcessor());
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

  private static final class DeprecatingProcessor implements SourceRewriter {
    @Override
    public void rewrite(final RewriteContext context) {
      context.getCompilationUnit().accept(new DeprecatingVisitor(context));
    }
  }

  private static final class DeprecatingVisitor extends ASTVisitor {
    private final RewriteContext context;

    public DeprecatingVisitor(final RewriteContext context) {
      this.context = context;
    }

    @Override
    public boolean visit(final TypeDeclaration node) {
      if (deprecated(node.modifiers())) {
        return true;
      }

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

      this.context.createASTRewrite()
        .getListRewrite(node, MethodDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);
      return false;
    }

    @Override
    public boolean visit(final FieldDeclaration node) {
      if (deprecated(node.modifiers())) {
        return false;
      }

      this.context.createASTRewrite()
        .getListRewrite(node, FieldDeclaration.MODIFIERS2_PROPERTY)
        .insertFirst(deprecated(node.getAST()), null);
      return false;
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
