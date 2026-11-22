package ru.mirea.autosalon.api.domain;
import jakarta.persistence.*; import java.time.*; import java.util.*;
@Entity @Table(name="test_drive_requests") public class TestDriveRequest {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="public_id",nullable=false,unique=true) public UUID publicId;
 @Column(name="external_id",nullable=false,length=100) public String externalId;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="client_id",nullable=false) public Client client;
 @ManyToOne(fetch=FetchType.EAGER,optional=false) @JoinColumn(name="car_id",nullable=false) public Car car;
 @Column(name="scheduled_at",nullable=false) public LocalDateTime scheduledAt;
 @Column(name="duration_minutes",nullable=false) public int durationMinutes;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public RequestStatus status;
 @Column(length=500) public String comment;
 @Column(name="created_at",nullable=false,updatable=false) public LocalDateTime createdAt;
 @Column(name="updated_at",nullable=false) public LocalDateTime updatedAt;
 @Column(name="callback_requested",nullable=false) public boolean callbackRequested;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) public RequestPriority priority;
 @Column(nullable=false) public boolean archived;
 @Column(name="archived_at") public Instant archivedAt;
 @Version public long version;
 @ElementCollection @CollectionTable(name="request_tag",joinColumns=@JoinColumn(name="request_id")) @Column(name="tag",nullable=false,length=50) public Set<String> tags=new LinkedHashSet<>();
 @PrePersist public void create(){if(publicId==null)publicId=UUID.randomUUID(); createdAt=LocalDateTime.now(); updatedAt=createdAt;}
 @PreUpdate public void update(){updatedAt=LocalDateTime.now();}
}
