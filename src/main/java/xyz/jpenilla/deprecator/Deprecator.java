package xyz.jpenilla.deprecator;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringJoiner;
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
    final List<String> argList = new ArrayList<>(Arrays.asList(args));
    final List<IOPaths> inputs = new ArrayList<>();

    String deprecationMessage0 = "Deprecated API.";
    int parallelism = 4;

    final Iterator<String> argIterator = argList.iterator();
    while (argIterator.hasNext()) {
      final String next = argIterator.next();

      if (next.equals("--parallelism")) {
        if (!argIterator.hasNext()) {
          throw new IllegalArgumentException("Missing argument for --parallelism");
        }
        final String value = argIterator.next();
        try {
          parallelism = Integer.parseInt(value);
        } catch (final NumberFormatException ex) {
          throw new IllegalArgumentException("Invalid format for --parallelism argument '" + value + "'", ex);
        }
        continue;
      } else if (next.equals("--message")) {
        final StringJoiner joiner = new StringJoiner(" ");
        while (argIterator.hasNext()) {
          joiner.add(argIterator.next());
        }
        deprecationMessage0 = joiner.toString();
        break;
      }

      if (!argIterator.hasNext()) {
        throw new IllegalArgumentException("Missing output directory for " + next);
      }
      final String out = argIterator.next();
      inputs.add(new IOPaths(java.nio.file.Paths.get(next), java.nio.file.Paths.get(out)));
    }

    final String deprecationMessage = deprecationMessage0;
    final ExecutorService taskExecutor = Executors.newFixedThreadPool(parallelism, new NamedThreadFactory("task-executor"));

    final List<ProcessingTask> futures = inputs.stream()
      .map(path -> scheduleProcessing(taskExecutor, path, deprecationMessage))
      .toList();

    for (final ProcessingTask task : futures) {
      try {
        task.future().join();
      } catch (final Exception ex) {
        LOGGER.error("Failed to process {}", task.paths().input(), ex);
      }
    }

    Util.shutdownExecutor(taskExecutor, TimeUnit.SECONDS, 3);
  }

  private static void processSourcesJar(final IOPaths paths, final String deprecationMessage) {
    try {
      SourcesProcessing.process(paths.input(), paths.output(), deprecationMessage);
    } catch (final IOException e) {
      Util.rethrow(e);
    }
  }

  private static void processJar(final IOPaths paths) {
    JarProcessing.process(paths.input(), paths.output());
  }

  private static ProcessingTask scheduleProcessing(final Executor executor, final IOPaths paths, final String deprecationMessage) {
    final CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
      final long start = System.currentTimeMillis();
      if (paths.input().getFileName().toString().endsWith("-sources.jar")) {
        LOGGER.info("Processing {} as sources jar...", paths.input());
        processSourcesJar(paths, deprecationMessage);
      } else {
        LOGGER.info("Processing {}...", paths.input());
        processJar(paths);
      }
      LOGGER.info("Successfully processed {} in {}ms. Saved to {}", paths.input(), System.currentTimeMillis() - start, paths.output());
    }, executor);
    return new ProcessingTask(paths, future);
  }

  private record IOPaths(Path input, Path output) {
  }

  private record ProcessingTask(IOPaths paths, CompletableFuture<Void> future) {
  }
}
