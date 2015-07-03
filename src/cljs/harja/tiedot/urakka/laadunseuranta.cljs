(ns harja.tiedot.urakka.laadunseuranta
  "Urakan tarkastukset: tiestötarkastukset, talvihoitotarkastukset sekä soratietarkastukset."
  (:require [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [cljs.core.async :refer [<!]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce laadunseurannassa? (atom false)) ; jos true, laadunseurantaosio nyt käytössä

(defonce valittu-valilehti (atom :tarkastukset))

;; Urakan tarkastusten karttataso
(defonce taso-tarkastukset (atom false))


  
(defonce sanktiotyypit
  (reaction<! [laadunseurannassa? @laadunseurannassa?]
              (when laadunseurannassa?
                (k/get! :hae-sanktiotyypit))))

(defn lajin-sanktiotyypit
  [laji]
  (filter #((:laji %) laji) @sanktiotyypit))
              


(defn hae-urakan-tarkastukset
  "Hakee annetun urakan tarkastukset urakka id:n ja ajan perusteella."
  [urakka-id alkupvm loppupvm]
  (k/post! :hae-urakan-tarkastukset {:urakka-id urakka-id
                                     :alkupvm alkupvm
                                     :loppupvm loppupvm}))

(defonce urakan-tarkastukset
  (reaction<! [urakka-id (:id @nav/valittu-urakka)
               [alku loppu] @tiedot-urakka/valittu-aikavali
               laadunseurannassa? @laadunseurannassa?
               valilehti @valittu-valilehti]
              
              (when (and laadunseurannassa? (= :tarkastukset valilehti)
                         urakka-id alku loppu)
                (go (mapv #(assoc %
                                  :type :tarkastus
                                  :alue {:type :circle
                                         :radius 100
                                         :coordinates (:sijainti %)
                                         :fill {:color "green"}
                                         :stroke {:color "black" :width 10}})
                          (<! (hae-urakan-tarkastukset urakka-id alku loppu)))))))

(defn hae-urakan-havainnot
  "Hakee annetun urakan havainnot urakka id:n ja aikavälin perusteella."
  [listaus urakka-id alkupvm loppupvm]
  (k/post! :hae-urakan-havainnot {:listaus listaus
                                  :urakka-id urakka-id
                                  :alku alkupvm
                                  :loppu loppupvm}))

(defn hae-havainnon-tiedot
  "Hakee urakan havainnon tiedot urakan id:n ja havainnon id:n perusteella.
  Palauttaa kaiken tiedon mitä havainnon muokkausnäkymään tarvitaan."
  [urakka-id havainto-id]
  (k/post! :hae-havainnon-tiedot {:urakka-id urakka-id
                                  :havainto-id havainto-id}))

(defn hae-tarkastus
  "Hakee tarkastuksen kaikki tiedot urakan id:n ja tarkastuksen id:n perusteella. Tähän liittyy havainnot sekä niiden reklamaatiot."
  [urakka-id tarkastus-id]
  (k/post! :hae-tarkastus {:urakka-id urakka-id
                           :tarkastus-id tarkastus-id}))

(defn tallenna-havainto [havainto]
  (k/post! :tallenna-havainto havainto))

  
(defn hae-urakan-sanktiot
  "Hakee urakan sanktiot annetulle hoitokaudelle."
  [urakka-id [alku loppu]]
  (k/post! :hae-urakan-sanktiot {:urakka-id urakka-id
                                 :alku alku
                                 :loppu loppu}))

(defn tallenna-tarkastus
  "Tallentaa tarkastuksen urakalle."
  [urakka-id tarkastus]
  (k/post! :tallenna-tarkastus {:urakka-id urakka-id
                                :tarkastus tarkastus}))

