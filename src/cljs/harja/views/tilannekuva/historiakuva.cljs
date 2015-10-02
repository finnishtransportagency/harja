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
            [harja.pvm :as pvm]
            [harja.tiedot.ilmoitukset :as ilmoitukset])
  (:require-macros [reagent.ratom :refer [reaction run!]]))

(defn lyhytsuodatin []
  [kentat/tee-kentta {:tyyppi     :aikavalitsin
                      :kellonaika {
                                   :valinnat ["00:00" "06:00" "12:00" "18:00"]
                                   }}
   tiedot/lyhyen-suodattimen-asetukset])

(def popupien-putsaus
  (run! (when (= :historiakuva @tilannekuva/valittu-valilehti)
          (log "välilehti vaihtui historiakuvaan")
          (kartta/poista-popup!))))

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

(defmulti nayta-popup :aihe)

(defmethod nayta-popup :toteuma-klikattu [tapahtuma]
  (log "toteuma-klikattu")
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
                          [:p "Lisätieto: " (:lisatieto tapahtuma)])]))


(defmethod nayta-popup :reittipiste-klikattu [tapahtuma]
  (log "reittipiste-klikattu")
  (kartta/nayta-popup! (get-in tapahtuma [:sijainti :coordinates])
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
                           [:p "Määrä: " (get-in tapahtuma [:materiaali :maara])]])]))

(defmethod nayta-popup :ilmoitus-klikattu [tapahtuma]
  (log "ilmoitus-klikattu")
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
                                                           (dissoc asia :type :alue))]
                                              (reset! nav/sivu :ilmoitukset)
                                              (reset! ilmoitukset/haetut-ilmoitukset
                                                      (map putsaa (filter
                                                                    (fn [asia] (= (:type asia) :ilmoitus))
                                                                    @tiedot/historiakuvan-asiat-kartalla)))
                                              (reset! ilmoitukset/valittu-ilmoitus (putsaa tapahtuma))
                                              ))}
                         "Siirry ilmoitusnäkymään"]]))

(defn historiakuva []
  (komp/luo
    (komp/ulos (paivita-periodisesti tiedot/asioiden-haku 60000)) ;1min
    (komp/kuuntelija [:toteuma-klikattu :reittipiste-klikattu :ilmoitus-klikattu] #(nayta-popup %2))
    {:component-will-mount   (fn [_]
                               (kartta/aseta-yleiset-kontrollit
                                 [yleiset/haitari hallintapaneeli {:piiloita-kun-kiinni? true}]))
     :component-will-unmount (fn [_]
                               (kartta/tyhjenna-yleiset-kontrollit))}
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-historiakuva)
    (fn []
      (run! (reset! tiedot/valittu-aikasuodatin (if (get-in @aikasuodattimet-rivit [1 :auki])
                                                  :lyhyt
                                                  :pitka)))
      nil)))