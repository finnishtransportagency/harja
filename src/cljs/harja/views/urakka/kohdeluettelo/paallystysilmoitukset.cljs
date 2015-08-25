(ns harja.views.urakka.kohdeluettelo.paallystysilmoitukset
  "Urakan kohdeluettelon toteumat"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.domain.paallystys.pot :as pot]

            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.kohdeluettelo.paallystys :as paallystys]
            [harja.domain.roolit :as roolit]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.muokkauslukko :as lukko])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce toteumarivit (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                         [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                         nakymassa? @paallystys/paallystysilmoitukset-nakymassa?]
                                        (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                          (log "PÄÄ Haetaan päällystystoteumat.")
                                          (paallystys/hae-paallystystoteumat valittu-urakka-id valittu-sopimus-id))))

(def lomake-lukittu-muokkaukselta? (reaction (let [_ @lukko/nykyinen-lukko]
                                               (lukko/nykyinen-nakyma-lukittu?))))

(defn nayta-tila [tila]
  (case tila
    :aloitettu "Aloitettu"
    :valmis "Valmis"
    :lukittu "Lukittu"
    "-"))

(defn nayta-paatos [tila]
  (case tila
    :hyvaksytty [:span.paallystysilmoitus-hyvaksytty "Hyväksytty"]
    :hylatty [:span.paallystysilmoitus-hylatty "Hylätty"]
    ""))

(defonce lomakedata (atom nil)) ; Vastaa rakenteeltaan päällystysilmoitus-taulun sisältöä

(def urakkasopimuksen-mukainen-kokonaishinta (reaction (:kokonaishinta @lomakedata)))
(def muutokset-kokonaishintaan
  (reaction (let [lomakedata @lomakedata
                  tulos (pot/laske-muutokset-kokonaishintaan (get-in lomakedata [:ilmoitustiedot :tyot]))]
              (log "PÄÄ Muutokset kokonaishintaan laskettu: " tulos)
              tulos)))

(def toteuman-kokonaishinta (reaction (+ @urakkasopimuksen-mukainen-kokonaishinta @muutokset-kokonaishintaan)))


(tarkkaile! "PÄÄ Lomakedata: " lomakedata)

(defn yhteenveto []
  (let []
    [yleiset/taulukkotietonakyma {}
     "Urakkasopimuksen mukainen kokonaishinta: " (fmt/euro-opt (or @urakkasopimuksen-mukainen-kokonaishinta 0))
     "Muutokset kokonaishintaan ilman kustannustasomuutoksia: " (fmt/euro-opt (or @muutokset-kokonaishintaan 0))
     "Yhteensä: " (fmt/euro-opt @toteuman-kokonaishinta)]))

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :hyvaksytty "Hyväksytty"
    :hylatty "Hylätty"))

(defn laske-tien-pituus [tie]
  (- (:let tie) (:losa tie)))

(defn kasittely
  "Ilmoituksen käsittelyosio, kun ilmoitus on valmis. Tilaaja voi muokata, urakoitsija voi tarkastella."
  [valmis-kasiteltavaksi?]
  (let [muokattava? (and
                     (roolit/roolissa? roolit/urakanvalvoja)
                     (not= (:tila @lomakedata) :lukittu)
                     (false? @lomake-lukittu-muokkaukselta?))
        paatostiedot-tekninen-osa (r/wrap {:paatos-tekninen            (:paatos_tekninen_osa @lomakedata)
                                           :perustelu-tekninen-osa     (:perustelu_tekninen_osa @lomakedata)
                                           :kasittelyaika-tekninen-osa (:kasittelyaika_tekninen_osa @lomakedata)}
                                          (fn [uusi-arvo]
                                            (swap! lomakedata
                                                   #(-> % 
                                                        (assoc :paatos_tekninen_osa (:paatos-tekninen uusi-arvo))
                                                        (assoc :perustelu_tekninen_osa (:perustelu-tekninen-osa uusi-arvo))
                                                        (assoc :kasittelyaika_tekninen_osa (:kasittelyaika-tekninen-osa uusi-arvo))))))
        paatostiedot-taloudellinen-osa (r/wrap {:paatos-taloudellinen            (:paatos_taloudellinen_osa @lomakedata)
                                                :perustelu-taloudellinen-osa     (:perustelu_taloudellinen_osa @lomakedata)
                                                :kasittelyaika-taloudellinen-osa (:kasittelyaika_taloudellinen_osa @lomakedata)}
                                               (fn [uusi-arvo]
                                                 (swap! lomakedata
                                                        #(-> %
                                                             (assoc :paatos_taloudellinen_osa (:paatos-taloudellinen uusi-arvo))
                                                             (assoc :perustelu_taloudellinen_osa (:perustelu-taloudellinen-osa uusi-arvo))
                                                             (assoc :kasittelyaika_taloudellinen_osa (:kasittelyaika-taloudellinen-osa uusi-arvo))))))]

    (when @valmis-kasiteltavaksi?
      [:div.pot-kasittely
       [:h3 "Käsittely"]
       [:h4 "Tekninen osa"]
       [lomake/lomake
        {:luokka   :horizontal
         :muokkaa! (fn [uusi]
                     (reset! paatostiedot-tekninen-osa uusi))
         :voi-muokata? muokattava?}
        [{:otsikko     "Käsitelty"
          :nimi        :kasittelyaika-tekninen-osa
          :tyyppi      :pvm
          :validoi     [[:ei-tyhja "Anna käsittelypäivämäärä"]]}

         {:otsikko       "Päätös"
          :nimi          :paatos-tekninen
          :tyyppi        :valinta
          :valinnat      [:hyvaksytty :hylatty]
          :validoi       [[:ei-tyhja "Anna päätös"]]
          :valinta-nayta #(if % (kuvaile-paatostyyppi %) (if muokattava? "- Valitse päätös -" "-"))
          :leveys-col    3}

         (when (:paatos-tekninen @paatostiedot-tekninen-osa)
           {:otsikko     "Selitys"
            :nimi        :perustelu-tekninen-osa
            :tyyppi      :text
            :koko        [60 3]
            :pituus-max  2048
            :leveys-col  6
            :validoi     [[:ei-tyhja "Anna päätöksen selitys"]]})]
        @paatostiedot-tekninen-osa]

       [:h4 "Taloudellinen osa"]
       [lomake/lomake
        {:luokka   :horizontal
         :muokkaa! (fn [uusi]
                     (reset! paatostiedot-taloudellinen-osa uusi))
         :voi-muokata? muokattava?}
        [{:otsikko     "Käsitelty"
          :nimi        :kasittelyaika-taloudellinen-osa
          :tyyppi      :pvm
          :validoi     [[:ei-tyhja "Anna käsittelypäivämäärä"]]}

         {:otsikko       "Päätös"
          :nimi          :paatos-taloudellinen
          :tyyppi        :valinta
          :valinnat      [:hyvaksytty :hylatty]
          :validoi       [[:ei-tyhja "Anna päätös"]]
          :valinta-nayta #(if % (kuvaile-paatostyyppi %) (if muokattava? "- Valitse päätös -" "-"))
          :leveys-col    3}

         (when (:paatos-taloudellinen @paatostiedot-taloudellinen-osa)
           {:otsikko     "Selitys"
            :nimi        :perustelu-taloudellinen-osa
            :tyyppi      :text
            :koko        [60 3]
            :pituus-max  2048
            :leveys-col  6
            :validoi     [[:ei-tyhja "Anna päätöksen selitys"]]})]
        @paatostiedot-taloudellinen-osa]])))

(defn tallennus
  [valmis-tallennettavaksi?]
  (let [huomautusteksti (reaction (let [valmispvm-kohde (:valmispvm_kohde @lomakedata)
                                        valmispvm-paallystys (:valmispvm_paallystys @lomakedata)
                                        paatos-tekninen (:paatos_tekninen_osa @lomakedata)
                                        paatos-taloudellinen (:paatos_taloudellinen_osa @lomakedata)
                                        tila (:tila @lomakedata)]
                                    (cond (not (and valmispvm-kohde valmispvm-paallystys))
                                          "Valmistusmispäivämäärää ei ole annettu, ilmoitus tallennetaan keskeneräisenä."
                                          (and (not= :lukittu tila)
                                               (= :hyvaksytty paatos-tekninen)
                                               (= :hyvaksytty paatos-taloudellinen))
                                          "Ilmoituksen molemmat osat on hyväksytty, ilmoitus lukitaan tallennuksen yhteydessä."
                                          :else
                                          nil)))
        urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero]

    [:div.pot-tallennus
     (when @huomautusteksti
       (lomake/yleinen-huomautus @huomautusteksti))

     [harja.ui.napit/palvelinkutsu-nappi
      "Tallenna"
      #(let [lomake @lomakedata
             lahetettava-data (-> (grid/poista-idt lomake [:ilmoitustiedot :osoitteet])
                                  (grid/poista-idt [:ilmoitustiedot :kiviaines])
                                  (grid/poista-idt [:ilmoitustiedot :alustatoimet])
                                  (grid/poista-idt [:ilmoitustiedot :tyot]))]
        (log "PÄÄ Lomake-data: " (pr-str @lomakedata))
        (log "PÄÄ Lähetetään data " (pr-str lahetettava-data))
        (paallystys/tallenna-paallystysilmoitus urakka-id sopimus-id lahetettava-data))
      {:luokka       "nappi-ensisijainen"
       :disabled     (false? @valmis-tallennettavaksi?)
       :ikoni (ikonit/tallenna)
       :kun-onnistuu (fn [vastaus]
                       (log "PÄÄ Lomake tallennettu, vastaus: " (pr-str vastaus))
                       (reset! toteumarivit vastaus)
                       (reset! lomakedata nil))}]]))

(defn paallystysilmoituslomake []
  (let [alikohteet-virheet (atom {})
        paallystystoimenpide-virheet (atom {})
        alustalle-tehdyt-toimet-virheet (atom {})
        toteutuneet-maarat-virheet (atom {})
        kiviaines-virheet (atom {})

        valmis-tallennettavaksi? (reaction
                                   (let [alikohteet-virheet @alikohteet-virheet
                                         paallystystoimenpide-virheet @paallystystoimenpide-virheet
                                         alustalle-tehdyt-toimet-virheet @alustalle-tehdyt-toimet-virheet
                                         toteutuneet-maarat-virheet @toteutuneet-maarat-virheet
                                         kiviaines-virheet @kiviaines-virheet
                                         tila (:tila @lomakedata)
                                         lomake-lukittu-muokkaukselta? @lomake-lukittu-muokkaukselta?]
                                     (and
                                      (not (= tila :lukittu))
                                      (empty? alikohteet-virheet)
                                       (empty? paallystystoimenpide-virheet)
                                       (empty? alustalle-tehdyt-toimet-virheet)
                                       (empty? toteutuneet-maarat-virheet)
                                       (empty? kiviaines-virheet)
                                       (false? lomake-lukittu-muokkaukselta?))))
        valmis-kasiteltavaksi? (reaction
                                 (let [valmispvm-kohde (:valmispvm_kohde @lomakedata)
                                       tila (:tila @lomakedata)]
                                   (log "PÄÄ valmis käsi " (pr-str valmispvm-kohde) (pr-str tila))
                                   (and tila
                                        valmispvm-kohde
                                        (not (= tila :aloitettu))
                                        (not (nil? valmispvm-kohde)))))]

    (komp/luo
      (komp/lukko (lukko/muodosta-lukon-id "paallystysilmoitus" (:kohdenumero @lomakedata)))
      (fn []
        (let [lomakedata-nyt @lomakedata
              kohteen-tiedot (r/wrap {:aloituspvm     (:aloituspvm lomakedata-nyt)
                                      :valmispvm_kohde (:valmispvm_kohde lomakedata-nyt)
                                      :valmispvm_paallystys (:valmispvm_paallystys lomakedata-nyt)
                                      :takuupvm       (:takuupvm lomakedata-nyt)}
                                     (fn [uusi-arvo]
                                       (reset! lomakedata (-> (assoc lomakedata-nyt :aloituspvm (:aloituspvm uusi-arvo))
                                                              (assoc :valmispvm_kohde (:valmispvm_kohde uusi-arvo))
                                                              (assoc :valmispvm_paallystys (:valmispvm_paallystys uusi-arvo))
                                                              (assoc :takuupvm (:takuupvm uusi-arvo))
                                                              (assoc :hinta (:hinta uusi-arvo))))))
              
                                        ; Sisältää päällystystoimenpiteen tiedot, koska one-to-one -suhde.
              toteutuneet-osoitteet
              (r/wrap (zipmap (iterate inc 1) (:osoitteet (:ilmoitustiedot lomakedata-nyt)))
                      (fn [uusi-arvo]
                        (let [tie (some :tie (vals uusi-arvo))]
                          (reset! lomakedata
                                  (assoc-in lomakedata-nyt [:ilmoitustiedot :osoitteet]
                                            (mapv (fn [rivi]
                                                    (assoc rivi :tie tie))
                                                  (grid/filteroi-uudet-poistetut uusi-arvo)))))))
              
                                        ; Kiviaines sisältää sideaineen, koska one-to-one -suhde
              kiviaines
              (r/wrap (zipmap (iterate inc 1) (:kiviaines (:ilmoitustiedot lomakedata-nyt)))
                      (fn [uusi-arvo] (reset! lomakedata
                                              (assoc-in @lomakedata [:ilmoitustiedot :kiviaines] (grid/filteroi-uudet-poistetut uusi-arvo)))))
              alustalle-tehdyt-toimet
              (r/wrap (zipmap (iterate inc 1) (:alustatoimet (:ilmoitustiedot lomakedata-nyt)))
                      (fn [uusi-arvo] (reset! lomakedata
                                              (assoc-in @lomakedata [:ilmoitustiedot :alustatoimet] (grid/filteroi-uudet-poistetut uusi-arvo)))))
              toteutuneet-maarat
              (r/wrap (zipmap (iterate inc 1) (:tyot (:ilmoitustiedot lomakedata-nyt)))
                      (fn [uusi-arvo] (reset! lomakedata
                                              (assoc-in lomakedata-nyt [:ilmoitustiedot :tyot] (grid/filteroi-uudet-poistetut uusi-arvo)))))]
          [:div.paallystysilmoituslomake

           [:button.nappi-toissijainen {:on-click #(reset! lomakedata nil)}
            (ikonit/chevron-left) " Takaisin ilmoitusluetteloon"]

           (when @lomake-lukittu-muokkaukselta?
             (lomake/lomake-lukittu-huomautus @lukko/nykyinen-lukko))

           [:h2 "Päällystysilmoitus"]

           [:div.row
            [:div.col-md-6
             [:h3 "Perustiedot"]
             [lomake/lomake {:luokka   :horizontal
                             :voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                                (false? @lomake-lukittu-muokkaukselta?))
                             :muokkaa! (fn [uusi]
                                         (log "PÄÄ Muokataan kohteen tietoja: " (pr-str uusi))
                                         (swap! lomakedata merge uusi))}
              [{:otsikko "Kohde" :nimi :kohde :hae (fn [_] (str "#" (:kohdenumero lomakedata-nyt) " " (:kohdenimi lomakedata-nyt))) :muokattava? (constantly false)}
               {:otsikko "Työ aloitettu" :nimi :aloituspvm :tyyppi :pvm}
               {:otsikko "Päällystys valmistunut" :nimi :valmispvm_paallystys :tyyppi :pvm}
               {:otsikko "Kohde valmistunut" :nimi :valmispvm_kohde
                :vihje   (when (and
                                (:valmispvm_paallystys lomakedata-nyt)
                                (:valmispvm_kohde lomakedata-nyt)
                                (= :aloitettu (:tila lomakedata-nyt)))
                           "Kohteen valmistumispäivämäärä annettu, ilmoitus tallennetaan valmiina urakanvalvojan käsiteltäväksi.")
                :tyyppi  :pvm :validoi [[:pvm-annettu-toisen-jalkeen :valmispvm_paallystys "Kohdetta ei voi merkitä valmistuneeksi ennen kuin päällystys on valmistunut."]]}
               {:otsikko "Takuupvm" :nimi :takuupvm :tyyppi :pvm}
               {:otsikko "Toteutunut hinta" :nimi :hinta :tyyppi :numero :leveys-col 2 :hae #(fmt/euro-opt @toteuman-kokonaishinta) :muokattava? (constantly false)}
               (when (or (= :valmis (:tila lomakedata-nyt))
                         (= :lukittu (:tila lomakedata-nyt)))
                 {:otsikko     "Kommentit" :nimi :kommentit
                  :komponentti [kommentit/kommentit {:voi-kommentoida? true
                                                     :voi-liittaa      false
                                                     :leveys-col       40
                                                     :placeholder      "Kirjoita kommentti..."
                                                     :uusi-kommentti   (r/wrap (:uusi-kommentti lomakedata-nyt)
                                                                               #(swap! lomakedata assoc :uusi-kommentti %))}
                                (:kommentit lomakedata-nyt)]})
               ]
              @kohteen-tiedot]]

            [:div.col-md-6
             (kasittely valmis-kasiteltavaksi?)]]

           [:fieldset.lomake-osa
            [:legend "Tekninen osa"]

            [grid/muokkaus-grid
             {:otsikko      "Päällystetyt tierekisteriosoitteet"
              :tunniste     :tie
              :voi-muokata? (do
                              (log "PÄÄ tila " (pr-str (:tila lomakedata-nyt)) " Päätös tekninen: " (pr-str (:paatos_tekninen_osa lomakedata-nyt)))
                              (and (not= :lukittu (:tila lomakedata-nyt))
                                   (not= :hyvaksytty (:paatos_tekninen_osa lomakedata-nyt))
                                   (false? @lomake-lukittu-muokkaukselta?)))
              :virheet alikohteet-virheet 
              :rivinumerot? true
              :uusi-id (inc (count @toteutuneet-osoitteet))}
             [{:otsikko     "Tie#" :nimi :tie :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]
               :muokattava? (fn [rivi index] (if (> index 0) false true))}
              {:otsikko       "Ajorata"
               :nimi          :ajorata
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta #(if % (:nimi %) "- Valitse ajorata -")
               :valinnat      pot/+ajoradat+
               :leveys        "20%"
               :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Suunta"
               :nimi          :suunta
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta #(if % (:nimi %) "- Valitse suunta -")
               :valinnat      pot/+suunnat+
               :leveys        "20%"
               :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Kaista"
               :nimi          :kaista
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta #(if % (:nimi %) "- Valitse kaista -")
               :valinnat      pot/+kaistat+
               :leveys        "20%"
               :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Alkutieosa" :nimi :aosa :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Alkuetäisyys" :nimi :aet :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Lopputieosa" :nimi :losa :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Loppuetäisyys" :nimi :let :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (laske-tien-pituus rivi))}]
             toteutuneet-osoitteet]

            [grid/muokkaus-grid
             {:otsikko      "Päällystystoimenpiteen tiedot"
              :validoi-aina? true
              :voi-lisata?  false
              :voi-kumota?  false
              :voi-poistaa? (constantly false)
              :voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                 (not= :hyvaksytty (:paatos_tekninen_osa lomakedata-nyt))
                                 (false? @lomake-lukittu-muokkaukselta?))
              :virheet paallystystoimenpide-virheet
              :rivinumerot? true}
             [{:otsikko       "Päällyste"
               :nimi          :paallystetyyppi
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta (fn [rivi]
                                (if rivi
                                  (str (:lyhenne rivi)  " - " (:nimi rivi))
                                  "- Valitse päällyste -"))
               :valinnat      pot/+paallystetyypit+
               :leveys        "30%"
               :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Raekoko" :nimi :raekoko :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Massa (kg/m2)" :nimi :massa :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "RC-%" :nimi :rc% :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Pääl. työmenetelmä"
               :nimi          :tyomenetelma
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta (fn [rivi]
                                (if rivi
                                  (str (:lyhenne rivi)  " - " (:nimi rivi))
                                  "- Valitse menetelmä -"))
               :valinnat      pot/+tyomenetelmat+
               :leveys        "30%"
               :validoi       [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Leveys (m)" :nimi :leveys :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Massa (kg/m2)" :nimi :massamaara :leveys "15%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Pinta-ala (m2)" :nimi :pinta-ala :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Edellinen päällyste"
               :nimi          :edellinen-paallystetyyppi
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta (fn [rivi]
                                (if rivi
                                  (str (:lyhenne rivi)  " - " (:nimi rivi))
                                  "- Valitse päällyste -"))
               :valinnat      pot/+paallystetyypit+
               :leveys        "30%"
               :validoi       [[:ei-tyhja "Tieto puuttuu"]]}]
             toteutuneet-osoitteet]

            [grid/muokkaus-grid
             {:otsikko      "Kiviaines ja sideaine"
              :voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                 (not= :hyvaksytty (:paatos_tekninen_osa lomakedata-nyt))
                                 (false? @lomake-lukittu-muokkaukselta?))
              :virheet kiviaines-virheet
              :uusi-id (inc (count @kiviaines))}
             [{:otsikko "Kiviaines-esiintymä" :nimi :esiintyma :tyyppi :string :pituus-max 256 :leveys "30%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "KM-arvo" :nimi :km-arvo :tyyppi :string :pituus-max 256 :leveys "20%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Muotoarvo" :nimi :muotoarvo :tyyppi :string :pituus-max 256 :leveys "20%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Sideaine-tyyppi" :nimi :sideainetyyppi :leveys "30%" :tyyppi :string :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Pitoisuus" :nimi :pitoisuus :leveys "20%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Lisäaineet" :nimi :lisaaineet :leveys "20%" :tyyppi :string :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"]]}]
             kiviaines]

            [grid/muokkaus-grid
             {:otsikko      "Alustalle tehdyt toimet"
              :voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                 (not= :hyvaksytty (:paatos_tekninen_osa lomakedata-nyt))
                                 (false? @lomake-lukittu-muokkaukselta?))
              :uusi-id (inc (count @alustalle-tehdyt-toimet))
              :virheet alustalle-tehdyt-toimet-virheet}
             [{:otsikko "Alkutieosa" :nimi :aosa :tyyppi :numero :leveys "10%" :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Alkuetäisyys" :nimi :aet :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Lopputieosa" :nimi :losa :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Loppuetäisyys" :nimi :let :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (laske-tien-pituus rivi))}
              {:otsikko       "Käsittelymenetelmä"
               :nimi          :kasittelymenetelma
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta (fn [rivi]
                                (if rivi
                                  (str (:lyhenne rivi)  " - " (:nimi rivi))
                                  "- Valitse menetelmä -"))
               :valinnat      pot/+alustamenetelmat+
               :leveys        "30%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Käsittelypaks. (cm)" :nimi :paksuus :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Verkkotyyppi"
               :nimi          :verkkotyyppi
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta #(if % (:nimi %) "- Valitse verkkotyyppi -")
               :valinnat      pot/+verkkotyypit+
               :leveys        "30%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Tekninen toimenpide"
               :nimi          :tekninen-toimenpide
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta #(if % (:nimi %) "- Valitse toimenpide -")
               :valinnat      pot/+tekniset-toimenpiteet+
               :leveys        "30%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}]
             alustalle-tehdyt-toimet]]

           [:fieldset.lomake-osa
            [:legend "Taloudellinen osa"]

            [grid/muokkaus-grid
             {:otsikko "Toteutuneet määrät"
              :voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                 (not= :hyvaksytty (:paatos_taloudellinen_osa lomakedata-nyt))
                                 (false? @lomake-lukittu-muokkaukselta?))
              :validoi-aina? true
              :uusi-id (inc (count @toteutuneet-maarat))
              :virheet toteutuneet-maarat-virheet}
             [{:otsikko       "Päällystetyön tyyppi"
               :nimi          :tyyppi
               :tyyppi        :valinta
               :valinta-arvo  :avain
               :valinta-nayta #(if % (:nimi %) "- Valitse työ -")
               :valinnat      pot/+paallystystyon-tyypit+
               :leveys        "30%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Työ" :nimi :tyo :tyyppi :string :leveys "30%" :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Yks." :nimi :yksikko :tyyppi :string :leveys "10%" :pituus-max 20 :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Tilattu määrä" :nimi :tilattu-maara :tyyppi :numero :leveys "15%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Toteutunut määrä" :nimi :toteutunut-maara :leveys "15%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Ero" :nimi :ero :leveys "15%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (- (:toteutunut-maara rivi) (:tilattu-maara rivi)))}
              {:otsikko "Yks.hinta" :nimi :yksikkohinta :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Muutos hintaan" :nimi :muutos-hintaan :leveys "15%" :muokattava? (constantly false) :tyyppi :numero :hae (fn [rivi] (* (- (:toteutunut-maara rivi) (:tilattu-maara rivi)) (:yksikkohinta rivi)))}]
             toteutuneet-maarat]]

           (yhteenveto)
           (tallennus valmis-tallennettavaksi?)])))))

(defn ilmoitusluettelo
  []
  (komp/luo
    (fn []
      [:div
       [grid/grid
        {:otsikko  "Päällystysilmoitukset"
         :tyhja    (if (nil? @toteumarivit) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
         :tunniste :kohdenumero}
        [{:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
         {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "50%"}
         {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys "20%" :hae (fn [rivi]
                                                                                                           (nayta-tila (:tila rivi)))}
         {:otsikko "Päätös (tekninen)" :nimi :paatos_tekninen_osa :muokattava? (constantly false) :tyyppi :komponentti :leveys "20%" :komponentti (fn [rivi]
                                                                                                                                                    (nayta-paatos (:paatos_tekninen_osa rivi)))}
         {:otsikko "Päätös (taloudellinen)" :nimi :paatos_taloudellinen_osa :muokattava? (constantly false) :tyyppi :komponentti :leveys "20%" :komponentti (fn [rivi]
                                                                                                                                                              (nayta-paatos (:paatos_taloudellinen_osa rivi)))}
         {:otsikko     "Päällystysilmoitus" :nimi :paallystysilmoitus :muokattava? (constantly false) :leveys "25%" :tyyppi :komponentti
          :komponentti (fn [rivi] (if (:tila rivi) [:button.nappi-toissijainen.nappi-grid {:on-click #(go
                                                                                                       (let [urakka-id (:id @nav/valittu-urakka)
                                                                                                             [sopimus-id _] @u/valittu-sopimusnumero
                                                                                                             vastaus (<! (paallystys/hae-paallystysilmoitus-paallystyskohteella urakka-id sopimus-id (:paallystyskohde_id rivi)))]
                                                                                                         (log "PÄÄ Rivi: " (pr-str rivi))
                                                                                                         (log "PÄÄ Vastaus: " (pr-str vastaus))
                                                                                                         (if-not (k/virhe? vastaus)
                                                                                                           (reset! lomakedata (-> (assoc vastaus :paallystyskohde-id (:paallystyskohde_id rivi))
                                                                                                                                  (assoc :kokonaishinta (+ (:sopimuksen_mukaiset_tyot rivi)
                                                                                                                                                           (:arvonvahennykset rivi)
                                                                                                                                                           (:bitumi_indeksi rivi)
                                                                                                                                                           (:kaasuindeksi rivi))))))))}
                                                    [:span (ikonit/eye-open) " Päällystysilmoitus"]]
                                                   [:button.nappi-toissijainen.nappi-grid {:on-click #(reset! lomakedata {:kohdenumero        (:kohdenumero rivi)
                                                                                                                          :kohdenimi          (:nimi rivi)
                                                                                                                          :paallystyskohde-id (:paallystyskohde_id rivi)
                                                                                                                          :kokonaishinta      (+ (:sopimuksen_mukaiset_tyot rivi)
                                                                                                                                                 (:arvonvahennykset rivi)
                                                                                                                                                 (:bitumi_indeksi rivi)
                                                                                                                                                 (:kaasuindeksi rivi))})}
                                                    [:span "Aloita päällystysilmoitus"]]))}]
        (sort-by
          (fn [toteuma] (case (:tila toteuma)
                          :lukittu 0
                          :valmis 1
                          :aloitettu 3
                          4))
          @toteumarivit)]])))

(defn paallystysilmoitukset []
  (komp/luo
    (komp/lippu paallystys/paallystysilmoitukset-nakymassa?)

    (fn []
      (if @lomakedata
        [paallystysilmoituslomake]
        [ilmoitusluettelo]))))
