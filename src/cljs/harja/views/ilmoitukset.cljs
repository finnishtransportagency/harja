(ns harja.views.ilmoitukset
  "Harjan ilmoituksien pääsivu."
  (:require [reagent.core :refer [atom] :as r]

            [harja.tiedot.ilmoitukset :as tiedot]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.loki :refer [log]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka :as u]

            [bootstrap :as bs]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [clojure.string :refer [capitalize]]))

(defn pollauksen-merkki
  []
  [:span " Automaattinen päivittäminen päällä."])

(defn urakan-sivulle-nappi
  [ilmoitus]
  (when (and (:urakka ilmoitus) (:hallintayksikko ilmoitus))
    [:button.nappi-toissijainen
     {:on-click (fn [e]
                  (.stopPropagation e)
                  (reset! nav/valittu-hallintayksikko-id (:hallintayksikko ilmoitus))
                  (reset! nav/valittu-urakka-id (:urakka ilmoitus))
                  (reset! nav/sivu :urakat))}
     "Urakan sivulle"]))

(defn nayta-tierekisteriosoite
  [tr]
  (if tr
    (str "Tie " (:numero tr) " / " (:alkuosa tr) " / " (:alkuetaisyys tr) " / " (:loppuosa tr) " / " (:loppuetaisyys tr))

    (str "Ei tierekisteriosoitetta")))

(defn nayta-henkilo
  "Palauttaa merkkijonon mallia 'Etunimi Sukunimi, Organisaatio Y1234'"
  [henkilo]
  (when henkilo
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
     (:ytunnus henkilo))))

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
     "Kuittaaja: " (nayta-henkilo (:kuittaaja kuittaus))
     "Puhelinnumero: " (parsi-puhelinnumero (:kuittaaja kuittaus))
     "Sähköposti: " (get-in kuittaus [:kuittaaja :sahkoposti])]
    ;;[:br]
    ;; Otettu pois 11.8.2015 asiakkaan kommenttien perusteella. Pelkäsivät että tämä siirtää vastuuta
    ;; pois urakoitsijalta, ja antaa tekosyyn syyttää aliurakoitsijaa.
    ;; Jätin tämän tänne, koska voihan olla että mieli esim. käyttäjätesteissä muuttuu..
    #_[yleiset/tietoja {}
     "Käsittelijä: " (nayta-henkilo (:kasittelija kuittaus))
     "Puhelinnumero: " (parsi-puhelinnumero (:kasittelija kuittaus))
     "Sähköposti: " (get-in kuittaus [:kasittelija :sahkoposti])]]])

(defn luo-ilmoituksen-otsikko [ilm]
  (case (:ilmoitustyyppi ilm)
    :kysely "Tieliikennekeskukseen saapunut kysely"
    (capitalize (name (:ilmoitustyyppi ilm)))))

(defn ilmoituksen-tiedot
  []
  [:div
   [napit/takaisin "Takaisin" #(reset! tiedot/valittu-ilmoitus nil)]
   (urakan-sivulle-nappi @tiedot/valittu-ilmoitus)
   (when @tiedot/pollaus-id (pollauksen-merkki))
   [bs/panel {}
    (luo-ilmoituksen-otsikko @tiedot/valittu-ilmoitus)
    [:span
     [yleiset/tietoja {}
      "Ilmoitettu: " (pvm/pvm-aika-sek (:ilmoitettu @tiedot/valittu-ilmoitus))
      "Sijainti: " (nayta-tierekisteriosoite (:tr @tiedot/valittu-ilmoitus))
      "Lisätiedot: " (:vapaateksti @tiedot/valittu-ilmoitus)]

     [:br]
     [yleiset/tietoja {}
      "Ilmoittaja:" (let [henkilo (nayta-henkilo (:ilmoittaja @tiedot/valittu-ilmoitus))
                          tyyppi (capitalize (name (get-in @tiedot/valittu-ilmoitus [:ilmoittaja :tyyppi])))]
                      (if (and henkilo tyyppi)
                        (str henkilo ", " tyyppi)
                        (str (or henkilo tyyppi))))
      "Puhelinnumero: " (parsi-puhelinnumero (:ilmoittaja @tiedot/valittu-ilmoitus))
      "Sähköposti: " (get-in @tiedot/valittu-ilmoitus [:ilmoittaja :sahkoposti])]

     [:br]
     [yleiset/tietoja {}
      "Lähettäjä:" (nayta-henkilo (:lahettaja @tiedot/valittu-ilmoitus))
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
      
      [lomake/lomake
       {:luokka :horizontal
        :muokkaa! (fn [uusi]
                    (log "UUDET ILMOITUSVALINNAT: " (pr-str uusi))
                    (swap! tiedot/valinnat
                           (fn [vanha]
                             (if (not= (:hoitokausi vanha) (:hoitokausi uusi))
                               (assoc uusi
                                      :aikavali (:hoitokausi uusi))
                               uusi))))}

       [(when @nav/valittu-urakka
          {:nimi :hoitokausi
           :leveys-col 4
           :otsikko "Hoitokausi"
           :tyyppi :valinta
           :valinnat @u/valitun-urakan-hoitokaudet
           :valinta-nayta fmt/pvm-vali-opt})
        
        (lomake/ryhma {:ulkoasu :rivi :otsikko "Saapunut" :leveys-col 5}
                      {:nimi :saapunut-alkaen
                       :hae (comp first :aikavali)
                       :aseta #(assoc-in %1 [:aikavali 0] %2)
                       :otsikko "Alkaen"
                       :tyyppi :pvm}
                      
                      {:nimi :saapunut-paattyen
                       :otsikko "Päättyen"
                       :hae (comp second :aikavali)
                       :aseta #(assoc-in %1 [:aikavali 1] %2)
                       :tyyppi :pvm})

        {:nimi :hakuehto :otsikko "Hae ilmoituksia"
         :placeholder "Hae tekstillä..."
         :tyyppi :string
         :leveys-col 4}

        {:nimi :tilat :otsikko "Tilat"
         :tyyppi :boolean-group
         :vaihtoehdot [:suljetut :avoimet]}

        {:nimi :tyypit :otsikko "Tyyppi"
         :tyyppi :boolean-group
         :vaihtoehdot [:kysely :toimenpidepyynto :tiedoitus]
         :nayta-vaihtoehto #(case %
                              :kysely "Kysely"
                              :toimenpidepyynto "Toimenpidepyyntö"
                              :tiedoitus "Tiedoitus")}
        ]

       @tiedot/valinnat
       ]
      
      [:div.container
       
       [palvelinkutsu-nappi
        "Hae ilmoitukset"
        #(tiedot/hae-ilmoitukset)
        {:ikoni        (harja.ui.ikonit/search)
         :kun-onnistuu #(tiedot/aloita-pollaus)}]

       (when @tiedot/pollaus-id (pollauksen-merkki))


       [grid
        {:tyhja         (if @tiedot/haetut-ilmoitukset "Ei löytyneitä tietoja" [ajax-loader "Haetaan ilmoutuksia"])
         :rivi-klikattu #(reset! tiedot/valittu-ilmoitus %)}

        [{:otsikko "Ilmoitettu" :nimi :ilmoitettu :hae (comp pvm/pvm-aika :ilmoitettu) :leveys "20%"}
         {:otsikko "Tyyppi" :nimi :ilmoitustyyppi :hae (comp capitalize name :ilmoitustyyppi) :leveys "20%"}
         {:otsikko "Sijainti" :nimi :tierekisteri :hae #(nayta-tierekisteriosoite (:tr %)) :leveys "20%"}
         {:otsikko "Viimeisin kuittaus" :nimi :uusinkuittaus
          :hae     #(if (:uusinkuittaus %) (pvm/pvm-aika (:uusinkuittaus %)) "-") :leveys "20%"}
         {:otsikko "Vast." :tyyppi :boolean :nimi :suljettu :leveys "20%"}]

        @tiedot/haetut-ilmoitukset]]])))

(defn ilmoitukset []
  (komp/luo
    (komp/lippu tiedot/ilmoitusnakymassa? tiedot/taso-ilmoitukset nav/pakota-nakyviin?)

    (fn []
      (if @tiedot/valittu-ilmoitus
        [ilmoituksen-tiedot]
        [ilmoitusten-paanakyma]))))
