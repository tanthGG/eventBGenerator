package app;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EventBMapper {
  private static final Map<String, String> EVENT_REFINES =
      Map.ofEntries(
          Map.entry("creatingdatapacket", "creatingPkt"),
          Map.entry("creatingcontrolpacket", "creatingPkt"));

  private static final Set<String> EVENT_EXTENDS =
      Set.of(
          "start_tx",
          "send_down",
          "send_up",
          "receive",
          "fwdr_receive_pkt",
          "dest_receive_pkt",
          "clear_recvdbuff",
          "finish_tx_pkt",
          "final_tx_pkt");

  private static final String[] PSENSING_REFINEMENT_VARIABLES = {
    "pktFwdr",
    "pktData",
    "createdPkts",
    "waitingBuff",
    "sentDown",
    "sentUp",
    "ctlNeighbours",
    "destBuff",
    "recvBuff",
    "clrRecvBuffFlg"
  };

  private static final String[][] PSENSING_REFINEMENT_EVENTS = {
    {"creatingDataPacket", "refines creatingPkt"},
    {"creatingControlPacket", "refines creatingPkt"},
    {"start_tx", "extends start_tx"},
    {"send_down", "extends send_down"},
    {"send_up", "extends send_up"},
    {"receive", "extends receive"},
    {"clear_recvdBuff", "extends clear_recvdBuff"},
    {"fwdr_receive_pkt", "extends fwdr_receive_pkt"},
    {"dest_recv_pkt", "extends dest_recv_pkt"},
    {"finish_tx_pkt", "extends finish_tx_pkt"}
  };

  public EventBIR toEventB(PatternModel m, int refinement) {
    String baseName = (m.name != null && !m.name.isBlank()) ? m.name.trim() : "Pattern";
    int refIndex = Math.max(refinement, 0);
    boolean includesPSensing = includesPattern(m, "PSensingUnit");
    int level = refIndex + 1;
    String ctxName;
    String machName;
    if (includesPSensing) {
      ctxName = "cM" + level;
      machName = level == 1 ? "pM1" : "uM" + level;
    } else {
      ctxName = baseName + "_C" + refIndex;
      machName = baseName + "_M" + refIndex;
    }
    String parentMachine = null;
    if (refIndex > 0) {
      if (includesPSensing) {
        parentMachine = level == 2 ? "pM1" : "uM" + (level - 1);
      } else {
        parentMachine = baseName + "_M" + (refIndex - 1);
      }
    }

    StringBuilder ctxSb = new StringBuilder();
    ctxSb.append("context ").append(ctxName).append("\n");

    // Sets
    if (m.context != null && m.context.sets != null && !m.context.sets.isEmpty()) {
      ctxSb.append("sets\n");
      for (String s : m.context.sets) {
        ctxSb.append("  ").append(s).append("\n");
      }
      ctxSb.append("\n");
    }

    // Constants
    if (m.context != null && m.context.constants != null && !m.context.constants.isEmpty()) {
      ctxSb.append("constants\n");
      for (String c : m.context.constants) {
        ctxSb.append("  ").append(c).append("\n");
      }
      ctxSb.append("\n");
    }

    // Axioms
    if (m.context != null && m.context.axioms != null && !m.context.axioms.isEmpty()) {
      ctxSb.append("axioms\n");
      int ax = 0;
      for (String axiom : m.context.axioms) {
        ctxSb.append(String.format("  @ax%02d %s\n", ++ax, axiom));
      }
      ctxSb.append("\n");
    }

    ctxSb.append("end\n");

    if (includesPSensing && level > 1) {
      String machineText = buildPSensingRefinementSkeleton(machName, ctxName, parentMachine);
      return new EventBIR(baseName, refIndex, ctxName, machName, ctxSb.toString(), machineText);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("MACHINE ").append(machName).append("\n");
    if (parentMachine != null) {
      sb.append("REFINES ").append(parentMachine).append("\n");
    }
    sb.append("SEES ").append(ctxName).append("\n\n");

    // Variables
    if (!m.variables.isEmpty()) {
      sb.append("VARIABLES\n");
      for (var v : m.variables) sb.append("  ").append(v.name).append("\n");
      sb.append("\n");
    }

    // Invariants
    if (!m.invariants.isEmpty()) {
      sb.append("INVARIANTS\n");
      int i = 0;
      for (var inv : m.invariants)
        sb.append(String.format("  @inv%02d %s\n", ++i, inv.expression));
      sb.append("\n");
    }

    // Events
    sb.append("EVENTS\n");

    PatternModel.Event initEvent = null;
    for (var e : m.events) {
      if ("initialisation".equalsIgnoreCase(e.name)) {
        initEvent = e;
        break;
      }
    }

    if (initEvent != null) {
      sb.append("  event INITIALISATION\n");
      if (refIndex > 1) {
        sb.append("    extends INITIALISATION\n");
      }
      sb.append("    then\n");
      int a = 0;
      for (var ac : initEvent.actions)
        sb.append(String.format("      @int%02d %s\n", ++a, ac.assignment));
      if (a == 0) sb.append("      @int01 skip\n");
      sb.append("  end\n\n");
    } else {
      sb.append("  event INITIALISATION\n");
      if (refIndex > 1) {
        sb.append("    extends INITIALISATION\n");
      }
      sb.append("    then\n      @int01 skip\n  end\n\n");
    }

    for (var e : m.events) {
      if (initEvent != null && e == initEvent) continue;
      sb.append("  event ").append(e.name).append("\n");
      if (refIndex > 1) {
        String clause = refinementClauseForEvent(e.name);
        if (clause != null) {
          sb.append("    ").append(clause).append("\n");
        }
      }
      if (!e.params.isEmpty()) {
        sb.append("    any ");
        for (int i = 0; i < e.params.size(); i++) {
          if (i > 0) sb.append(" ");
          sb.append(e.params.get(i).name);
        }
        sb.append("\n");
      }

      int g = 0;
      StringBuilder guardSb = new StringBuilder();
      for (var p : e.params) {
        if (p.type != null && !p.type.isBlank() && !hasExplicitTypeGuard(e.guards, p.name, p.type)) {
          guardSb.append(String.format("      @g%02d %s ∈ %s\n", ++g, p.name, p.type));
        }
      }
      for (var gu : e.guards) {
        guardSb.append(String.format("      @g%02d %s\n", ++g, gu.expr));
      }

      if (guardSb.length() > 0) {
        sb.append("    where\n");
        sb.append(guardSb);
      }

      if (e.actions.isEmpty()) {
        sb.append("  end\n\n");
        continue;
      }

      sb.append("    then\n");
      int a = 0;
      for (var ac : e.actions)
        sb.append(String.format("      @a%02d %s\n", ++a, ac.assignment));
      sb.append("  end\n\n");
    }

    sb.append("end\n");
    return new EventBIR(baseName, refIndex, ctxName, machName, ctxSb.toString(), sb.toString());
  }

  private static boolean hasExplicitTypeGuard(java.util.List<PatternModel.Guard> guards, String param, String type) {
    if (guards == null || guards.isEmpty()) return false;
    String needle = (param + " ∈ " + type).replaceAll("\\s+", "");
    for (var g : guards) {
      if (g.expr == null) continue;
      String normalized = g.expr.replaceAll("\\s+", "");
      if (normalized.equals(needle)) return true;
    }
    return false;
  }

  private static boolean includesPattern(PatternModel model, String targetPatternName) {
    if (model == null || targetPatternName == null) return false;
    String target = targetPatternName.replaceAll("\\s+", "");
    if (target.isEmpty()) return false;

    if (model.name != null) {
      String normalizedModelName = model.name.replaceAll("\\s+", "");
      if (normalizedModelName.equalsIgnoreCase(target)) {
        return true;
      }
    }

    for (PatternModel.Event event : model.events) {
      if (event == null || event.sourcePattern == null) continue;
      String[] parts = event.sourcePattern.split("\\+");
      for (String part : parts) {
        String normalizedPart = part.replaceAll("\\s+", "");
        if (normalizedPart.equalsIgnoreCase(target)) {
          return true;
        }
      }
    }

    return false;
  }

  private static String refinementClauseForEvent(String eventName) {
    if (eventName == null || eventName.isBlank()) return null;
    String normalized = eventName.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    if (EVENT_REFINES.containsKey(normalized)) {
      return "refines " + EVENT_REFINES.get(normalized);
    }
    if (EVENT_EXTENDS.contains(normalized)) {
      return "extends " + eventName;
    }
    return null;
  }

  private static String buildPSensingRefinementSkeleton(
      String machName, String ctxName, String parentMachine) {
    StringBuilder sb = new StringBuilder();
    sb.append("MACHINE ").append(machName).append("\n");
    if (parentMachine != null) {
      sb.append("REFINES ").append(parentMachine).append("\n");
    }
    sb.append("SEES ").append(ctxName).append("\n\n");
    sb.append("VARIABLES\n");
    for (String var : PSENSING_REFINEMENT_VARIABLES) {
      sb.append("  ").append(var).append("\n");
    }
    sb.append("\nEVENTS\n");
    sb.append("  Initialisation\n");
    sb.append("    extends\n");
    sb.append("    begin\n");
    sb.append("      skip\n");
    sb.append("    end\n\n");
    for (int i = 0; i < PSENSING_REFINEMENT_EVENTS.length; i++) {
      String[] event = PSENSING_REFINEMENT_EVENTS[i];
      sb.append("Event ").append(event[0]).append(" ≙\n");
      sb.append(event[1]).append("\n");
      sb.append("then\n");
      sb.append("  skip\n");
      sb.append("end");
      if (i < PSENSING_REFINEMENT_EVENTS.length - 1) {
        sb.append("\n\n");
      }
    }
    sb.append("\n\nend\n");
    return sb.toString();
  }
}
