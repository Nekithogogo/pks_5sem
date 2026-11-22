package ru.mirea.autosalon.api.domain;
import jakarta.persistence.*; import java.time.*; import java.util.UUID;
@Entity @Table(name="clients") public class Client {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="public_id",nullable=false,unique=true) public UUID publicId;
 @Column(name="full_name",nullable=false,length=150) public String fullName;
 @Column(nullable=false,unique=true,length=12) public String phone;
 @Column(unique=true,length=100) public String email;
 @Column(name="birth_date",nullable=false) public LocalDate birthDate;
 @Column(name="license_number",nullable=false,unique=true,length=10) public String licenseNumber;
 @Column(name="registered_at",nullable=false) public LocalDateTime registeredAt;
 @PrePersist public void initialize(){if(publicId==null)publicId=UUID.randomUUID();if(registeredAt==null)registeredAt=LocalDateTime.now();}
}
