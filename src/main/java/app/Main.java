package app;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception {
    List<Path> patternXmls = new ArrayList<>();
    Path workspacePath = null;
    String projectName = null;
    boolean startServer = false;
    int port = 8080;
    for (int i = 0; i < args.length; i++) {
      switch (args[i]) {
        case "-i" -> {
          if (i + 1 >= args.length) {
            usage();
            return;
          }
          String value = args[++i];
          for (String part : value.split(",")) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            patternXmls.add(Paths.get(trimmed));
          }
        }
        case "-p" -> {
          if (i + 1 >= args.length) {
            usage();
            return;
          }
          projectName = args[++i];
        }
        case "-o" -> {
          if (i + 1 >= args.length) {
            usage();
            return;
          }
          workspacePath = Paths.get(args[++i]);
        }
        case "--server" -> startServer = true;
        case "--port" -> {
          if (i + 1 >= args.length) {
            usage();
            return;
          }
          port = Integer.parseInt(args[++i]);
        }
        default -> {
          // ignore unknown switches for now
        }
      }
    }
    Path projectRoot = Paths.get("").toAbsolutePath();
    if (startServer) {
      Path workspace = workspacePath != null ? workspacePath : projectRoot.resolve("generated");
      GenerationService generationService = new GenerationService(
        new PatternDomParser(),
        new EventBMapper(),
        new EventBWriter(),
        new RodinProjectService(workspace)
      );
      WebServer server = new WebServer(projectRoot, generationService);
      try {
        server.start(port);
        System.out.println("Web server started at http://localhost:" + port);
      } catch (IOException e) {
        System.err.println("Failed to start web server: " + e.getMessage());
      }
      return;
    }
    if (patternXmls.isEmpty() || projectName == null || workspacePath == null) {
      usage();
      return;
    }
    GenerationService generationService = new GenerationService(
      new PatternDomParser(),
      new EventBMapper(),
      new EventBWriter(),
      new RodinProjectService(workspacePath)
    );
    if (patternXmls.size() == 1) {
      generationService.generate(patternXmls.get(0), projectName, ReqSpec.empty());
    } else {
      generationService.generate(patternXmls, projectName, ReqSpec.empty());
    }
    System.out.println("Generated in: " + workspacePath.resolve(projectName));
  }

  private static void usage() {
    System.out.println("Usage:");
    System.out.println("  CLI mode:   -i <pattern.xml>[,pattern2.xml...] [-i <patternN.xml> ...] -p <ProjectName> -o <WorkspacePath>");
    System.out.println("  Server mode: --server [--port <Port>] [-o <WorkspacePath>]");
  }
}
