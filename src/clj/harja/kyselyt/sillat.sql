-- name: luo-silta<!
INSERT INTO silta
(tyyppi, siltanro, siltanimi, alue, tr_numero, tr_alkuosa, tr_alkuetaisyys, siltatunnus, trex_oid, muutospvm, luotu, luoja, urakat, vastuu_urakka)
VALUES
(:tyyppi, :siltanro, :siltanimi, ST_GeomFromText(:geometria) :: GEOMETRY, :numero, :aosa, :aet, :tunnus, :trex-oid, :muutospvm::DATE, CURRENT_TIMESTAMP,
(select id from kayttaja where kayttajanimi = 'Integraatio'),
-- Jollain tavalla täytyy varmistaa, ettei kantaan mene array, jossa on NULL arvo.
-- array_agg palauttaa NULL, jos sille ei anneta mitään, niin siltä varalta pitää yhdistää tyhjä array.
(SELECT array_agg(a) ::INT[] || '{}' ::INT[] FROM unnest(ARRAY[:urakat] ::INT[]) a WHERE a IS NOT NULL),
 :vastuu-urakka);

-- name: paivita-silta!
UPDATE silta
SET tyyppi          = :tyyppi,
    siltanimi       = :siltanimi,
    alue            = ST_GeomFromText(:geometria) :: GEOMETRY,
    tr_numero       = :numero,
    tr_alkuosa      = :aosa,
    tr_alkuetaisyys = :aet,
    muutospvm       = :muutospvm::DATE,
    loppupvm        = :loppupvm::DATE,
    poistettu       = :poistettu,
    kunnan_vastuulla = :kunnan-vastuulla,
    trex_oid        = :trex-oid,
    urakat          = (SELECT array_agg(a) ::INT[] || '{}' ::INT[] FROM unnest(ARRAY[:urakat] ::INT[]) a WHERE a IS NOT NULL),
    vastuu_urakka   = :vastuu-urakka,
    muokattu        = CURRENT_TIMESTAMP,
    muokkaaja       = (select id from kayttaja where kayttajanimi = 'Integraatio')
WHERE (:trex-oid ::TEXT IS NOT NULL AND trex_oid = :trex-oid);

-- name: hae-sillan-tiedot
SELECT
  u.id AS "urakka-id",
  u.loppupvm,
  s.id AS "silta-taulun-id",
  s.siltaid AS "aineiston_silta_id",
  s.siltatunnus AS "siltatunnus",
  s.trex_oid AS "trex-oid",
  s.poistettu,
  s.vastuu_urakka,
  s.urakkatieto_kasin_muokattu,
  EXISTS(SELECT 1 FROM siltatarkastus st WHERE st.urakka=u.id AND st.silta=s.id AND st.poistettu = false) AS "siltatarkastuksia?"
FROM silta s
  LEFT OUTER JOIN urakka u ON ARRAY[u.id] :: INT[] <@ s.urakat
WHERE (:trex-oid ::TEXT IS NOT NULL AND trex_oid = :trex-oid) OR
      (trex_oid IS NULL AND (:siltatunnus ::TEXT IS NOT NULL AND siltatunnus = :siltatunnus) AND (:siltanimi ::TEXT IS NOT NULL AND siltanimi = :siltanimi));

-- name: poista-urakka-sillalta!
UPDATE silta
SET urakat = array_remove(urakat, :urakka-id)
WHERE id = :silta-taulun-id;

-- name: merkkaa-kunnan-silta-poistetuksi!
UPDATE silta
SET poistettu        = TRUE,
    kunnan_vastuulla = TRUE
WHERE id = :silta-id;
