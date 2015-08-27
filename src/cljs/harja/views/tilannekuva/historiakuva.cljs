(ns harja.views.tilannekuva.historiakuva
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.historiakuva :as tiedot]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :as yleiset]
            [harja.views.tilannekuva.tilannekuvien-yhteiset-komponentit :refer [nayta-hallinnolliset-tiedot]]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.views.kartta :as kartta]
            [clojure.string :as str]
            [harja.pvm :as pvm])
  (:require-macros [reagent.ratom :refer [reaction run!]]))


(defn lyhytsuodatin []
  [kentat/tee-kentta {:tyyppi     :aikavalitsin
                      :kellonaika {
                                   :valinnat ["00:00" "06:00" "12:00" "18:00"]
                                   }}
   tiedot/lyhyen-suodattimen-asetukset])

(defn pitkasuodatin []
  [:span
   [kentat/tee-kentta {:tyyppi :pvm :absoluuttinen? true}
    (r/wrap (:alku @tiedot/pitkan-suodattimen-asetukset)
            (fn [u] (swap! tiedot/pitkan-suodattimen-asetukset assoc :alku u)))]

   [kentat/tee-kentta {:tyyppi :pvm :absoluuttinen? true}
    (r/wrap (:loppu @tiedot/pitkan-suodattimen-asetukset)
            (fn [u] (swap! tiedot/pitkan-suodattimen-asetukset assoc :loppu u)))]])

(defonce aikasuodattimet-rivit (atom {1 {:auki    (= :lyhyt @tiedot/valittu-aikasuodatin)
                                         :otsikko "Lyhyt aikaväli" :sisalto [lyhytsuodatin]}
                                      2 {:auki    (not (= :lyhyt @tiedot/valittu-aikasuodatin))
                                         :otsikko "Pitkä aikaväli" :sisalto [pitkasuodatin]}}))

(defonce aikasuodattimet [harja.ui.yleiset/haitari aikasuodattimet-rivit {:vain-yksi-auki? true
                                                                          :aina-joku-auki? true}])

(defn muut-suodattimet []
  [kentat/tee-kentta {:tyyppi           :boolean-group
                      :vaihtoehdot      [:toimenpidepyynnot ;; FIXME: formatteri, tai tämän Ö:t muuttuu O:ksi UI:ssa...
                                         :kyselyt
                                         :tiedoitukset
                                         :turvallisuuspoikkeamat
                                         :tarkastukset
                                         :havainnot
                                         :onnettomuudet
                                         :paikkaustyot      ;; ... ja näiden kahden
                                         :paallystystyot]

                      :vaihtoehto-nayta {
                                         :toimenpidepyynnot      "Toimenpidepyynnöt"
                                         :kyselyt                "Kyselyt"
                                         :tiedoitukset           "Tiedoitukset"
                                         :turvallisuuspoikkeamat "Turvallisuuspoikkeamat"
                                         :tarkastukset           "Tarkastukset"
                                         :havainnot              "Havainnot"
                                         :onnettomuudet          "Onnettomuudet"
                                         :paikkaustyot           "Paikkaustyöt"
                                         :paallystystyot         "Päällystystyöt"
                                         }}

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
                      :vaihtoehdot @tiedot/naytettavat-toteumatyypit}
   tiedot/valitut-toteumatyypit])

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
  (komp/luo
    {:component-will-mount
     (fn [_]
       (reset! tiedot/nakymassa? true)
       (reset! tiedot/taso-historiakuva true))
     :component-will-unmount
     (fn [_]
       (reset! tiedot/nakymassa? false)
       (reset! tiedot/taso-historiakuva false)
       (tiedot/lopeta-asioiden-haku))}
    (fn []
      (run! (reset! tiedot/valittu-aikasuodatin (if (get-in @aikasuodattimet-rivit [1 :auki])
                                                  :lyhyt
                                                  :pitka)))

      [harja.ui.yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true
                                                 :leijuva?             300}])))

(tapahtumat/kuuntele! :toteuma-klikattu
                      (fn [tapahtuma]
                        (log (pr-str (dissoc tapahtuma :reittipisteet)))
                        (log (pr-str (:tehtava tapahtuma)))
                        (log (pr-str (get-in tapahtuma [:tehtava :id])))
                        (kartta/nayta-popup! (:klikkaus-koordinaatit tapahtuma)
                                             [:div.kartta-toteuma-popup
                                              [:p [:b "Toteuma"]]
                                              [:p "Aika: " (pvm/pvm (:alkanut tapahtuma)) "-" (pvm/pvm (:paattynyt tapahtuma))]
                                              (when (:suorittaja tapahtuma)
                                                [:span
                                                 [:p "Suorittaja: " (get-in tapahtuma [:suorittaja :nimi])]])
                                              (when-not (empty? (:tehtavat tapahtuma))
                                                (doall
                                                  (for [tehtava (:tehtavat tapahtuma)]
                                                        [:span
                                                         [:p "Toimenpide: " (:toimenpide tehtava)]
                                                         [:p "Määrä: " (:maara tehtava)]
                                                         [:p "Päivän hinta: " (:paivanhinta tehtava)]
                                                         [:p "Lisätieto: " (:lisatieto tehtava)]])))
                                              (when-not (empty? (:materiaalit tapahtuma))
                                                (doall
                                                  (for [toteuma (:materiaalit tapahtuma)]
                                                    [:span
                                                     [:p "Materiaali: " (get-in toteuma [:materiaali :nimi])]
                                                     [:p "Määrä: " (:maara toteuma)]])))
                                              (when (:lisatieto tapahtuma)
                                                [:p "Lisätieto: " (:lisatieto tapahtuma)])])))

(tapahtumat/kuuntele! :reittipiste-klikattu
                      (fn [tapahtuma]
                        (kartta/nayta-popup! (:sijainti tapahtuma)
                                             [:div.kartta-reittipiste-popup
                                              [:p [:b "Reittipiste"]]
                                              [:p "Aika: " (pvm/pvm (:aika tapahtuma))]
                                              (when (get-in tapahtuma [:tehtava :id])
                                                [:span
                                                 [:p "Toimenpide: " (get-in tapahtuma [:tehtava :toimenpide])]
                                                 [:p "Määrä: " (get-in tapahtuma [:tehtava :maara])]])
                                              (when (get-in tapahtuma [:materiaali :id])
                                                [:span
                                                 [:p "Materiaali: " (get-in tapahtuma [:materiaali :nimi])]
                                                 [:p "Määrä: " (get-in tapahtuma [:materiaali :maara])]])])))