package app;

import java.nio.file.Path;

public class GenerationService {
  private final PatternDomParser parser;
  private final EventBMapper mapper;
  private final EventBWriter writer;
  private final RodinProjectService rodinService;

  public GenerationService(PatternDomParser parser, EventBMapper mapper, EventBWriter writer, RodinProjectService rodinService) {
    this.parser = parser;
    this.mapper = mapper;
    this.writer = writer;
    this.rodinService = rodinService;
  }

  public void generate(Path patternXml, String projectName, ReqSpec requirements) throws Exception {
    Path projectDir = rodinService.ensureProject(projectName);
    PatternModel model = parser.parse(patternXml);
    EventBIR ir = mapper.toEventB(model);
    writer.write(projectDir, ir.ctxText(), ir.machineText());
    rodinService.refresh(projectDir);
  }
}