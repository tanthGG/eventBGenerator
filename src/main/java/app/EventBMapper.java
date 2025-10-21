package app;

public class EventBMapper {
  public EventBIR toEventB(PatternModel m, int refinement) {
    String baseName = (m.name != null && !m.name.isBlank()) ? m.name.trim() : "Pattern";
    int refIndex = Math.max(refinement, 0);
    String ctxName = baseName + "_C" + refIndex;
    String machName = baseName + "_M" + refIndex;

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

    StringBuilder sb = new StringBuilder();
    sb.append("machine ").append(machName).append("\n")
      .append("sees ").append(ctxName).append("\n\n");

    // Variables
    if (!m.variables.isEmpty()) {
      sb.append("variables\n");
      for (var v : m.variables) sb.append("  ").append(v.name).append("\n");
      sb.append("\n");
    }

    // Invariants
    if (!m.invariants.isEmpty()) {
      sb.append("invariants\n");
      int i = 0;
      for (var inv : m.invariants)
        sb.append(String.format("  @inv%02d %s\n", ++i, inv.expression));
      sb.append("\n");
    }

    // Events
    sb.append("events\n");

    PatternModel.Event initEvent = null;
    for (var e : m.events) {
      if ("initialisation".equalsIgnoreCase(e.name)) {
        initEvent = e;
        break;
      }
    }

    if (initEvent != null) {
      sb.append("  event INITIALISATION\n");
      sb.append("    then\n");
      int a = 0;
      for (var ac : initEvent.actions)
        sb.append(String.format("      @int%02d %s\n", ++a, ac.assignment));
      if (a == 0) sb.append("      @int01 skip\n");
      sb.append("  end\n\n");
    } else {
      sb.append("  event INITIALISATION\n    then\n      @int01 skip\n  end\n\n");
    }

    for (var e : m.events) {
      if (initEvent != null && e == initEvent) continue;
      sb.append("  event ").append(e.name).append("\n");
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
}
