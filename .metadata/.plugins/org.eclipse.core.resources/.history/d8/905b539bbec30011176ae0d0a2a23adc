package com.posapp.catalog.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Options to generate variants from, e.g. Size = S, M, L and Colour = Red, Blue → six variants. */
public class VariantRequest {

    @NotEmpty(message = "Add at least one option (e.g. Size)")
    @Valid
    private List<Attribute> attributes;

    public List<Attribute> getAttributes() { return attributes; }
    public void setAttributes(List<Attribute> attributes) { this.attributes = attributes; }

    public static class Attribute {
        @NotBlank(message = "Option name is required")
        private String name;

        @NotEmpty(message = "Each option needs at least one value")
        private List<String> values;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<String> getValues() { return values; }
        public void setValues(List<String> values) { this.values = values; }
    }
}
