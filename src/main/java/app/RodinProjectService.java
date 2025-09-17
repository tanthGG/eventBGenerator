package app;

import java.io.IOException;
import java.nio.file.*;

public class RodinProjectService {
  private final Path workspace;
  public RodinProjectService(Path workspace) { this.workspace = workspace; }

  public Path ensureProject(String name) throws IOException {
    Path project = workspace.resolve(name);
    Files.createDirectories(project);
    return project;
  }
  public void refresh(Path project) { /* No-op for CLI */ }
}