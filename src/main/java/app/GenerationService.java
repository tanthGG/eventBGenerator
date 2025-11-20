package app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GenerationService {
  private final PatternDomParser parser;
  private final EventBMapper mapper;
  private final EventBWriter writer;
  private final RodinProjectService rodinService;
  private final PatternComposer composer = new PatternComposer();
  private final Path thesisFolder;

  public GenerationService(PatternDomParser parser, EventBMapper mapper, EventBWriter writer, RodinProjectService rodinService) {
    this(parser, mapper, writer, rodinService, Paths.get("").toAbsolutePath().resolve("ThesisFolder"));
  }

  public GenerationService(PatternDomParser parser, EventBMapper mapper, EventBWriter writer,
      RodinProjectService rodinService, Path thesisFolder) {
    this.parser = parser;
    this.mapper = mapper;
    this.writer = writer;
    this.rodinService = rodinService;
    this.thesisFolder = thesisFolder;
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
    EventBIR ir = mapper.toEventB(model, refinement);
    return applyLegacyTemplate(patternXmls, refinement, ir);
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

  public Path writeToProject(String projectName, EventBIR ir) throws IOException {
    Path projectDir = rodinService.ensureProject(projectName);
    if (ir.refinement() == 0) {
      writer.clearProject(projectDir);
    } else {
      writer.removeContextFiles(projectDir);
    }
    writer.write(projectDir, ir);
    rodinService.refresh(projectDir);
    return projectDir;
  }

  public Path workspaceRoot() {
    return rodinService.workspace();
  }

  private EventBIR applyLegacyTemplate(List<Path> patternXmls, int refinement, EventBIR ir) {
    if (thesisFolder == null || !Files.isDirectory(thesisFolder)) return ir;
    if (patternXmls == null || patternXmls.isEmpty()) return ir;
    if (refinement != 1) return ir;

    Set<String> selected = new HashSet<>();
    for (Path path : patternXmls) {
      if (path != null && path.getFileName() != null) {
        selected.add(path.getFileName().toString());
      }
    }

    Set<String> legacyM2Set = Set.of(
        "PDestBuffer.xml",
        "PNDBuffer.xml",
        "PPacket.xml",
        "PReceive.xml",
        "PSend.xml",
        "PSensingUnit.xml");

    if (!selected.equals(legacyM2Set)) return ir;

    Path template = thesisFolder.resolve("M2(NoGuard).txt");
    if (!Files.isRegularFile(template)) return ir;
    try {
      String machineText = Files.readString(template, StandardCharsets.UTF_8);
      return new EventBIR(
          ir.baseName(),
          ir.refinement(),
          ir.ctxName(),
          ir.machName(),
          ir.ctxText(),
          machineText);
    } catch (IOException e) {
      System.err.println("Failed to load legacy template " + template + ": " + e.getMessage());
      return ir;
    }
  }
}
