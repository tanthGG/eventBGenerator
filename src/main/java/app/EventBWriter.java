package app;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Comparator;

public class EventBWriter {
  public void write(Path project, EventBIR ir) throws IOException {
    Files.createDirectories(project);
    clearPreviousArtifacts(project);
    Files.writeString(project.resolve(ir.ctxName() + ".ctx"), ir.ctxText(), StandardCharsets.UTF_8);
    Files.writeString(project.resolve(ir.machName() + ".bcm"), ir.machineText(), StandardCharsets.UTF_8);
  }

  private void clearPreviousArtifacts(Path project) throws IOException {
    if (!Files.exists(project)) return;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(project)) {
      for (Path entry : stream) {
        String name = entry.getFileName().toString();
        if (Files.isDirectory(entry)) {
          if (name.startsWith("machine")) {
            deleteRecursively(entry);
          }
          continue;
        }
        if (name.endsWith(".ctx") || name.endsWith(".bcm")) {
          Files.deleteIfExists(entry);
        }
      }
    }
  }

  private void deleteRecursively(Path path) throws IOException {
    if (!Files.exists(path)) return;
    try (var walk = Files.walk(path)) {
      walk.sorted(Comparator.reverseOrder()).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      });
    } catch (UncheckedIOException e) {
      throw e.getCause();
    }
  }
}
