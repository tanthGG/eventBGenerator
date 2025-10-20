package app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class EventBWriter {
  public void write(Path project, String ctxText, String machineText) throws IOException {
    Files.createDirectories(project);
    Files.writeString(project.resolve("Generated_Composite_C0.ctx"), ctxText, StandardCharsets.UTF_8);
    Files.writeString(project.resolve("Generated_Composite_M0.bcm"), machineText, StandardCharsets.UTF_8);
  }
}
