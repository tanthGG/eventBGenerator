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

  public EventBIR compose(List<Path> patternXmls, int refinement) throws Exception {
    if (patternXmls == null || patternXmls.isEmpty()) {
      throw new IllegalArgumentException("No pattern XML paths provided");
    }
    List<PatternModel> models = new ArrayList<>();
    for (Path path : patternXmls) {
      models.add(parser.parse(path));
    }
    PatternModel model = models.size() == 1 ? models.get(0) : composer.compose(models);
    return mapper.toEventB(model, refinement);
  }

  public EventBIR compose(List<Path> patternXmls) throws Exception {
    return compose(patternXmls, 0);
  }

  public void generate(List<Path> patternXmls, String projectName, ReqSpec requirements) throws Exception {
    Path projectDir = rodinService.ensureProject(projectName);
    EventBIR ir = compose(patternXmls);
    writer.write(projectDir, ir);
    rodinService.refresh(projectDir);
  }
}
