(ns harja.kyselyt.kayttajat
  (:require [jeesql.core :refer [defqueries]]
            [harja.palvelin.asetukset :refer [ominaisuus-kaytossa?]]))

(defqueries "harja/kyselyt/kayttajat.sql"
  {:positional? true})

(defn onko-kayttaja-urakan-organisaatiossa? [db urakka-id kayttaja-id]
  (:exists (first (onko-kayttaja-urakan-organisaatiossa db urakka-id kayttaja-id))))

(defn onko-kayttajalla-lisaoikeus-urakkaan? [db urakka-id kayttaja-id]
  (:exists (first
             ;; Lokaali ympäristöön on sallittu admin käyttäjän toimia myös API rajapinnan käyttäjänä. Voit säätää tätä pois-kytketyt-omimaisuudet setissä asetuksissa.
             (if (ominaisuus-kaytossa? :toteumatyokalu)
                    (onko-normikayttajalla-lisaoikeus-urakkaan db {:urakka urakka-id
                                                              :kayttaja kayttaja-id})
                    (onko-kayttajalla-lisaoikeus-urakkaan db {:urakka urakka-id
                                                              :kayttaja kayttaja-id})))))


(defn onko-kayttaja-organisaatiossa? [db ytunnus kayttaja-id]
  (:exists (first (onko-kayttaja-organisaatiossa db ytunnus kayttaja-id))))

(defn onko-kayttaja-nimella-urakan-organisaatiossa? [db urakka-id ilmoitus]
  (:exists (first (onko-kayttaja-nimella-urakan-organisaatiossa
                    db
                    {:urakka urakka-id
                     :etunimi (get-in ilmoitus [:ilmoittaja :etunimi])
                     :sukunimi (get-in ilmoitus [:ilmoittaja :sukunimi])
                     :puhelin (or (get-in ilmoitus [:ilmoittaja :matkapuhelin]) (get-in ilmoitus [:ilmoittaja :tyopuhelin]) nil)}))))
