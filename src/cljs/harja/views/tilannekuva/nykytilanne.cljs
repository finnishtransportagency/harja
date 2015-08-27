(ns harja.views.tilannekuva.nykytilanne
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.nykytilanne :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.kentat :as kentat]
            [harja.views.tilannekuva.tilannekuvien-yhteiset-komponentit :refer [nayta-hallinnolliset-tiedot]]
            [reagent.core :as r]
            [harja.views.kartta :as kartta]
            [clojure.string :as str]
            [harja.asiakas.tapahtumat :as tapahtumat])
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
                                    :tyokoneet
                                    :onnettomuudet
                                    :havainnot]
                      :vaihtoehto-nayta {
                                         :toimenpidepyynnot "Toimenpidepyynnöt"
                                         :kyselyt "Kyselyt"
                                         :tiedoitukset "Tiedoitukset"
                                         :tyokoneet "Työkoneiden seuranta"
                                         :onnettomuudet "Onnettomuudet"
                                         :havainnot "Havainnot"
                                         }}
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
                      [nayta-hallinnolliset-tiedot]
                      [aikavalinta]
                      [haettavien-asioiden-valinta]])

(defonce hallintapaneeli (atom {1 {:auki true :otsikko "Nykytilanne" :sisalto suodattimet}}))

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

(tapahtumat/kuuntele! :tyokone-klikattu
                      (fn [tapahtuma]
                        (kartta/nayta-popup! (:sijainti tapahtuma)
                                             [:div.kartta-tyokone-popup
                                              [:p [:b "Työkone"]]
                                              [:div "Tyyppi: " (:tyokonetyyppi tapahtuma)]
                                              [:div "Organisaatio: " (:organisaationimi tapahtuma)]
                                              [:div "Urakka: " (:urakkanimi tapahtuma)]
                                              [:div "Tehtävät: "
                                               (let [tehtavat (str/join "," (:tehtavat tapahtuma))]
                                                 [:span tehtavat])]])))