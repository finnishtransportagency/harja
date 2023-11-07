ALTER TABLE tarkastus ADD COLUMN alkuperainen_sijainti geometry;
COMMENT ON COLUMN tarkastus.alkuperainen_sijainti IS 'Sarake sisältää tieturvallisuus-tyyppisen tarkastuksen alkuperäisen reitin, sijainti-sarake sisältää tieturvallisuusverkko-geometrioiden perusteella leikatun reitin.'
