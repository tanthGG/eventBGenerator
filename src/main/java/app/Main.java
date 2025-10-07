package app;

import java.nio.file.*;

public class Main {
  public static void main(String[] args) throws Exception {
    if (args.length < 6) {
      System.out.println("Usage: -i <pattern.xml> -p <ProjectName> -o <WorkspacePath>");
      return;
    }
    Path patternXml = null, workspacePath = null;
    String projectName = null;
    for (int i=0;i<args.length;i++) {
      switch (args[i]) {
        case "-i": patternXml = Paths.get(args[++i]); break;
        case "-p": projectName = args[++i]; break;
        case "-o": workspacePath = Paths.get(args[++i]); break;
      }
    }
    GenerationService generationService = new GenerationService(
      new PatternDomParser(),
      new EventBMapper(),
      new EventBWriter(),
      new RodinProjectService(workspacePath)
    );
    generationService.generate(patternXml, projectName, ReqSpec.empty());
    System.out.println("Generated in: " + workspacePath.resolve(projectName));
  }
}