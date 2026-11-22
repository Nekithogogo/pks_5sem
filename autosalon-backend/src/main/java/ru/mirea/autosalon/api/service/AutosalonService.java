package ru.mirea.autosalon.api.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mirea.autosalon.api.domain.*;
import ru.mirea.autosalon.api.dto.AutosalonDtos.*;
import ru.mirea.autosalon.api.error.*;
import ru.mirea.autosalon.api.filter.RequestFilters;
import ru.mirea.autosalon.api.repository.*;

import java.time.*;
import java.util.*;
import java.util.function.Predicate;

@Service
public class AutosalonService {
    private static final List<RequestStatus> ACTIVE = List.of(RequestStatus.NEW, RequestStatus.CONFIRMED);
    private final RequestRepository requests;
    private final CarRepository cars;
    private final ClientRepository clients;
    private final RequestRules rules;
    private final AuditService audit;

    public AutosalonService(RequestRepository requests, CarRepository cars, ClientRepository clients,
                            RequestRules rules, AuditService audit) {
        this.requests = requests;
        this.cars = cars;
        this.clients = clients;
        this.rules = rules;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<RequestView> search(RequestFilters.Filter filter, Pageable page) {
        return requests.findAll(RequestFilters.from(filter), page).map(this::view);
    }

    @Transactional(readOnly = true)
    public RequestView get(UUID id) {
        return view(require(id));
    }

    @Transactional
    public RequestView create(RequestInput input) {
        TestDriveRequest request = new TestDriveRequest();
        apply(request, input, false);
        requests.saveAndFlush(request);
        audit.record("REQUEST_CREATED", "REQUEST", request.publicId, "Создана заявка", Map.of());
        return view(request);
    }

    @Transactional
    public RequestView update(UUID id, RequestInput input) {
        TestDriveRequest request = require(id);
        apply(request, input, true);
        requests.flush();
        audit.record("REQUEST_UPDATED", "REQUEST", id, "Изменена заявка", Map.of());
        return view(request);
    }

    /** Проверяет те же правила, что и запись, не меняя сущности и не создавая заявку. */
    @Transactional(readOnly = true)
    public void validateImport(RequestInput input, UUID existingId, List<ImportRow> previous) {
        TestDriveRequest request = existingId == null ? new TestDriveRequest() : require(existingId);
        validate(request, input, existingId != null, false, previous);
    }

    public record ImportRow(long number, UUID existingId, RequestInput input) { }

    @Transactional
    public RequestView archive(UUID id, boolean value) {
        TestDriveRequest request = require(id);
        request.archived = value;
        request.archivedAt = value ? Instant.now() : null;
        requests.flush();
        audit.record(value ? "REQUEST_ARCHIVED" : "REQUEST_RESTORED", "REQUEST", id,
                value ? "Заявка архивирована" : "Заявка восстановлена", Map.of());
        return view(request);
    }

    @Transactional
    public void permanent(UUID id) {
        TestDriveRequest request = require(id);
        requests.delete(request);
        requests.flush();
        audit.record("REQUEST_DELETED", "REQUEST", id, "Заявка удалена", Map.of());
    }

    public TestDriveRequest require(UUID id) {
        return requests.findByPublicId(id).orElseThrow(() -> new NotFoundException("Заявка не найдена"));
    }

    private void apply(TestDriveRequest request, RequestInput input, boolean update) {
        Resolved resolved = validate(request, input, update, true, List.of());
        request.client = resolved.client();
        request.car = resolved.car();
        request.externalId = input.externalId().strip();
        request.scheduledAt = input.scheduledAt();
        request.durationMinutes = input.durationMinutes();
        request.status = input.status();
        request.comment = input.comment();
        request.callbackRequested = input.callbackRequested();
        request.priority = rules.priority(request.status, request.callbackRequested);
        request.tags = new LinkedHashSet<>();
        if (input.tags() != null) {
            for (String tag : input.tags()) request.tags.add(tag.strip().toLowerCase(Locale.ROOT));
        }
    }

    private Resolved validate(TestDriveRequest request, RequestInput input, boolean update, boolean lock,
                              List<ImportRow> previous) {
        if (!update && input.status() != RequestStatus.NEW)
            throw new BadRequestException("Новая заявка создаётся со статусом NEW");
        if (update && (input.version() == null || input.version() != request.version))
            throw new ConflictException("Заявка изменена, обновите данные");
        if (update && request.archived) throw new BadRequestException("Архивную заявку нельзя изменять");
        if (update && !request.client.publicId.equals(input.clientId()))
            throw new BadRequestException("Клиента заявки менять нельзя");
        if (input.tags() != null && input.tags().size() > 20)
            throw new BadRequestException("Допустимо не более 20 тегов");
        requests.findByExternalIdIgnoreCase(input.externalId().strip())
                .filter(other -> !Objects.equals(other.id, request.id))
                .ifPresent(other -> { throw new ConflictException("Номер заявки уже используется"); });

        // Всегда захватываем блокировки в одном порядке: клиент, затем автомобиль.
        Client client = (lock ? clients.lockByPublicId(input.clientId()) : clients.findByPublicId(input.clientId()))
                .orElseThrow(() -> new BadRequestException("Клиент не найден"));
        Car car = (lock ? cars.lockByPublicId(input.carId()) : cars.findByPublicId(input.carId()))
                .orElseThrow(() -> new BadRequestException("Автомобиль не найден"));
        boolean changed = !update || !Objects.equals(request.scheduledAt, input.scheduledAt())
                || request.durationMinutes != input.durationMinutes() || !Objects.equals(request.car.id, car.id);
        if (changed) {
            if (!car.active || !"AVAILABLE".equals(car.status)) throw new BadRequestException("Автомобиль недоступен");
            validateSchedule(input.scheduledAt(), input.durationMinutes());
        }
        if (update && request.status != input.status()) {
            boolean valid = (request.status == RequestStatus.NEW &&
                    (input.status() == RequestStatus.CONFIRMED || input.status() == RequestStatus.CANCELLED)) ||
                    (request.status == RequestStatus.CONFIRMED && (input.status() == RequestStatus.COMPLETED ||
                            input.status() == RequestStatus.CANCELLED || input.status() == RequestStatus.NO_SHOW));
            if (!valid) throw new BadRequestException("Недопустимый переход статуса");
            if ((input.status() == RequestStatus.COMPLETED || input.status() == RequestStatus.NO_SHOW)
                    && LocalDateTime.now().isBefore(input.scheduledAt()))
                throw new BadRequestException("Тест-драйв ещё не начался");
            if (input.status() == RequestStatus.CONFIRMED && !input.scheduledAt().isAfter(LocalDateTime.now()))
                throw new BadRequestException("Нельзя подтвердить прошедший тест-драйв");
        }
        if (update && (request.status == RequestStatus.COMPLETED || request.status == RequestStatus.CANCELLED
                || request.status == RequestStatus.NO_SHOW) && changed)
            throw new BadRequestException("Закрытую заявку менять нельзя");
        if (!update || changed) {
            Set<UUID> replaced = new HashSet<>();
            for (ImportRow row : previous) if (row.existingId() != null) replaced.add(row.existingId());
            List<TestDriveRequest> activeForClient = requests.findByStatusInAndClientId(ACTIVE, client.id).stream()
                    .filter(item -> !Objects.equals(item.id, request.id) && !replaced.contains(item.publicId)).toList();
            List<ImportRow> pendingActive = previous.stream().filter(row -> ACTIVE.contains(row.input().status())).toList();
            long pendingForClient = pendingActive.stream()
                    .filter(row -> row.input().clientId().equals(input.clientId())).count();
            if (!update && activeForClient.size() + pendingForClient >= 3)
                throw new BadRequestException("У клиента уже три активные заявки");
            LocalDateTime end = input.scheduledAt().plusMinutes(input.durationMinutes());
            Predicate<TestDriveRequest> overlaps = item -> !Objects.equals(item.id, request.id)
                    && !replaced.contains(item.publicId)
                    && item.scheduledAt.isBefore(end)
                    && input.scheduledAt().isBefore(item.scheduledAt.plusMinutes(item.durationMinutes));
            if (activeForClient.stream().anyMatch(overlaps)
                    || requests.findByStatusInAndCarId(ACTIVE, car.id).stream().anyMatch(overlaps))
                throw new ConflictException("Клиент или автомобиль уже занят в это время");
            for (ImportRow row : pendingActive) {
                RequestInput other = row.input();
                boolean sameResource = other.clientId().equals(input.clientId()) || other.carId().equals(input.carId());
                boolean overlap = other.scheduledAt().isBefore(end)
                        && input.scheduledAt().isBefore(other.scheduledAt().plusMinutes(other.durationMinutes()));
                if (sameResource && overlap)
                    throw new ConflictException("В файле есть пересекающаяся заявка в строке " + row.number());
            }
        }
        return new Resolved(client, car);
    }

    private record Resolved(Client client, Car car) { }

    public static void validateSchedule(LocalDateTime time, int minutes) {
        LocalDateTime now = LocalDateTime.now(), end = time.plusMinutes(minutes);
        if (!time.isAfter(now) || time.isAfter(now.plusDays(60)))
            throw new BadRequestException("Дата должна быть в будущем и не более чем через 60 дней");
        if (time.toLocalTime().isBefore(LocalTime.of(9, 0)) || !end.toLocalDate().equals(time.toLocalDate())
                || end.toLocalTime().isAfter(LocalTime.of(21, 0)))
            throw new BadRequestException("Тест-драйв должен проходить с 09:00 до 21:00");
    }

    public RequestView view(TestDriveRequest request) {
        return new RequestView(request.publicId, request.externalId, request.client.publicId, request.client.fullName,
                request.car.publicId, request.car.brand + " " + request.car.model, request.car.vin,
                request.scheduledAt, request.durationMinutes, request.status, request.comment,
                request.callbackRequested, request.priority, request.archived, request.archivedAt,
                Set.copyOf(request.tags), request.createdAt, request.updatedAt, request.version);
    }

    @Transactional(readOnly = true)
    public Analytics analytics() {
        return new Analytics(requests.count(), requests.countByArchivedFalse(), requests.countByArchivedTrue(),
                requests.countByArchivedFalseAndCallbackRequestedTrue(), requests.averageDuration(),
                counts(requests.countByCar()), counts(requests.countByStatus()), counts(requests.countByPriority()));
    }

    private List<NamedCount> counts(List<Object[]> rows) {
        return rows.stream().map(row -> new NamedCount(String.valueOf(row[0]), ((Number) row[1]).longValue())).toList();
    }
}
