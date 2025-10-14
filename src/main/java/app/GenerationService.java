package app;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GenerationService {
  private final PatternDomParser parser;
  private final EventBMapper mapper;
  private final EventBWriter writer;
  private final RodinProjectService rodinService;
  private final PatternComposer composer = new PatternComposer();

  public GenerationService(PatternDomParser parser, EventBMapper mapper, EventBWriter writer, RodinProjectService rodinService) {
    this.parser = parser;
    this.mapper = mapper;
    this.writer = writer;
    this.rodinService = rodinService;
  }

  public void generate(Path patternXml, String projectName, ReqSpec requirements) throws Exception {
    generate(List.of(patternXml), projectName, requirements);
  }

  public void generate(List<Path> patternXmls, String projectName, ReqSpec requirements) throws Exception {
    if (patternXmls == null || patternXmls.isEmpty()) {
      throw new IllegalArgumentException("No pattern XML paths provided");
    }
    Path projectDir = rodinService.ensureProject(projectName);
    List<PatternModel> models = new ArrayList<>();
    for (Path path : patternXmls) {
      models.add(parser.parse(path));
    }
    PatternModel model = models.size() == 1 ? models.get(0) : composer.compose(models);
    EventBIR ir = mapper.toEventB(model);
    writer.write(projectDir, ir.ctxText(), ir.machineText());
    rodinService.refresh(projectDir);
  }
}
