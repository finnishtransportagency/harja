-- name: hae-urakan-turvallisuuspoikkeamat
SELECT t.id, t.urakka, t.tapahtunut, t.paattynyt, t.kasitelty, t.tyontekijanammatti, t.tyontekijanammatti_muu as tyontekijanammattimuu,
       t.tyotehtava, t.kuvaus, t.vammat, t.sairauspoissaolopaivat, t.sairaalavuorokaudet, t.sijainti,
       t.tr_numero, t.tr_alkuetaisyys, t.tr_loppuetaisyys, t.tr_alkuosa, t.tr_loppuosa, t.tyyppi,
       k.id              AS korjaavatoimenpide_id,
       k.kuvaus          AS korjaavatoimenpide_kuvaus,
       k.suoritettu      AS korjaavatoimenpide_suoritettu,
       k.vastaavahenkilo AS korjaavatoimenpide_vastaavahenkilo
  FROM turvallisuuspoikkeama t
       LEFT JOIN korjaavatoimenpide k ON t.id = k.turvallisuuspoikkeama AND k.poistettu IS NOT TRUE
 WHERE t.urakka = :urakka
       AND t.tapahtunut :: DATE BETWEEN :alku AND :loppu
 ORDER BY t.tapahtunut DESC;

-- name: hae-hallintayksikon-turvallisuuspoikkeamat
-- Hakee turvallisuuspoikkeamat, jotka ovat annetun hallintayksikön urakoissa raportoituja
SELECT t.id, t.urakka, t.tapahtunut, t.paattynyt, t.kasitelty, t.tyontekijanammatti, t.tyontekijanammatti_muu as tyontekijanammattimuu,
       t.tyotehtava, t.kuvaus, t.vammat, t.sairauspoissaolopaivat, t.sairaalavuorokaudet, t.sijainti,
       t.tr_numero, t.tr_alkuetaisyys, t.tr_loppuetaisyys, t.tr_alkuosa, t.tr_loppuosa, t.tyyppi,
       k.id AS korjaavatoimenpide_id,
       k.kuvaus AS korjaavatoimenpide_kuvaus,
       k.suoritettu AS korjaavatoimenpide_suoritettu,
       k.vastaavahenkilo AS korjaavatoimenpide_vastaavahenkilo
  FROM turvallisuuspoikkeama t
      LEFT JOIN korjaavatoimenpide k ON t.id = k.turvallisuuspoikkeama AND k.poistettu IS NOT TRUE
 WHERE t.urakka IN (SELECT id FROM urakka WHERE hallintayksikko = :hallintayksikko)
       AND t.tapahtunut :: DATE BETWEEN :alku AND :loppu
 ORDER BY t.tapahtunut DESC;

-- name: hae-turvallisuuspoikkeamat
-- Hakee kaikki turvallisuuspoikkeamat aikavälillä ilman aluerajausta
SELECT t.id, t.urakka, t.tapahtunut, t.paattynyt, t.kasitelty, t.tyontekijanammatti, t.tyontekijanammatti_muu as tyontekijanammattimuu,
       t.tyotehtava, t.kuvaus, t.vammat, t.sairauspoissaolopaivat, t.sairaalavuorokaudet, t.sijainti,
       t.tr_numero, t.tr_alkuetaisyys, t.tr_loppuetaisyys, t.tr_alkuosa, t.tr_loppuosa, t.tyyppi,
       k.id AS korjaavatoimenpide_id,
       k.kuvaus AS korjaavatoimenpide_kuvaus,
       k.suoritettu AS korjaavatoimenpide_suoritettu,
       k.vastaavahenkilo AS korjaavatoimenpide_vastaavahenkilo
  FROM turvallisuuspoikkeama t
      LEFT JOIN korjaavatoimenpide k ON t.id = k.turvallisuuspoikkeama AND k.poistettu IS NOT TRUE
 WHERE t.tapahtunut :: DATE BETWEEN :alku AND :loppu
 ORDER BY t.tapahtunut DESC;

-- name: hae-turvallisuuspoikkeama
-- Hakee yksittäisen urakan turvallisuuspoikkeaman
SELECT
  t.id,
  t.urakka,
  t.tapahtunut,
  t.paattynyt,
  t.kasitelty,
  t.tyontekijanammatti,
  t.tyontekijanammatti_muu as tyontekijanammattimuu,
  t.tyotehtava,
  t.kuvaus,
  t.vammat,
  t.sairauspoissaolopaivat,
  t.sairaalavuorokaudet,
  t.vahingoittuneet_ruumiinosat as vahingoittuneetruumiinosat,
  t.sairauspoissaolo_jatkuu as sairauspoissaolojatkuu,
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
  t.laatija_etunimi as laatijaetunimi,
  t.laatija_sukunimi as laatijasukunimi,
  t.turvallisuuskoordinaattori_etunimi as turvallisuuskoordinaattorietunimi,
  t.turvallisuuskoordinaattori_sukunimi as turvallisuuskoordinaattorisukunimi,
  t.aiheutuneet_seuraukset as seuraukset,

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

WHERE t.id = :id AND t.urakka = :urakka;

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

--name: paivita-korjaava-toimenpide<!
UPDATE korjaavatoimenpide
SET
  kuvaus          = :kuvaus,
  suoritettu      = :suoritettu,
  vastaavahenkilo = :vastaava,
  poistettu       = :poistettu
WHERE id = :id AND turvallisuuspoikkeama = :tp;

--name: luo-korjaava-toimenpide<!
INSERT INTO korjaavatoimenpide
(turvallisuuspoikkeama, kuvaus, suoritettu, vastaavahenkilo, poistettu)
VALUES
  (:tp, :kuvaus, :suoritettu, :vastaava, FALSE);

--name: paivita-turvallisuuspoikkeama!
UPDATE turvallisuuspoikkeama
SET
  urakka                 = :urakka,
  tapahtunut             = :tapahtunut,
  paattynyt              = :paattynyt,
  kasitelty              = :kasitelty,
  tyontekijanammatti     = :ammatti :: tyontekijanammatti,
  tyontekijanammatti_muu = :ammatti_muu,
  tyotehtava             = :tehtava,
  kuvaus                 = :kuvaus,
  vammat                 = :vammat :: turvallisuuspoikkeama_aiheutuneet_vammat [],
  sairauspoissaolopaivat = :poissa,
  sairaalavuorokaudet    = :sairaalassa,
  tyyppi                 = :tyyppi :: turvallisuuspoikkeama_luokittelu [],
  muokkaaja              = :kayttaja,
  muokattu               = NOW(),
  vahinkoluokittelu      = :vahinkoluokittelu :: turvallisuuspoikkeama_vahinkoluokittelu [],
  vakavuusaste           = :vakavuusaste :: turvallisuuspoikkeama_vakavuusaste,
  toteuttaja             = :toteuttaja,
  tilaaja                = :tilaaja,
  sijainti               = :sijainti,
  tr_numero              = :numero,
  tr_alkuetaisyys        = :alkuetaisyys,
  tr_loppuetaisyys       = :loppuetaisyys,
  tr_alkuosa             = :alkuosa,
  tr_loppuosa            = :loppuosa,
  vahingoittuneet_ruumiinosat = :vahingoittuneet_ruumiinosat :: turvallisuuspoikkeama_vahingoittunut_ruumiinosa [],
  sairauspoissaolo_jatkuu     = :sairauspoissaolo_jatkuu,
  aiheutuneet_seuraukset      = :aiheutuneet_seuraukset,
  vaylamuoto                  = :vaylamuoto :: vaylamuoto,
  laatija_etunimi             = :laatija_etunimi,
  laatija_sukunimi            = :laatija_sukunimi,
  turvallisuuskoordinaattori_etunimi  = :turvallisuuskoordinaattori_etunimi,
  turvallisuuskoordinaattori_sukunimi = :turvallisuuskoordinaattori_sukunimi
WHERE id = :id;

-- name: hae-turvallisuuspoikkeaman-id-ulkoisella-idlla
-- single?: true
SELECT id FROM turvallisuuspoikkeama
 WHERE ulkoinen_id = :ulkoinen_id AND
       luoja = :luoja

--name: paivita-turvallisuuspoikkeama-ulkoisella-idlla!
UPDATE turvallisuuspoikkeama
SET urakka               = :urakka,
  tapahtunut             = :tapahtunut,
  paattynyt              = :paattynyt,
  kasitelty              = :kasitelty,
  tyontekijanammatti     = :ammatti :: tyontekijanammatti,
  tyontekijanammatti_muu = :ammatti_muu,
  tyotehtava             = :tehtava,
  kuvaus                 = :kuvaus,
  vammat                 = :vammat :: turvallisuuspoikkeama_aiheutuneet_vammat [],
  sairauspoissaolopaivat = :poissa,
  sairaalavuorokaudet    = :sairaalassa,
  tyyppi                 = :tyyppi :: turvallisuuspoikkeama_luokittelu [],
  muokkaaja              = :kayttaja,
  vahinkoluokittelu      = :vahinkoluokittelu :: turvallisuuspoikkeama_vahinkoluokittelu [],
  vakavuusaste           = :vakavuusaste :: turvallisuuspoikkeama_vakavuusaste,
  toteuttaja             = :toteuttaja,
  tilaaja                = :tilaaja,
  muokattu               = NOW(),
  sijainti         = POINT(:x_koordinaatti, :y_koordinaatti) :: GEOMETRY,
  tr_numero        = :numero,
  tr_alkuetaisyys  = :aet,
  tr_loppuetaisyys = :let,
  tr_alkuosa       = :aos,
  tr_loppuosa      = :los,
  vahingoittuneet_ruumiinosat = :vahingoittuneet_ruumiinosat :: turvallisuuspoikkeama_vahingoittunut_ruumiinosa [],
  sairauspoissaolo_jatkuu     = :sairauspoissaolo_jatkuu,
  aiheutuneet_seuraukset      = :aiheutuneet_seuraukset,
  ilmoittaja_etunimi = :ilmoittaja_etunimi,
  ilmoittaja_sukunimi = :ilmoittaja_sukunimi,
  vaylamuoto = :vaylamuoto :: vaylamuoto,
  turvallisuuskoordinaattori_etunimi = :turvallisuuskoordinaattori_etunimi,
  turvallisuuskoordinaattori_sukunimi = :turvallisuuskoordinaattori_sukunimi,
  laatija_etunimi = :laatija_etunimi,
  laatija_sukunimi = :laatija_sukunimi
WHERE ulkoinen_id = :ulkoinen_id AND
      luoja = :luoja;

--name: aseta-ulkoinen-id<!
UPDATE turvallisuuspoikkeama
SET ulkoinen_id = :ulk
WHERE id = :id;

-- name: luo-turvallisuuspoikkeama<!
INSERT INTO turvallisuuspoikkeama
(urakka, tapahtunut, paattynyt, kasitelty, tyontekijanammatti, tyontekijanammatti_muu,
 tyotehtava, kuvaus, vammat, sairauspoissaolopaivat, sairaalavuorokaudet, tyyppi, luoja, luotu,
 vahinkoluokittelu, vakavuusaste, toteuttaja, tilaaja, sijainti,
 tr_numero, tr_alkuosa, tr_alkuetaisyys, tr_loppuosa, tr_loppuetaisyys,
 vahingoittuneet_ruumiinosat, sairauspoissaolo_jatkuu, aiheutuneet_seuraukset, vaylamuoto,
 laatija_etunimi, laatija_sukunimi,
 turvallisuuskoordinaattori_etunimi, turvallisuuskoordinaattori_sukunimi,
 ilmoittaja_etunimi, ilmoittaja_sukunimi, ulkoinen_id)
VALUES
  (:urakka, :tapahtunut, :paattynyt, :kasitelty, :ammatti :: tyontekijanammatti, :ammatti_muu,
  :tehtava, :kuvaus, :vammat :: turvallisuuspoikkeama_aiheutuneet_vammat[], :poissa,
  :sairaalassa, :tyyppi :: turvallisuuspoikkeama_luokittelu [], :kayttaja, NOW(),
  :vahinkoluokittelu :: turvallisuuspoikkeama_vahinkoluokittelu[],
  :vakavuusaste :: turvallisuuspoikkeama_vakavuusaste, :toteuttaja, :tilaaja,
  :sijainti, :numero, :alkuosa, :alkuetaisyys, :loppuosa, :loppuetaisyys,
  :vahingoittuneet_ruumiinosat ::turvallisuuspoikkeama_vahingoittunut_ruumiinosa[],
  :sairauspoissaolo_jatkuu, :aiheutuneet_seuraukset, :vaylamuoto ::vaylamuoto,
  :laatija_etunimi, :laatija_sukunimi,
  :turvallisuuskoordinaattori_etunimi, :turvallisuuskoordinaattori_sukunimi,
  :ilmoittaja_etunimi, :ilmoittaja_sukunimi,
  :ulkoinen_id);
