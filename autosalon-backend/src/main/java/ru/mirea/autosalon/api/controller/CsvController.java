package ru.mirea.autosalon.api.controller;

import jakarta.validation.Validator;
import org.apache.commons.csv.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import ru.mirea.autosalon.api.domain.*;
import ru.mirea.autosalon.api.dto.AutosalonDtos.*;
import ru.mirea.autosalon.api.error.*;
import ru.mirea.autosalon.api.filter.RequestFilters;
import ru.mirea.autosalon.api.repository.*;
import ru.mirea.autosalon.api.service.AutosalonService;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/requests")
public class CsvController {
    private static final String[] HEADERS = {"externalId", "clientPhone", "carVin", "scheduledAt",
            "durationMinutes", "status", "comment", "callbackRequested", "tags"};
    private final AutosalonService service;
    private final ClientRepository clients;
    private final CarRepository cars;
    private final RequestRepository requests;
    private final Validator validator;

    public CsvController(AutosalonService service, ClientRepository clients, CarRepository cars,
                         RequestRepository requests, Validator validator) {
        this.service = service;
        this.clients = clients;
        this.cars = cars;
        this.requests = requests;
        this.validator = validator;
    }

    public record Issue(long row, String message) { }
    public record Preview(int total, int valid, int invalid, List<Issue> errors) { }
    public record ImportResult(int total, int created, int updated, int skipped, List<Issue> errors) { }
    private record Row(long number, RequestInput input, UUID existingId) { }
    private record Parsed(int total, List<Row> rows, List<Issue> errors) { }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public Preview preview(@RequestPart MultipartFile file, @RequestParam(defaultValue = "CREATE_ONLY") String mode) {
        Parsed parsed = parse(file, mode);
        return new Preview(parsed.total(), parsed.rows().size(), parsed.errors().size(), parsed.errors());
    }

    @PostMapping(value = "/import/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public ImportResult apply(@RequestPart MultipartFile file, @RequestParam(defaultValue = "CREATE_ONLY") String mode) {
        Parsed parsed = parse(file, mode);
        int created = 0, updated = 0;
        List<Issue> errors = new ArrayList<>(parsed.errors());
        for (Row row : parsed.rows()) {
            try {
                if (row.existingId() == null) {
                    service.create(row.input());
                    created++;
                } else {
                    service.update(row.existingId(), row.input());
                    updated++;
                }
            } catch (RuntimeException ex) {
                String message = ex instanceof BadRequestException || ex instanceof ConflictException
                        || ex instanceof NotFoundException ? ex.getMessage() : "Не удалось импортировать строку";
                errors.add(new Issue(row.number(), message));
            }
        }
        return new ImportResult(parsed.total(), created, updated, errors.size(), errors);
    }

    private Parsed parse(MultipartFile file, String mode) {
        if (!mode.equals("CREATE_ONLY") && !mode.equals("UPSERT"))
            throw new BadRequestException("Режим: CREATE_ONLY или UPSERT");
        if (file == null || file.isEmpty()) throw new BadRequestException("Файл пуст");
        List<Row> rows = new ArrayList<>();
        List<Issue> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int count = 0;
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true).get();
        try (Reader reader = new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
             CSVParser parser = format.parse(reader)) {
            Set<String> headers = new HashSet<>(parser.getHeaderNames());
            if (headers.remove("\ufeffexternalId")) headers.add("externalId");
            for (String header : HEADERS)
                if (!headers.contains(header)) throw new BadRequestException("Нет колонки " + header);
            for (CSVRecord record : parser) {
                count++;
                if (count > 5000) throw new BadRequestException("Не более 5000 строк");
                long line = record.getRecordNumber() + 1;
                try {
                    String externalId = value(record, "externalId").strip();
                    if (externalId.isBlank() || !seen.add(externalId.toLowerCase(Locale.ROOT)))
                        throw new BadRequestException("Пустой или повторный externalId");
                    Client client = clients.findByPhone(value(record, "clientPhone").strip())
                            .orElseThrow(() -> new BadRequestException("Клиент по телефону не найден"));
                    Car car = cars.findByVinIgnoreCase(value(record, "carVin").strip())
                            .orElseThrow(() -> new BadRequestException("Автомобиль по VIN не найден"));
                    TestDriveRequest existing = requests.findByExternalIdIgnoreCase(externalId).orElse(null);
                    if (existing != null && mode.equals("CREATE_ONLY"))
                        throw new BadRequestException("Заявка уже существует");
                    RequestView old = existing == null ? null : service.get(existing.publicId);
                    String bool = value(record, "callbackRequested").strip();
                    if (!bool.equalsIgnoreCase("true") && !bool.equalsIgnoreCase("false"))
                        throw new BadRequestException("callbackRequested: true или false");
                    Set<String> tags = parseTags(value(record, "tags"));
                    RequestInput input = new RequestInput(externalId, client.publicId, car.publicId,
                            LocalDateTime.parse(value(record, "scheduledAt").strip()),
                            Integer.parseInt(value(record, "durationMinutes").strip()),
                            RequestStatus.valueOf(value(record, "status").strip().toUpperCase(Locale.ROOT)),
                            value(record, "comment"), Boolean.parseBoolean(bool), tags,
                            old == null ? null : old.version());
                    var violations = validator.validate(input);
                    if (!violations.isEmpty()) throw new BadRequestException(violations.iterator().next().getMessage());
                    UUID existingId = old == null ? null : old.id();
                    service.validateImport(input, existingId, rows.stream()
                            .map(row -> new AutosalonService.ImportRow(row.number(), row.existingId(), row.input()))
                            .toList());
                    rows.add(new Row(line, input, existingId));
                } catch (RuntimeException ex) {
                    String message = ex.getMessage();
                    errors.add(new Issue(line, message == null || message.isBlank() ? "Некорректная строка" : message));
                }
            }
        } catch (IOException | IllegalArgumentException ex) {
            throw new BadRequestException("Не удалось прочитать CSV");
        }
        return new Parsed(count, List.copyOf(rows), List.copyOf(errors));
    }

    private String value(CSVRecord record, String name) {
        String key = name.equals("externalId") && record.isMapped("\ufeffexternalId") ? "\ufeffexternalId" : name;
        return unescapeCell(record.get(key));
    }

    /** Вертикальная черта и обратная косая черта внутри тега экранируются обратной косой чертой. */
    private Set<String> parseTags(String text) {
        Set<String> tags = new LinkedHashSet<>();
        StringBuilder tag = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\\' && i + 1 < text.length()
                    && (text.charAt(i + 1) == '\\' || text.charAt(i + 1) == '|')) {
                tag.append(text.charAt(++i));
            } else if (ch == '|') {
                if (!tag.toString().isBlank()) tags.add(tag.toString().strip());
                tag.setLength(0);
            } else {
                tag.append(ch);
            }
        }
        if (!tag.toString().isBlank()) tags.add(tag.toString().strip());
        return tags;
    }

    private String formatTags(Set<String> tags) {
        return tags.stream().map(tag -> tag.replace("\\", "\\\\").replace("|", "\\|"))
                .collect(java.util.stream.Collectors.joining("|"));
    }

    /** Экранирование для электронных таблиц обратимо даже для значения, начинающегося с апострофа. */
    private String safe(String value) {
        if (value == null) return "";
        return !value.isEmpty() && (value.charAt(0) == '\'' || "=+-@\t\r".indexOf(value.charAt(0)) >= 0)
                ? "'" + value : value;
    }

    private String unescapeCell(String value) {
        if (value != null && value.length() >= 2 && value.charAt(0) == '\''
                && (value.charAt(1) == '\'' || "=+-@\t\r".indexOf(value.charAt(1)) >= 0))
            return value.substring(1);
        return value == null ? "" : value;
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(defaultValue = "") String q,
            @RequestParam(required = false) UUID carId, @RequestParam(required = false) RequestStatus status,
            @RequestParam(required = false) Boolean callback, @RequestParam(required = false) RequestPriority priority,
            @RequestParam(required = false) String tag, @RequestParam(defaultValue = "false") String archived,
            @RequestParam(required = false) Integer durationMin, @RequestParam(required = false) Integer durationMax)
            throws IOException {
        Boolean archive = switch (archived) {
            case "all" -> null;
            case "true" -> true;
            case "false" -> false;
            default -> throw new BadRequestException("Неверный архивный фильтр");
        };
        var filter = new RequestFilters.Filter(q, carId, status, callback, priority, tag, archive, durationMin, durationMax);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write("\ufeff".getBytes(StandardCharsets.UTF_8));
        try (Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             CSVPrinter csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(HEADERS).get())) {
            int page = 0;
            while (true) {
                var batch = service.search(filter, PageRequest.of(page++, 100, Sort.by("id")));
                for (RequestView request : batch) {
                    Client client = clients.findByPublicId(request.clientId()).orElseThrow();
                    csv.printRecord(safe(request.externalId()), safe(client.phone), safe(request.vin()),
                            request.scheduledAt(), request.durationMinutes(), request.status(), safe(request.comment()),
                            request.callbackRequested(), safe(formatTags(request.tags())));
                }
                if (batch.isLast()) break;
            }
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=requests.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8)).body(output.toByteArray());
    }
}
