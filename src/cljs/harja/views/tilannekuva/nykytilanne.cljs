(ns harja.views.tilannekuva.nykytilanne
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.tilannekuva.nykytilanne :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.kentat :as kentat]
            [harja.views.tilannekuva.tilannekuvien-yhteiset-komponentit :refer [nayta-hallinnolliset-tiedot]]
            [reagent.core :as r]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.views.kartta :as kartta]
            [harja.pvm :as pvm]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.ilmoitukset :as ilmoitukset])
  (:require-macros [reagent.ratom :refer [run!]]))

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
                                    :havainnot]
                      :vaihtoehto-nayta {
                                         :toimenpidepyynnot "Toimenpidepyynnöt"
                                         :kyselyt "Kyselyt"
                                         :tiedoitukset "Tiedotukset"
                                         :tyokoneet "Työkoneiden seuranta"
                                         :havainnot "Havainnot"
                                         }}
   (r/wrap
    (into #{}
          (keep identity)
          [(when @tiedot/hae-toimenpidepyynnot? :toimenpidepyynnot)
           (when @tiedot/hae-kyselyt? :kyselyt)
           (when @tiedot/hae-tiedoitukset? :tiedoitukset)
           (when @tiedot/hae-tyokoneet? :tyokoneet)
           (when @tiedot/hae-havainnot? :havainnot)])
    
    (fn [uusi]
      (reset! tiedot/hae-toimenpidepyynnot? (:toimenpidepyynnot uusi))
      (reset! tiedot/hae-kyselyt? (:kyselyt uusi))
      (reset! tiedot/hae-tyokoneet? (:tyokoneet uusi))
      (reset! tiedot/hae-tiedoitukset? (:tiedoitukset uusi))
      (reset! tiedot/hae-havainnot? (:havainnot uusi))))])

(defonce suodattimet [:span
                      [nayta-hallinnolliset-tiedot]
                      [aikavalinta]
                      [haettavien-asioiden-valinta]])

(defonce hallintapaneeli (atom {1 {:auki true :otsikko "Nykytilanne" :sisalto suodattimet}}))

(defn nykytilanne []
  (komp/luo
    (komp/ulos (paivita-periodisesti tiedot/asioiden-haku 3000)) ;3s
    (komp/kuuntelija :ilmoitus-klikattu
                     (fn [this tapahtuma]
                       (kartta/nayta-popup! (get-in tapahtuma [:sijainti :coordinates])
                                            [:div.kartta-ilmoitus-popup
                                             (log (pr-str tapahtuma))
                                             [:p [:b (name (:tyyppi tapahtuma))]]
                                             [:p "Ilmoitettu: " (pvm/pvm-aika-sek (:ilmoitettu tapahtuma))]
                                             [:p "Vapaateksti: " (:vapaateksti tapahtuma)]
                                             [:p (count (:kuittaukset tapahtuma)) " kuittausta."]
                                             [:a {:href     "#"
                                                  :on-click #(do (.preventDefault %)
                                                                 (let [putsaa (fn [asia]
                                                                                (clojure.set/rename-keys
                                                                                  (dissoc asia :type :alue)
                                                                                  {:tyyppi :ilmoitustyyppi}))]
                                                                   (reset! nav/sivu :ilmoitukset)
                                                                   (reset! ilmoitukset/haetut-ilmoitukset
                                                                           (map putsaa (filter
                                                                                         (fn [asia] (= (:type asia) :ilmoitus))
                                                                                         @tiedot/nykytilanteen-asiat-kartalla)))
                                                                   (reset! ilmoitukset/valittu-ilmoitus (putsaa tapahtuma))))}
                                              "Siirry ilmoitusnäkymään"]])))

    {:component-will-mount   (fn [_]
                               (kartta/aseta-yleiset-kontrollit
                                 [yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true}]))
     :component-will-unmount (fn [_]
                               (kartta/tyhjenna-yleiset-kontrollit))}
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-nykytilanne)
    (constantly nil)))