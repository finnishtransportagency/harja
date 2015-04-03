(ns harja.tiedot.urakka.suunnittelu
  "Tämä nimiavaruus hallinnoi urakan suunnittelun tietoja"
  (:require [reagent.core :refer [atom] :as r]
            [cljs-time.core :as time]

            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]))

(def valittu-sopimusnumero "Sopimusnumero" (atom nil))

(defn valitse-sopimusnumero! [sn]
  (reset! valittu-sopimusnumero sn))

(def valittu-hoitokausi "Hoitokausi" (atom nil))

(defn valitse-hoitokausi! [hk]
  (reset! valittu-hoitokausi hk))

(defn hoitokaudet [ur]
  (let [ensimmainen-vuosi (.getYear (:alkupvm ur))
        viimeinen-vuosi (.getYear (:loppupvm ur))]
    (mapv (fn [vuosi]
            [(pvm/hoitokauden-alkupvm vuosi)
             (pvm/hoitokauden-loppupvm (inc vuosi))])
          (range ensimmainen-vuosi viimeinen-vuosi))))


;; rivit ryhmitelty tehtävittäin, rivissä oltava :alkupvm ja :loppupvm
(defn jaljella-olevien-hoitokausien-rivit
  "Palauttaa ne rivit joiden loppupvm on joku jaljella olevien kausien pvm:stä"
  [rivit-tehtavittain jaljella-olevat-kaudet]
  (mapv (fn [tehtavan-rivit]
                 (filter (fn [tehtavan-rivi]
                           (some #(pvm/sama-pvm? (second %) (:loppupvm tehtavan-rivi)) jaljella-olevat-kaudet))
                         tehtavan-rivit)
          ) rivit-tehtavittain))

(defn tulevat-hoitokaudet [ur hoitokausi]
  (drop-while #(not (pvm/sama-pvm? (second %) (second hoitokausi)))
              (hoitokaudet ur)))

(defn ryhmittele-hoitokausittain
  "Ottaa rivejä, jotka sisältävät :alkupvm ja :loppupvm, ja palauttaa ne ryhmiteltynä hoitokausiin.
  Palauttaa mäpin, jossa avaimena on hoitokauden [alku loppu] ja arvona on sen hoitokauden rivit.
  Jos sekvenssi hoitokausia on annettu, varmistetaan että mäpissä on kaikille niille avaimet. Tällä tavalla
  voidaan luoda tyhjät ryhmät myös hoitokausille, joilla ei ole yhtään riviä."
  ([rivit] (ryhmittele-hoitokausittain rivit nil))
  ([rivit hoitokaudet]
     (loop [ryhmitelty (group-by (juxt :alkupvm :loppupvm)
                                 rivit)
            [kausi & hoitokaudet] hoitokaudet]
       (if-not kausi
         ryhmitelty
         (if (contains? ryhmitelty kausi)
           (recur ryhmitelty hoitokaudet)
           (recur (assoc ryhmitelty kausi []) hoitokaudet))))))


(defn hoitokausien-sisalto-sama?
  "Kertoo onko eri hoitokausien sisältö sama päivämääriä lukuunottamatta.
  Suunniteltu käytettäväksi mm. yks.hint. ja kok.hint. töiden sekä materiaalien suunnittelussa."
  ;; uudelleennimetään muuttujia jos tästä saadaan yleiskäyttöinen esim. kok. hintaisille ja materiaaleille
  [tyorivit-tehtavittain]
  (let [tyorivit-aikajarjestyksessa (map #(sort-by :alkupvm %) tyorivit-tehtavittain)
        tyorivit-ilman-pvmia (into []
                                   (map #(map (fn [tyorivi]
                                                (dissoc tyorivi :alkupvm :loppupvm)) %) tyorivit-aikajarjestyksessa))]
      (every? #(apply = %) tyorivit-ilman-pvmia)))

;; FIXME: hoitokausien-sisalto-sama? ja hoitokaudet-samat? pitäisi olla 1 funktio
;; erona on nyt, että hoitokausien-sisalto-sama? ottaa tehtävittäin ryhmitellyt tiedot
;; ja hoitokaudet-samat? ottaa hoitokausittain ryhmitellyt

(defn hoitokaudet-samat? 
  "Testaa onko eri hoitokausien tiedot samat (päivämääriä ja id numeroita lukuunottamatta).
  Ottaa sisään hoitokausittain jaotellut rivit."
  [hoitokausien-rivit]
  (let [vertailumuoto (fn [rivit]
                        ;; vertailtaessa "samuutta" eri hoitokausien välillä poistetaan pvm:t ja id:t
                        (into #{}
                              (map #(dissoc % :alkupvm :loppupvm :id))
                              rivit))
        kaudet (map vertailumuoto hoitokausien-rivit)]
    (apply =  kaudet)))

(defn varoita-ylikirjoituksesta?
  "Ottaa sisään sekvenssin hoitokausien rivejä, ja tarkistaa että seuraavat kaudet ovat samoja
ensimmäisen kanssa tai ovat kaikki tyhjiä."
  [hoitokausien-rivit]
  (if (every? empty? (rest hoitokausien-rivit))
    false
    (not (hoitokaudet-samat? hoitokausien-rivit))))

  

(defn toiden-kustannusten-summa
  "Laskee yhteen annettujen työrivien kustannusten summan"
  [tyorivit]
  (apply + (map (fn [tyorivi]
                  (:yhteensa tyorivi))
                tyorivit)))

(defn rivit-tulevillekin-kausille [ur rivit hoitokausi]
  (into []
        (mapcat (fn [[alku loppu]]
                  (map (fn [rivi]
                         ;; tässä hoitokausien alkupvm ja loppupvm liitetään töihin
                         (assoc rivi :alkupvm alku :loppupvm loppu)) rivit)))
        (tulevat-hoitokaudet ur hoitokausi)))
