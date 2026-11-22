package ru.mirea.autosalon.api.domain;
import jakarta.persistence.*; import java.util.UUID;
@Entity @Table(name="request_note") public class RequestNote extends AuditedVersionedEntity {
 @Id public UUID id;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="request_id",nullable=false) public TestDriveRequest request;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="author_id",nullable=false) public UserAccount author;
 @Column(nullable=false,length=4000) public String body;
 @Column(nullable=false) public boolean pinned;
}
