-- Tuotemuutos 2019 maaliskuu
-- Päivitä toimenpidekoodi-tauluun uudet tuotenumerot. Tuotenumeroilla ei Harjassa tehdä varsinaisesti mitään.
-- Tuotenumeroa käytetään ainoastaan, kun muodostetaan Sampolle maksueriä ja päätellään tilinumeroa.
-- Toimenpideinstanssi-taulun tuotekoodi ja -polku päivittyvät Sampo-sanomien avulla.

-- Hoidon ja Käytön tuotteet siirtyvät uuden Hoidon alle.
UPDATE toimenpidekoodi
SET tuotenumero = 203 WHERE tuotenumero in (201,202);

UPDATE toimenpidekoodi
SET tuotenumero = 205 WHERE tuotenumero in (301,202,303);