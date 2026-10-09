package com.posapp.manufacturing.dto;

import com.posapp.manufacturing.Routing;
import com.posapp.manufacturing.RoutingOperation;

import java.math.BigDecimal;

public record RoutingDto(
        Long id,
        String name,
        String description,
        boolean active,
        java.util.List<OperationDto> operations
) {
    public record OperationDto(
            Long id,
            int sequence,
            String name,
            Long workstationId,
            String workstationName,
            BigDecimal estimatedMinutesPerUnit
    ) {
        public static OperationDto from(RoutingOperation op) {
            var ws = op.getWorkstation();
            return new OperationDto(op.getId(), op.getSequence(), op.getName(),
                    ws != null ? ws.getId() : null, ws != null ? ws.getName() : null,
                    op.getEstimatedMinutesPerUnit());
        }
    }

    public static RoutingDto from(Routing r) {
        return new RoutingDto(r.getId(), r.getName(), r.getDescription(), r.isActive(),
                r.getOperations().stream()
                        .sorted(java.util.Comparator.comparingInt(RoutingOperation::getSequence))
                        .map(OperationDto::from)
                        .toList());
    }
}
