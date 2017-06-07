(ns harja.views.kartta.tasot
  "Määrittelee kartan näkyvät tasot. Tämä kerää kaikkien yksittäisten tasojen
  päällä/pois flägit ja osaa asettaa ne."
  (:require [reagent.core :refer [atom]]
            [cljs.core.async :refer [<!]]
            [harja.views.kartta.pohjavesialueet :as pohjavesialueet]
            [harja.tiedot.sillat :as sillat]
            [harja.tiedot.urakka.laadunseuranta.tarkastukset-kartalla
             :as tarkastukset]
            [harja.tiedot.urakka.laadunseuranta.laatupoikkeamat-kartalla
             :as laatupoikkeamat]
            [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as ilmoitukset]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset]
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
            [harja.ui.kartta.varit.puhtaat :as varit]
            [harja.tiedot.tilannekuva.tienakyma :as tienakyma-tiedot])
  (:require-macros [reagent.ratom :refer [reaction] :as ratom]
                   [cljs.core.async.macros :refer [go]]))

;; Kaikki näytettävät karttatasot
(def +karttatasot+
  #{:organisaatio
    :pohjavesi
    :sillat
    :tarkastusreitit
    :laatupoikkeamat
    :turvallisuus
    :ilmoitukset
    :tietyoilmoitukset
    :yks-hint-toteumat
    :kok-hint-toteumat
    :varusteet
    :varustetoteumat
    :muut-tyot
    :paallystyskohteet
    :paikkauskohteet
    :tr-valitsin
    :nakyman-geometriat
    :infopaneelin-merkki
    :tilannekuva
    :tilannekuva-organisaatiot
    :tienakyma-valitut
    :tienakyma-muut})

(def
  ^{:doc
    "Niiden tasojen nimet, jotka eivät ole 'näkymän tasoja', vaan ovat 'aina päällä'.
    Näiden tasojen ei haluta esimerkiksi vaikuttavan kartan zoom-tasoon kuin erikoistapauksissa."}
  +yleiset-tasot+
  #{:organisaatio
    :nakyman-geometriat
    :infopaneelin-merkki
    :tr-valitsin})

(defn kartan-asioiden-z-indeksit [taso]
  (case taso
    :hallintayksikko 0
    :urakka 1
    :pohjavesialueet 2
    :sillat 3
    :tienakyma-muut 3
    4))

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
                               :hy (kartan-asioiden-z-indeksit :hallintayksikko)
                               :ur (kartan-asioiden-z-indeksit :urakka)
                               :pohjavesialueet (kartan-asioiden-z-indeksit :pohjavesialueet)
                               :sillat (kartan-asioiden-z-indeksit :sillat)
                               oletus-zindex))))))

(defn- urakat-ja-organisaatiot-kartalla*
  [hals v-hal v-ur sivu valilehti urakat-kartalla]
  (cond
    ;; Näillä sivuilla ei ikinä näytetä murupolun kautta valittujen organisaatiorajoja
    (#{:tilannekuva} sivu)
    nil

    ;; Ilmoituksissa ei haluta näyttää navigointiin
    ;; tarkoitettuja geometrioita (kuten urakat), mutta jos esim HY on
    ;; valittu, voidaan näyttää sen rajat.
    (and (#{:ilmoitukset} sivu) (nil? v-hal))
    nil

    (and (#{:ilmoitukset} sivu)
         (nil? v-ur))
    [(assoc v-hal :valittu true)]

    (and (#{:ilmoitukset} sivu) v-ur)
    [(assoc v-ur :valittu true)]

    ;; Ylläpitourakan rajoja ei haluta piirtää näkymissä, joissa piirretään
    ;; urakan varsinaiset tiet. Ylläpitourakan rajat piirretään vaan näiden teiden ympärille,
    ;; joten jos tiet piirretään jo kartalle, ovat rajat täysin turhaa informaatiota.
    (and (#{:kohdeluettelo-paallystys :kohdeluettelo-paikkaus} valilehti)
         (some? v-hal)
         (some? v-ur))
    nil

    ;; Ei valittua hallintayksikköä, näytetään hallintayksiköt
    (nil? v-hal)
    hals

    ;; Ei valittua urakkaa, näytetään valittu hallintayksikkö
    ;; ja sen urakat
    (nil? v-ur)
    (vec (concat [(assoc v-hal
                    :valittu true)]
                 urakat-kartalla))

    ;; Valittu urakka, mitä näytetään?
    :default [(assoc v-ur
                :valittu true)]))

(def urakat-ja-organisaatiot-kartalla
  (reaction
   (into []
         (keep organisaation-geometria)
         (urakat-ja-organisaatiot-kartalla*
           @hal/hallintayksikot
           @nav/valittu-hallintayksikko
           @nav/valittu-urakka
           @nav/valittu-sivu
           (nav/valittu-valilehti @nav/valittu-sivu)
           @nav/urakat-kartalla))))

;; Ad hoc geometrioiden näyttäminen näkymistä
;; Avain on avainsana ja arvo on itse geometria
(defonce nakyman-geometriat (atom {}))
(defonce infopaneelin-merkki (atom {}))

(defn- aseta-z-index
  ([taso] (aseta-z-index taso oletus-zindex))
  ([taso z-index]
   (when taso
     (taso/aseta-z-index taso z-index))))

;; Asettaaa läpinäkyvyyden geometriatasolle
(defn- aseta-opacity [taso opacity]
  (with-meta taso
    (merge (meta taso)
           {:opacity opacity})))

(declare tasojen-nakyvyys-atomit)

(def geometrioiden-atomit
  {:organisaatio urakat-ja-organisaatiot-kartalla
   :pohjavesi pohjavesialueet/pohjavesialueet-kartalla
   :sillat sillat/sillat-kartalla
   :turvallisuus turvallisuuspoikkeamat/turvallisuuspoikkeamat-kartalla
   :ilmoitukset ilmoitukset/ilmoitukset-kartalla
   :tietyoilmoitukset tietyoilmoitukset/tietyoilmoitukset-kartalla
   :tarkastusreitit tarkastukset/tarkastusreitit-kartalla
   :laatupoikkeamat laatupoikkeamat/laatupoikkeamat-kartalla
   :yks-hint-toteumat yksikkohintaiset-tyot/yksikkohintainen-toteuma-kartalla
   :kok-hint-toteumat kokonaishintaiset-tyot/kokonaishintainen-toteuma-kartalla
   :varusteet varusteet/varusteet-kartalla
   :muut-tyot muut-tyot/muut-tyot-kartalla
   :paallystyskohteet paallystys/paallystyskohteet-kartalla
   :paikkauskohteet paikkaus/paikkauskohteet-kartalla
   :tr-valitsin tierekisteri/tr-alkupiste-kartalla
   :nakyman-geometriat nakyman-geometriat
   :infopaneelin-merkki infopaneelin-merkki
   :tilannekuva tilannekuva/tilannekuvan-asiat-kartalla
   :tilannekuva-organisaatiot tilannekuva/tilannekuvan-organisaatiot
   :tienakyma-valitut tienakyma-tiedot/valitut-tulokset-kartalla
   :tienakyma-muut tienakyma-tiedot/muut-tulokset-kartalla})

(defn nayta-geometria!
  ([avain geometria] (nayta-geometria! avain geometria :nakyman-geometriat))
  ([avain geometria taso]
   (assert (and (map? geometria)
                (contains? geometria :alue))
           "Geometrian tulee olla mäpissä :alue avaimessa!")
   (swap! (taso geometrioiden-atomit) assoc avain geometria)))

(defn poista-geometria!
  ([avain] (poista-geometria! avain :nakyman-geometriat))
  ([avain taso]
   (swap! (taso geometrioiden-atomit) dissoc avain)))

(defn nakyvat-geometriat-z-indeksilla
  "Palauttaa valitun aiheen geometriat z-indeksilla jos geometrian taso on päällä."
  ([geometria taso]
   (nakyvat-geometriat-z-indeksilla geometria taso oletus-zindex))
  ([geometria taso z-index]
   (when (true? taso)
     (aseta-z-index geometria z-index))))

(defn- taso
  ([nimi] (taso nimi nimi))
  ([nimi z-index]
   (nakyvat-geometriat-z-indeksilla @(geometrioiden-atomit nimi)
                                    @(tasojen-nakyvyys-atomit nimi)
                                    (if (number? z-index)
                                      z-index
                                      (kartan-asioiden-z-indeksit z-index))))
  ([nimi z-index opacity]
   (aseta-opacity
     (taso nimi z-index)
     opacity)))

(def geometriat-kartalle
  (reaction
    (merge
      {:organisaatio (taso :organisaatio :urakka 0.7)
       :tilannekuva-organisaatiot (taso :tilannekuva-organisaatiot :urakka)
       :pohjavesi (taso :pohjavesi :pohjavesialueet)
       :sillat (taso :sillat :sillat)
       :tarkastusreitit (taso :tarkastusreitit)
       :laatupoikkeamat (taso :laatupoikkeamat)
       :turvallisuus (taso :turvallisuus)
       :ilmoitukset (taso :ilmoitukset)
       :tietyoilmoitukset (taso :tietyoilmoitukset)
       :yks-hint-toteumat (taso :yks-hint-toteumat)
       :kok-hint-toteumat (taso :kok-hint-toteumat)
       :varusteet (taso :varusteet)
       :muut-tyot (taso :muut-tyot)
       :paallystyskohteet (taso :paallystyskohteet)
       :paikkauskohteet (taso :paikkauskohteet)
       :tr-valitsin (taso :tr-valitsin (inc oletus-zindex))
       :tienakyma-valitut (taso :tienakyma-valitut)
       :tienakyma-muut (taso :tienakyma-muut :tienakyma-muut 0.4)
       ;; Yksittäisen näkymän omat mahdolliset geometriat
       :nakyman-geometriat
       (aseta-z-index (vec (vals @(geometrioiden-atomit :nakyman-geometriat)))
                      (inc oletus-zindex))
       :infopaneelin-merkki (aseta-z-index (vec (vals @(geometrioiden-atomit :infopaneelin-merkki)))
                                           (+ oletus-zindex 2))}
      ;; Tilannekuvan geometriat muodostetaan hieman eri tavalla
     (when (true? @(tasojen-nakyvyys-atomit :tilannekuva))
        (into {}
              (map (fn [[tason-nimi tason-sisalto]]
                     {tason-nimi (aseta-z-index tason-sisalto oletus-zindex)})
                   @(geometrioiden-atomit :tilannekuva)))))))

(def ^{:private true} tasojen-nakyvyys-atomit
  {:organisaatio (atom true)
   :pohjavesi pohjavesialueet/karttataso-pohjavesialueet
   :sillat sillat/karttataso-sillat
   :tarkastusreitit tarkastukset/karttataso-tarkastukset
   :laatupoikkeamat laatupoikkeamat/karttataso-laatupoikkeamat
   :turvallisuus turvallisuuspoikkeamat/karttataso-turvallisuuspoikkeamat
   :ilmoitukset ilmoitukset/karttataso-ilmoitukset
   :tietyoilmoitukset tietyoilmoitukset/karttataso-tietyoilmoitukset
   :yks-hint-toteumat yksikkohintaiset-tyot/karttataso-yksikkohintainen-toteuma
   :kok-hint-toteumat kokonaishintaiset-tyot/karttataso-kokonaishintainen-toteuma
   :varusteet varusteet/karttataso-varustetoteuma
   :muut-tyot muut-tyot/karttataso-muut-tyot
   :paallystyskohteet paallystys/karttataso-paallystyskohteet
   :paikkauskohteet paikkaus/karttataso-paikkauskohteet
   :tr-valitsin tierekisteri/karttataso-tr-alkuosoite
   :tilannekuva tilannekuva/karttataso-tilannekuva
   :tilannekuva-organisaatiot tilannekuva/karttataso-tilannekuva
   :tienakyma-valitut tienakyma-tiedot/karttataso-tienakyma
   :tienakyma-muut tienakyma-tiedot/karttataso-tienakyma
   :nakyman-geometriat (atom true)
   :infopaneelin-merkki (atom true)})

(defn- nykyiset-karttatasot* [atomit nimet-set]
  (->> atomit
       (filter (comp deref val))
       (filter (comp nimet-set key))
       (map key)))

(def nykyiset-karttatasot (partial nykyiset-karttatasot* tasojen-nakyvyys-atomit +karttatasot+))

(defn- aktiiviset-nakymien-tasot*
  [aktiiviset-tasot-nimet-set
   ei-halutut-tasot-set
   nimi-taso-map
   filter-fn]
  (->> aktiiviset-tasot-nimet-set
       (remove ei-halutut-tasot-set)
       (keep nimi-taso-map)
       (filter filter-fn)))

(defn aktiiviset-nakymien-tasot []
  (aktiiviset-nakymien-tasot*
           (nykyiset-karttatasot)
           +yleiset-tasot+
           @geometriat-kartalle
           taso/aktiivinen?))

(defn taso-paalle! [nimi]
  (log "Karttataso päälle: " (pr-str nimi))
  (reset! (tasojen-nakyvyys-atomit nimi) true))

(defn taso-pois! [nimi]
  (log "Karttataso pois: " (pr-str nimi))
  (reset! (tasojen-nakyvyys-atomit nimi) false))
