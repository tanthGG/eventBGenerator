package app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenerationService {
  private final PatternDomParser parser;
  private final EventBMapper mapper;
  private final EventBWriter writer;
  private final RodinProjectService rodinService;
  private final PatternComposer composer = new PatternComposer();
  private final Path thesisFolder;
  private static final Pattern MACHINE_HEADER =
      Pattern.compile("(?im)^\\s*machine\\s+([A-Za-z0-9_]+)");

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
    PatternModel model =
        models.size() == 1 ? models.get(0) : composer.compose(models, refinement);
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

  public Optional<EventBIR> buildAdditionalMachineFromTemplate(
      String templateName,
      EventBIR reference,
      List<Path> activatePatternPaths,
      int refinementCount) {
    if (reference == null || thesisFolder == null) return Optional.empty();
    Path template = thesisFolder.resolve(templateName);
    if (!Files.isRegularFile(template)) return Optional.empty();
    boolean includeActivateContent =
        activatePatternPaths != null && !activatePatternPaths.isEmpty();
    try {
      String machineText = Files.readString(template, StandardCharsets.UTF_8);
      machineText = maybeAugmentWithActivate(machineText, activatePatternPaths);
      int suffix = Math.max(refinementCount, reference.refinement() + 2);
      String machineName = "uM" + suffix;
      machineText = renameMachine(machineText, machineName);
      String contextText =
          includeActivateContent
              ? augmentContextWithActivate(reference.ctxText())
              : reference.ctxText();
      return Optional.of(
          new EventBIR(
              reference.baseName(),
              reference.refinement() + 1,
              reference.ctxName(),
              machineName,
              contextText,
              machineText));
    } catch (IOException e) {
      System.err.println("Failed to load template " + templateName + ": " + e.getMessage());
      return Optional.empty();
    }
  }

  private EventBIR applyLegacyTemplate(List<Path> patternXmls, int refinement, EventBIR ir) {
    if (thesisFolder == null || !Files.isDirectory(thesisFolder)) return ir;
    if (patternXmls == null || patternXmls.isEmpty()) return ir;

    Set<String> selected = new HashSet<>();
    for (Path path : patternXmls) {
      if (path != null && path.getFileName() != null) {
        selected.add(path.getFileName().toString());
      }
    }
    Set<String> normalizedSelection = new HashSet<>(selected);
    normalizedSelection.remove("IActivate.xml");
    normalizedSelection.remove("PActivate.xml");

    List<LegacyTemplate> templates =
        List.of(
            new LegacyTemplate(
                Set.of(
                    "IDestBuffer.xml",
                    "INDBuffer.xml",
                    "IPacket.xml",
                    "IReceive.xml",
                    "ISend.xml"),
                0,
                "M1GGD.txt",
                false),
            new LegacyTemplate(
                Set.of(
                    "IDestBuffer.xml",
                    "INDBuffer.xml",
                    "IPacket.xml",
                    "IReceive.xml",
                    "ISend.xml",
                    "ISensingUnit.xml"),
                null,
                "M2GGD.txt",
                true),
            new LegacyTemplate(
                Set.of(
                    "PDestBuffer.xml",
                    "PNDBuffer.xml",
                    "PPacket.xml",
                    "PReceive.xml",
                    "PSend.xml",
                    "PSensingUnit.xml"),
                1,
                "M2(NoGuard).txt",
                false));

    for (LegacyTemplate template : templates) {
      if (template.matches(normalizedSelection, refinement)) {
        return loadTemplate(ir, template);
      }
    }

    return ir;
  }

  private EventBIR loadTemplate(EventBIR ir, LegacyTemplate templateInfo) {
    Path template = thesisFolder.resolve(templateInfo.fileName());
    if (!Files.isRegularFile(template)) return ir;
    try {
      String machineText = Files.readString(template, StandardCharsets.UTF_8);
      if (templateInfo.adaptNames()) {
        machineText = adaptTemplate(machineText, ir);
      }
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

  private String adaptTemplate(String templateText, EventBIR ir) {
    int level = ir.refinement() + 1;
    String machineName = ir.machName();
    String ctxName = "Context";
    String parentName = level > 1 ? "M" + (level - 1) : "M1";
    return templateText
        .replace("machine uM2", "machine " + machineName)
        .replace("refines pM1", "refines " + parentName)
        .replace("sees cM2", "sees " + ctxName);
  }

  private record LegacyTemplate(
      Set<String> selection, Integer refinement, String fileName, boolean adaptNames) {
    boolean matches(Set<String> selected, int refinement) {
      return selected.equals(selection)
          && (this.refinement == null || this.refinement == refinement);
    }
  }

  private String maybeAugmentWithActivate(String machineText, List<Path> activatePatterns) {
    if (machineText == null || activatePatterns == null || activatePatterns.isEmpty()) {
      return machineText;
    }

    String augmented = machineText;
    if (!augmented.contains("emergencyAlert")) {
      String withVariable = insertBeforeMarker(augmented, "\ninvariants", "  emergencyAlert\n");
      String withInvariant =
          insertBeforeMarker(
              withVariable, "\nevents", "  @PActivate_inv_1 emergencyAlert ∈ BOOL\n");
      augmented = insertBeforeFinalEnd(withInvariant, ACTIVATE_EVENTS_BLOCK);
    }

    List<String> initAssignments = loadActivateInitAssignments(activatePatterns);
    return insertActivateInitActions(augmented, initAssignments);
  }

  private static String insertBeforeMarker(String text, String marker, String addition) {
    if (text == null) return null;
    int idx = text.indexOf(marker);
    if (idx < 0) return text;
    return text.substring(0, idx) + addition + text.substring(idx);
  }

  private static String insertBeforeFinalEnd(String text, String addition) {
    if (text == null || addition == null || addition.isBlank()) return text;
    int idx = text.lastIndexOf("\nend");
    if (idx < 0) {
      return text + addition;
    }
    return text.substring(0, idx) + addition + text.substring(idx);
  }

  private static final String ACTIVATE_EVENTS_BLOCK =
      """

  event actuating
    any data
    where
      @PActivate_actuating_g1 data ∈ ℤ
      @PActivate_actuating_g2 data ≥ safetyThreshold
      @PActivate_actuating_g3 emergencyAlert = FALSE
    then
      @PActivate_actuating_a1 emergencyAlert := TRUE
    end

  event no_actuating
    any data
    where
      @PActivate_no_actuating_g1 data ∈ ℤ
      @PActivate_no_actuating_g2 data < safetyThreshold
  end

  event reset_actuatingStatus
    any data
    where
      @PActivate_reset_actuating_g1 data ≥ safetyThreshold
      @PActivate_reset_actuating_g2 emergencyAlert = TRUE
    then
      @PActivate_reset_actuating_a1 emergencyAlert := FALSE
    end

""";

  private String insertActivateInitActions(String machineText, List<String> assignments) {
    if (machineText == null || assignments == null || assignments.isEmpty()) {
      return machineText;
    }
    int eventIdx = machineText.indexOf("event INITIALISATION");
    if (eventIdx < 0) return machineText;
    int thenIdx = machineText.indexOf("then", eventIdx);
    if (thenIdx < 0) return machineText;
    int insertPos = machineText.indexOf('\n', thenIdx);
    if (insertPos < 0) return machineText;
    insertPos += 1;

    StringBuilder block = new StringBuilder();
    for (String assignment : assignments) {
      if (assignment == null) continue;
      String trimmed = assignment.trim();
      if (trimmed.isEmpty() || machineText.contains(trimmed)) continue;
      block.append("      ").append(trimmed).append("\n");
    }
    if (block.length() == 0) return machineText;
    return machineText.substring(0, insertPos) + block + machineText.substring(insertPos);
  }

  private List<String> loadActivateInitAssignments(List<Path> activatePatterns) {
    List<String> assignments = new ArrayList<>();
    if (activatePatterns == null || activatePatterns.isEmpty()) return assignments;
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (Path path : activatePatterns) {
      if (path == null) continue;
      try {
        PatternModel model = parser.parse(path);
        PatternModel.Event initEvent =
            model.events.stream()
                .filter(e -> e.name != null && "Initialisation".equalsIgnoreCase(e.name))
                .findFirst()
                .orElse(null);
        if (initEvent == null) continue;
        for (PatternModel.Action action : initEvent.actions) {
          if (action == null || action.assignment == null) continue;
          String trimmed = action.assignment.trim();
          if (trimmed.isEmpty() || !seen.add(trimmed)) continue;
          assignments.add(trimmed);
        }
      } catch (Exception e) {
        System.err.println("Failed to parse activate pattern " + path + ": " + e.getMessage());
      }
    }
    return assignments;
  }

  private static String renameMachine(String text, String newName) {
    if (text == null || newName == null || newName.isBlank()) return text;
    Matcher matcher = MACHINE_HEADER.matcher(text);
    if (matcher.find()) {
      return matcher.replaceFirst("machine " + newName);
    }
    return text;
  }

  private String augmentContextWithActivate(String ctxText) {
    if (ctxText == null || ctxText.isBlank()) return ctxText;
    String updated = ctxText;
    updated = ensureSectionLine(updated, "constants", "  BROADCAST");
    updated = ensureSectionLine(updated, "constants", "  safetyThreshold");
    updated = ensureSectionLine(updated, "axioms", "  @cM1_axm0_8 safetyThreshold ∈ ℤ");
    updated = ensureSectionLine(updated, "axioms", "  @cM1_axm0_9 BROADCAST = -1");
    return updated;
  }

  private static String ensureSectionLine(String text, String sectionHeader, String line) {
    if (line == null || line.isBlank()) return text;
    String needle = "\n" + line + "\n";
    if (text.contains(needle)) return text;
    int headerIdx = text.indexOf(sectionHeader);
    if (headerIdx < 0) return text;
    int insertPos = text.indexOf('\n', headerIdx);
    if (insertPos < 0) insertPos = text.length();
    insertPos++;
    return text.substring(0, insertPos) + line + "\n" + text.substring(insertPos);
  }
}
