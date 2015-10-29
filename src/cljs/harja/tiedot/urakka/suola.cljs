(ns harja.tiedot.urakka.suola
  "Tämän nimiavaruuden avulla voidaan hakea urakan suola- ja lämpötilatietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.atom :refer-macros [reaction<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defonce suolatoteumissa? (atom false))

(defonce lampotilojen-hallinnassa? (atom false))

(defn hae-lampotilat-ilmatieteenlaitokselta [talvikauden-alkuvuosi]
  (k/post! :hae-lampotilat-ilmatieteenlaitokselta {:vuosi talvikauden-alkuvuosi}))

(defn hae-teiden-hoitourakoiden-lampotilat [hoitokausi]
  (log "tiedot hae-teiden-hoitourakoiden-lampotilat hoitokaudelle:" (pr-str hoitokausi))
  (k/post! :hae-teiden-hoitourakoiden-lampotilat {:hoitokausi hoitokausi}))

(def hoitokaudet
  (vec
    (let [nyt (pvm/nyt)
          tama-vuosi (pvm/vuosi nyt)
          ;; sydäntalvi on joulu-helmikuu, tarjotaan sydäntalven keskilämpöltilan hakua aikaisintaan
          ;; maaliskuussa. Ei tarpeen huomioida karkauspäivää koska manuaalinen integraatio.
          sydantalvi-ohi? (pvm/jalkeen? nyt (pvm/->pvm (str "28.2." tama-vuosi)))
          vanhin-haettava-vuosi 2005]
      (for [vuosi (range vanhin-haettava-vuosi
                         (if sydantalvi-ohi?
                           tama-vuosi
                           (dec tama-vuosi)))]
        [(pvm/hoitokauden-alkupvm vuosi) (pvm/hoitokauden-loppupvm (inc vuosi))]))))

(def valittu-hoitokausi (atom (last hoitokaudet)))

(defn valitse-hoitokausi! [tk]
  (reset! valittu-hoitokausi tk))

(defonce hoitourakoiden-lampotilat
  (reaction<! [lampotilojen-hallinnassa? @lampotilojen-hallinnassa?
               valittu-hoitokausi @valittu-hoitokausi]
              (when (and lampotilojen-hallinnassa?
                         valittu-hoitokausi)
                (hae-teiden-hoitourakoiden-lampotilat valittu-hoitokausi))))

(defn hae-urakan-suolasakot-ja-lampotilat [urakka-id]
  (k/post! :hae-urakan-suolasakot-ja-lampotilat urakka-id))

(defn aseta-suolasakon-kaytto [urakka-id kaytossa?]
  (k/post! :aseta-suolasakon-kaytto {:urakka-id urakka-id
                                     :kaytossa? kaytossa?}))

(defn tallenna-lampotilat [talvikausi rivit]
  (log "EI VIELÄ IMPLEMENTOITU! front, suola tiedot, tallenna-lampotilat: " talvikausi "rivit " (pr-str rivit)))
