(ns harja.tiedot.urakka.sanktiot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]

            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.istunto :as istunto])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(def nakymassa? (atom false))
(def +uusi-sanktio+
  (reaction {:suorasanktio true
             :havainto
                           {
                            :tekijanimi @istunto/kayttajan-nimi
                            :paatos     {:paatos "sanktio"}
                            }}))

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
                                      _ @nakymassa?]
                                     (when @nakymassa?
                                       (hae-urakan-sanktiot urakka hoitokausi tpi))))

(defn kasaa-tallennuksen-parametrit
  [s]
  {:sanktio  (dissoc s :havainto)
   :havainto (if-not (get-in s [:havainto :urakka])
               (:havainto (assoc-in s [:havainto :urakka] (:id @nav/valittu-urakka)))
               (:havainto s))})

(defn tallenna-sanktio
  [sanktio]
  (k/post! :tallenna-suorasanktio (kasaa-tallennuksen-parametrit sanktio)))

(defn sanktion-tallennus-onnistui
  [palautettu-id sanktio]
  (when (and
          palautettu-id
          (= (:toimenpideinstanssi sanktio) (:tpi_id @urakka/valittu-toimenpideinstanssi))
          (pvm/valissa?
            (get-in sanktio [:havainto :aika])
            (first @urakka/valittu-hoitokausi)
            (second @urakka/valittu-hoitokausi)))
    (assoc sanktio :id palautettu-id)
    (if (some #(= (:id %) palautettu-id) @haetut-sanktiot)
     (reset! haetut-sanktiot
             (into [] (map (fn [vanha] (if (= palautettu-id (:id vanha)) sanktio vanha)) @haetut-sanktiot)))

     (reset! haetut-sanktiot
             (into [] (concat @haetut-sanktiot [sanktio])))))

  (log (pr-str (map :id @haetut-sanktiot))))

