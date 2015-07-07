(ns harja.views.ilmoitukset
  "Harjan ilmoituksien pääsivu."
  (:require [reagent.core :refer [atom] :as r]

            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log]]
            [harja.ui.napit :refer [palvelinkutsu-nappi]]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]

            [harja.tiedot.urakka :as u]

            [bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [clojure.string :refer [capitalize]]))

(defn urakan-sivulle-nappi
  []
  (when @tiedot/valittu-urakka
    [:button.nappi-toissijainen
     {:on-click #(reset! nav/sivu :urakat)}
     "Urakan sivulle"]))

(defn parsi-tierekisteri
  [tr]
  (if tr
    (str "Tie " (:numero tr) " / " (:alkuosa tr) " / " (:alkuetaisyys tr) " / " (:loppuosa tr) " / " (:loppuetaisyys tr))

    (str "Ei tierekisteriosoitetta")))

(defn parsi-henkilo
  "Palauttaa merkkijonon mallia 'Etunimi Sukunimi, Organisaatio Y1234'"
  [henkilo]
  (let [tulos (when henkilo
                (str
                  (:etunimi henkilo)
                  (when (and (:etunimi henkilo) (:sukunimi henkilo)) " ")
                  (:sukunimi henkilo)
                  (when
                    (and
                      (or (:etunimi henkilo) (:sukunimi henkilo))
                      (or (:organisaatio henkilo) (:ytunnus henkilo)))

                    ", ")
                  (:organisaatio henkilo)
                  (when (and (:ytunnus henkilo) (:organisaatio henkilo)) " ")
                  (:ytunnus henkilo)))]
    (if (empty? tulos) nil tulos)))

(defn parsi-puhelinnumero
  [henkilo]
  (let [tp (:tyopuhelin henkilo)
        mp (:matkapuhelin henkilo)
        puh (:puhelinnumero henkilo)
        tulos (when henkilo
                (str
                  (if puh                                   ;; Jos puhelinnumero löytyy, käytetään vaan sitä
                    (str puh)
                    (when (or tp mp)
                      (if (and tp mp (not (= tp mp)))       ;; Jos on matkapuhelin JA työpuhelin, ja ne ovat erit..
                        (str tp " / " mp)

                        (str (or mp tp)))                   ;; Muuten käytetään vaan jompaa kumpaa

                      ))))]
    (if (empty? tulos) nil tulos)))

(defn parsi-yhteystiedot
  "Palauttaa merkkijonon, jossa on henkilön puhelinnumero(t) ja sähköposti.
  Ilmoituksen lähettäjällä on vain 'puhelinnumero', muilla voi olla matkapuhelin ja/tai työpuhelin."
  [henkilo]
  (let [puhelin (parsi-puhelinnumero henkilo)
        sp (:sahkoposti henkilo)
        tulos (when henkilo
                (str
                  (or puhelin)
                  (when (and puhelin sp) ", ")
                  (when sp (str sp))))]
    (if (empty? tulos) nil tulos)))

(defn kuittauksen-tiedot
  [kuittaus]
  [bs/panel {:class "kuittaus-viesti"}
   (capitalize (name (:kuittaustyyppi kuittaus)))
   [:span
    [yleiset/tietoja {}
     "Kuitattu: " (pvm/pvm-aika-sek (:kuitattu kuittaus))
     "Lisätiedot: " (:vapaateksti kuittaus)]
    [:br]
    [yleiset/tietoja {}
     "Kuittaaja: " (parsi-henkilo (:kuittaaja kuittaus))
     "Puhelinnumero: " (parsi-puhelinnumero (:kuittaaja kuittaus))
     "Sähköposti: " (get-in kuittaus [:kuittaaja :sahkoposti])]
    [:br]
    [yleiset/tietoja {}
     "Käsittelijä: " (parsi-henkilo (:kasittelija kuittaus))
     "Puhelinnumero: " (parsi-puhelinnumero (:kasittelija kuittaus))
     "Sähköposti: " (get-in kuittaus [:kasittelija :sahkoposti])]]])

(defn ilmoituksen-tiedot
  []
  [:div
   [:button.nappi-ensisijainen
    {:on-click #(reset! tiedot/valittu-ilmoitus nil)}
    "Palaa"]
   (urakan-sivulle-nappi)
   [bs/panel {}
    (capitalize (name (:ilmoitustyyppi @tiedot/valittu-ilmoitus)))
    [:span
     [yleiset/tietoja {}
      "Ilmoitettu: " (pvm/pvm-aika-sek (:ilmoitettu @tiedot/valittu-ilmoitus))
      "Sijainti: " (parsi-tierekisteri (:tr @tiedot/valittu-ilmoitus))
      "Lisätiedot: " (:vapaateksti @tiedot/valittu-ilmoitus)]

     [:br]
     [yleiset/tietoja {}
      "Ilmoittaja:" (let [henkilo (parsi-henkilo (:ilmoittaja @tiedot/valittu-ilmoitus))
                          tyyppi (capitalize (name (get-in @tiedot/valittu-ilmoitus [:ilmoittaja :tyyppi])))]
                      (if (and henkilo tyyppi)
                        (str henkilo ", " tyyppi)
                        (str (or henkilo tyyppi))))
      "Puhelinnumero: " (parsi-puhelinnumero (:ilmoittaja @tiedot/valittu-ilmoitus))
      "Sähköposti: " (get-in @tiedot/valittu-ilmoitus [:ilmoittaja :sahkoposti])]

     [:br]
     [yleiset/tietoja {}
      "Lähettäjä:" (parsi-henkilo (:lahettaja @tiedot/valittu-ilmoitus))
      "Puhelinnumero: " (parsi-puhelinnumero (:lahettaja @tiedot/valittu-ilmoitus))
      "Sähköposti: " (get-in @tiedot/valittu-ilmoitus [:lahettaja :sahkoposti])]]]

   (when-not (empty? (:kuittaukset @tiedot/valittu-ilmoitus))
     [bs/panel {}
      "Kuittaukset"
      [:div
       (for [kuittaus (:kuittaukset @tiedot/valittu-ilmoitus)]
         (kuittauksen-tiedot kuittaus))]])])

(defn ilmoitusten-paanakyma
  []
  (komp/luo
    (fn []
      [:span
       [:h3 "Ilmoitukset"]
       (urakan-sivulle-nappi)

       [:div

        [:div.col-md-12 [:label "Hae ilmoituksia: "] [tee-kentta {:tyyppi :string} tiedot/hakuehto]]

        (if @tiedot/valittu-urakka
          [:div.col-md-12
           [urakan-hoitokausi-ja-aikavali
            @tiedot/valittu-urakka
            (u/hoitokaudet @tiedot/valittu-urakka) u/valittu-hoitokausi u/valitse-hoitokausi!
            tiedot/valittu-aikavali]]

          [:div.row
           [:div.col-md-4
            [:label "Saapunut:"]
            [tee-kentta {:tyyppi :pvm :otsikko "Saapunut" :leveys "100%"}
             (r/wrap
               (first @tiedot/valittu-aikavali)
               (fn [uusi-arvo]
                 (reset! tiedot/valittu-aikavali
                         [uusi-arvo
                          (second @tiedot/valittu-aikavali)])))]]

           [:div.col-md-4
            [:label " \u2014 "]
            [tee-kentta {:tyyppi :pvm :leveys "100%"} (r/wrap
                                                        (second @tiedot/valittu-aikavali)
                                                        (fn [uusi-arvo]
                                                          (swap! tiedot/valittu-aikavali
                                                                 (fn [[alku _]]
                                                                   [alku uusi-arvo]))))]]])

        [:div.row
         [:div.col-md-2
          [:label "Tilat"
           (for [ehto @tiedot/valitut-tilat]
             ^{:key (str "Tilan " (name (first ehto)) " checkbox")}
             [tee-kentta
              {:tyyppi :boolean :otsikko (capitalize (name (first ehto)))}
              (r/wrap
                (second ehto)
                (fn [uusi-tila]
                  (reset! tiedot/valitut-tilat
                          (assoc @tiedot/valitut-tilat (first ehto) uusi-tila))))])]]

         [:div.col-md-2
          [:label "Ilmoituksen tyyppi"
           (for [ehto @tiedot/valitut-ilmoitusten-tyypit]
             ^{:key (str "Tyypin " (name (first ehto)) " checkbox")}
             [tee-kentta
              {:tyyppi :boolean :otsikko (capitalize (name (first ehto)))}
              (r/wrap
                (second ehto)
                (fn [uusi-tila]
                  (reset! tiedot/valitut-ilmoitusten-tyypit
                          (assoc @tiedot/valitut-ilmoitusten-tyypit (first ehto) uusi-tila))))])]]]]

       [palvelinkutsu-nappi
        "Hae ilmoitukset"
        #(tiedot/hae-ilmoitukset)
        {:ikoni        (harja.ui.ikonit/search)
         :kun-onnistuu #(tiedot/aloita-pollaus)}]


       [grid
        {:tyhja         (if @tiedot/haetut-ilmoitukset "Ei löytyneitä tietoja" [ajax-loader "Haetaan ilmoutuksia"])
         :rivi-klikattu #(reset! tiedot/valittu-ilmoitus %)}

        [{:otsikko "Ilmoitettu" :nimi :ilmoitettu :hae (comp pvm/pvm-aika :ilmoitettu)}
         {:otsikko "Tyyppi" :nimi :ilmoitustyyppi :hae (comp capitalize name :ilmoitustyyppi)}
         {:otsikko "Sijainti" :nimi :tierekisteri :hae #(parsi-tierekisteri (:tr %))}
         {:otsikko "Viimeisin kuittaus" :nimi :uusinkuittaus
          :hae     #(if (:uusinkuittaus %) (pvm/pvm-aika (:uusinkuittaus %)) "-")}
         {:otsikko "Vast." :tyyppi :boolean :nimi :suljettu}]

        @tiedot/haetut-ilmoitukset]])))

(defn ilmoitukset []
  (komp/luo
    (komp/lippu tiedot/ilmoitusnakymassa?)
    (komp/lippu tiedot/taso-ilmoitukset)

    (fn []
      (if @tiedot/valittu-ilmoitus
        [ilmoituksen-tiedot]
        [ilmoitusten-paanakyma]))))