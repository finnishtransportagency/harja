(ns harja.tiedot.urakka.sanktiot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]

            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))

(defonce valittu-sanktio (atom nil))

(defn hae-urakan-sanktiot
  "Hakee urakan sanktiot annetulle hoitokaudelle."
  [urakka-id [alku loppu] tpi]
  (k/post! :hae-urakan-sanktiot {:urakka-id urakka-id
                                 :alku      alku
                                 :loppu     loppu
                                 :tpi       tpi}))

(defonce haetut-sanktiot (reaction<! [urakka (:id @nav/valittu-urakka)
                                      hoitokausi @urakka/valittu-hoitokausi
                                      tpi (:tpi_id @urakka/valittu-toimenpideinstanssi)
                                      nakymassa?]
                                     (when nakymassa?
                                       (hae-urakan-sanktiot urakka hoitokausi tpi))))

(defn kasaa-tallennuksen-parametrit
  [s]
  ;; Käytetään palvelimella funktiota, jota käytetään myös havainnon tallettamisessa.
  ;; Koska funktiota käytetiin alunperin havainnon tallentamiseen, se odottaa tietynlaiset parametrit
  ;; ja tässä "joudutaan" kaivamaan erikseen ensimmäiselle tasolle havainnon ja urakan id:t.
  {:sanktio     s
   :havainto-id (get-in s [:havainto :id])
   :urakka-id   (get-in s [:havainto :urakka])})

(defn tallenna-sanktio
  [sanktio]
  (k/post! :tallenna-sanktio (kasaa-tallennuksen-parametrit sanktio)))

(defn sanktion-tallennus-onnistui
  [palautettu-id sanktio]
  (if (some #(= (:id %) palautettu-id) @haetut-sanktiot)
    (reset! haetut-sanktiot
            (into [] (map (fn [vanha] (if (= palautettu-id (:id vanha)) sanktio vanha)) @haetut-sanktiot)))

    (reset! haetut-sanktiot
            (into [] (concat @haetut-sanktiot sanktio)))))

