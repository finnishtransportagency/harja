-- name: lukitse-kustannussuunnitelma!
-- Lukitsee kustannussuunnitelman lähetyksen ajaksi
UPDATE kustannussuunnitelma
SET lukko = :lukko, lukittu = current_timestamp
WHERE maksuera = :numero AND (lukko IS NULL OR
                            (EXTRACT(EPOCH FROM (current_timestamp - lukittu)) > 300));

-- name: hae-maksuera-lahetys-idlla
-- Hakee kustannussuunnitel lahetys-id:llä
SELECT maksuera
FROM kustannussuunnitelma
WHERE lahetysid = :lahetysid;

-- name: merkitse-kustannussuunnitelma-odottamaan-vastausta!
-- Merkitsee kustannussuunnitelma lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE kustannussuunnitelma
SET lahetysid = :lahetysid, lukko = NULL, tila = 'odottaa_vastausta'
WHERE maksuera = :numero;

-- name: merkitse-kustannussuunnitelma-lahetetyksi!
-- Merkitsee kustannussuunnitelman lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE kustannussuunnitelma
SET lahetetty = current_timestamp, tila = 'lahetetty'
WHERE maksuera = :numero;

-- name: merkitse-kustannussuunnitelmalle-lahetysvirhe!
-- Merkitsee kustannussuunnitelman lähetetyksi, kirjaa lähetyksen id:n ja avaa lukon
UPDATE kustannussuunnitelma
SET tila = 'virhe'
WHERE maksuera = :numero;