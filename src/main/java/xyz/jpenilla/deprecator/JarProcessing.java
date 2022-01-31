package xyz.jpenilla.deprecator;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cadixdev.atlas.Atlas;
import org.cadixdev.atlas.AtlasTransformerContext;
import org.cadixdev.bombe.asm.analysis.ClassProviderInheritanceProvider;
import org.cadixdev.bombe.asm.jar.ClassProvider;
import org.cadixdev.bombe.jar.JarClassEntry;
import org.cadixdev.bombe.jar.JarEntryTransformer;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

@DefaultQualifier(NonNull.class)
public final class JarProcessing {
  private JarProcessing() {
  }

  public static void process(final Path path, final Path output) {
    final Atlas atlas = new Atlas();
    atlas.install(ctx -> {
      replaceInheritanceProvider(ctx); // https://github.com/CadixDev/Atlas/issues/12
      return new DeprecatingTransformer();
    });
    try {
      atlas.run(path, output);
    } catch (final IOException e) {
      Util.rethrow(e);
    }
  }

  private static boolean deprecated(final List<AnnotationNode> list) {
    for (final AnnotationNode annotationNode : list) {
      if (annotationNode.desc.equals("Ljava/lang/Deprecated;")) {
        return true;
      }
    }
    return false;
  }

  private static AnnotationNode deprecated() {
    return new AnnotationNode("Ljava/lang/Deprecated;");
  }

  private static void replaceInheritanceProvider(AtlasTransformerContext ctx) {
    try {
      final Field inheritanceProvider = AtlasTransformerContext.class.getDeclaredField("inheritanceProvider");
      inheritanceProvider.setAccessible(true);
      final Field classProvider = ClassProviderInheritanceProvider.class.getDeclaredField("provider");
      classProvider.setAccessible(true);
      inheritanceProvider.set(ctx, new ClassProviderInheritanceProvider(Deprecator.OPCODES, (ClassProvider) classProvider.get(inheritanceProvider.get(ctx))));
    } catch (final ReflectiveOperationException ex) {
      throw new RuntimeException(ex);
    }
  }

  private static final class DeprecatingTransformer implements JarEntryTransformer {
    @Override
    public JarClassEntry transform(final JarClassEntry entry) {
      final ClassNode node = new ClassNode();
      new ClassReader(entry.getContents()).accept(node, 0);

      if (node.visibleAnnotations == null) {
        node.visibleAnnotations = new ArrayList<>();
      }
      if (!deprecated(node.visibleAnnotations)) {
        node.visibleAnnotations.add(deprecated());
      }
      for (final MethodNode method : node.methods) {
        if (method.visibleAnnotations == null) {
          method.visibleAnnotations = new ArrayList<>();
        }
        if (!deprecated(method.visibleAnnotations)) {
          method.visibleAnnotations.add(deprecated());
        }
      }
      for (final FieldNode field : node.fields) {
        if (field.visibleAnnotations == null) {
          field.visibleAnnotations = new ArrayList<>();
        }
        if (!deprecated(field.visibleAnnotations)) {
          field.visibleAnnotations.add(deprecated());
        }
      }

      final ClassWriter writer = new ClassWriter(Deprecator.OPCODES);
      node.accept(writer);
      return new JarClassEntry(entry.getName(), entry.getTime(), writer.toByteArray());
    }
  }
}
