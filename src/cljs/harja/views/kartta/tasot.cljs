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
            [harja.ui.openlayers.taso :as taso]
            [harja.ui.kartta.varit.alpha :as varit])
  (:require-macros [reagent.ratom :refer [reaction] :as ratom]))


;; Lisää uudet karttatasot tänne
(def +karttatasot+
  #{:pohjavesialueet :sillat :tarkastukset :ilmoitukset :turvallisuuspoikkeamat
    :tilannekuva :paallystyskohteet :tr-alkupiste :yksikkohintainen-toteuma
    :kokonaishintainen-toteuma :varusteet})

(def ^{:doc "Kartalle piirrettävien tasojen oletus-zindex. Urakat ja muut
  piirretään pienemmällä zindexillä." :const true}
  oletus-zindex 4)

(defn- organisaation-geometria [piirrettava]
  (let [{:keys [stroke] :as alue} (:alue piirrettava)]
    (when (map? alue)
      (update-in piirrettava
                 [:alue]
                 assoc
                 :fill (if (:valittu piirrettava) false true)
                 :stroke (if stroke
                           stroke
                           (when (or (:valittu piirrettava)
                                     (= :silta (:type piirrettava)))
                             {:width 3}))
                 :color (or (:color alue)
                            (nth varit/kaikki (mod (:id piirrettava)
                                                   (count varit/kaikki))))
                 :zindex (or (:zindex alue)
                             (case (:type piirrettava)
                               :hy 0
                               :ur 1
                               :pohjavesialueet 2
                               :sillat 3
                               oletus-zindex))))))
(def organisaatio
  ;; Kartalla näytettävät organisaatiot / urakat
  (reaction
   (into []
         (keep organisaation-geometria)
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

             (and (#{:tilannekuva :ilmoitukset} sivu)
                  (nil? @nav/valittu-urakka))
             [(assoc v-hal :valittu true)]

             (and (#{:tilannekuva :ilmoitukset} sivu) @nav/valittu-urakka)
             [(assoc v-ur :valittu true)]

             ;; Ei valittua hallintayksikköä, näytetään hallintayksiköt
             (nil? v-hal)
             hals

             ;; Ei valittua urakkaa, näytetään valittu hallintayksikkö
             ;; ja sen urakat
             (nil? v-ur)
             (vec (concat [(assoc v-hal
                                  :valittu true)]
                          @nav/urakat-kartalla))

             ;; Valittu urakka, mitä näytetään?
             :default [(assoc v-ur
                              :valittu true)])))))



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

;; PENDING: Tasot, niiden lippu atomit ja keyword nimet ja z-indexit,
;; olisi hyvä saada määriteltyä tiivisti, kerran ja yhdessä paikassa.
;;
;; esim. (def tasot [[:mun-taso mun-taso/taso-lippu mun-taso/geometriat 4] ...])
;; sitä samaa tietoa voisi sitten käyttää kaikkialla alla.

(defn- aseta-z-index
  ([taso] (aseta-z-index taso oletus-zindex))
  ([taso z-index]
   (when taso
     (taso/aseta-z-index taso z-index))))

(def geometriat
  (reaction
   (merge
    {:organisaatio
     (aseta-z-index @organisaatio 0)

     :pohjavesi
     (aseta-z-index @pohjavesialueet/pohjavesialueet 1)

     :sillat
     (aseta-z-index @sillat/sillat 2)

     :tarkastukset
     (aseta-z-index @tarkastukset/tarkastukset-kartalla)

     :turvallisuus
     (aseta-z-index
      @turvallisuuspoikkeamat/turvallisuuspoikkeamat-kartalla)

     :ilmoitukset
     (aseta-z-index @ilmoitukset/ilmoitukset-kartalla)

     :yks-hint-toteumat
     (aseta-z-index
      @yksikkohintaiset-tyot/yksikkohintainen-toteuma-kartalla)

     :kok-hint-toteumat
     (aseta-z-index
      @kokonaishintaiset-tyot/kokonaishintainen-toteuma-kartalla)

     :varusteet
     (aseta-z-index @varusteet/varusteet-kartalla)

     :muut-tyot
     (aseta-z-index @muut-tyot/muut-tyot-kartalla)

     :paallystyskohteet
     (aseta-z-index @paallystys/paallystyskohteet-kartalla)

     :paikkauskohteet
     (aseta-z-index @paikkaus/paikkauskohteet-kartalla)

     :tr-valitsin
     (aseta-z-index @tierekisteri/tr-alkupiste-kartalla
                          (inc oletus-zindex))
     :nakyman-geometriat
     (aseta-z-index (vec (vals @nakyman-geometriat))
                          (inc oletus-zindex))}
    (into {}
          (map (fn [[tason-nimi tason-sisalto]]
                 {tason-nimi (aseta-z-index tason-sisalto oletus-zindex)})
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
