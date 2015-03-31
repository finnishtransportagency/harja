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
            {:alkupvm  (pvm/hoitokauden-alkupvm vuosi)
             :loppupvm (pvm/hoitokauden-loppupvm (inc vuosi))})
          (range ensimmainen-vuosi viimeinen-vuosi))))

(defn tehtavan-sisalto-sama? [])

;; FIXME: vertailu tehtävä vain tuleville hoitokausille, ei kaikille urakan hoitokausille
(defn hoitokausien-sisalto-sama?
  "Kertoo onko eri hoitokausien sisältö sama päivämääriä lukuunottamatta.
  Suunniteltu käytettäväksi mm. yks.hint. ja kok.hint. töiden sekä materiaalien suunnittelussa.
  Tarkistaa onko töitä kaikilta hoitokausilta ja jos on, ovatko ne sisällöltään samat."
  ;; uudelleennimetään muuttujia jos tästä saadaan yleiskäyttöinen esim. kok. hintaisille ja materiaaleille
  [tyorivit-tehtavittain hoitokaudet]
  (let [tyorivit-aikajarjestyksessa (sort-by :alkupvm tyorivit-tehtavittain)
        alkupvmt-toista (into #{}
                              (first (map (fn [yhden-tehtavan-tyorivi]
                                            (map #(:alkupvm %) yhden-tehtavan-tyorivi)) tyorivit-aikajarjestyksessa)))
        alkupvmt-hoitokausista (into #{}
                                     (map (fn [hk]
                                            (:alkupvm hk)) hoitokaudet))
        tyorivit-ilman-pvmia (into []
                                   (map #(map (fn [tyorivi]
                                                (dissoc tyorivi :alkupvm :loppupvm)) %) tyorivit-aikajarjestyksessa))]
    (and
      (= (count alkupvmt-toista) (count alkupvmt-hoitokausista))
      (every? #(apply = %) tyorivit-ilman-pvmia))))

(defn toiden-kustannusten-summa
  "Laskee yhteen annettujen työrivien kustannusten summan"
  [tyorivit]
  (apply + (map (fn [tyorivi]
                  (:yhteensa tyorivi))
                tyorivit)))
