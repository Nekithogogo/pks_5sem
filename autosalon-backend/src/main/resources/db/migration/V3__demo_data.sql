-- Демонстрационные данные добавляются только в полностью пустую базу.
-- Существующие данные КР 1 не изменяются и не дополняются.
DO $$
BEGIN
 IF NOT EXISTS (SELECT 1 FROM clients) AND NOT EXISTS (SELECT 1 FROM cars)
    AND NOT EXISTS (SELECT 1 FROM test_drive_requests) THEN
  INSERT INTO clients(full_name,phone,email,birth_date,license_number)
  VALUES ('Иванов Алексей Сергеевич','+79161234501','ivanov@example.org','1990-04-12','7712345601'),
         ('Смирнова Екатерина Андреевна','+79262345602','smirnova@example.org','1995-08-23','7723456702'),
         ('Кузнецов Дмитрий Игоревич','+79033456703','kuznetsov@example.org','1987-01-30','5034567803');
  INSERT INTO cars(brand,model,manufacture_year,vin,color,price,status)
  VALUES ('Toyota','Camry',2024,'XW7BF4FK10S123451','Белый',3850000,'AVAILABLE'),
         ('Kia','K5',2023,'XWEG3417BP0123453','Чёрный',3100000,'AVAILABLE'),
         ('Haval','Jolion',2024,'XZGFF06A1RA123456','Красный',2300000,'AVAILABLE');
  INSERT INTO test_drive_requests(client_id,car_id,scheduled_at,duration_minutes,status,comment,external_id,callback_requested)
  SELECT c.id,a.id,CURRENT_DATE + 1 + TIME '11:00',60,'NEW','Интересует комплектация','TD-DEMO-1',true FROM clients c,cars a WHERE c.phone='+79161234501' AND a.vin='XW7BF4FK10S123451';
  INSERT INTO test_drive_requests(client_id,car_id,scheduled_at,duration_minutes,status,comment,external_id)
  SELECT c.id,a.id,CURRENT_DATE + 2 + TIME '15:00',45,'CONFIRMED','Повторный тест-драйв','TD-DEMO-2' FROM clients c,cars a WHERE c.phone='+79262345602' AND a.vin='XWEG3417BP0123453';
  INSERT INTO test_drive_requests(client_id,car_id,scheduled_at,duration_minutes,status,comment,external_id)
  SELECT c.id,a.id,CURRENT_DATE - 2 + TIME '12:00',60,'COMPLETED','Тест-драйв состоялся','TD-DEMO-3' FROM clients c,cars a WHERE c.phone='+79033456703' AND a.vin='XZGFF06A1RA123456';
 END IF;
END $$;
