-- name: luo-silta!
INSERT INTO silta
(tyyppi, siltanro, siltanimi, alue, tr_numero, tr_alkuosa, tr_alkuetaisyys, siltatunnus, siltaid, trex_oid, loppupvm, lakkautuspvm, muutospvm, status, luotu, luoja)
VALUES
(:tyyppi, :siltanro, :siltanimi, ST_GeomFromText(:geometria) :: GEOMETRY, :numero, :aosa, :aet, :tunnus, :siltaid, :trex_oid, :loppupvm, :lakkautuspvm, :muutospvm, :status, CURRENT_TIMESTAMP,
(select id from kayttaja where kayttajanimi = 'Integraatio'));

-- name: paivita-silta!
UPDATE silta
SET tyyppi          = :tyyppi,
    siltanro        = :siltanro,
    siltanimi       = :siltanimi,
    alue            = ST_GeomFromText(:geometria) :: GEOMETRY,
    tr_numero       = :numero,
    tr_alkuosa      = :aosa,
    tr_alkuetaisyys = :aet,
    siltatunnus     = :tunnus,
    siltaid         = :siltaid,
    loppupvm        = :loppupvm,
    lakkautuspvm    = :lakkautuspvm,
    muutospvm       = :muutospvm,
    status          = :status,
    muokattu  = CURRENT_TIMESTAMP,
    muokkaaja = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE (trex_oid NOT IN (null, '') AND trex_oid = :trex_oid) OR
(siltaid IS NOT NULL AND siltaid = :siltaid);

-- name: hae-silta-idlla
-- Hakee sillan siltaidllä
SELECT
  id,
  tyyppi,
  siltatunnus,
  siltanimi
FROM silta
WHERE siltaid = :siltaid OR siltatunnus = :siltatunnus;

-- name: hae-silta-trex-idlla
-- Hakee sillan trex-idlla. Trex_oid on Taitorakennerekisterin käyttämä yksilöivä tunniste.
SELECT
  id
FROM silta
WHERE trex_oid = :trex_oid;

-- name: paivita-urakoiden-sillat
SELECT paivita_sillat_alueurakoittain();

