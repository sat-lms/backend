package com.sat.lms.attachment.entity;

import com.sat.lms.assignment.entity.Assignment;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "assignment_attachment",
        uniqueConstraints = @UniqueConstraint(name = "uk_assignment_attachment_assignment_attachment",
                columnNames = {"assignment_id", "attachment_id"}),
        indexes = {
                @Index(name = "idx_assignment_attachment_assignment_id", columnList = "assignment_id"),
                @Index(name = "idx_assignment_attachment_attachment_id", columnList = "attachment_id")
        })
public class AssignmentAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attachment_id", nullable = false)
    private Attachment attachment;

    protected AssignmentAttachment() {
    }

    public static AssignmentAttachment create(Assignment assignment, Attachment attachment) {
        AssignmentAttachment link = new AssignmentAttachment();
        link.assignment = assignment;
        link.attachment = attachment;
        return link;
    }

    public Long getId() { return id; }
    public Assignment getAssignment() { return assignment; }
    public Attachment getAttachment() { return attachment; }
}
