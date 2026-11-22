package ru.mirea.autosalon.api.repository;
import org.springframework.data.jpa.repository.*; import ru.mirea.autosalon.api.domain.*; import java.util.*;
public interface RequestRepository extends JpaRepository<TestDriveRequest,Long>, JpaSpecificationExecutor<TestDriveRequest> {
 Optional<TestDriveRequest> findByPublicId(UUID id); Optional<TestDriveRequest> findByExternalIdIgnoreCase(String externalId);
 List<TestDriveRequest> findByStatusInAndClientId(Collection<RequestStatus> statuses,Long clientId);
 List<TestDriveRequest> findByStatusInAndCarId(Collection<RequestStatus> statuses,Long carId);
 long countByArchivedFalse(); long countByArchivedTrue(); long countByArchivedFalseAndCallbackRequestedTrue();
 @Query("select avg(r.durationMinutes) from TestDriveRequest r where r.archived=false") Double averageDuration();
 @Query("select concat(c.brand, ' ', c.model),count(r) from TestDriveRequest r join r.car c where r.archived=false group by c.brand,c.model order by count(r) desc") List<Object[]> countByCar();
 @Query("select r.status,count(r) from TestDriveRequest r where r.archived=false group by r.status") List<Object[]> countByStatus();
 @Query("select r.priority,count(r) from TestDriveRequest r where r.archived=false group by r.priority") List<Object[]> countByPriority();
}
