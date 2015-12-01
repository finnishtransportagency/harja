(ns harja.views.kartta.tasot
  "Määrittelee kartan näkyvät tasot. Tämä kerää kaikkien yksittäisten tasojen päällä/pois flägit ja osaa asettaa ne."
  (:require [reagent.core :refer [atom]]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.tiedot.sillat :as sillat]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.turvallisuuspoikkeamat :as turvallisuuspoikkeamat]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot :as kokonaishintaiset-tyot]
            [harja.tiedot.urakka.toteumat.varusteet :as varusteet]
            [harja.tiedot.tilannekuva.tilannekuva :as tilannekuva]
            [harja.tiedot.urakka.kohdeluettelo.paallystys :as paallystys]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.tiedot.tierekisteri :as tierekisteri]
            [harja.tiedot.urakka.muut-tyot :as muut-tyot])
  (:require-macros [reagent.ratom :refer [reaction] :as ratom]))


;; Lisää uudet karttatasot tänne
(def +karttatasot+ #{:pohjavesialueet :sillat :tarkastukset :ilmoitukset :turvallisuuspoikkeamat
                    :historiakuva :nykytilanne :paallystyskohteet :tr-alkupiste :yksikkohintainen-toteuma
                     :kokonaishintainen-toteuma :varusteet})

(def geometriat (reaction
                  (loop [geometriat (transient [])
                        [g & gs] (concat ;; Pohjavesi
                                         @pohjavesialueet/pohjavesialueet
                                         ;; Laadunseunranta
                                         @laadunseuranta/tarkastukset-kartalla
                                         @sillat/sillat
                                         ;; Turvallisuus
                                         @turvallisuuspoikkeamat/turvallisuuspoikkeamat-kartalla
                                         ;; Ilmoitukset
                                         @ilmoitukset/ilmoitukset-kartalla
                                         ;; TR-valitsin
                                         @tierekisteri/tr-alkupiste-kartalla
                                         ;; Toteumat
                                         @toteumat/yksikkohintainen-toteuma-kartalla
                                         @kokonaishintaiset-tyot/kokonaishintainen-toteuma-kartalla
                                         @varusteet/varusteet-kartalla
                                         @muut-tyot/muut-tyot-kartalla
                                         ;; Tilannekuva
                                         @tilannekuva/tilannekuvan-asiat-kartalla
                                         ;; Päällystys & paikkaus
                                         @paallystys/paallystyskohteet-kartalla
                                         @paallystys/paikkauskohteet-kartalla)]
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
    :yksikkohintainen-toteuma toteumat/karttataso-yksikkohintainen-toteuma
    :kokonaishintainen-toteuma kokonaishintaiset-tyot/karttataso-kokonaishintainen-toteuma
    :varusteet varusteet/karttataso-varustetoteuma
    :tilannekuva tilannekuva/karttataso-tilannekuva
    :paallystyskohteet paallystys/karttataso-paallystyskohteet
    :tr-alkupiste tierekisteri/karttataso-tr-alkuosoite
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
  (log "Karttataso päälle: " (pr-str nimi))
  (reset! (taso-atom nimi) true))

(defn taso-pois! [nimi]
  (tapahtumat/julkaise! {:aihe :karttatasot-muuttuneet :taso-pois nimi})
  (log "Karttataso pois: " (pr-str nimi))
  (reset! (taso-atom nimi) false))

(defn taso-paalla? [nimi]
  @(taso-atom nimi))
