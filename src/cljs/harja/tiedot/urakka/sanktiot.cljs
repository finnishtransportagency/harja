(ns harja.tiedot.urakka.sanktiot
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.pvm :as pvm]

            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.navigaatio :as nav])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [cljs.core.async.macros :refer [go]]))

(defonce nakymassa? (atom false))
(defonce valittu-sanktio (atom nil))
(defonce haetut-sanktiot (atom {}) #_(reaction<! [urakka (:id @nav/valittu-urakka)
                                      hoitokausi @urakka/valittu-hoitokausi
                                      nakymassa?]
                                      ;; Jos urakka ja hoitokausi on valittu ja käyttäjä on laadunseurannassa tällä välilehdellä,
                                      ;; haetaan urakalle sanktiot
                                      (when nakymassa?
                                        (laadunseuranta/hae-urakan-sanktiot urakka hoitokausi))))