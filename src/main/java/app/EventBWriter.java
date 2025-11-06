package app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class EventBWriter {
  public void write(Path project, EventBIR ir) throws IOException {
    Files.createDirectories(project);
    Files.writeString(project.resolve(ir.ctxName() + ".ctx"), ir.ctxText(), StandardCharsets.UTF_8);
    Files.writeString(project.resolve(ir.machName() + ".bcm"), ir.machineText(), StandardCharsets.UTF_8);
  }

  public void removeContextFiles(Path project) throws IOException {
    if (!Files.exists(project)) return;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(project, "*.ctx")) {
      for (Path entry : stream) {
        Files.deleteIfExists(entry);
      }
    }
  }

  public void clearProject(Path project) throws IOException {
    if (!Files.exists(project)) return;
    try (DirectoryStream<Path> stream = Files.newDirectoryStream(project)) {
      for (Path entry : stream) {
        String name = entry.getFileName().toString();
        if (name.endsWith(".ctx") || name.endsWith(".bcm")) {
          Files.deleteIfExists(entry);
        }
      }
    }
  }
}
