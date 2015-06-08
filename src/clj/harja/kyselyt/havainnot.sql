-- name: hae-kaikki-havainnot
-- Hakee listaukseen kaikki urakan havainnot annetulle aikavälille
SELECT h.id, h.aika, h.kohde,
       h.tekija, CONCAT(k.etunimi, ' ', k.sukunimi) as tekijanimi,
       h.kasittelyaika as paatos_kasittelyaika,
       h.paatos as paatos_paatos, h.kasittelytapa as paatos_kasittelytapa,
       h.toimenpideinstanssi
  FROM havainto h
       JOIN toimenpideinstanssi tpi ON h.toimenpideinstanssi = tpi.id
       JOIN kayttaja k ON h.luoja = k.id       
 WHERE tpi.urakka = :urakka
   AND (aika >= :alku AND aika <= :loppu)

-- name: hae-havainnon-tiedot
-- Hakee havainnon tiedot muokkausnäkymiin.
SELECT h.id, h.aika, h.kohde,
       h.tekija, CONCAT(k.etunimi, ' ', k.sukunimi) as tekijanimi,
       h.kasittelyaika as paatos_kasittelyaika,
       h.paatos as paatos_paatos, h.kasittelytapa as paatos_kasittelytapa,
       h.perustelu as paatos_perustelu, h.muu_kasittelytapa as paatos_muukasittelytapa,
       h.toimenpideinstanssi
  FROM havainto h
       JOIN toimenpideinstanssi tpi ON h.toimenpideinstanssi = tpi.id
       JOIN kayttaja k ON h.luoja = k.id       
 WHERE h.toimenpideinstanssi IN (SELECT id FROM toimenpideinstanssi tpi WHERE tpi.urakka = :urakka)
   AND h.id = :id

-- name: hae-havainnon-kommentit
-- Hakee annetun havainnon kaikki kommentit (joita ei ole poistettu) sekä
-- kommentin mahdollisen liitteen tiedot. Kommentteja on vaikea hakea
-- array aggregoimalla itse havainnon hakukyselyssä.
SELECT k.id, k.tekija, k.kommentti, k.luoja, k.luotu as aika,
       CONCAT(ka.etunimi, ' ', ka.sukunimi) as tekijanimi, 
       l.id as liite_id, l.tyyppi as liite_tyyppi, l.koko as liite_koko,
       l.nimi as liite_nimi, l.liite_oid as liite_oid
  FROM kommentti k
       JOIN kayttaja ka ON k.luoja = ka.id 
       LEFT JOIN liite l ON l.id=k.liite
 WHERE k.poistettu = false
   AND k.id IN (SELECT hk.kommentti
                  FROM havainto_kommentti hk
		 WHERE hk.havainto = :id)
ORDER BY k.luotu ASC
 

-- name: paivita-havainnon-perustiedot!
-- Päivittää aiemmin luodun havainnon perustiedot
UPDATE havainto
   SET toimenpideinstanssi = :toimenpideinstanssi,
       aika = :aika,
       tekija = :tekija::osapuoli,
       kohde = :kohde,
       muokkaaja = :muokkaaja,
       muokattu = current_timestamp
 WHERE id = :id

-- name: luo-havainto<!
-- Luo uuden havainnon annetuille perustiedoille. Luontivaiheessa ei
-- voi antaa päätöstietoja.
INSERT
  INTO havainto
       (toimenpideinstanssi, aika, tekija, kohde, luoja, luotu)
VALUES (:toimenpideinstanssi, :aika, :tekija::osapuoli, :kohde, :luoja, current_timestamp)

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
