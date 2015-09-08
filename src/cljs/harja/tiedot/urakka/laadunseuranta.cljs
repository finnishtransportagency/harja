(ns harja.tiedot.urakka.laadunseuranta
  "Urakan tarkastukset: tiestötarkastukset, talvihoitotarkastukset sekä soratietarkastukset."
  (:require [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom] :as r]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto]
            [harja.pvm :as pvm]
            [harja.loki :refer [log logt tarkkaile!]]
            [cljs.core.async :refer [<!]]
            [harja.loki :refer [log]]
            [harja.domain.roolit :as roolit])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce laadunseurannassa? (atom false))                   ; jos true, laadunseurantaosio nyt käytössä

(defonce voi-kirjata? (reaction
                        (let [kayttaja @istunto/kayttaja
                              urakka @nav/valittu-urakka]
                          (and kayttaja
                               urakka
                               (roolit/rooli-urakassa? kayttaja roolit/havaintojen-kirjaus (:id urakka))))))


(defonce valittu-valilehti (atom :tarkastukset))

;; Urakan tarkastusten karttataso
(defonce karttataso-tarkastukset (atom false))

(defonce valittu-tarkastus (atom nil))

(defonce sanktiotyypit
         (reaction<! [laadunseurannassa? @laadunseurannassa?]
                     (when laadunseurannassa?
                       (k/get! :hae-sanktiotyypit))))

(defn lajin-sanktiotyypit
  [laji]
  (filter #((:laji %) laji) @sanktiotyypit))



(defn hae-urakan-tarkastukset
  "Hakee annetun urakan tarkastukset urakka id:n ja ajan perusteella."
  [urakka-id alkupvm loppupvm tienumero tyyppi]
  (k/post! :hae-urakan-tarkastukset {:urakka-id urakka-id
                                     :alkupvm   alkupvm
                                     :loppupvm  loppupvm
                                     :tienumero tienumero
                                     :tyyppi    tyyppi}))

(def tarkastus-xf
  (map #(assoc %
         :type :tarkastus
         :alue {:type        :icon
                :coordinates (let [sijainti (:sijainti %)]
                               (case (:type sijainti)
                                 ;; Pistemäisen sijainnin koordinaatti on piste itse
                                 :point (:coordinates sijainti)

                                 ;; Viivamaisen sijainnin koordinaati on 1. viivan 1. piste
                                 ;; (FIXME: viivan keskipiste parempi?)
                                 :multiline (-> sijainti :lines first :points first)))
                :direction   0
                :img         (if (= (:id %) (:id @valittu-tarkastus))
                               "images/tyokone_highlight.png"
                               "images/tyokone.png")})))

(defonce tienumero (atom nil))                              ;; tienumero, tai kaikki
(defonce tarkastustyyppi (atom nil))                        ;; nil = kaikki, :tiesto, :talvihoito, :soratie

(defonce urakan-tarkastukset
         (reaction<! [urakka-id (:id @nav/valittu-urakka)
                      [alku loppu] @tiedot-urakka/valittu-aikavali
                      laadunseurannassa? @laadunseurannassa?
                      valilehti @valittu-valilehti
                      tienumero @tienumero
                      tyyppi @tarkastustyyppi]
                     {:odota 500}
                     (when (and laadunseurannassa? (= :tarkastukset valilehti)
                                urakka-id alku loppu)
                       (go (into [] (<! (hae-urakan-tarkastukset urakka-id alku loppu tienumero tyyppi)))))))

(defonce tarkastukset-kartalla
         (reaction
           @valittu-tarkastus
           (when @karttataso-tarkastukset
             (into [] tarkastus-xf @urakan-tarkastukset))))

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


(defn hae-urakan-havainnot
  "Hakee annetun urakan havainnot urakka id:n ja aikavälin perusteella."
  [listaus urakka-id alkupvm loppupvm]
  (k/post! :hae-urakan-havainnot {:listaus   listaus
                                  :urakka-id urakka-id
                                  :alku      alkupvm
                                  :loppu     loppupvm}))

(defn hae-havainnon-tiedot
  "Hakee urakan havainnon tiedot urakan id:n ja havainnon id:n perusteella.
  Palauttaa kaiken tiedon mitä havainnon muokkausnäkymään tarvitaan."
  [urakka-id havainto-id]
  (k/post! :hae-havainnon-tiedot {:urakka-id   urakka-id
                                  :havainto-id havainto-id}))

(defn hae-tarkastus
  "Hakee tarkastuksen kaikki tiedot urakan id:n ja tarkastuksen id:n perusteella. Tähän liittyy havainnot sekä niiden reklamaatiot."
  [urakka-id tarkastus-id]
  (k/post! :hae-tarkastus {:urakka-id    urakka-id
                           :tarkastus-id tarkastus-id}))

(defn tallenna-havainto [havainto]
  (k/post! :tallenna-havainto havainto))


(defn tallenna-tarkastus
  "Tallentaa tarkastuksen urakalle."
  [urakka-id tarkastus]
  (k/post! :tallenna-tarkastus {:urakka-id urakka-id
                                :tarkastus tarkastus}))

