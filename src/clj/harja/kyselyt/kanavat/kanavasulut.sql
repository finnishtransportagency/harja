-- name: luo-kanavasulku<!
-- vie entryn kan_sulku-tauluun, kanavasulku on osa kanavakokonaisuutta
INSERT INTO kan_sulku
(
  kanavanro,
  aluenro,
  nimi,
  kanavatyyppi,
  aluetyyppi,
  kiinnitys,
  porttityyppi,
  kayttotapa,
  sulku_leveys,
  sulku_pituus,
  alus_leveys,
  alus_pituus,
  alus_syvyys,
  alus_korkeus,
  sulkumaara,
  putouskorkeus_1,
  putouskorkeus_2,
  alakanavan_alavertaustaso,
  alakanavan_ylavertaustaso,
  ylakanavan_alavertaustaso,
  ylakanavan_ylavertaustaso,
  kynnys_1,
  kynnys_2,
  vesisto,
  kanavakokonaisuus,
  kanava_pituus,
  kanava_leveys,
  lahtopaikka,
  kohdepaikka,
  omistaja,
  geometria,
  luotu,
  luoja,
  poistettu
)
VALUES
  (
    :kanavanro,
    :aluenro,
    :nimi,
    :kanavatyyppi,
    :aluetyyppi,
    :kiinnitys,
    :porttityyppi,
    :kayttotapa,
    :sulku_leveys,
    :sulku_pituus,
    :alus_leveys,
    :alus_pituus,
    :alus_syvyys,
    :alus_korkeus,
    :sulkumaara,
    :putouskorkeus_1,
    :putouskorkeus_2,
    :alakanavan_alavertaustaso,
    :alakanavan_ylavertaustaso,
    :ylakanavan_alavertaustaso,
    :ylakanavan_ylavertaustaso,
    :kynnys_1,
    :kynnys_2,
    :vesisto,
    :kanavakokonaisuus,
    :kanava_pituus,
    :kanava_leveys,
    :lahtopaikka,
    :kohdepaikka,
    :omistaja,
    ST_GeomFromText(:geometria) :: GEOMETRY,
    current_timestamp,
    :luoja,
    :poistettu
  )
ON CONFLICT (kanavanro)
  DO UPDATE
    SET
      kanavanro                 = :kanavanro,
      aluenro                   = :aluenro,
      nimi                      = :nimi,
      kanavatyyppi              = :kanavatyyppi,
      aluetyyppi                = :aluetyyppi,
      kiinnitys                 = :kiinnitys,
      porttityyppi              = :porttityyppi,
      kayttotapa                = :kayttotapa,
      sulku_leveys              = :sulku_leveys,
      sulku_pituus              = :sulku_pituus,
      alus_leveys               = :alus_leveys,
      alus_pituus               = :alus_pituus,
      alus_syvyys               = :alus_syvyys,
      alus_korkeus              = :alus_korkeus,
      sulkumaara                = :sulkumaara,
      putouskorkeus_1           = :putouskorkeus_1,
      putouskorkeus_2           = :putouskorkeus_2,
      alakanavan_alavertaustaso = :alakanavan_alavertaustaso,
      alakanavan_ylavertaustaso = :alakanavan_ylavertaustaso,
      ylakanavan_alavertaustaso = :ylakanavan_alavertaustaso,
      ylakanavan_ylavertaustaso = :ylakanavan_ylavertaustaso,
      kynnys_1                  = :kynnys_1,
      kynnys_2                  = :kynnys_2,
      vesisto                   = :vesisto,
      kanavakokonaisuus         = :kanavakokonaisuus,
      kanava_pituus             = :kanava_pituus,
      kanava_leveys             = :kanava_leveys,
      lahtopaikka               = :lahtopaikka,
      kohdepaikka               = :kohdepaikka,
      omistaja                  = :omistaja,
      geometria                 = :geometria :: GEOMETRY,
      muokattu                  = current_timestamp,
      muokkaaja                 = :muokkaaja,
      poistettu                 = :poistettu;


-- name: merkitse-kanavasulut-poistetuksi<!
UPDATE kan_sulku set poistettu = true, muokattu = current_timestamp, muokkaaja = :muokkaaja;

-- name: hae-kanavasulut
SELECT * FROM kan_sulku;

-- name: hae-kanavasulku-tunnuksella
SELECT * FROM kan_sulku WHERE kanavanro = :kanavanumero;

-- name: hae-kanavasulku-ja-kohde
SELECT
 sulku.nimi,
 sulku.kanavakokonaisuus,
 sulku.poistettu,
 osa.nimi,
 osa.oletuspalvelumuoto,
 osa.muokkaaja,
 osa.poistettu,
 osa.kohde-id,
 kohde.nimi,
 kohde.kohdekokonaisuus-id,
 kohde.poistettu,
 kanava.nimi,
 kanava.kanavanro
 from
kan_sulku as sulku,
kan_kohteenosa as osa,
kan_kohde as kohde,
kan_kanavakokonaisuus as kanava
WHERE
sulku.kanavanro = osa.lahdetunnus AND
osa.kohde-id = kohde.id AND
kohde.kohdekokonaisuus-id = kanava.id
and sulku.kanavanro = :kanavanumero
