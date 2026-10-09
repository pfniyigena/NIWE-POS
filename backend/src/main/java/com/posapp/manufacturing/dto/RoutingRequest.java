package com.posapp.manufacturing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.List;

public class RoutingRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private boolean active = true;

    @NotEmpty(message = "A routing needs at least one operation")
    @Valid
    private List<OperationEntry> operations;

    public static class OperationEntry {
        @NotNull(message = "Sequence is required")
        private Integer sequence;

        @NotBlank(message = "Operation name is required")
        private String name;

        /** Nullable — see RoutingOperation.workstation's javadoc. */
        private Long workstationId;

        @PositiveOrZero(message = "Estimated minutes cannot be negative")
        private BigDecimal estimatedMinutesPerUnit;

        public Integer getSequence() { return sequence; }
        public void setSequence(Integer sequence) { this.sequence = sequence; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Long getWorkstationId() { return workstationId; }
        public void setWorkstationId(Long workstationId) { this.workstationId = workstationId; }
        public BigDecimal getEstimatedMinutesPerUnit() { return estimatedMinutesPerUnit; }
        public void setEstimatedMinutesPerUnit(BigDecimal estimatedMinutesPerUnit) { this.estimatedMinutesPerUnit = estimatedMinutesPerUnit; }
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public List<OperationEntry> getOperations() { return operations; }
    public void setOperations(List<OperationEntry> operations) { this.operations = operations; }
}
