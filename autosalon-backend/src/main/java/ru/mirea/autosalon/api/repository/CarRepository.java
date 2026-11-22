package ru.mirea.autosalon.api.repository;
import org.springframework.data.jpa.repository.JpaRepository; import ru.mirea.autosalon.api.domain.Car; import java.util.*;
public interface CarRepository extends JpaRepository<Car,Long> { Optional<Car> findByPublicId(UUID id); @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE) @org.springframework.data.jpa.repository.Query("select e from Car e where e.publicId = :id") Optional<Car> lockByPublicId(UUID id); Optional<Car> findByVinIgnoreCase(String vin); List<Car> findAllByOrderByBrandAscModelAsc(); }
