package com.rometransit.util.validation;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    private final List<String> errors;
    private final List<String> warnings;

    public ValidationResult() {
        this.errors = new ArrayList<>();
        this.warnings = new ArrayList<>();
    }

    public void addError(String error) {
        errors.add(error);
    }

    public void addWarning(String warning) {
        warnings.add(warning);
    }

    public void addErrors(List<String> errors) {
        this.errors.addAll(errors);
    }

    public void addWarnings(List<String> warnings) {
        this.warnings.addAll(warnings);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    public List<String> getErrors() {
        return new ArrayList<>(errors);
    }

    public List<String> getWarnings() {
        return new ArrayList<>(warnings);
    }

    public String getErrorsAsString() {
        return String.join("; ", errors);
    }

    public String getWarningsAsString() {
        return String.join("; ", warnings);
    }

    public void merge(ValidationResult other) {
        if (other != null) {
            this.errors.addAll(other.errors);
            this.warnings.addAll(other.warnings);
        }
    }

    public void clear() {
        errors.clear();
        warnings.clear();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ValidationResult{");
        sb.append("valid=").append(isValid());
        
        if (hasErrors()) {
            sb.append(", errors=").append(errors);
        }
        
        if (hasWarnings()) {
            sb.append(", warnings=").append(warnings);
        }
        
        sb.append("}");
        return sb.toString();
    }
}