package app;

import java.util.*;

/** Combines multiple PatternModel instances into a single merged model. */
public class PatternComposer {

  private final PatternCombinationEngine combinationEngine = new PatternCombinationEngine();

  public PatternModel compose(List<PatternModel> models) {
    if (models == null || models.isEmpty()) {
      throw new IllegalArgumentException("At least one pattern model is required for composition");
    }

    PatternModel result = new PatternModel();
    result.name = deriveName(models);
    result.context = mergeContexts(models);
    mergeVariables(models, result);
    mergeInvariants(models, result);
    mergeEvents(models, result);
    return result;
  }

  private String deriveName(List<PatternModel> models) {
    for (PatternModel model : models) {
      if (model != null && model.name != null && !model.name.isBlank()
          && !"Pattern".equalsIgnoreCase(model.name.trim())) {
        return model.name.trim() + "_Composite";
      }
    }
    return "PatternComposite";
  }

  private PatternModel.Context mergeContexts(List<PatternModel> models) {
    LinkedHashSet<String> sets = new LinkedHashSet<>();
    LinkedHashSet<String> constants = new LinkedHashSet<>();
    LinkedHashSet<String> axioms = new LinkedHashSet<>();

    for (PatternModel model : models) {
      if (model == null || model.context == null) continue;
      sets.addAll(model.context.sets);
      constants.addAll(model.context.constants);
      axioms.addAll(model.context.axioms);
    }

    if (sets.isEmpty() && constants.isEmpty() && axioms.isEmpty()) return null;

    PatternModel.Context ctx = new PatternModel.Context();
    ctx.sets.addAll(sets);
    ctx.constants.addAll(constants);
    ctx.axioms.addAll(axioms);
    return ctx;
  }

  private void mergeVariables(List<PatternModel> models, PatternModel target) {
    Map<String, String> seen = new LinkedHashMap<>();
    for (PatternModel model : models) {
      if (model == null) continue;
      for (PatternModel.Variable var : model.variables) {
        if (var == null || var.name == null) continue;
        String name = var.name.trim();
        if (name.isEmpty()) continue;
        String type = var.type;
        String existing = seen.get(name);
        if (existing != null) {
          if (!Objects.equals(existing, type)) {
            throw new IllegalArgumentException(
                "Variable name clash with different type: " + name);
          }
          continue;
        }
        seen.put(name, type);
        PatternModel.Variable copy = new PatternModel.Variable();
        copy.name = name;
        copy.type = type;
        target.variables.add(copy);
      }
    }
  }

  private void mergeInvariants(List<PatternModel> models, PatternModel target) {
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    for (PatternModel model : models) {
      if (model == null) continue;
      for (PatternModel.Invariant inv : model.invariants) {
        if (inv == null || inv.expression == null) continue;
        String expr = inv.expression.trim();
        if (expr.isEmpty()) continue;
        if (!seen.add(expr)) continue;
        PatternModel.Invariant copy = new PatternModel.Invariant();
        copy.expression = expr;
        target.invariants.add(copy);
      }
    }
  }

  private void mergeEvents(List<PatternModel> models, PatternModel target) {
    PatternModel.Event initEvent = new PatternModel.Event();
    initEvent.name = "Initialisation";
    initEvent.sourcePattern = "Composite";
    LinkedHashSet<String> initAssignments = new LinkedHashSet<>();

    List<PatternModel.Event> collectedEvents = new ArrayList<>();

    for (PatternModel model : models) {
      if (model == null) continue;
      for (PatternModel.Event event : model.events) {
        if (event == null || event.name == null) continue;
        if ("Initialisation".equalsIgnoreCase(event.name)) {
          for (PatternModel.Action act : event.actions) {
            if (act == null || act.assignment == null) continue;
            String assignment = act.assignment.trim();
            if (assignment.isEmpty()) continue;
            if (!initAssignments.add(assignment)) continue;
            PatternModel.Action copy = new PatternModel.Action();
            copy.assignment = assignment;
            initEvent.actions.add(copy);
          }
          continue;
        }

        PatternModel.Event copy = copyEvent(event);
        if (copy.sourcePattern == null || copy.sourcePattern.isBlank()) {
          copy.sourcePattern = model.name;
        }
        collectedEvents.add(copy);
      }
    }

    List<PatternModel.Event> processedEvents = combinationEngine.apply(collectedEvents);

    Map<String, PatternModel.Event> eventsByName = new LinkedHashMap<>();
    Set<String> lowerCaseNames = new HashSet<>();

    for (PatternModel.Event event : processedEvents) {
      if (event == null || event.name == null) continue;
      String baseName = event.name.trim();
      if (baseName.isEmpty()) continue;

      PatternModel.Event existing = eventsByName.get(baseName);
      if (existing != null && eventsEquivalent(existing, event)) {
        continue;
      }

      String candidate = baseName;
      String lower = candidate.toLowerCase(Locale.ROOT);
      if (lowerCaseNames.contains(lower)) {
        int suffix = 2;
        do {
          candidate = baseName + "_" + suffix++;
          lower = candidate.toLowerCase(Locale.ROOT);
        } while (lowerCaseNames.contains(lower));
      }
      lowerCaseNames.add(lower);

      if (!candidate.equals(event.name)) {
        event.name = candidate;
      }
      eventsByName.put(event.name, event);
    }

    target.events.add(initEvent);
    target.events.addAll(eventsByName.values());
  }

  private PatternModel.Event copyEvent(PatternModel.Event source) {
    PatternModel.Event copy = new PatternModel.Event();
    copy.name = source.name;
    copy.sourcePattern = source.sourcePattern;
    for (PatternModel.Param param : source.params) {
      PatternModel.Param p = new PatternModel.Param();
      p.name = param.name;
      p.type = param.type;
      copy.params.add(p);
    }
    for (PatternModel.Guard guard : source.guards) {
      if (guard == null || guard.expr == null) continue;
      PatternModel.Guard g = new PatternModel.Guard();
      g.expr = guard.expr.trim();
      if (g.expr.isEmpty()) continue;
      copy.guards.add(g);
    }
    for (PatternModel.Action action : source.actions) {
      if (action == null || action.assignment == null) continue;
      PatternModel.Action a = new PatternModel.Action();
      a.assignment = action.assignment.trim();
      if (a.assignment.isEmpty()) continue;
      copy.actions.add(a);
    }
    return copy;
  }

  private boolean eventsEquivalent(PatternModel.Event a, PatternModel.Event b) {
    if (!Objects.equals(a.name, b.name)) return false;
    if (a.params.size() != b.params.size()) return false;
    for (int i = 0; i < a.params.size(); i++) {
      PatternModel.Param ap = a.params.get(i);
      PatternModel.Param bp = b.params.get(i);
      if (!Objects.equals(ap.name, bp.name) || !Objects.equals(ap.type, bp.type)) {
        return false;
      }
    }
    if (a.guards.size() != b.guards.size()) return false;
    for (int i = 0; i < a.guards.size(); i++) {
      PatternModel.Guard ag = a.guards.get(i);
      PatternModel.Guard bg = b.guards.get(i);
      if (!Objects.equals(ag.expr, bg.expr)) return false;
    }
    if (a.actions.size() != b.actions.size()) return false;
    for (int i = 0; i < a.actions.size(); i++) {
      PatternModel.Action aa = a.actions.get(i);
      PatternModel.Action ba = b.actions.get(i);
      if (!Objects.equals(aa.assignment, ba.assignment)) return false;
    }
    return true;
  }
}
