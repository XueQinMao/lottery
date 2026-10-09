package com.my.project.application.pojo;

import java.util.List;
import java.util.function.Function;

public class KillRule {
    private final String name;
    private final Function<String, List<Integer>> func;

    public KillRule(String name, Function<String, List<Integer>> func) {
        this.name = name;
        this.func = func;
    }

    public String getName() { return name; }

    public List<Integer> apply(String period) {
        try {
            List<Integer> r = func.apply(period);
            return r == null ? List.of() : r;
        } catch (Exception e) {
            return List.of();
        }
    }
}
