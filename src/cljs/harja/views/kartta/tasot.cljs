(ns harja.views.kartta.tasot
  "Määrittelee kartan näkyvät tasot. Tämä kerää kaikkien yksittäisten tasojen päällä/pois flägit ja osaa asettaa ne."
  (:require [reagent.core :refer [atom]]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.tiedot.sillat :as sillat]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.tiedot.urakka.turvallisuus.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.tiedot.tilannekuva.historiakuva :as historiakuva]
            [harja.tiedot.tilannekuva.nykytilanne :as nykytilanne]
            [harja.tiedot.urakka.kohdeluettelo.paallystys :as paallystys]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.tiedot.urakka.muut-tyot :as muut-tyot])
  (:require-macros [reagent.ratom :refer [reaction] :as ratom]))


;; Lisää uudet karttatasot tänne
(def +karttatasot+ #{:pohjavesialueet :sillat :tarkastukset :ilmoitukset :turvallisuuspoikkeamat
                    :historiakuva :nykytilanne :paallystyskohteet :yksikkohintainen-toteuma :kokonaishintainen-toteuma})

(def geometriat (reaction
                 (loop [geometriat (transient [])
                        [g & gs] (concat @pohjavesialueet/pohjavesialueet
                                         @sillat/sillat
                                         @laadunseuranta/tarkastukset-kartalla
                                         @ilmoitukset/ilmoitukset-kartalla
                                         @turvallisuuspoikkeamat/turvallisuuspoikkeamat-kartalla
                                         @toteumat/yksikkohintainen-toteuma-kartalla
                                         @kokonaishintaiset-tyot/kokonaishintainen-toteuma-kartalla
                                         @historiakuva/historiakuvan-asiat-kartalla
                                         @nykytilanne/nykytilanteen-asiat-kartalla
                                         @paallystys/paallystyskohteet-kartalla
                                         @paallystys/paikkauskohteet-kartalla
                                         @muut-tyot/muut-tyot-kartalla)]
                   (if-not g
                     (persistent! geometriat)
                     (recur (conj! geometriat g) gs)))))

(defn- taso-atom [nimi]
  (case nimi
    :pohjavesialueet pohjavesialueet/karttataso-pohjavesialueet
    :sillat sillat/karttataso-sillat
    :tarkastukset laadunseuranta/karttataso-tarkastukset
    :ilmoitukset ilmoitukset/karttataso-ilmoitukset
    :turvallisuuspoikkeamat turvallisuuspoikkeamat/karttataso-turvallisuuspoikkeamat
    :historiakuva historiakuva/karttataso-historiakuva
    :yksikkohintainen-toteuma toteumat/karttataso-yksikkohintainen-toteuma
    :kokonaishintainen-toteuma kokonaishintaiset-tyot/karttataso-kokonaishintainen-toteuma
    :nykytilanne nykytilanne/karttataso-nykytilanne
    :paallystyskohteet paallystys/karttataso-paallystyskohteet
    :muut-tyot muut-tyot/karttataso-muut-tyot))

(defonce nykyiset-karttatasot
  (reaction (into #{}
                  (keep (fn [nimi]
                          (when @(taso-atom nimi)
                            nimi)))
                  +karttatasot+)))

(defonce karttatasot-muuttuneet
  (ratom/run! (let [tasot @nykyiset-karttatasot]
                (tapahtumat/julkaise! {:aihe :karttatasot-muuttuneet :karttatasot tasot}))))

(defn taso-paalle! [nimi]
  (tapahtumat/julkaise! {:aihe :karttatasot-muuttuneet :taso-paalle nimi})
  (reset! (taso-atom nimi) true))

(defn taso-pois! [nimi]
  (tapahtumat/julkaise! {:aihe :karttatasot-muuttuneet :taso-pois nimi})
  (reset! (taso-atom nimi) false))

(defn taso-paalla? [nimi]
  @(taso-atom nimi))
