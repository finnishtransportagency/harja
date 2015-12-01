(ns harja.tiedot.urakka.laadunseuranta.tarkastukset
  (:require [harja.asiakas.kommunikaatio :as k]
            [harja.geo :as geo]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.urakka.laadunseuranta.laadunseuranta :as laadunseuranta]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defn hae-tarkastus
  "Hakee tarkastuksen kaikki tiedot urakan id:n ja tarkastuksen id:n perusteella. Tähän liittyy havainnot sekä niiden reklamaatiot."
  [urakka-id tarkastus-id]
  (k/post! :hae-tarkastus {:urakka-id    urakka-id
                           :tarkastus-id tarkastus-id}))

(defn tallenna-tarkastus
  "Tallentaa tarkastuksen urakalle."
  [urakka-id tarkastus]
  (k/post! :tallenna-tarkastus {:urakka-id urakka-id
                                :tarkastus tarkastus}))

(defonce urakan-tarkastukset
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      [alku loppu] @tiedot-urakka/valittu-aikavali
                      laadunseurannassa? @laadunseuranta/laadunseurannassa?
                      valilehti @valittu-valilehti
                      tienumero @tienumero
                      tyyppi @tarkastustyyppi]
                     {:odota 500}
                     (when (and laadunseurannassa? (= :tarkastukset valilehti)
                                urakka-id alku loppu)
                       (go (into [] (<! (hae-urakan-tarkastukset urakka-id alku loppu tienumero tyyppi)))))))

;; Urakan tarkastusten karttataso
(defonce karttataso-tarkastukset (atom false))

(defonce valittu-tarkastus (atom nil))

(def tarkastus-xf
  (map
    #(assoc %
      :tyyppi-kartalla :tarkastus
      :type :tarkastus
      :alue {:type        :icon
             :coordinates (geo/ikonin-sijainti (:sijainti %))
             :direction   0
             :img         (if (= (:id %) (:id @valittu-tarkastus))
                            "images/tyokone_highlight.png"
                            "images/tyokone.png")})))

(defn paivita-tarkastus-listaan!
  "Päivittää annetun tarkastuksen urakan-tarkastukset listaan, jos se on valitun aikavälin sisällä."
  [{:keys [aika id] :as tarkastus}]
  (let [[alkupvm loppupvm] @tiedot-urakka/valittu-aikavali
        tarkastus (first (sequence tarkastus-xf [tarkastus]))
        sijainti-listassa (first (keep-indexed (fn [i {tarkastus-id :id}]
                                                 (when (= id tarkastus-id) i))
                                               @urakan-tarkastukset))]
    (if (pvm/valissa? aika alkupvm loppupvm)
      ;; Tarkastus on valitulla välillä: päivitetään
      (if sijainti-listassa
        (swap! urakan-tarkastukset assoc sijainti-listassa tarkastus)
        (swap! urakan-tarkastukset conj tarkastus))

      ;; Ei pvm välillä, poistetaan listasta jos se aiemmin oli välillä
      (when sijainti-listassa
        (swap! urakan-tarkastukset (fn [tarkastukset]
                                     (into []
                                           (remove #(= (:id %) id))
                                           tarkastukset)))))))

(defn hae-urakan-tarkastukset
  "Hakee annetun urakan tarkastukset urakka id:n ja ajan perusteella."
  [urakka-id alkupvm loppupvm tienumero tyyppi]
  (k/post! :hae-urakan-tarkastukset {:urakka-id urakka-id
                                     :alkupvm   alkupvm
                                     :loppupvm  loppupvm
                                     :tienumero tienumero
                                     :tyyppi    tyyppi}))

(defonce tarkastukset-kartalla
         (reaction
           @urakan-tarkastukset
           (when @karttataso-tarkastukset
             (kartalla-esitettavaan-muotoon
               (map #(assoc % :tyyppi-kartalla :tarkastus) @urakan-tarkastukset)
               @valittu-tarkastus))))

