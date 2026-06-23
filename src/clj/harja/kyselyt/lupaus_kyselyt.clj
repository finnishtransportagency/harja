(ns harja.kyselyt.lupaus-kyselyt
  (:require [jeesql.core :refer [defqueries]]
            [harja.kyselyt.konversio :as konv]))

(declare hae-kuukausittaiset-pisteet hae-sitoutumistiedot hae-kustannusennuste-kuukausi-pisterajat
  generoi-lupaukset-urakalle)

;; Tämä on käytössä, vaikka kondo ei sitä huomaakaan. Sitä käytetään jeesql:ssä.
(defn muunna-lupaus [lupaus]
  (-> lupaus
    (update :kirjaus-kkt konv/pgarray->vector)
    (update :paatos-kk konv/pgarray->vector)))

(defqueries "harja/kyselyt/lupaus_kyselyt.sql")

(declare
  tallenna-lopputilanne! hae-urakan-lupaukset
  hae-lupaus hae-urakan-lupaustiedot hae-lupaus-vaihtoehdot hae-indeksikorotus-summalle
  hae-lupauksen-urakkatieto paivita-urakan-luvatut-pisteet<!
  lisaa-urakan-luvatut-pisteet<! paivita-lupaus-vastaus! hae-lupaus-vastaus lisaa-lupaus-vastaus<!
  hae-lupaus-vaihtoehto
  kommentit lisaa-lupaus-kommentti<! poista-kayttajan-oma-kommentti!
  paivita-kuukausittaiset-pisteet<! tallenna-kuukausittaiset-pisteet<! poista-kuukausittaiset-pisteet<!
  hae-kuukausivastaus hae-lupauksen-hoitovuoden-kirjauskuukaudet
  hae-puuttuvat-urakka-linkitykset hae-rivin-tunnistin-selitteet hae-kategorian-urakat
  hae-hoitovuoden-lopputilanne)
