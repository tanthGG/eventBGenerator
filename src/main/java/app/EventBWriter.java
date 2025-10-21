package app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class EventBWriter {
  public void write(Path project, EventBIR ir) throws IOException {
    Files.createDirectories(project);
    Path refinementDir = project.resolve("machine" + ir.refinement());
    Files.createDirectories(refinementDir);
    Files.writeString(refinementDir.resolve(ir.ctxName() + ".ctx"), ir.ctxText(), StandardCharsets.UTF_8);
    Files.writeString(refinementDir.resolve(ir.machName() + ".bcm"), ir.machineText(), StandardCharsets.UTF_8);
  }
}
