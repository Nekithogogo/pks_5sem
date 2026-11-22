package ru.mirea.autosalon.api.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import ru.mirea.autosalon.api.domain.*;
import ru.mirea.autosalon.api.repository.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class RequestTimeTest {
    @Autowired ClientRepository clients;
    @Autowired CarRepository cars;
    @Autowired RequestRepository requests;
    @Autowired JdbcTemplate jdbc;

    @Test
    void scheduledTimeIsStoredAsTheSameLocalTime() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6);
        int digits = Integer.parseInt(suffix, 16) % 1000000;
        Client client = new Client();
        client.fullName = "Клиент проверки времени";
        client.phone = "+7901" + String.format("%07d", digits);
        client.email = "time-" + suffix + "@example.org";
        client.birthDate = LocalDate.of(1990, 1, 1);
        client.licenseNumber = "88" + String.format("%08d", digits);
        clients.saveAndFlush(client);
        Car car = new Car();
        car.brand = "Время";
        car.model = "Проверка";
        car.manufactureYear = 2024;
        car.vin = "XW7BF4FK10S" + String.format("%06d", digits);
        car.color = "Белый";
        car.price = new BigDecimal("100000.00");
        car.status = "AVAILABLE";
        cars.saveAndFlush(car);
        TestDriveRequest request = new TestDriveRequest();
        request.externalId = "TIME-" + UUID.randomUUID();
        request.client = client;
        request.car = car;
        request.scheduledAt = LocalDate.now().plusDays(2).atTime(10, 30);
        request.durationMinutes = 30;
        request.status = RequestStatus.NEW;
        request.priority = RequestPriority.MEDIUM;
        requests.saveAndFlush(request);

        LocalDateTime stored = jdbc.queryForObject("SELECT scheduled_at FROM test_drive_requests WHERE external_id = ?",
                (row, index) -> row.getObject(1, LocalDateTime.class), request.externalId);
        assertThat(stored).isEqualTo(request.scheduledAt);
    }
}
