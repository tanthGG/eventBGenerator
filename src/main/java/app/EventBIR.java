package app;

public record EventBIR(
    String baseName,
    int refinement,
    String ctxName,
    String machName,
    String ctxText,
    String machineText) {}
