(ns harja.views.tilannekuva.historiakuva
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.historiakuva :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :as yleiset]
            [harja.views.kartta :as kartta]
            [harja.views.tilannekuva.tilannekuvien-yhteiset-komponentit :refer [nayta-hallinnolliset-tiedot]]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r])
  (:require-macros [reagent.ratom :refer [reaction run!]]))


(defn lyhytsuodatin []
  [kentat/tee-kentta {:tyyppi     :aikavalitsin
                      :kellonaika {
                                   :valinnat ["00:00" "06:00" "12:00" "18:00"]
                                   }}
   tiedot/lyhyen-suodattimen-asetukset])

(defn pitkasuodatin []
  [:span
   [kentat/tee-kentta {:tyyppi :pvm}
    (r/wrap (:alku @tiedot/pitkan-suodattimen-asetukset)
            (fn [u] (swap! assoc :alku u)))]

   [kentat/tee-kentta {:tyyppi :pvm}
    (r/wrap (:loppu @tiedot/pitkan-suodattimen-asetukset)
            (fn [u] (swap! assoc :loppu u)))]])

(defonce aikasuodattimet-rivit (atom {1 {:auki    (= :live @tiedot/valittu-aikasuodatin)
                                         :otsikko "Lyhyt aikaväli" :sisalto [lyhytsuodatin]}
                                      2 {:auki    (not (= :live @tiedot/valittu-aikasuodatin))
                                         :otsikko "Pitkä aikaväli" :sisalto [pitkasuodatin]}}))

(defonce aikasuodattimet [harja.ui.yleiset/haitari aikasuodattimet-rivit {:vain-yksi-auki? true
                                                                          :aina-joku-auki? true}])

(defn muut-suodattimet []
  [kentat/tee-kentta {:tyyppi      :boolean-group
                      :vaihtoehdot [:toimenpidepyynnot ;; FIXME: formatteri, tai tämän Ö:t muuttuu O:ksi UI:ssa...
                                    :kyselyt
                                    :tiedoitukset
                                    :turvallisuuspoikkeamat
                                    :tarkastukset
                                    :havainnot
                                    :onnettomuudet
                                    :paikkaustyot   ;; ... ja näiden kahden
                                    :paallystystyot]}

   (r/wrap
    (into #{}
          (keep identity)
          [(when @tiedot/hae-toimenpidepyynnot? :toimenpidepyynnot)
           (when @tiedot/hae-kyselyt? :kyselyt)
           (when @tiedot/hae-tiedoitukset? :tiedoitukset)
           (when @tiedot/hae-turvallisuuspoikkeamat? :turvallisuuspoikkeamat)
           (when @tiedot/hae-tarkastukset? :tarkastukset)
           (when @tiedot/hae-havainnot? :havainnot)
           (when @tiedot/hae-onnettomuudet? :onnettomuudet)
           (when @tiedot/hae-paikkaustyot? :paikkaustyot)
           (when @tiedot/hae-paallystystyot? :paallystystyot)])

    (fn [uusi]
      (reset! tiedot/hae-toimenpidepyynnot? (:toimenpidepyynnot uusi))
      (reset! tiedot/hae-kyselyt? (:kyselyt uusi))
      (reset! tiedot/hae-tiedoitukset? (:tiedoitukset uusi))
      (reset! tiedot/hae-turvallisuuspoikkeamat? (:turvallisuuspoikkeamat uusi))
      (reset! tiedot/hae-tarkastukset? (:tarkastukset uusi))
      (reset! tiedot/hae-onnettomuudet? (:onnettomuudet uusi))
      (reset! tiedot/hae-havainnot? (:havainnot uusi))
      (reset! tiedot/hae-paikkaustyot? (:paikkaustyot uusi))
      (reset! tiedot/hae-paallystystyot? (:paallystystyot uusi))))])

(defn toteuma-suodattimet []
  [kentat/tee-kentta {:tyyppi      :boolean-group
                      :vaihtoehdot  tiedot/+toteumatyypit+}
   tiedot/haettavat-toteumatyypit])


(defonce toteumat-rivit (atom {1 {:auki false :otsikko "Toteumat" :sisalto [toteuma-suodattimet]}
                               2 {:auki false :otsikko "Muut" :sisalto [muut-suodattimet]}}))

(defonce toteumat [harja.ui.yleiset/haitari toteumat-rivit {:otsikko "Muut suodattimet"}])

(defn suodattimet []
  [:span
   [nayta-hallinnolliset-tiedot]
   aikasuodattimet
   toteumat])

(defonce hallintapaneeli (atom {1 {:auki false :otsikko "Esimerkki" :sisalto [suodattimet]}}))

(defn historiakuva []
  (komp/luo {:component-will-mount (fn [_]
                                     (kartta/aseta-yleiset-kontrollit [harja.ui.yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true}]))
             :component-will-unmount (fn [_]
                                       (kartta/tyhjenna-yleiset-kontrollit))}
    (komp/lippu tiedot/nakymassa? tiedot/taso-historiakuva)
    (fn []
      (reaction (reset! tiedot/valittu-aikasuodatin (if (get-in @aikasuodattimet-rivit [1 :auki])
                                                      :lyhyt
                                                      :pitka)))
      [:span])))
