package app;

import java.nio.file.*;

public class Main {
  public static void main(String[] args) throws Exception {
    if (args.length < 6) {
      System.out.println("Usage: -i <pattern.xml> -p <ProjectName> -o <WorkspacePath>");
      return;
    }
    Path xml = null, workspace = null; String projectName = null;
    for (int i=0;i<args.length;i++) {
      switch (args[i]) {
        case "-i": xml = Paths.get(args[++i]); break;
        case "-p": projectName = args[++i]; break;
        case "-o": workspace = Paths.get(args[++i]); break;
      }
    }
    GenerationService svc = new GenerationService(
      new PatternDomParser(),
      new EventBMapper(),
      new EventBWriter(),
      new PatternGrammarWriter(),
      new RodinProjectService(workspace)
    );
    svc.generate(xml, projectName, ReqSpec.empty());
    System.out.println("Generated in: " + workspace.resolve(projectName));
  }
}