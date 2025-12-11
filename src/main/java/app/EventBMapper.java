package app;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
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
          "dest_recv_pkt",
          "clear_recvdbuff",
          "finish_tx_pkt",
          "final_tx_pkt",
          "creatingdatapacket",
          "creatingcontrolpacket");

  public EventBIR toEventB(PatternModel m, int refinement) {
    String baseName = (m.name != null && !m.name.isBlank()) ? m.name.trim() : "Pattern";
    int refIndex = Math.max(refinement, 0);
    boolean includesPSensing = includesPattern(m, "PSensingUnit") || includesPattern(m, "MSensingUnit");
    boolean allowExtends = includesPSensing;
    int level = refIndex + 1;
    String ctxName = "Context";
    String machName = includesPSensing ? "uM" + level : "M" + level;
    String parentMachine = null;
    if (refIndex > 0) {
      parentMachine = includesPSensing ? "M" + refIndex : "M" + refIndex;
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
      for (String axiom : m.context.axioms) {
        ctxSb.append("  ").append(axiom).append("\n");
      }
      ctxSb.append("\n");
    }

    ctxSb.append("end\n");

    StringBuilder sb = new StringBuilder();
    sb.append("MACHINE ").append(machName).append("\n");
    if (parentMachine != null) {
      sb.append("REFINES ").append(parentMachine).append("\n");
    }
    sb.append("SEES ").append(ctxName).append("\n\n");

    // Variables
    List<PatternModel.Variable> orderedVars = orderVariables(m.variables);
    if (!orderedVars.isEmpty()) {
      sb.append("VARIABLES\n");
      for (var v : orderedVars) sb.append("  ").append(v.name).append("\n");
      sb.append("\n");
    }

    // Invariants
    List<PatternModel.Invariant> orderedInvs = orderInvariants(m.invariants);
    if (!orderedInvs.isEmpty()) {
      sb.append("INVARIANTS\n");
      for (var inv : orderedInvs) {
        if (inv.expression != null && !inv.expression.isBlank()) {
          sb.append("  ").append(inv.expression.trim()).append("\n");
        }
      }
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
      if (refIndex > 0 && allowExtends) {
        sb.append("    extends INITIALISATION\n");
      }
      sb.append("    then\n");
      int actionCount = 0;
      for (var ac : initEvent.actions) {
        if (ac.assignment != null && !ac.assignment.isBlank()) {
          sb.append("      ").append(ac.assignment).append("\n");
          actionCount++;
        }
      }
      if (actionCount == 0) sb.append("      skip\n");
      sb.append("  end\n\n");
    } else {
      sb.append("  event INITIALISATION\n");
      if (refIndex > 0) {
        sb.append("    extends INITIALISATION\n");
      }
      sb.append("    then\n      skip\n  end\n\n");
    }

    for (var e : m.events) {
      if (initEvent != null && e == initEvent) continue;
      sb.append("  event ").append(e.name).append("\n");
      String clause = refinementClauseForEvent(e.name);
      if (refIndex > 0 && allowExtends && clause != null) {
        sb.append("    ").append(clause).append("\n");
      }
      if (!e.params.isEmpty()) {
        sb.append("    any ");
        for (int i = 0; i < e.params.size(); i++) {
          if (i > 0) sb.append(" ");
          sb.append(e.params.get(i).name);
        }
        sb.append("\n");
      }

      StringBuilder guardSb = new StringBuilder();
      for (var p : e.params) {
        if (p.type != null && !p.type.isBlank() && !hasExplicitTypeGuard(e.guards, p.name, p.type)) {
          guardSb.append("      ").append(p.name).append(" ∈ ").append(p.type).append("\n");
        }
      }
      for (var gu : e.guards) {
        if (gu.expr != null && !gu.expr.isBlank()) {
          guardSb.append("      ").append(gu.expr).append("\n");
        }
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
      for (var ac : e.actions) {
        if (ac.assignment != null && !ac.assignment.isBlank()) {
          sb.append("      ").append(ac.assignment).append("\n");
        }
      }
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

  private static final List<String> VARIABLE_ORDER =
      List.of(
          "pktFwdr",
          "pktData",
          "createdPkts",
          "waitingBuff",
          "sentDown",
          "sentUp",
          "destBuff",
          "recvBuff",
          "clrRecvBuffFlg");

  private static final Map<String, Integer> VARIABLE_RANK;

  static {
    Map<String, Integer> ranks = new HashMap<>();
    for (int i = 0; i < VARIABLE_ORDER.size(); i++) {
      ranks.put(VARIABLE_ORDER.get(i), i);
    }
    VARIABLE_RANK = Map.copyOf(ranks);
  }

  private static List<PatternModel.Variable> orderVariables(List<PatternModel.Variable> variables) {
    List<PatternModel.Variable> ordered = new ArrayList<>(variables);
    ordered.sort(
        Comparator.comparingInt((PatternModel.Variable v) -> rankVariable(v == null ? null : v.name))
            .thenComparing(
                (PatternModel.Variable v) -> v == null || v.name == null ? "" : v.name));
    return ordered;
  }

  private static int rankVariable(String name) {
    if (name == null) return Integer.MAX_VALUE;
    Integer rank = VARIABLE_RANK.get(name.trim());
    return rank != null ? rank : Integer.MAX_VALUE;
  }

  private static List<PatternModel.Invariant> orderInvariants(List<PatternModel.Invariant> invariants) {
    List<PatternModel.Invariant> ordered = new ArrayList<>(invariants);
    ordered.sort(
        Comparator.comparingInt(
                (PatternModel.Invariant inv) -> rankInvariant(inv == null ? null : inv.expression))
            .thenComparing(
                (PatternModel.Invariant inv) ->
                    inv == null || inv.expression == null ? "" : inv.expression));
    return ordered;
  }

  private static int rankInvariant(String expression) {
    if (expression == null) return Integer.MAX_VALUE;
    for (String var : VARIABLE_ORDER) {
      if (expression.contains(var)) {
        return rankVariable(var);
      }
    }
    return Integer.MAX_VALUE;
  }
}
