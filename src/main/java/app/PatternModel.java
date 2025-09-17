package app;

import java.util.*;

public class PatternModel {
  public String name = "Pattern";
  public final List<Variable> variables = new ArrayList<>();
  public final List<Invariant> invariants = new ArrayList<>();
  public final List<Event> events = new ArrayList<>();

  public static class Variable { public String name, type; }
  public static class Invariant { public String expression; }

  public static class Event {
    public String name;
    public final List<Param> params = new ArrayList<>();
    public final List<Guard> guards = new ArrayList<>();
    public final List<Action> actions = new ArrayList<>();
  }
  public static class Param { public String name, type; }
  public static class Guard { public String expr; }
  public static class Action { public String assignment; }
}