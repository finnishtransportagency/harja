(ns harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla
  (:require [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.openlayers :as openlayers]
            [harja.ui.kartta.varit.puhtaat :as varit]

            [harja.asiakas.kommunikaatio :as k])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-tarkastukset (atom false))

(defonce tarkastukset-kartalla
         (reaction
           @tarkastukset/urakan-tarkastukset
           (when @karttataso-tarkastukset
             (kartalla-esitettavaan-muotoon
               @tarkastukset/urakan-tarkastukset
               @tarkastukset/valittu-tarkastus
               nil
               (comp
                 (filter #(not (nil? (:sijainti %))))
                 (map #(assoc % :tyyppi-kartalla :tarkastus)))))))

(defn- luo-tarkastusreitit-kuvataso [taso-paalla? urakka [alku loppu] tienumero tyyppi]
  (when taso-paalla?
    (openlayers/luo-kuvataso
     :tarkastusreitit #{{:teksti "Tarkastusreitti" :vari varit/harmaa}}
     "tr" (k/url-parametri {:urakka-id (:id urakka)
                            :alkupvm alku
                            :loppupvm loppu
                            :tienumero tienumero
                            :tyyppi tyyppi}))))
(defonce tarkastusreitit
  (reaction
   (luo-tarkastusreitit-kuvataso
    @karttataso-tarkastukset
    @nav/valittu-urakka @tiedot-urakka/valittu-aikavali
    @tarkastukset/tienumero @tarkastukset/tarkastustyyppi)))
