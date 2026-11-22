-- Старое настольное приложение не знает о version, поэтому увеличиваем её в БД.
CREATE FUNCTION sync_car_version() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.version = OLD.version THEN NEW.version := OLD.version + 1; END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER car_version BEFORE UPDATE ON cars FOR EACH ROW EXECUTE FUNCTION sync_car_version();
