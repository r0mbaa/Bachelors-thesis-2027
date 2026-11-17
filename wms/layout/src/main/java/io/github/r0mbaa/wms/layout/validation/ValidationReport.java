package io.github.r0mbaa.wms.layout.validation;

import java.util.List;

public record ValidationReport(List<Issue> issues) {

    public ValidationReport {
        issues = List.copyOf(issues);
    }

    public boolean hasErrors() {
        return issues.stream().anyMatch(i -> i.severity() == Issue.Severity.ERROR);
    }

    public List<Issue> errors() {
        return issues.stream().filter(i -> i.severity() == Issue.Severity.ERROR).toList();
    }
}
