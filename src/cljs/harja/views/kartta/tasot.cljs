(ns harja.views.kartta.tasot
  "Määrittelee kartan näkyvät tasot. Tämä kerää kaikkien yksittäisten tasojen
  päällä/pois flägit ja osaa asettaa ne."
  (:require [reagent.core :refer [atom]]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.tiedot.sillat :as sillat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla
             :as tarkastukset]
            [harja.tiedot.ilmoitukset :as ilmoitukset]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.tiedot.urakka.turvallisuuspoikkeamat
             :as turvallisuuspoikkeamat]
            [harja.tiedot.urakka.toteumat.yksikkohintaiset-tyot
             :as yksikkohintaiset-tyot]
            [harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot
             :as kokonaishintaiset-tyot]
            [harja.tiedot.urakka.toteumat.varusteet :as varusteet]
            [harja.tiedot.tilannekuva.tilannekuva-kartalla :as tilannekuva]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.urakka.paikkaus :as paikkaus]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.tiedot.tierekisteri :as tierekisteri]
            [harja.tiedot.urakka.toteumat.muut-tyot-kartalla :as muut-tyot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.hallintayksikot :as hal]
            [harja.ui.openlayers.tasot :as tasot])
  (:require-macros [reagent.ratom :refer [reaction] :as ratom]))


;; Lisää uudet karttatasot tänne
(def +karttatasot+
  #{:pohjavesialueet :sillat :tarkastukset :ilmoitukset :turvallisuuspoikkeamat
    :tilannekuva :paallystyskohteet :tr-alkupiste :yksikkohintainen-toteuma
    :kokonaishintainen-toteuma :varusteet})

(def ^{:doc "Kartalle piirrettävien tasojen oletus-zindex. Urakat ja muut
  piirretään pienemmällä zindexillä." :const true}
  oletus-zindex 4)

(def organisaatio
  ;; Kartalla näytettävät organisaatiot / urakat
  (reaction
    (let [hals @hal/hallintayksikot
          v-hal @nav/valittu-hallintayksikko
          v-ur @nav/valittu-urakka
          sivu (nav/sivu)]
      (cond
        ;; Tilannekuvassa ja ilmoituksissa ei haluta näyttää navigointiin
        ;; tarkoitettuja geometrioita (kuten urakat), mutta jos esim HY on
        ;; valittu, voidaan näyttää sen rajat.
        (and (#{:tilannekuva :ilmoitukset} sivu) (nil? v-hal))
        nil

        (and (#{:tilannekuva :ilmoitukset} sivu) (nil? @nav/valittu-urakka))
        [(assoc v-hal :valittu true)]

        (and (#{:tilannekuva :ilmoitukset} sivu) @nav/valittu-urakka)
        [(assoc v-ur :valittu true)]

        ;; Ei valittua hallintayksikköä, näytetään hallintayksiköt
        (nil? v-hal)
        hals

        ;; Ei valittua urakkaa, näytetään valittu hallintayksikkö ja sen urakat
        (nil? v-ur)
        (vec (concat [(assoc v-hal
                        :valittu true)]
                     @nav/urakat-kartalla))

        ;; Valittu urakka, mitä näytetään?
        :default [(assoc v-ur
                    :valittu true)]))))


;; Ad hoc geometrioiden näyttäminen näkymistä
;; Avain on avainsana ja arvo on itse geometria
(defonce nakyman-geometriat (atom {}))

(defn nayta-geometria! [avain geometria]
  (assert (and (map? geometria)
               (contains? geometria :alue))
          "Geometrian tulee olla mäpissä :alue avaimessa!")
  (swap! nakyman-geometriat assoc avain geometria))

(defn poista-geometria! [avain]
  (swap! nakyman-geometriat dissoc avain))

(def geometriat
  (reaction
   (merge
    {:organisaatio
     (tasot/aseta-z-index @organisaatio 0)

     :pohjavesi
     (tasot/aseta-z-index @pohjavesialueet/pohjavesialueet 1)

     :sillat
     (tasot/aseta-z-index @sillat/sillat 2)

     :tarkastukset
     (tasot/aseta-z-index @tarkastukset/tarkastukset-kartalla)

     :turvallisuus
     (tasot/aseta-z-index
      @turvallisuuspoikkeamat/turvallisuuspoikkeamat-kartalla)

     :ilmoitukset
     (tasot/aseta-z-index @ilmoitukset/ilmoitukset-kartalla)

     :yks-hint-toteumat
     (tasot/aseta-z-index
      @yksikkohintaiset-tyot/yksikkohintainen-toteuma-kartalla)

     :kok-hint-toteumat
     (tasot/aseta-z-index
      @kokonaishintaiset-tyot/kokonaishintainen-toteuma-kartalla)

     :varusteet
     (tasot/aseta-z-index @varusteet/varusteet-kartalla)

     :muut-tyot
     (tasot/aseta-z-index @muut-tyot/muut-tyot-kartalla)

     :paallystyskohteet
     (tasot/aseta-z-index @paallystys/paallystyskohteet-kartalla)

     :paikkauskohteet
     (tasot/aseta-z-index @paikkaus/paikkauskohteet-kartalla)

     :tr-valitsin
     (tasot/aseta-z-index @tierekisteri/tr-alkupiste-kartalla
                          (inc oletus-zindex))
     :nakyman-geometriat
     (tasot/aseta-z-index (vals @nakyman-geometriat)
                          (inc oletus-zindex))}
    (into {}
          (map (fn [[tason-nimi tason-sisalto]]
                 {tason-nimi (tasot/aseta-z-index tason-sisalto oletus-zindex)})
               @tilannekuva/tilannekuvan-asiat-kartalla)))))

(defn- taso-atom [nimi]
  (case nimi
    :pohjavesialueet pohjavesialueet/karttataso-pohjavesialueet
    :sillat sillat/karttataso-sillat
    :tarkastukset tarkastukset/karttataso-tarkastukset
    :ilmoitukset ilmoitukset/karttataso-ilmoitukset
    :turvallisuuspoikkeamat
    turvallisuuspoikkeamat/karttataso-turvallisuuspoikkeamat
    :yksikkohintainen-toteuma
    yksikkohintaiset-tyot/karttataso-yksikkohintainen-toteuma
    :kokonaishintainen-toteuma
    kokonaishintaiset-tyot/karttataso-kokonaishintainen-toteuma
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
  (ratom/run!
   (let [tasot @nykyiset-karttatasot]
     (tapahtumat/julkaise! {:aihe :karttatasot-muuttuneet
                            :karttatasot tasot}))))

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
