package ru.mirea.autosalon.api.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.security.test.context.support.WithMockUser;
import ru.mirea.autosalon.api.domain.*;
import ru.mirea.autosalon.api.repository.*;
import ru.mirea.autosalon.api.service.AutosalonService;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@WithMockUser(roles = "MANAGER")
class CsvImportTest {
    private static final AtomicInteger SERIAL = new AtomicInteger(100000);
    private static final String HEADER = "externalId,clientPhone,carVin,scheduledAt,durationMinutes,status,comment,callbackRequested,tags\n";
    @Autowired CsvController csv;
    @Autowired ClientRepository clients;
    @Autowired CarRepository cars;
    @Autowired RequestRepository requests;
    @Autowired AutosalonService service;

    @Test
    void previewRejectsUnavailableCarAndDoesNotWrite() {
        Fixture f = fixture("IN_SERVICE");
        long before = requests.count();
        var preview = csv.preview(file(HEADER + row("NEW-" + UUID.randomUUID(), f, f.time(), "описание")), "CREATE_ONLY");
        assertThat(preview.valid()).isZero();
        assertThat(preview.invalid()).isEqualTo(1);
        assertThat(preview.errors().getFirst().message()).contains("недоступен");
        assertThat(requests.count()).isEqualTo(before);
    }

    @Test
    void previewDetectsOverlapsInsideOneFile() {
        Fixture f = fixture("AVAILABLE");
        String rows = row("FIRST-" + UUID.randomUUID(), f, f.time(), "one")
                + row("SECOND-" + UUID.randomUUID(), f, f.time().plusMinutes(5), "two");
        var preview = csv.preview(file(HEADER + rows), "CREATE_ONLY");
        assertThat(preview.valid()).isEqualTo(1);
        assertThat(preview.invalid()).isEqualTo(1);
        assertThat(preview.errors().getFirst().message()).contains("пересекающаяся");
    }

    @Test
    void cancellationFreesLimitAndTimeForFollowingRow() {
        Fixture f = fixture("AVAILABLE");
        String cancelled = "CANCEL-" + UUID.randomUUID();
        seedRequest(f, cancelled, f.time());
        seedRequest(f, "OTHER-" + UUID.randomUUID(), f.time().plusHours(1));
        seedRequest(f, "OTHER-" + UUID.randomUUID(), f.time().plusHours(2));
        String created = "REPLACEMENT-" + UUID.randomUUID();
        var input = file(HEADER + row(cancelled, f, f.time(), "cancel").replace(",NEW,", ",CANCELLED,")
                + row(created, f, f.time(), "new"));

        var preview = csv.preview(input, "UPSERT");
        assertThat(preview.valid()).isEqualTo(2);
        assertThat(preview.invalid()).isZero();
        assertThat(requests.findByExternalIdIgnoreCase(cancelled).orElseThrow().status).isEqualTo(RequestStatus.NEW);
        var result = csv.apply(input, "UPSERT");
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isZero();
        assertThat(requests.findByExternalIdIgnoreCase(cancelled).orElseThrow().status).isEqualTo(RequestStatus.CANCELLED);
        assertThat(requests.findByExternalIdIgnoreCase(created)).isPresent();
    }

    @Test
    void invalidCancellationDoesNotFreeSlot() {
        Fixture f = fixture("AVAILABLE");
        String existing = "EXISTING-" + UUID.randomUUID();
        seedRequest(f, existing, f.time());
        var input = file(HEADER + row(existing, f, f.time(), "invalid").replace(",NEW,", ",NO_SHOW,")
                + row("NEW-" + UUID.randomUUID(), f, f.time(), "new"));
        var preview = csv.preview(input, "UPSERT");
        assertThat(preview.valid()).isZero();
        assertThat(preview.invalid()).isEqualTo(2);
    }

    @Test
    void rescheduledRowFreesOldTimeButOccupiesNewTime() {
        Fixture f = fixture("AVAILABLE");
        String existing = "MOVED-" + UUID.randomUUID();
        seedRequest(f, existing, f.time());
        String newId = "NEW-" + UUID.randomUUID();
        var input = file(HEADER + row(existing, f, f.time().plusHours(2), "moved")
                + row(newId, f, f.time(), "old time")
                + row("CONFLICT-" + UUID.randomUUID(), f, f.time().plusHours(2), "conflict"));
        var preview = csv.preview(input, "UPSERT");
        assertThat(preview.valid()).isEqualTo(2);
        assertThat(preview.invalid()).isEqualTo(1);
        var result = csv.apply(input, "UPSERT");
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void exportedFormulaLikeFieldsRoundTripInUpsert() throws Exception {
        Fixture f = fixture("AVAILABLE");
        String externalId = "-ROUND-" + UUID.randomUUID();
        String comment = "'=начинается с апострофа";
        TestDriveRequest request = new TestDriveRequest();
        request.externalId = externalId;
        request.client = f.client();
        request.car = f.car();
        request.scheduledAt = f.time();
        request.durationMinutes = 30;
        request.status = RequestStatus.NEW;
        request.priority = RequestPriority.MEDIUM;
        request.comment = comment;
        request.tags.add("alpha|beta");
        request.tags.add("path\\name");
        requests.saveAndFlush(request);
        byte[] exported = csv.export(externalId, null, null, null, null, null, "false", null, null).getBody();
        var file = new MockMultipartFile("file", "requests.csv", "text/csv", exported);
        var preview = csv.preview(file, "UPSERT");
        assertThat(preview.valid()).isEqualTo(1);
        assertThat(preview.invalid()).isZero();
        var result = csv.apply(file, "UPSERT");
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.created()).isZero();
        assertThat(requests.findByExternalIdIgnoreCase(externalId).orElseThrow().comment).isEqualTo(comment);
        assertThat(service.get(request.publicId).tags()).containsExactlyInAnyOrder("alpha|beta", "path\\name");
    }

    private Fixture fixture(String status) {
        int n = SERIAL.incrementAndGet();
        Client client = new Client();
        client.fullName = "Проверочный клиент";
        client.phone = "+7900" + String.format("%07d", n);
        client.email = "csv-" + n + "@example.org";
        client.birthDate = LocalDate.of(1990, 1, 1);
        client.licenseNumber = "99" + String.format("%08d", n);
        clients.saveAndFlush(client);
        Car car = new Car();
        car.brand = "Тест";
        car.model = "Модель";
        car.manufactureYear = 2024;
        car.vin = "XW7BF4FK10S" + String.format("%06d", n);
        car.color = "Серый";
        car.price = new BigDecimal("100000.00");
        car.status = status;
        cars.saveAndFlush(car);
        return new Fixture(client, car, LocalDate.now().plusDays(2).atTime(10, 0));
    }

    private void seedRequest(Fixture f, String id, LocalDateTime time) {
        TestDriveRequest request = new TestDriveRequest();
        request.externalId = id;
        request.client = f.client();
        request.car = f.car();
        request.scheduledAt = time;
        request.durationMinutes = 30;
        request.status = RequestStatus.NEW;
        request.priority = RequestPriority.MEDIUM;
        requests.saveAndFlush(request);
    }

    private String row(String id, Fixture f, LocalDateTime time, String comment) {
        return id + "," + f.client().phone + "," + f.car().vin + "," + time
                + ",30,NEW," + comment + ",false,tag\n";
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "test.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private record Fixture(Client client, Car car, LocalDateTime time) { }
}
