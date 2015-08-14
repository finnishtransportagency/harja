(ns harja.views.kartta.tasot
  "Määrittelee kartan näkyvät tasot. Tämä kerää kaikkien yksittäisten tasojen päällä/pois flägit ja osaa asettaa ne."
  (:require [reagent.core :refer [atom]]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.tiedot.sillat :as sillat]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.views.tyokoneseuranta :as tyokoneseuranta]
            [harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.tiedot.tilannekuva :as tilannekuva])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def geometriat (reaction
                 (loop [geometriat (transient [])
                        [g & gs] (concat @pohjavesialueet/pohjavesialueet
                                         @sillat/sillat
                                         @laadunseuranta/urakan-tarkastukset
                                         @ilmoitukset/ilmoitukset-kartalla
                                         @turvallisuuspoikkeamat/turvallisuuspoikkeamat-kartalla
                                         @tyokoneseuranta/alueen-tyokoneet
                                         @tilannekuva/tilannekuvan-asiat-kartalla)]
                   (if-not g
                     (persistent! geometriat)
                     (recur (conj! geometriat g) gs)))))


(defn- taso-atom [nimi]
  (case nimi
    :pohjavesialueet pohjavesialueet/taso-pohjavesialueet
    :sillat sillat/taso-sillat
    :tarkastukset laadunseuranta/taso-tarkastukset
    :ilmoitukset ilmoitukset/taso-ilmoitukset
    :tyokoneet tyokoneseuranta/tyokonetaso-paalla
    :turvallisuuspoikkeamat turvallisuuspoikkeamat/taso-turvallisuuspoikkeamat
    :tilannekuva tilannekuva/taso-tilannekuva))
    
(defn taso-paalle! [nimi]
  (reset! (taso-atom nimi) true))

(defn taso-pois! [nimi]
  (reset! (taso-atom nimi) false))

(defn taso-paalla? [nimi]
  @(taso-atom nimi))
