-- name: hae-urakan-turvallisuuspoikkeamat
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.paattynyt,
  t.kasitelty,
  t.tyontekijanammatti,
  t.tyotehtava,
  t.kuvaus,
  t.vammat,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.sijainti,
  t.tr_numero,
  t.tr_alkuetaisyys,
  t.tr_loppuetaisyys,
  t.tr_alkuosa,
  t.tr_loppuosa,
  t.tyyppi
 FROM turvallisuuspoikkeama t
WHERE t.urakka = :urakka
  AND t.tapahtunut :: DATE BETWEEN :alku AND :loppu;

-- name: hae-turvallisuuspoikkeama
-- Hakee yksittäisen urakan turvallisuuspoikkeaman
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.paattynyt,
  t.kasitelty,
  t.tyontekijanammatti,
  t.tyotehtava,
  t.kuvaus,
  t.vammat,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.sijainti,
  t.tr_numero,
  t.tr_alkuetaisyys,
  t.tr_loppuetaisyys,
  t.tr_alkuosa,
  t.tr_loppuosa,
  t.tyyppi,

  k.id                   AS korjaavatoimenpide_id,
  k.kuvaus               AS korjaavatoimenpide_kuvaus,
  k.suoritettu           AS korjaavatoimenpide_suoritettu,
  k.vastaavahenkilo      AS korjaavatoimenpide_vastaavahenkilo,

  kom.id                 AS kommentti_id,
  kom.tekija             AS kommentti_tekija,
  kom.kommentti          AS kommentti_kommentti,
  kom.luotu              AS kommentti_aika,
  (SELECT CONCAT(etunimi, ' ', sukunimi)
     FROM kayttaja
    WHERE id = kom.luoja) AS kommentti_tekijanimi,

  koml.id                AS kommentti_liite_id,
  koml.tyyppi            AS kommentti_liite_tyyppi,
  koml.koko              AS kommentti_liite_koko,
  koml.nimi              AS kommentti_liite_nimi,
  koml.liite_oid         AS kommentti_liite_oid,

  l.id                   AS liite_id,
  l.tyyppi               AS liite_tyyppi,
  l.koko                 AS liite_koko,
  l.nimi                 AS liite_nimi,
  l.liite_oid            AS liite_oid,
  l.pikkukuva            AS liite_pikkukuva

 FROM turvallisuuspoikkeama t
      LEFT JOIN korjaavatoimenpide k
      ON t.id = k.turvallisuuspoikkeama

      LEFT JOIN turvallisuuspoikkeama_liite tl
      ON t.id = tl.turvallisuuspoikkeama
      LEFT JOIN liite l
      ON l.id = tl.liite

      LEFT JOIN turvallisuuspoikkeama_kommentti tpk
       ON t.id = tpk.turvallisuuspoikkeama
      LEFT JOIN kommentti kom
       ON tpk.kommentti = kom.id
        AND kom.poistettu IS NOT TRUE

  LEFT JOIN liite koml ON kom.liite = koml.id

WHERE t.id = :id AND t.urakka = :urakka


  


-- name: liita-kommentti<!
INSERT INTO turvallisuuspoikkeama_kommentti (turvallisuuspoikkeama, kommentti)
VALUES (:turvallisuuspoikkeama, :kommentti);

--name: paivita-korjaava-toimenpide<!
UPDATE korjaavatoimenpide
SET
  kuvaus          = :kuvaus,
  suoritettu      = :suoritettu,
  vastaavahenkilo = :vastaava
WHERE id = :id AND turvallisuuspoikkeama = :tp;

--name: luo-korjaava-toimenpide<!
INSERT INTO korjaavatoimenpide
(turvallisuuspoikkeama, kuvaus, suoritettu, vastaavahenkilo)
VALUES
  (:tp, :kuvaus, :suoritettu, :vastaava);

--name: paivita-turvallisuuspoikkeama<!
-- Kysely piti katkaista kahtia, koska Yesql <0.5 tukee vain positional parametreja, joita
-- Clojuressa voi olla max 20.
UPDATE turvallisuuspoikkeama
SET urakka               = :urakka,
  tapahtunut             = :tapahtunut,
  paattynyt              = :paattynyt,
  kasitelty              = :kasitelty,
  tyontekijanammatti     = :ammatti,
  tyotehtava             = :tehtava,
  kuvaus                 = :kuvaus,
  vammat                 = :vammat,
  sairauspoissaolopaivat = :poissa,
  sairaalavuorokaudet    = :sairaalassa,
  tyyppi                 = :tyyppi :: turvallisuuspoikkeamatyyppi [],
  muokkaaja              = :kayttaja,
  muokattu               = NOW()
WHERE id = :id;

--name: aseta-turvallisuuspoikkeaman-sijanti<!
-- Kysely piti katkaista kahtia, koska Yesql <0.5 tukee vain positional parametreja, joita
-- Clojuressa voi olla max 20. Ei aseta muokkaajaa ja muokattua, koska:
-- * kyselyä kutsutaan heti paivita1:sen jälkeen, joka jo asettaa ne
-- * kyselyä kutsutaan heti luonnin jälkeen
UPDATE turvallisuuspoikkeama
SET
  sijainti         = POINT(:x_koordinaatti, :y_koordinaatti),
  tr_numero        = :numero,
  tr_alkuetaisyys  = :aet,
  tr_loppuetaisyys = :let,
  tr_alkuosa       = :aos,
  tr_loppuosa      = :los
WHERE id = :id;

--name: luo-turvallisuuspoikkeama<!
-- Kysely piti katkaista kahtia, koska Yesql <0.5 tukee vain positional parametreja, joita
-- Clojuressa voi olla max 20.
INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyotehtava, kuvaus, vammat,
 sairauspoissaolopaivat, sairaalavuorokaudet, tyyppi, luoja, luotu)
VALUES
  (:urakka, :tapahtunut, :paattynyt, :kasitelty, :ammatti, :tehtava, :kuvaus, :vammat, :poissaolot, :sairaalassa,
   :tyyppi :: turvallisuuspoikkeamatyyppi [], :kayttaja, NOW());
