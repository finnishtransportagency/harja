-- Muunna vanhat TR pistesijainnit geometrioiksi

ALTER TABLE tarkastus ALTER COLUMN sijainti TYPE geometry USING ST_GeomFromText(ST_AsText(sijainti::geometry));
ALTER TABLE turvallisuuspoikkeama ALTER COLUMN sijainti TYPE geometry USING ST_GeomFromText(ST_AsText(sijainti::geometry));
ALTER TABLE havainto ALTER COLUMN sijainti TYPE geometry USING ST_GeomFromText(ST_AsText(sijainti::geometry));
ALTER TABLE ilmoitus ALTER COLUMN sijainti TYPE geometry USING ST_GeomFromText(ST_AsText(sijainti::geometry));
