(ns harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla
  (:require [harja.ui.kartta.esitettavat-asiat :as esitettavat-asiat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset :as tarkastukset]
            [harja.tiedot.urakka :as tiedot-urakka]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.openlayers :as openlayers]
            [harja.ui.kartta.varit.puhtaat :as varit]

            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [cljs-time.core :as t]
            [harja.domain.urakka :as u-domain]
            [clojure.set :as set])
  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]))

(defonce karttataso-tarkastukset (atom false))

(defn- luo-tarkastusreitit-kuvataso [taso-paalla? urakkatyyppi parametrit]
  (when taso-paalla?
    (openlayers/luo-kuvataso
     :tarkastusreitit (if (not (u-domain/vesivaylaurakkatyyppi? urakkatyyppi))
                        esitettavat-asiat/tarkastus-selitteet-reiteille
                        esitettavat-asiat/tarkastus-selitteet-ikoneille)
     "tr" (k/url-parametri (assoc parametrit
                             :valittu {:id (:id @tarkastukset/valittu-tarkastus)})))))

(def tarkastusreitit-kartalla
  (reaction
    @tarkastukset/urakan-tarkastukset
    (luo-tarkastusreitit-kuvataso
      @karttataso-tarkastukset
      (:tyyppi @nav/valittu-urakka)
      (tarkastukset/kasaa-haun-parametrit
        @tiedot-urakka/valittu-urakka-kaynnissa?
        @nav/valittu-urakka-id
        @tiedot-urakka/valittu-hoitokausi
        @tiedot-urakka/valittu-hoitokauden-kuukausi
        @tarkastukset/valittu-aikavali
        @tarkastukset/tienumero @tarkastukset/tarkastustyyppi
        @tarkastukset/havaintoja-sisaltavat?
        @tarkastukset/vain-laadunalitukset?
        @tarkastukset/tarkastuksen-tekija))))
