-- Indeksoi toteuman ulkoinen_id ja luoja
-- Tämä huomattavasti nopeuttaa ulkoisen id:n olemassaolon tarkistusta
CREATE INDEX toteuma_ulkoinen_id_luoja ON toteuma (ulkoinen_id, luoja);
