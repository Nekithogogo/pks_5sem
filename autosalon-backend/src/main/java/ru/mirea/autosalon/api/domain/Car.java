package ru.mirea.autosalon.api.domain;
import jakarta.persistence.*; import java.math.BigDecimal; import java.util.UUID;
@Entity @Table(name="cars") public class Car {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) public Long id;
 @Column(name="public_id",nullable=false,unique=true) public UUID publicId;
 @Column(nullable=false,length=50) public String brand;
 @Column(nullable=false,length=50) public String model;
 @Column(name="manufacture_year",nullable=false) public int manufactureYear;
 @Column(nullable=false,unique=true,length=17) public String vin;
 @Column(nullable=false,length=30) public String color;
 @Column(nullable=false,precision=12,scale=2) public BigDecimal price;
 @Column(nullable=false,length=20) public String status;
 @Column(nullable=false) public boolean active=true;
 @Version public long version;
 @PrePersist public void initialize(){if(publicId==null)publicId=UUID.randomUUID();}
}
