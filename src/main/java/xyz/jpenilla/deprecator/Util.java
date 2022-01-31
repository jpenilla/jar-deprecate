package xyz.jpenilla.deprecator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

@DefaultQualifier(NonNull.class)
public final class Util {
  private Util() {
  }

  public static void shutdownExecutor(final ExecutorService service, final TimeUnit timeoutUnit, final long timeoutLength) {
    service.shutdown();
    boolean didShutdown;
    try {
      didShutdown = service.awaitTermination(timeoutLength, timeoutUnit);
    } catch (final InterruptedException ignore) {
      didShutdown = false;
    }
    if (!didShutdown) {
      service.shutdownNow();
    }
  }

  @SuppressWarnings("unchecked")
  public static <T extends Throwable> void rethrow(final Throwable throwable) throws T {
    throw (T) throwable;
  }

  public static void copyRecursively(final Path from, final Path target) throws IOException {
    Files.createDirectories(target);
    try (final Stream<Path> stream = Files.walk(from)) {
      stream.forEach(path -> {
        final Path targetPath = target.resolve(from.relativize(path).toString());
        try {
          if (Files.isDirectory(path)) {
            Files.createDirectories(targetPath);
          } else {
            Files.copy(path, targetPath);
          }
        } catch (final IOException e) {
          Util.rethrow(e);
        }
      });
    }
  }

  public static void deleteRecursively(final Path path) {
    if (!Files.exists(path)) {
      return;
    }
    try (final Stream<Path> stream = Files.walk(path)) {
      stream.sorted(Comparator.reverseOrder())
        .forEach(file -> {
          try {
            Files.delete(file);
          } catch (final IOException e) {
            Util.rethrow(e);
          }
        });
    } catch (final IOException ex) {
      Util.rethrow(ex);
    }
  }
}
