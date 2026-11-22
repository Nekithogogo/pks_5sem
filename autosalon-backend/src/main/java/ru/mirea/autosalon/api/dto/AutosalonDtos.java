package ru.mirea.autosalon.api.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.time.*; import java.util.*; import ru.mirea.autosalon.api.domain.*;
public final class AutosalonDtos { private AutosalonDtos(){}
 public record ClientInput(@NotBlank @Size(min=2,max=150) String fullName,@NotBlank @Pattern(regexp="[+]7[0-9]{10}") String phone,@Email @Size(max=100) String email,@NotNull @Past LocalDate birthDate,@NotBlank @Pattern(regexp="[0-9]{10}") String licenseNumber){}
 public record ClientView(UUID id,String fullName,String phone,String email,LocalDate birthDate,String licenseNumber){}
 public record CarInput(@NotBlank @Size(max=50) String brand,@NotBlank @Size(max=50) String model,@Min(1990) @Max(2100) int manufactureYear,@NotBlank @Pattern(regexp="[A-HJ-NPR-Z0-9]{17}") String vin,@NotBlank @Size(max=30) String color,@NotNull @DecimalMin(value="0.01") @Digits(integer=10,fraction=2) BigDecimal price,@NotBlank @Pattern(regexp="AVAILABLE|IN_SERVICE|SOLD") String status,boolean active,Long version){}
 public record CarView(UUID id,String brand,String model,int manufactureYear,String vin,String color,BigDecimal price,String status,boolean active,long version){}
 public record RequestInput(@NotBlank @Size(max=100) String externalId,@NotNull UUID clientId,@NotNull UUID carId,@NotNull LocalDateTime scheduledAt,@Min(15) @Max(120) int durationMinutes,@NotNull RequestStatus status,@Size(max=500) String comment,boolean callbackRequested,@Size(max=20) Set<@NotBlank @Size(max=50) String> tags,Long version){}
 public record RequestView(UUID id,String externalId,UUID clientId,String clientName,UUID carId,String carName,String vin,LocalDateTime scheduledAt,int durationMinutes,RequestStatus status,String comment,boolean callbackRequested,RequestPriority priority,boolean archived,Instant archivedAt,Set<String> tags,LocalDateTime createdAt,LocalDateTime updatedAt,long version){}
 public enum BulkAction { ARCHIVE,RESTORE,SET_STATUS,ADD_TAG,REMOVE_TAG }
 public record BulkInput(@NotNull @Size(min=1,max=500) Set<UUID> ids,@NotNull BulkAction action,RequestStatus status,@Size(max=50) String tag){}
 public record BulkFailure(UUID id,String message){} public record BulkResult(int requested,int succeeded,List<BulkFailure> failures){}
 public record NoteInput(@NotBlank @Size(max=4000) String body,boolean pinned,Long version){}
 public record NoteView(UUID id,String author,String body,boolean pinned,Instant createdAt,Instant updatedAt,long version){}
 public record NamedCount(String name,long count){} public record Analytics(long total,long active,long archived,long callbackRequested,Double averageDuration,List<NamedCount> byCar,List<NamedCount> byStatus,List<NamedCount> byPriority){}
}
