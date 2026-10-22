package com.posapp.hr;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/** A file kept on an employee's record (contract copy, ID, qualification, permit...). The bytes are in {@link EmployeeDocumentBlob}, so lists never load them. */
@Getter
@Setter
@Entity
@Table(name = "employee_document", indexes = {@Index(columnList = "employee_id"), @Index(columnList = "expiry_date")})
public class EmployeeDocument extends BaseEntity {

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmployeeDocCategory category = EmployeeDocCategory.OTHER;

    @Column(length = 1000)
    private String description;

    /** For things that lapse: a work permit, an ID card, a driving licence, a certificate. */
    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    /** When true the employee can see and download it under My HR. */
    @Column(name = "shared_with_employee", nullable = false)
    private boolean sharedWithEmployee;

    @Column(nullable = false)
    private String fileName;

    @Column(length = 120)
    private String contentType;

    @Column(nullable = false)
    private long sizeBytes;

    private String uploadedByName;
}
