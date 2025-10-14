package app;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception {
    List<Path> patternXmls = new ArrayList<>();
    Path workspacePath = null;
    String projectName = null;
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
        default -> {
          // ignore unknown switches for now
        }
      }
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
    System.out.println("Usage: -i <pattern.xml>[,pattern2.xml...] [-i <patternN.xml> ...] -p <ProjectName> -o <WorkspacePath>");
  }
}
