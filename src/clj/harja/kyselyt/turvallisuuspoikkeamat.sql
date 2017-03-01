-- name: hae-urakan-turvallisuuspoikkeamat
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.kasitelty,
  t.tyontekijanammatti,
  t.tyontekijanammatti_muu AS tyontekijanammattimuu,
  t.kuvaus,
  t.vammat,
  t.tila,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.sijainti,
  t.tr_numero,
  t.tr_alkuetaisyys,
  t.tr_loppuetaisyys,
  t.tr_alkuosa,
  t.tr_loppuosa,
  t.tyyppi,
  t.luotu,
  t.lahetetty,
  t.lahetys_onnistunut     AS lahetysonnistunut,
  t.ilmoitukset_lahetetty  AS ilmoituksetlahetetty,
  k.id                     AS korjaavatoimenpide_id,
  k.kuvaus                 AS korjaavatoimenpide_kuvaus,
  k.suoritettu             AS korjaavatoimenpide_suoritettu
FROM turvallisuuspoikkeama t
  LEFT JOIN korjaavatoimenpide k ON t.id = k.turvallisuuspoikkeama AND k.poistettu IS NOT TRUE
WHERE t.urakka = :urakka
      AND t.tapahtunut :: DATE BETWEEN :alku AND :loppu
ORDER BY t.tapahtunut DESC;

-- name: hae-turvallisuuspoikkeamat
-- Hakee kaikki turvallisuuspoikkeamat aikavälillä ilman aluerajausta
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.kasitelty,
  t.tyontekijanammatti,
  t.tyontekijanammatti_muu AS tyontekijanammattimuu,
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
  t.luotu,
  t.lahetetty,
  t.lahetys_onnistunut     AS lahetysonnistunut,
  t.ilmoitukset_lahetetty  AS ilmoituksetlahetetty,
  k.id                     AS korjaavatoimenpide_id,
  k.kuvaus                 AS korjaavatoimenpide_kuvaus,
  k.suoritettu             AS korjaavatoimenpide_suoritettu,
FROM turvallisuuspoikkeama t
  LEFT JOIN korjaavatoimenpide k ON t.id = k.turvallisuuspoikkeama AND k.poistettu IS NOT TRUE
WHERE t.tapahtunut :: DATE BETWEEN :alku AND :loppu
ORDER BY t.tapahtunut DESC;

-- name: hae-urakan-turvallisuuspoikkeama
-- Hakee yksittäisen urakan turvallisuuspoikkeaman
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.kasitelty,
  t.tyontekijanammatti,
  t.tyontekijanammatti_muu              AS tyontekijanammattimuu,
  t.kuvaus,
  t.vammat,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.vahingoittuneet_ruumiinosat         AS vahingoittuneetruumiinosat,
  t.sairauspoissaolo_jatkuu             AS sairauspoissaolojatkuu,
  t.sijainti,
  t.tr_numero,
  t.tr_alkuetaisyys,
  t.tr_loppuetaisyys,
  t.tr_alkuosa,
  t.tr_loppuosa,
  t.vakavuusaste,
  t.vahinkoluokittelu,
  t.tyyppi,
  t.vaylamuoto,
  t.toteuttaja,
  t.tilaaja,
  t.luotu,
  t.lahetetty,
  t.lahetys_onnistunut                  AS lahetysonnistunut,
  t.ilmoitukset_lahetetty               AS ilmoituksetlahetetty,
  t.turvallisuuskoordinaattori_etunimi  AS turvallisuuskoordinaattorietunimi,
  t.turvallisuuskoordinaattori_sukunimi AS turvallisuuskoordinaattorisukunimi,
  t.aiheutuneet_seuraukset              AS seuraukset,

  t.tapahtuman_otsikko                  AS otsikko,
  t.paikan_kuvaus                       AS "paikan-kuvaus",
  t.vaarallisten_aineiden_kuljetus      AS "vaarallisten-aineiden-kuljetus",
  t.vaarallisten_aineiden_vuoto         AS "vaarallisten-aineiden-vuoto",
  t.tila,

  k.id                                  AS korjaavatoimenpide_id,
  k.kuvaus                              AS korjaavatoimenpide_kuvaus,
  k.suoritettu                          AS korjaavatoimenpide_suoritettu,
  k.tila                                AS korjaavatoimenpide_tila,
  k.otsikko                             AS korjaavatoimenpide_otsikko,
  k.vastuuhenkilo                       AS korjaavatoimenpide_vastuuhenkilo,
  k.toteuttaja                          AS korjaavatoimenpide_toteuttaja,
  k_ka.etunimi                          AS "korjaavatoimenpide_laatija-etunimi",
  k_ka.sukunimi                         AS "korjaavatoimenpide_laatija-sukunimi",

  kom.id                                AS kommentti_id,
  kom.tekija                            AS kommentti_tekija,
  kom.kommentti                         AS kommentti_kommentti,
  kom.luotu                             AS kommentti_aika,
  (SELECT CONCAT(etunimi, ' ', sukunimi)
   FROM kayttaja
   WHERE id = kom.luoja)                AS kommentti_tekijanimi,

  koml.id                               AS kommentti_liite_id,
  koml.tyyppi                           AS kommentti_liite_tyyppi,
  koml.koko                             AS kommentti_liite_koko,
  koml.nimi                             AS kommentti_liite_nimi,
  koml.liite_oid                        AS kommentti_liite_oid,

  l.id                                  AS liite_id,
  l.tyyppi                              AS liite_tyyppi,
  l.koko                                AS liite_koko,
  l.nimi                                AS liite_nimi,
  l.liite_oid                           AS liite_oid,
  l.pikkukuva                           AS liite_pikkukuva,

  ka.etunimi                            AS "laatija-etunimi",
  ka.sukunimi                           AS "laatija-sukunimi"

FROM turvallisuuspoikkeama t
  LEFT JOIN korjaavatoimenpide k
    ON t.id = k.turvallisuuspoikkeama
       AND k.poistettu IS NOT TRUE

  LEFT JOIN turvallisuuspoikkeama_liite tl
    ON t.id = tl.turvallisuuspoikkeama
  LEFT JOIN liite l
    ON l.id = tl.liite

  LEFT JOIN kayttaja ka ON t.laatija = ka.id
  LEFT JOIN kayttaja k_ka ON k.laatija = k_ka.id

  LEFT JOIN turvallisuuspoikkeama_kommentti tpk
    ON t.id = tpk.turvallisuuspoikkeama
  LEFT JOIN kommentti kom
    ON tpk.kommentti = kom.id
       AND kom.poistettu IS NOT TRUE

  LEFT JOIN liite koml ON kom.liite = koml.id

WHERE t.id = :id AND t.urakka = :urakka;

-- name: hae-turvallisuuspoikkeama-lahetettavaksi-turiin
-- Hakee yksittäisen urakan turvallisuuspoikkeaman
SELECT
  t.id,
  t.turi_id                             AS "turi-id",
  t.urakka,
  t.tapahtunut,
  t.kasitelty,
  t.tapahtuman_otsikko                  AS "tapahtuman-otsikko",
  t.paikan_kuvaus                       AS "paikan-kuvaus",
  t.tyontekijanammatti,
  t.tyontekijanammatti_muu              AS tyontekijanammattimuu,
  t.kuvaus,
  t.vammat,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.vahingoittuneet_ruumiinosat         AS vahingoittuneetruumiinosat,
  t.sairauspoissaolo_jatkuu             AS sairauspoissaolojatkuu,
  t.tila,
  t.sijainti,
  t.tr_numero,
  t.tr_alkuetaisyys,
  t.tr_loppuetaisyys,
  t.tr_alkuosa,
  t.tr_loppuosa,
  t.vakavuusaste,
  t.vahinkoluokittelu,
  t.vaarallisten_aineiden_kuljetus      AS "vaarallisten-aineiden-kuljetus",
  t.vaarallisten_aineiden_vuoto         AS "vaarallisten-aineiden-vuoto",
  t.tyyppi,
  t.vaylamuoto,
  t.toteuttaja,
  t.tilaaja,
  t.luotu,
  t.lahetetty,
  t.lahetys_onnistunut                  AS lahetysonnistunut,
  t.ilmoitukset_lahetetty               AS ilmoituksetlahetetty,
  t.turvallisuuskoordinaattori_etunimi  AS turvallisuuskoordinaattorietunimi,
  t.turvallisuuskoordinaattori_sukunimi AS turvallisuuskoordinaattorisukunimi,
  t.aiheutuneet_seuraukset              AS seuraukset,

  u.urakkanro                           AS alueurakkanro,

  u.sampoid                             AS "urakka-sampoid",
  hlo.kayttajatunnus                    AS "tilaajanvastuuhenkilo-kayttajatunnus",
  hlo.etunimi                           AS "tilaajanvastuuhenkilo-etunimi",
  hlo.sukunimi                          AS "tilaajanvastuuhenkilo-sukunimi",
  hlo.sahkoposti                        AS "tilaajanvastuuhenkilo-sposti",
  u.tyyppi                              AS "urakka-tyyppi",
  o.lyhenne                             AS "urakka-ely",
  u.loppupvm                            AS "urakka-loppupvm",
  u.nimi                                AS "urakka-nimi",
  h.nimi                                AS "hanke-nimi",


  k.id                                  AS korjaavatoimenpide_id,
  k.kuvaus                              AS korjaavatoimenpide_kuvaus,
  khlo.kayttajanimi                     AS korjaavatoimenpide_vastuuhenkilokayttajatunnus,
  khlo.etunimi                          AS korjaavatoimenpide_vastuuhenkiloetunimi,
  khlo.sukunimi                         AS korjaavatoimenpide_vastuuhenkilosukunimi,
  khlo.sahkoposti                       AS korjaavatoimenpide_vastuuhenkilosposti,
  k.suoritettu                          AS korjaavatoimenpide_suoritettu,
  k.otsikko                             AS korjaavatoimenpide_otsikko,
  k.toteuttaja                          AS korjaavatoimenpide_toteuttaja,
  k.tila                                AS korjaavatoimenpide_tila,

  kom.id                                AS kommentti_id,
  kom.tekija                            AS kommentti_tekija,
  kom.kommentti                         AS kommentti_kommentti,
  kom.luotu                             AS kommentti_aika,
  (SELECT CONCAT(etunimi, ' ', sukunimi)
   FROM kayttaja
   WHERE id = kom.luoja)                AS kommentti_tekijanimi,

  koml.id                               AS kommentti_liite_id,
  koml.tyyppi                           AS kommentti_liite_tyyppi,
  koml.koko                             AS kommentti_liite_koko,
  koml.nimi                             AS kommentti_liite_nimi,
  koml.liite_oid                        AS kommentti_liite_oid,

  l.id                                  AS liite_id,
  l.tyyppi                              AS liite_tyyppi,
  l.koko                                AS liite_koko,
  l.nimi                                AS liite_nimi,
  l.liite_oid                           AS liite_oid,
  l.pikkukuva                           AS liite_pikkukuva,
  l.kuvaus                              AS liite_kuvaus

FROM turvallisuuspoikkeama t
  LEFT JOIN urakka u
    ON t.urakka = u.id

  LEFT JOIN urakanvastuuhenkilo hlo
    ON hlo.urakka = u.id  AND hlo.id = (SELECT id from urakanvastuuhenkilo WHERE rooli = 'ELY_Urakanvalvoja' and urakka = u.id ORDER BY ensisijainen DESC LIMIT 1 )

  LEFT JOIN hanke h
    ON u.hanke = h.id

  LEFT JOIN korjaavatoimenpide k
    ON t.id = k.turvallisuuspoikkeama
       AND k.poistettu IS NOT TRUE

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

  LEFT JOIN kayttaja khlo ON k.vastuuhenkilo = khlo.id

  LEFT JOIN organisaatio o ON u.hallintayksikko = o.id

WHERE t.id = :id;

-- name: onko-olemassa-ulkoisella-idlla
-- Tarkistaa löytyykö turvallisuuspoikkeamaa ulkoisella id:llä
SELECT exists(
    SELECT tp.id
    FROM turvallisuuspoikkeama tp
    WHERE tp.ulkoinen_id = :ulkoinen_id AND luoja = :luoja);

-- name: liita-kommentti<!
INSERT INTO turvallisuuspoikkeama_kommentti (turvallisuuspoikkeama, kommentti)
VALUES (:turvallisuuspoikkeama, :kommentti);

-- name: liita-liite<!
INSERT INTO turvallisuuspoikkeama_liite (turvallisuuspoikkeama, liite)
VALUES (:turvallisuuspoikkeama, :liite);

-- name: hae-turvallisuuspoikkeaman-liitteet
-- Hakee annetun turvallisuuspoikkeaman kaikki liitteet
SELECT
  l.id        AS id,
  l.tyyppi    AS tyyppi,
  l.koko      AS koko,
  l.nimi      AS nimi,
  l.liite_oid AS oid
FROM liite l
  JOIN turvallisuuspoikkeama_liite hl ON l.id = hl.liite
WHERE hl.turvallisuuspoikkeama = :turvallisuuspoikkeamaid
ORDER BY l.luotu ASC;

--name: paivita-korjaava-toimenpide<!
UPDATE korjaavatoimenpide
SET
  otsikko       = :otsikko,
  tila          = :tila :: korjaavatoimenpide_tila,
  vastuuhenkilo = :vastuuhenkilo,
  toteuttaja    = :toteuttaja,
  kuvaus        = :kuvaus,
  suoritettu    = :suoritettu,
  laatija       = :laatija,
  poistettu     = :poistettu
WHERE id = :id
      AND turvallisuuspoikkeama = :tp
      AND (SELECT urakka
           FROM turvallisuuspoikkeama
           WHERE id = :tp) = :urakka;

--name: luo-korjaava-toimenpide<!
INSERT INTO korjaavatoimenpide
(turvallisuuspoikkeama,
 otsikko,
 tila,
 vastuuhenkilo,
 toteuttaja,
 kuvaus,
 suoritettu,
 laatija,
 poistettu)
VALUES
  (:tp,
   :otsikko,
   :tila :: korjaavatoimenpide_tila,
   :vastuuhenkilo,
   :toteuttaja,
   :kuvaus,
   :suoritettu,
   :laatija,
   FALSE);

--name: paivita-turvallisuuspoikkeama!
UPDATE turvallisuuspoikkeama
SET
  urakka                              = :urakka,
  tapahtunut                          = :tapahtunut,
  kasitelty                           = :kasitelty,
  tyontekijanammatti                  = :ammatti :: tyontekijanammatti,
  tyontekijanammatti_muu              = :ammatti_muu,
  kuvaus                              = :kuvaus,
  vammat                              = :vammat :: turvallisuuspoikkeama_aiheutuneet_vammat [],
  sairauspoissaolopaivat              = :poissa,
  sairaalavuorokaudet                 = :sairaalassa,
  tyyppi                              = :tyyppi :: turvallisuuspoikkeama_luokittelu [],
  muokkaaja                           = :kayttaja,
  muokattu                            = NOW(),
  vahinkoluokittelu                   = :vahinkoluokittelu :: turvallisuuspoikkeama_vahinkoluokittelu [],
  vakavuusaste                        = :vakavuusaste :: turvallisuuspoikkeama_vakavuusaste,
  toteuttaja                          = :toteuttaja,
  tilaaja                             = :tilaaja,
  tapahtuman_otsikko                  = :tapahtuman_otsikko,
  tila                                = :tila :: turvallisuuspoikkeama_tila,
  vaarallisten_aineiden_kuljetus      = :vaarallisten_aineiden_kuljetus,
  vaarallisten_aineiden_vuoto         = :vaarallisten_aineiden_vuoto,
  paikan_kuvaus                       = :paikan_kuvaus,
  sijainti                            = :sijainti,
  tr_numero                           = :numero,
  tr_alkuetaisyys                     = :alkuetaisyys,
  tr_loppuetaisyys                    = :loppuetaisyys,
  tr_alkuosa                          = :alkuosa,
  tr_loppuosa                         = :loppuosa,
  vahingoittuneet_ruumiinosat         = :vahingoittuneet_ruumiinosat :: turvallisuuspoikkeama_vahingoittunut_ruumiinosa [],
  sairauspoissaolo_jatkuu             = :sairauspoissaolo_jatkuu,
  aiheutuneet_seuraukset              = :aiheutuneet_seuraukset,
  vaylamuoto                          = :vaylamuoto :: vaylamuoto,
  laatija                             = :laatija,
  turvallisuuskoordinaattori_etunimi  = :turvallisuuskoordinaattori_etunimi,
  turvallisuuskoordinaattori_sukunimi = :turvallisuuskoordinaattori_sukunimi,
  ilmoitukset_lahetetty               = :ilmoitukset_lahetetty
WHERE id = :id
      AND urakka = :urakka;

-- name: hae-turvallisuuspoikkeaman-id-ulkoisella-idlla
-- single?: true
SELECT id
FROM turvallisuuspoikkeama
WHERE ulkoinen_id = :ulkoinen_id AND
      luoja = :luoja;

--name: paivita-turvallisuuspoikkeama-ulkoisella-idlla!
UPDATE turvallisuuspoikkeama
SET urakka                            = :urakka,
  tapahtunut                          = :tapahtunut,
  kasitelty                           = :kasitelty,
  tyontekijanammatti                  = :ammatti :: tyontekijanammatti,
  tyontekijanammatti_muu              = :ammatti_muu,
  kuvaus                              = :kuvaus,
  vammat                              = :vammat :: turvallisuuspoikkeama_aiheutuneet_vammat [],
  sairauspoissaolopaivat              = :poissa,
  sairaalavuorokaudet                 = :sairaalassa,
  tyyppi                              = :tyyppi :: turvallisuuspoikkeama_luokittelu [],
  muokkaaja                           = :kayttaja,
  vahinkoluokittelu                   = :vahinkoluokittelu :: turvallisuuspoikkeama_vahinkoluokittelu [],
  vakavuusaste                        = :vakavuusaste :: turvallisuuspoikkeama_vakavuusaste,
  tapahtuman_otsikko                  = :tapahtuman_otsikko,
  tila                                = :tila :: turvallisuuspoikkeama_tila,
  vaarallisten_aineiden_kuljetus      = :vaarallisten_aineiden_kuljetus,
  vaarallisten_aineiden_vuoto         = :vaarallisten_aineiden_vuoto,
  toteuttaja                          = :toteuttaja,
  paikan_kuvaus                       = :paikan_kuvaus,
  tilaaja                             = :tilaaja,
  muokattu                            = NOW(),
  sijainti                            = POINT(:x_koordinaatti, :y_koordinaatti) :: GEOMETRY,
  tr_numero                           = :numero,
  tr_alkuosa                          = :aos,
  tr_alkuetaisyys                     = :aet,
  tr_loppuosa                         = :los,
  tr_loppuetaisyys                    = :let,
  vahingoittuneet_ruumiinosat         = :vahingoittuneet_ruumiinosat :: turvallisuuspoikkeama_vahingoittunut_ruumiinosa [],
  sairauspoissaolo_jatkuu             = :sairauspoissaolo_jatkuu,
  aiheutuneet_seuraukset              = :aiheutuneet_seuraukset,
  ilmoittaja_etunimi                  = :ilmoittaja_etunimi,
  ilmoittaja_sukunimi                 = :ilmoittaja_sukunimi,
  vaylamuoto                          = :vaylamuoto :: vaylamuoto,
  turvallisuuskoordinaattori_etunimi  = :turvallisuuskoordinaattori_etunimi,
  turvallisuuskoordinaattori_sukunimi = :turvallisuuskoordinaattori_sukunimi,
  laatija                             = :laatija
WHERE ulkoinen_id = :ulkoinen_id AND
      luoja = :luoja;

--name: aseta-ulkoinen-id<!
UPDATE turvallisuuspoikkeama
SET ulkoinen_id = :ulk
WHERE id = :id;

-- name: luo-turvallisuuspoikkeama<!
INSERT INTO turvallisuuspoikkeama
(urakka,
 tapahtunut,
 kasitelty,
 tyontekijanammatti,
 tyontekijanammatti_muu,
 kuvaus,
 vammat,
 sairauspoissaolopaivat,
 sairaalavuorokaudet,
 tyyppi,
 luoja,
 luotu,
 vahinkoluokittelu,
 vakavuusaste,
 toteuttaja,
 tilaaja,
 sijainti,
 tr_numero,
 tr_alkuosa,
 tr_alkuetaisyys,
 tr_loppuosa,
 tr_loppuetaisyys,
 vahingoittuneet_ruumiinosat,
 sairauspoissaolo_jatkuu,
 aiheutuneet_seuraukset,
 vaylamuoto,
 laatija,
 turvallisuuskoordinaattori_etunimi,
 turvallisuuskoordinaattori_sukunimi,
 ilmoittaja_etunimi,
 ilmoittaja_sukunimi,
 ulkoinen_id,
 ilmoitukset_lahetetty,
 tapahtuman_otsikko,
 paikan_kuvaus,
 vaarallisten_aineiden_kuljetus,
 vaarallisten_aineiden_vuoto,
 tila,
 lahde)
VALUES
  (:urakka,
    :tapahtunut,
    :kasitelty,
    :ammatti :: tyontekijanammatti,
    :ammatti_muu,
    :kuvaus,
    :vammat :: turvallisuuspoikkeama_aiheutuneet_vammat [],
    :poissa,
    :sairaalassa,
    :tyyppi :: turvallisuuspoikkeama_luokittelu [],
    :kayttaja,
    NOW(),
    :vahinkoluokittelu :: turvallisuuspoikkeama_vahinkoluokittelu [],
    :vakavuusaste :: turvallisuuspoikkeama_vakavuusaste,
    :toteuttaja,
    :tilaaja,
    :sijainti,
    :numero,
    :alkuosa,
    :alkuetaisyys,
    :loppuosa,
    :loppuetaisyys,
    :vahingoittuneet_ruumiinosat :: turvallisuuspoikkeama_vahingoittunut_ruumiinosa [],
    :sairauspoissaolo_jatkuu,
    :aiheutuneet_seuraukset,
    :vaylamuoto :: vaylamuoto,
    :laatija,
    :turvallisuuskoordinaattori_etunimi,
    :turvallisuuskoordinaattori_sukunimi,
    :ilmoittaja_etunimi,
    :ilmoittaja_sukunimi,
   :ulkoinen_id,
   :ilmoitukset_lahetetty,
   :tapahtuman_otsikko,
   :paikan_kuvaus,
   :vaarallisten_aineiden_kuljetus,
   :vaarallisten_aineiden_vuoto,
   :tila :: turvallisuuspoikkeama_tila,
   :lahde :: lahde);

--name: lokita-lahetys<!
UPDATE turvallisuuspoikkeama
SET lahetetty = now(), lahetys_onnistunut = :onnistunut
WHERE id = :id;

--name: hae-lahettamattomat-turvallisuuspoikkeamat
SELECT id
FROM turvallisuuspoikkeama
WHERE lahetys_onnistunut IS NOT TRUE;

--name: hae-vastuuhenkilon-tiedot
SELECT
  id,
  kayttajanimi,
  etunimi,
  sukunimi
FROM kayttaja
WHERE id = :id
      AND poistettu IS FALSE
      AND jarjestelma IS FALSE;

--name: hae-kayttajat-parametreilla
SELECT
  id,
  kayttajanimi,
  etunimi,
  sukunimi
FROM kayttaja
WHERE (:kayttajanimi IS NULL OR lower(kayttajanimi) LIKE (CONCAT(lower(:kayttajanimi), '%')))
      AND (:etunimi IS NULL OR lower(etunimi) LIKE (CONCAT(lower(:etunimi), '%')))
      AND (:sukunimi IS NULL OR lower(sukunimi) LIKE (CONCAT(lower(:sukunimi), '%')))
      AND poistettu IS FALSE
      AND jarjestelma IS FALSE;

-- name: hae-turvallisuuspoikkeaman-urakka
SELECT urakka
FROM turvallisuuspoikkeama
WHERE id = :id;

-- name: tallenna-turvallisuuspoikkeaman-turi-id!
UPDATE turvallisuuspoikkeama SET
  turi_id = :turi_id
WHERE id = :id;