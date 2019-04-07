-- name: onko-olemassa?
-- single?: true
SELECT exists(SELECT *
              FROM tietyomaa
              WHERE osuus_id = :id AND jarjestelma = :jarjestelma);

-- name: luo-tietyomaa<!
INSERT INTO tietyomaa (jarjestelma,
                       osuus_id,
                       alkuaidan_sijainti,
                       loppuaidan_sijainti,
                       asetettu,
                       kaistat,
                       ajoradat,
                       yllapitokohde,
                       kirjaaja,
                       tr_tie,
                       tr_aosa,
                       tr_aet,
                       tr_losa,
                       tr_let,
                       nopeusrajoitus,
                       geometria)
VALUES (
  :jarjestelma,
  :osuusid,
  ST_MakePoint(:alkux, :alkuy) :: POINT,
  ST_MakePoint(:loppux, :loppuy) :: POINT,
  :asetettu,
  :kaistat :: INTEGER [],
  :ajoradat :: INTEGER [],
  :yllapitokohde,
  :kirjaaja,
  :tr_tie,
  :tr_aosa,
  :tr_aet,
  :tr_losa,
  :tr_let,
  :nopeusrajoitus,
  (SELECT geometria
   FROM tieviivat_pisteille(ST_Collect
                            (ST_MakePoint(:alkux, :alkuy),
                             ST_MakePoint(:loppux, :loppuy)),
                            CAST(10000 AS INTEGER))
     AS vali(alku GEOMETRY, loppu GEOMETRY, geometria GEOMETRY)));

-- name: paivita-tietyomaa!
UPDATE tietyomaa
SET
  alkuaidan_sijainti  = ST_MakePoint(:alkux, :alkuy) :: POINT,
  loppuaidan_sijainti = ST_MakePoint(:loppux, :loppuy) :: POINT,
  kaistat             = :kaistat :: INTEGER [],
  ajoradat            = :ajoradat :: INTEGER [],
  muokattu            = now(),
  asetettu            = :asetettu,
  poistettu           = NULL,
  tr_tie              = :tr_tie,
  tr_aosa             = :tr_aosa,
  tr_aet              = :tr_aet,
  tr_losa             = :tr_losa,
  tr_let              = :tr_let,
  nopeusrajoitus      = :nopeusrajoitus,
  geometria           = (SELECT geometria
                         FROM tieviivat_pisteille(ST_Collect
                                                  (ST_MakePoint(:alkux, :alkuy),
                                                   ST_MakePoint(:loppux, :loppuy)),
                                                  CAST(10000 AS INTEGER))
                           AS vali(alku GEOMETRY, loppu GEOMETRY, geometria GEOMETRY))
WHERE osuus_id = :osuusid AND jarjestelma = :jarjestelma;

-- name: merkitse-tietyomaa-poistetuksi!
UPDATE tietyomaa
SET poistettu = :poistettu, poistaja = :poistaja
WHERE osuus_id = :osuusid AND jarjestelma = :jarjestelma;
