(ns harja.views.tilannekuva.historiakuva
  "Harjan tilannekuvan pääsivu."
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.tilannekuva.historiakuva :as tiedot]
            [harja.tiedot.tilannekuva.tilannekuva :as tilannekuva]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.loki :refer [log]]
            [harja.ui.yleiset :as yleiset]
            [harja.views.kartta :as kartta]
            [harja.views.tilannekuva.tilannekuvien-yhteiset-komponentit :refer [nayta-hallinnolliset-tiedot]]
            [harja.ui.kentat :as kentat]
            [reagent.core :as r]
            [harja.views.kartta :as kartta]

            [harja.views.tilannekuva.tilannekuva-popupit :refer [nayta-popup]])
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
            (fn [u] (swap! tiedot/pitkan-suodattimen-asetukset assoc :alku u)))]

   [kentat/tee-kentta {:tyyppi :pvm}
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
                      :valitse-kaikki?  true
                      :tyhjenna-kaikki? true
                      :vaihtoehdot      [:toimenpidepyynnot
                                         :kyselyt
                                         :tiedoitukset
                                         :turvallisuuspoikkeamat
                                         :tarkastukset
                                         :havainnot
                                         :paikkaustyot
                                         :paallystystyot]

                      :vaihtoehto-nayta {
                                         :toimenpidepyynnot      "Toimenpidepyynnöt"
                                         :kyselyt                "Kyselyt"
                                         :tiedoitukset           "Tiedotukset"
                                         :turvallisuuspoikkeamat "Turvallisuuspoikkeamat"
                                         :tarkastukset           "Tarkastukset"
                                         :havainnot              "Havainnot"
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
            (when @tiedot/hae-paikkaustyot? :paikkaustyot)
            (when @tiedot/hae-paallystystyot? :paallystystyot)])

     (fn [uusi]
       (reset! tiedot/hae-toimenpidepyynnot? (:toimenpidepyynnot uusi))
       (reset! tiedot/hae-kyselyt? (:kyselyt uusi))
       (reset! tiedot/hae-tiedoitukset? (:tiedoitukset uusi))
       (reset! tiedot/hae-turvallisuuspoikkeamat? (:turvallisuuspoikkeamat uusi))
       (reset! tiedot/hae-tarkastukset? (:tarkastukset uusi))
       (reset! tiedot/hae-havainnot? (:havainnot uusi))
       (reset! tiedot/hae-paikkaustyot? (:paikkaustyot uusi))
       (reset! tiedot/hae-paallystystyot? (:paallystystyot uusi))))])

(defn toteuma-suodattimet [toteumakoodit]
  [kentat/tee-kentta {:tyyppi           :boolean-group
                      :tyhjenna-kaikki? true
                      :valitse-kaikki?  true
                      :vaihtoehdot      (vec (sort (set (map :nimi toteumakoodit))))}
   (r/wrap
     (into #{} (keep (fn [[avain arvo]] (when arvo avain)) @tiedot/valitut-toteumatyypit))
     (fn [uusi]
       (swap! tiedot/valitut-toteumatyypit
              (fn [vanha]
                (let [avaimet (keys vanha)]
                  (zipmap avaimet
                          (map (fn [avain] (if (uusi avain) true false)) avaimet)))))))])

(defonce toteumat-rivit (reaction
                          (merge
                            (into {}
                                  (keep-indexed
                                    (fn [index asia] {index asia})
                                    (mapv (fn [emo] {:otsikko emo
                                                     :auki    false
                                                     :sisalto [toteuma-suodattimet (get @tiedot/naytettavat-toteumatyypit emo)]})
                                          (sort (keys @tiedot/naytettavat-toteumatyypit)))))
                            {(count (keys @tiedot/naytettavat-toteumatyypit))
                             {:otsikko "Muut" :auki false :sisalto [muut-suodattimet]}})))

(defonce toteumat [harja.ui.yleiset/haitari toteumat-rivit {:otsikko "Muut suodattimet"}])

(defn suodattimet []
  [:span
   [nayta-hallinnolliset-tiedot]
   aikasuodattimet
   toteumat])

(defonce hallintapaneeli (atom {1 {:auki true :otsikko "Historiakuva" :sisalto [suodattimet]}}))

(defn historiakuva []
  (komp/luo
    (komp/ulos (paivita-periodisesti tiedot/asioiden-haku 60000)) ;1min
    (komp/kuuntelija [:toteuma-klikattu :reittipiste-klikattu :ilmoitus-klikattu
                      :havainto-klikattu :tarkastus-klikattu :turvallisuuspoikkeama-klikattu
                      :paallystyskohde-klikattu :paikkaustoteuma-klikattu] #(nayta-popup %2))
    {:component-will-mount   (fn [_]
                               (kartta/aseta-yleiset-kontrollit
                                 [yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true}]))
     :component-will-unmount (fn [_]
                               (kartta/tyhjenna-yleiset-kontrollit)
                               (kartta/poista-popup!))}
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-historiakuva)
    (fn []
      (run! (reset! tiedot/valittu-aikasuodatin (if (get-in @aikasuodattimet-rivit [1 :auki])
                                                  :lyhyt
                                                  :pitka)))
      nil)))