package app;

import java.nio.file.Path;

public class GenerationService {
  private final PatternDomParser parser;
  private final EventBMapper mapper;
  private final EventBWriter writer;
  private final PatternGrammarWriter grammarWriter;
  private final RodinProjectService rodin;

  public GenerationService(PatternDomParser p, EventBMapper m, EventBWriter w,
      PatternGrammarWriter g, RodinProjectService r) {
    this.parser = p; this.mapper = m; this.writer = w; this.grammarWriter = g; this.rodin = r;
  }

  public void generate(Path xml, String projectName, ReqSpec req) throws Exception {
    Path projectDir = rodin.ensureProject(projectName);
    grammarWriter.write(projectDir);
    PatternModel model = parser.parse(xml);
    EventBIR ir = mapper.toEventB(model);
    writer.write(projectDir, ir.ctxText(), ir.machineText());
    rodin.refresh(projectDir);
  }
}