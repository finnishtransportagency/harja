(ns harja.views.tilannekuva.nykytilanne
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.nykytilanne :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.views.tilannekuva.tyokoneet :as tyokoneet]
            [reagent.core :as r])
  (:require-macros [reagent.ratom :refer [reaction run!]]))

(defn aikavalinta []
  [kentat/tee-kentta {:tyyppi   :radio
                      :valinnat ["0-4h" "0-12h" "0-24h"]}
   tiedot/livesuodattimen-asetukset])

(defn haettavien-asioiden-valinta []
  [kentat/tee-kentta {:tyyppi      :boolean-group
                      :vaihtoehdot [:toimenpidepyynnot
                                    :kyselyt
                                    :tiedoitukset
                                    :kalusto
                                    :onnettomuudet
                                    :havainnot]}
   (r/wrap
    (into #{}
          (keep identity)
          [(when @tiedot/hae-toimenpidepyynnot? :toimenpidepyynnot)
           (when @tiedot/hae-kyselyt? :kyselyt)
           (when @tiedot/hae-tiedoitukset? :tiedoitukset)
           (when @tiedot/hae-onnettomuudet? :onnettomuudet)
           (when @tiedot/hae-tyokoneet? :tyokoneet)
           (when @tiedot/hae-havainnot? :havainnot)])
    
    (fn [uusi]
      (reset! tiedot/hae-toimenpidepyynnot? (:toimenpidepyynnot uusi))
      (reset! tiedot/hae-kyselyt? (:kyselyt uusi))
      (reset! tiedot/hae-tyokoneet? (:tyokoneet uusi))
      (reset! tiedot/hae-tiedoitukset? (:tiedoitukset uusi))
      (reset! tiedot/hae-onnettomuudet? (:onnettomuudet uusi))
      (reset! tiedot/hae-havainnot? (:havainnot uusi))))])

(defonce suodattimet [:span
                      [aikavalinta]
                      [haettavien-asioiden-valinta]])

(defonce hallintapaneeli (atom {1 {:auki false :otsikko "Nykytilanne" :sisalto suodattimet}}))

(defn nykytilanne []
  (komp/luo
    {:component-will-mount
     (fn [_]
       (reset! tiedot/nakymassa? true)
       (reset! tiedot/taso-nykytilanne true))
     :component-will-unmount
     (fn [_]
       (reset! tiedot/nakymassa? false)
       (reset! tiedot/taso-nykytilanne false)
       (tiedot/lopeta-asioiden-haku))}
    (fn []
      [yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true
                                        :leijuva?             300}])))
