-- name: hae-kaikki-havainnot
-- Hakee listaukseen kaikki urakan havainnot annetulle aikavälille
SELECT h.id, h.aika, h.kohde,
       h.tekija, CONCAT(k.etunimi, ' ', k.sukunimi) as tekijanimi,
       h.kasittelyaika, h.kasittelytapa, h.paatos
  FROM havainto h
       JOIN toimenpideinstanssi tpi ON h.toimenpideinstanssi = tpi.id
       JOIN kayttaja k ON h.luoja = k.id       
 WHERE tpi.urakka = :urakka
   AND (aika >= :alku AND aika <= :loppu)


-- name: paivita-havainnon-perustiedot!
-- Päivittää aiemmin luodun havainnon perustiedot
UPDATE havainto
   SET toimenpideinstanssi = :toimenpideinstanssi,
       aika = :aika,
       tekija = :tekija::osapuoli,
       kohde = :kohde
 WHERE id = :id

-- name: luo-havainto<!
-- Luo uuden havainnon annetuille perustiedoille. Luontivaiheessa ei
-- voi antaa päätöstietoja.
INSERT
  INTO havainto
       (toimenpideinstanssi, aika, tekija, kohde)
VALUES (:toimenpideinstanssi, :aika, :tekija::osapuoli, :kohde)

-- name: kirjaa-havainnon-paatos!
-- Kirjaa havainnolle päätöksen.
UPDATE havainto
   SET kasittelyaika = :kasittelyaika,
       paatos = :paatos::havainnon_paatostyyppi,
       perustelu = :perustelu,
       kasittelytapa = :kasittelytapa::havainnon_kasittelytapa,
       muu_kasittelytapa = :muukasittelytapa,
       muokkaaja = :muokkaaja,
       muokattu = current_timestamp
 WHERE id = :id

-- name: liita-kommentti<!
-- Liittää havaintoon uuden kommentin
INSERT INTO havainto_kommentti (havainto,kommentti) VALUES (:havainto, :kommentti)
