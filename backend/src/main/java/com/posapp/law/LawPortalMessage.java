package com.posapp.law;

import com.posapp.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** One message between the client and the matter's team. */
@Getter
@Setter
@Entity
@Table(indexes = @Index(columnList = "matter_id"))
public class LawPortalMessage extends BaseEntity {

    @Column(name = "matter_id", nullable = false)
    private Long matterId;

    @Column(nullable = false)
    private boolean fromClient;

    private Long portalAccountId;
    private Long senderUserId;

    @Column(nullable = false)
    private String senderName;

    @Column(nullable = false, length = 2000)
    private String body;

    /** A team member has read a message from the client. */
    @Column(nullable = false)
    private boolean readByStaff = false;

    /** The client has read a message from the team. */
    @Column(nullable = false)
    private boolean readByClient = false;
}
