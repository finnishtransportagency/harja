(ns harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla
  (:require [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.openlayers :as openlayers]
            [harja.ui.kartta.varit.puhtaat :as varit]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-tarkastukset (atom false))

(defn- luo-tarkastusreitit-kuvataso [taso-paalla? urakka [alku loppu] tienumero tyyppi]
  (when taso-paalla?
    (openlayers/luo-kuvataso
     :tarkastusreitit esitettavat-asiat/tarkastus-selitteet
     "tr" (k/url-parametri {:urakka-id (:id urakka)
                            :alkupvm alku
                            :loppupvm loppu
                            :tienumero tienumero
                            :tyyppi tyyppi}))))
(defonce tarkastusreitit
  (reaction
   (let [aikavali (tarkastukset/naytettava-aikavali @tiedot-urakka/valittu-urakka-kaynnissa?
                                                    @tiedot-urakka/valittu-hoitokauden-kuukausi
                                                    @tiedot-urakka/valittu-aikavali)]
     (log "tarkastusreitteillään")
     (luo-tarkastusreitit-kuvataso
      @karttataso-tarkastukset
      @nav/valittu-urakka aikavali
      @tarkastukset/tienumero @tarkastukset/tarkastustyyppi))))
