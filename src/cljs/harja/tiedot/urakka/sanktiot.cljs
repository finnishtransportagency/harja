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

(def +testidata+
  [{:aika (pvm/nyt) :kohde "Raahe" :kuvaus "Maan tasalle vaan" :tekija "Me kaikki" :paatos "Antaa mennä vaan"}
   {:aika (pvm/nyt) :kohde "Masan koti" :kuvaus "Seinässä on reikä" :tekija "Masa itte" :paatos "Kai sen vois korjata"}])

(def nakymassa? (atom false))

(defonce valittu-sanktio (atom nil))

(defonce haetut-sanktiot (reaction<! [urakka (:id @nav/valittu-urakka)
                                      hoitokausi @urakka/valittu-hoitokausi
                                      tpi (:tpi_id @urakka/valittu-toimenpideinstanssi)
                                      nakymassa?]
                                      (when nakymassa?
                                        (laadunseuranta/hae-urakan-sanktiot urakka hoitokausi tpi))))