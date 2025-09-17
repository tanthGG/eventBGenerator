package app;

public class EventBMapper {
  public EventBIR toEventB(PatternModel m) {
    String ctxName = m.name + "_C0";
    String machName = m.name + "_M0";

    String ctx = "context " + ctxName + "\n"
               + "sets\n  ND\n  Dests\n"
               + "end\n";

    StringBuilder sb = new StringBuilder();
    sb.append("machine ").append(machName).append("\n")
      .append("sees ").append(ctxName).append("\n\n");

    if (!m.variables.isEmpty()) {
      sb.append("variables\n");
      for (var v : m.variables) sb.append("  ").append(v.name).append("\n");
      sb.append("\n");
    }

    if (!m.invariants.isEmpty()) {
      sb.append("invariants\n");
      int i = 0;
      for (var inv : m.invariants)
        sb.append("  @inv").append(++i).append(" ").append(inv.expression).append("\n");
      sb.append("\n");
    }

    sb.append("events\n");
    sb.append("  event INITIALISATION\n    then\n      @a1 skip\n  end\n\n");

    for (var e : m.events) {
      sb.append("  event ").append(e.name).append("\n");
      if (!e.params.isEmpty()) {
        sb.append("    any ");
        for (int i=0;i<e.params.size();i++) {
          if (i>0) sb.append(" ");
          sb.append(e.params.get(i).name);
        }
        sb.append("\n    where\n");
        int g = 0;
        for (var p : e.params)
          sb.append("      @g").append(++g).append(" ").append(p.name).append(" ∈ ").append(p.type).append("\n");
      } else if (!e.guards.isEmpty()) {
        sb.append("    where\n");
      }
      int g = e.params.size();
      for (var gu : e.guards)
        sb.append("      @g").append(++g).append(" ").append(gu.expr).append("\n");

      sb.append("    then\n");
      int a = 0;
      for (var ac : e.actions)
        sb.append("      @a").append(++a).append(" ").append(ac.assignment).append("\n");
      if (a == 0) sb.append("      @a1 skip\n");
      sb.append("  end\n\n");
    }

    sb.append("end\n");
    return new EventBIR(ctx, sb.toString());
  }
}