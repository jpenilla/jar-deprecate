package xyz.jpenilla.deprecator;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.objectweb.asm.Opcodes;

public final class Deprecator {
  public static final int OPCODES = Opcodes.ASM9;
  private static final Logger LOGGER = LogManager.getLogger();

  public static void main(final String[] args) {
    final List<String> argList = new ArrayList<>();

    int parallelism = 4;
    for (int i = 0; i < args.length; i++) {
      final String arg = args[i];

      if (arg.equals("--parallelism")) {
        i++;
        final String value = args[i];
        try {
          parallelism = Integer.parseInt(value);
        } catch (final NumberFormatException ex) {
          throw new RuntimeException("Invalid input for argument parallelism '" + value + "'", ex);
        }
        continue;
      }

      argList.add(arg);
    }

    final ExecutorService taskExecutor = Executors.newFixedThreadPool(parallelism, new NamedThreadFactory("task-executor"));

    final List<ProcessingTask> futures = argList.stream()
      .map(Paths::get)
      .map(path -> scheduleProcessing(taskExecutor, path))
      .toList();

    for (final ProcessingTask task : futures) {
      try {
        task.future().join();
      } catch (final Exception ex) {
        LOGGER.error("Failed to process jar {}", task.path(), ex);
      }
    }

    Util.shutdownExecutor(taskExecutor, TimeUnit.SECONDS, 3);
  }

  private static Path output(final Path path) {
    return path.resolveSibling(path.getFileName().toString().replace(".jar", "") + "-deprecated.jar");
  }

  private static void processSourcesJar(final Path path) {
    final Path output = output(path);
    try {
      SourcesProcessing.process(path, output);
    } catch (final IOException e) {
      Util.rethrow(e);
    }
  }

  private static void processJar(Path path) {
    final Path output = output(path);
    JarProcessing.process(path, output);
  }

  private static ProcessingTask scheduleProcessing(final Executor executor, final Path path) {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
      final long start = System.currentTimeMillis();
      if (path.getFileName().toString().endsWith("-sources.jar")) {
        LOGGER.info("Processing {} as sources jar...", path);
        processSourcesJar(path);
      } else {
        LOGGER.info("Processing {}...", path);
        processJar(path);
      }
      LOGGER.info("Successfully processed {} in {}ms", path, System.currentTimeMillis() - start);
    }, executor);
    return new ProcessingTask(path, future);
  }

  private record ProcessingTask(Path path, CompletableFuture<Void> future) {
  }
}
