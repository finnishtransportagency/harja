-- name: luo-silta<!
INSERT INTO silta
(tyyppi, siltanro, siltanimi, alue, tr_numero, tr_alkuosa, tr_alkuetaisyys, siltatunnus, siltaid, trex_oid, loppupvm, lakkautuspvm, muutospvm, status, luotu, luoja, urakat)
VALUES
(:tyyppi, :siltanro, :siltanimi, ST_GeomFromText(:geometria) :: GEOMETRY, :numero, :aosa, :aet, :tunnus, :siltaid, :trex-oid, :loppupvm, :lakkautuspvm, :muutospvm, :status, CURRENT_TIMESTAMP,
(select id from kayttaja where kayttajanimi = 'Integraatio'),
-- Jollain tavalla täytyy varmistaa, ettei kantaan mene array, jossa on NULL arvo.
-- array_agg palauttaa NULL, jos sille ei anneta mitään, niin siltä varalta pitää yhdistää tyhjä array.
(SELECT array_agg(a) ::INT[] || '{}' ::INT[] FROM unnest(ARRAY[:urakat] ::INT[]) a WHERE a IS NOT NULL));

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
    urakat          = (SELECT array_agg(a) ::INT[] || '{}' ::INT[] FROM unnest(ARRAY[:urakat] ::INT[]) a WHERE a IS NOT NULL),
    muokattu        = CURRENT_TIMESTAMP,
    muokkaaja       = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE (:trex-oid ::TEXT IS NOT NULL AND trex_oid = :trex-oid) OR
      (:siltaid ::INT IS NOT NULL AND siltaid = :siltaid);

-- name: hae-sillan-tiedot
SELECT
  u.id AS "urakka-id",
  u.loppupvm,
  s.id AS "silta-id",
  EXISTS(SELECT 1 FROM siltatarkastus st WHERE st.urakka=u.id AND st.silta=s.id) AS "siltatarkastuksia?"
FROM silta s
  LEFT OUTER JOIN urakka u ON ARRAY[u.id] :: INT[] <@ s.urakat
WHERE (:siltaid ::INT IS NOT NULL AND siltaid = :siltaid) OR
      (:siltatunnus ::TEXT IS NOT NULL AND s.siltatunnus = :siltatunnus) OR
      (:trex-oid ::TEXT IS NOT NULL AND trex_oid = :trex-oid);

-- name: poista-urakka-sillalta!
UPDATE silta
SET urakat = array_remove(urakat, :urakka-id)
WHERE id = :silta-id

-- name: merkkaa-silta-poistetuksi!
UPDATE silta
SET poistettu = TRUE
WHERE id=:silta-id;