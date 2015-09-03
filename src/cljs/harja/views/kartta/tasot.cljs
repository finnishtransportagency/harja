(ns harja.views.kartta.tasot
  "Määrittelee kartan näkyvät tasot. Tämä kerää kaikkien yksittäisten tasojen päällä/pois flägit ja osaa asettaa ne."
  (:require [reagent.core :refer [atom]]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.tiedot.sillat :as sillat]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.tiedot.tilannekuva.historiakuva :as historiakuva]
            [harja.tiedot.tilannekuva.nykytilanne :as nykytilanne]
            [harja.tiedot.urakka.kohdeluettelo.paallystys :as paallystys])
  (:require-macros [reagent.ratom :refer [reaction]]))

(def geometriat (reaction
                 (loop [geometriat (transient [])
                        [g & gs] (concat @pohjavesialueet/pohjavesialueet
                                         @sillat/sillat
                                         @laadunseuranta/tarkastukset-kartalla
                                         @ilmoitukset/ilmoitukset-kartalla
                                         @turvallisuuspoikkeamat/turvallisuuspoikkeamat-kartalla
                                         @historiakuva/historiakuvan-asiat-kartalla
                                         @nykytilanne/nykytilanteen-asiat-kartalla
                                         @paallystys/paallystyskohteet-kartalla)]
                   (if-not g
                     (persistent! geometriat)
                     (recur (conj! geometriat g) gs)))))


(defn- taso-atom [nimi]
  (case nimi
    :pohjavesialueet pohjavesialueet/taso-pohjavesialueet
    :sillat sillat/taso-sillat
    :tarkastukset laadunseuranta/taso-tarkastukset
    :ilmoitukset ilmoitukset/taso-ilmoitukset
    :turvallisuuspoikkeamat turvallisuuspoikkeamat/taso-turvallisuuspoikkeamat
    :historiakuva historiakuva/taso-historiakuva
    :nykytilanne nykytilanne/taso-nykytilanne
    :paallystyskohteet paallystys/taso-paallystyskohteet))
    
(defn taso-paalle! [nimi]
  (reset! (taso-atom nimi) true))

(defn taso-pois! [nimi]
  (reset! (taso-atom nimi) false))

(defn taso-paalla? [nimi]
  @(taso-atom nimi))
