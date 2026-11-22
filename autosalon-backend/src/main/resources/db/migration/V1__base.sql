CREATE TABLE clients
(
    id             BIGINT GENERATED ALWAYS AS IDENTITY,
    full_name      VARCHAR(150) NOT NULL,
    phone          VARCHAR(12)  NOT NULL,
    email          VARCHAR(100),
    birth_date     DATE         NOT NULL,
    license_number VARCHAR(10)  NOT NULL,
    registered_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_clients PRIMARY KEY (id),
    CONSTRAINT uq_clients_phone UNIQUE (phone),
    CONSTRAINT uq_clients_email UNIQUE (email),
    CONSTRAINT uq_clients_license_number UNIQUE (license_number),
    CONSTRAINT ck_clients_full_name CHECK (char_length(btrim(full_name)) >= 2),
    CONSTRAINT ck_clients_phone CHECK (phone ~ '^\+7[0-9]{10}$'),
    CONSTRAINT ck_clients_email CHECK (email ~ '^[^@\s]+@[^@\s]+\.[^@\s]+$'),
    CONSTRAINT ck_clients_license_number CHECK (license_number ~ '^[0-9]{10}$'),
    CONSTRAINT ck_clients_birth_date CHECK (birth_date >= DATE '1900-01-01')
);

COMMENT ON TABLE clients IS 'Клиенты автосалона';
COMMENT ON COLUMN clients.full_name IS 'ФИО клиента';
COMMENT ON COLUMN clients.phone IS 'Телефон в формате +7XXXXXXXXXX';
COMMENT ON COLUMN clients.email IS 'Электронная почта (необязательно)';
COMMENT ON COLUMN clients.birth_date IS 'Дата рождения (тест-драйв только с 18 лет)';
COMMENT ON COLUMN clients.license_number IS 'Номер водительского удостоверения, 10 цифр';
COMMENT ON COLUMN clients.registered_at IS 'Дата и время регистрации клиента';

-- ---------------------------------------------------------------------
--  Автомобили автосалона
-- ---------------------------------------------------------------------
CREATE TABLE cars
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    brand            VARCHAR(50)   NOT NULL,
    model            VARCHAR(50)   NOT NULL,
    manufacture_year INTEGER       NOT NULL,
    vin              VARCHAR(17)   NOT NULL,
    color            VARCHAR(30)   NOT NULL,
    price            NUMERIC(12, 2) NOT NULL,
    status           VARCHAR(20)   NOT NULL DEFAULT 'AVAILABLE',

    CONSTRAINT pk_cars PRIMARY KEY (id),
    CONSTRAINT uq_cars_vin UNIQUE (vin),
    CONSTRAINT ck_cars_manufacture_year CHECK (manufacture_year BETWEEN 1990 AND 2100),
    CONSTRAINT ck_cars_vin CHECK (vin ~ '^[A-HJ-NPR-Z0-9]{17}$'),
    CONSTRAINT ck_cars_price CHECK (price > 0),
    CONSTRAINT ck_cars_status CHECK (status IN ('AVAILABLE', 'IN_SERVICE', 'SOLD'))
);

COMMENT ON TABLE cars IS 'Автомобили автосалона';
COMMENT ON COLUMN cars.vin IS 'VIN: 17 символов, латинские буквы (кроме I, O, Q) и цифры';
COMMENT ON COLUMN cars.price IS 'Цена в рублях';
COMMENT ON COLUMN cars.status IS 'AVAILABLE - в наличии, IN_SERVICE - на обслуживании, SOLD - продан';

-- ---------------------------------------------------------------------
--  Заявки на тест-драйв (основная сущность)
--  client_id -> clients.id, car_id -> cars.id
-- ---------------------------------------------------------------------
CREATE TABLE test_drive_requests
(
    id               BIGINT GENERATED ALWAYS AS IDENTITY,
    client_id        BIGINT      NOT NULL,
    car_id           BIGINT      NOT NULL,
    scheduled_at     TIMESTAMP   NOT NULL,
    duration_minutes INTEGER     NOT NULL DEFAULT 30,
    status           VARCHAR(20) NOT NULL DEFAULT 'NEW',
    comment          VARCHAR(500),
    created_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_test_drive_requests PRIMARY KEY (id),
    CONSTRAINT fk_test_drive_requests_client FOREIGN KEY (client_id)
        REFERENCES clients (id) ON DELETE RESTRICT,
    CONSTRAINT fk_test_drive_requests_car FOREIGN KEY (car_id)
        REFERENCES cars (id) ON DELETE RESTRICT,
    CONSTRAINT ck_test_drive_requests_duration CHECK (duration_minutes BETWEEN 15 AND 120),
    CONSTRAINT ck_test_drive_requests_status
        CHECK (status IN ('NEW', 'CONFIRMED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
    CONSTRAINT ck_test_drive_requests_updated_at CHECK (updated_at >= created_at)
);

COMMENT ON TABLE test_drive_requests IS 'Заявки клиентов на тест-драйв автомобилей';
COMMENT ON COLUMN test_drive_requests.scheduled_at IS 'Дата и время начала тест-драйва';
COMMENT ON COLUMN test_drive_requests.duration_minutes IS 'Длительность тест-драйва, 15-120 минут';
COMMENT ON COLUMN test_drive_requests.status IS
    'NEW - новая, CONFIRMED - подтверждена, COMPLETED - завершена, CANCELLED - отменена, NO_SHOW - неявка';

-- Индексы для частых запросов: заявки клиента, заявки по автомобилю, поиск по дате
CREATE INDEX idx_test_drive_requests_client ON test_drive_requests (client_id);
CREATE INDEX idx_test_drive_requests_car ON test_drive_requests (car_id);
CREATE INDEX idx_test_drive_requests_scheduled_at ON test_drive_requests (scheduled_at);

-- Один автомобиль не может иметь две активные заявки с одинаковым временем начала
CREATE UNIQUE INDEX uq_test_drive_requests_active_slot
    ON test_drive_requests (car_id, scheduled_at)
    WHERE status IN ('NEW', 'CONFIRMED');


