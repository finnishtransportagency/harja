(ns harja.views.urakka.kohdeluettelo.paallystysilmoitukset
  "Urakan kohdeluettelon toteumat"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]

            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.yleiset :as yleiset]

            [harja.domain.paallystys.pot :as pot]
            [harja.domain.roolit :as roolit]

            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.kohdeluettelo.paallystys :refer [paallystystoteumat paallystysilmoitus-lomakedata] :as paallystys]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.muokkauslukko :as lukko]

            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as tapahtumat]
            [harja.views.kartta :as kartta]
            [harja.ui.tierekisteri :as tierekisteri])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))



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



(def urakkasopimuksen-mukainen-kokonaishinta (reaction (:kokonaishinta @paallystysilmoitus-lomakedata)))
(def muutokset-kokonaishintaan
  (reaction (let [lomakedata @paallystysilmoitus-lomakedata
                  tulos (pot/laske-muutokset-kokonaishintaan (get-in lomakedata [:ilmoitustiedot :tyot]))]
              (log "PÄÄ Muutokset kokonaishintaan laskettu: " tulos)
              tulos)))

(def toteuman-kokonaishinta (reaction (+ @urakkasopimuksen-mukainen-kokonaishinta @muutokset-kokonaishintaan)))


(tarkkaile! "PÄÄ Lomakedata: " paallystysilmoitus-lomakedata)

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



(defn kasittely
  "Ilmoituksen käsittelyosio, kun ilmoitus on valmis. Tilaaja voi muokata, urakoitsija voi tarkastella."
  [valmis-kasiteltavaksi?]
  (let [muokattava? (and
                     (roolit/roolissa? roolit/urakanvalvoja)
                     (not= (:tila @paallystysilmoitus-lomakedata) :lukittu)
                     (false? @lomake-lukittu-muokkaukselta?))
        paatostiedot-tekninen-osa (r/wrap {:paatos-tekninen            (:paatos_tekninen_osa @paallystysilmoitus-lomakedata)
                                           :perustelu-tekninen-osa     (:perustelu_tekninen_osa @paallystysilmoitus-lomakedata)
                                           :kasittelyaika-tekninen-osa (:kasittelyaika_tekninen_osa @paallystysilmoitus-lomakedata)}
                                          (fn [uusi-arvo]
                                            (swap! paallystysilmoitus-lomakedata
                                                   #(-> %
                                                        (assoc :paatos_tekninen_osa (:paatos-tekninen uusi-arvo))
                                                        (assoc :perustelu_tekninen_osa (:perustelu-tekninen-osa uusi-arvo))
                                                        (assoc :kasittelyaika_tekninen_osa (:kasittelyaika-tekninen-osa uusi-arvo))))))
        paatostiedot-taloudellinen-osa (r/wrap {:paatos-taloudellinen            (:paatos_taloudellinen_osa @paallystysilmoitus-lomakedata)
                                                :perustelu-taloudellinen-osa     (:perustelu_taloudellinen_osa @paallystysilmoitus-lomakedata)
                                                :kasittelyaika-taloudellinen-osa (:kasittelyaika_taloudellinen_osa @paallystysilmoitus-lomakedata)}
                                               (fn [uusi-arvo]
                                                 (swap! paallystysilmoitus-lomakedata
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
          :validoi     [[:ei-tyhja "Anna käsittelypäivämäärä"]
                        [:pvm-toisen-pvmn-jalkeen (:valmispvm_kohde @paallystysilmoitus-lomakedata) "Käsittely ei voi olla ennen valmistumista"]]}

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
          :validoi     [[:ei-tyhja "Anna käsittelypäivämäärä"]
                        [:pvm-toisen-pvmn-jalkeen (:valmispvm_kohde @paallystysilmoitus-lomakedata) "Käsittely ei voi olla ennen valmistumista"]]}

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
  (let [huomautusteksti (reaction (let [valmispvm-kohde (:valmispvm_kohde @paallystysilmoitus-lomakedata)
                                        valmispvm-paallystys (:valmispvm_paallystys @paallystysilmoitus-lomakedata)
                                        paatos-tekninen (:paatos_tekninen_osa @paallystysilmoitus-lomakedata)
                                        paatos-taloudellinen (:paatos_taloudellinen_osa @paallystysilmoitus-lomakedata)
                                        tila (:tila @paallystysilmoitus-lomakedata)]
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
      #(let [lomake @paallystysilmoitus-lomakedata
             lahetettava-data (-> (grid/poista-idt lomake [:ilmoitustiedot :osoitteet])
                                  (grid/poista-idt [:ilmoitustiedot :kiviaines])
                                  (grid/poista-idt [:ilmoitustiedot :alustatoimet])
                                  (grid/poista-idt [:ilmoitustiedot :tyot]))]
        (log "PÄÄ Lomake-data: " (pr-str @paallystysilmoitus-lomakedata))
        (log "PÄÄ Lähetetään data " (pr-str lahetettava-data))
        (paallystys/tallenna-paallystysilmoitus urakka-id sopimus-id lahetettava-data))
      {:luokka       "nappi-ensisijainen"
       :disabled     (false? @valmis-tallennettavaksi?)
       :ikoni (ikonit/tallenna)
       :kun-onnistuu (fn [vastaus]
                       (log "PÄÄ Lomake tallennettu, vastaus: " (pr-str vastaus))
                       (reset! paallystystoteumat vastaus)
                       (reset! paallystysilmoitus-lomakedata nil))}]]))

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
                                         tila (:tila @paallystysilmoitus-lomakedata)
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
                                 (let [valmispvm-kohde (:valmispvm_kohde @paallystysilmoitus-lomakedata)
                                       tila (:tila @paallystysilmoitus-lomakedata)]
                                   (log "PÄÄ valmis käsi " (pr-str valmispvm-kohde) (pr-str tila))
                                   (and tila
                                        valmispvm-kohde
                                        (not (= tila :aloitettu))
                                        (not (nil? valmispvm-kohde)))))]

    (komp/luo
      (komp/lukko (lukko/muodosta-lukon-id "paallystysilmoitus" (:kohdenumero @paallystysilmoitus-lomakedata)))
      (fn []
        (let [lomakedata-nyt @paallystysilmoitus-lomakedata
              kohteen-tiedot (r/wrap {:aloituspvm     (:aloituspvm lomakedata-nyt)
                                      :valmispvm_kohde (:valmispvm_kohde lomakedata-nyt)
                                      :valmispvm_paallystys (:valmispvm_paallystys lomakedata-nyt)
                                      :takuupvm       (:takuupvm lomakedata-nyt)}
                                     (fn [uusi-arvo]
                                       (reset! paallystysilmoitus-lomakedata (-> (assoc lomakedata-nyt :aloituspvm (:aloituspvm uusi-arvo))
                                                              (assoc :valmispvm_kohde (:valmispvm_kohde uusi-arvo))
                                                              (assoc :valmispvm_paallystys (:valmispvm_paallystys uusi-arvo))
                                                              (assoc :takuupvm (:takuupvm uusi-arvo))
                                                              (assoc :hinta (:hinta uusi-arvo))))))

                                        ; Sisältää päällystystoimenpiteen tiedot, koska one-to-one -suhde.
              toteutuneet-osoitteet
              (r/wrap (zipmap (iterate inc 1) (:osoitteet (:ilmoitustiedot lomakedata-nyt)))
                      (fn [uusi-arvo]
                        (let [tie (some :tie (vals uusi-arvo))]
                          (reset! paallystysilmoitus-lomakedata
                                  (assoc-in lomakedata-nyt [:ilmoitustiedot :osoitteet]
                                            (mapv (fn [rivi]
                                                    (assoc rivi :tie tie))
                                                  (grid/filteroi-uudet-poistetut uusi-arvo)))))))

                                        ; Kiviaines sisältää sideaineen, koska one-to-one -suhde
              kiviaines
              (r/wrap (zipmap (iterate inc 1) (:kiviaines (:ilmoitustiedot lomakedata-nyt)))
                      (fn [uusi-arvo] (reset! paallystysilmoitus-lomakedata
                                              (assoc-in @paallystysilmoitus-lomakedata [:ilmoitustiedot :kiviaines] (grid/filteroi-uudet-poistetut uusi-arvo)))))
              alustalle-tehdyt-toimet
              (r/wrap (zipmap (iterate inc 1) (:alustatoimet (:ilmoitustiedot lomakedata-nyt)))
                      (fn [uusi-arvo] (reset! paallystysilmoitus-lomakedata
                                              (assoc-in @paallystysilmoitus-lomakedata [:ilmoitustiedot :alustatoimet] (grid/filteroi-uudet-poistetut uusi-arvo)))))
              toteutuneet-maarat
              (r/wrap (zipmap (iterate inc 1) (:tyot (:ilmoitustiedot lomakedata-nyt)))
                      (fn [uusi-arvo] (reset! paallystysilmoitus-lomakedata
                                              (assoc-in lomakedata-nyt [:ilmoitustiedot :tyot] (grid/filteroi-uudet-poistetut uusi-arvo)))))]
          [:div.paallystysilmoituslomake

           [:button.nappi-toissijainen {:on-click #(reset! paallystysilmoitus-lomakedata nil)}
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
                                         (swap! paallystysilmoitus-lomakedata merge uusi))}
              [{:otsikko "Kohde" :nimi :kohde :hae (fn [_] (str "#" (:kohdenumero lomakedata-nyt) " " (:kohdenimi lomakedata-nyt))) :muokattava? (constantly false)}
               {:otsikko "Työ aloitettu" :nimi :aloituspvm :tyyppi :pvm}
               {:otsikko "Päällystys valmistunut" :nimi :valmispvm_paallystys :tyyppi :pvm}
               {:otsikko "Kohde valmistunut" :nimi :valmispvm_kohde
                :vihje   (when (and
                                (:valmispvm_paallystys lomakedata-nyt)
                                (:valmispvm_kohde lomakedata-nyt)
                                (= :aloitettu (:tila lomakedata-nyt)))
                           "Kohteen valmistumispäivämäärä annettu, ilmoitus tallennetaan valmiina urakanvalvojan käsiteltäväksi.")
                :tyyppi  :pvm :validoi [[:pvm-ei-annettu-ennen-toista :valmispvm_paallystys "Kohdetta ei voi merkitä valmistuneeksi ennen kuin päällystys on valmistunut."]]}
               {:otsikko "Takuupvm" :nimi :takuupvm :tyyppi :pvm}
               {:otsikko "Toteutunut hinta" :nimi :hinta :tyyppi :numero :leveys-col 2 :hae #(fmt/euro-opt @toteuman-kokonaishinta) :muokattava? (constantly false)}
               (when (or (= :valmis (:tila lomakedata-nyt))
                         (= :lukittu (:tila lomakedata-nyt)))
                 {:otsikko     "Kommentit" :nimi :kommentit
                  :komponentti [kommentit/kommentit {:voi-kommentoida? (not= :lukittu (:tila lomakedata-nyt))
                                                     :voi-liittaa      false
                                                     :leveys-col       40
                                                     :placeholder      "Kirjoita kommentti..."
                                                     :uusi-kommentti   (r/wrap (:uusi-kommentti lomakedata-nyt)
                                                                               #(swap! paallystysilmoitus-lomakedata assoc :uusi-kommentti %))}
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
              {:otsikko "Alku\u00ADtieosa" :nimi :aosa :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Alku\u00ADetäi\u00ADsyys" :nimi :aet :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Loppu\u00ADtieosa" :nimi :losa :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Loppu\u00ADetäi\u00ADsyys" :nimi :let :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (tierekisteri/laske-tien-pituus rivi))}]
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
              {:otsikko "Rae\u00ADkoko" :nimi :raekoko :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Massa (kg/m2)" :nimi :massa :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "RC-%" :nimi :rc% :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Pääll. työ\u00ADmenetelmä"
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
             [{:otsikko "Kiviaines\u00ADesiintymä" :nimi :esiintyma :tyyppi :string :pituus-max 256 :leveys "30%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "KM-arvo" :nimi :km-arvo :tyyppi :string :pituus-max 256 :leveys "20%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Muoto\u00ADarvo" :nimi :muotoarvo :tyyppi :string :pituus-max 256 :leveys "20%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Sideaine\u00ADtyyppi" :nimi :sideainetyyppi :leveys "30%" :tyyppi :string :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Pitoisuus" :nimi :pitoisuus :leveys "20%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Lisä\u00ADaineet" :nimi :lisaaineet :leveys "20%" :tyyppi :string :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"]]}]
             kiviaines]

            [grid/muokkaus-grid
             {:otsikko      "Alustalle tehdyt toimet"
              :voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                 (not= :hyvaksytty (:paatos_tekninen_osa lomakedata-nyt))
                                 (false? @lomake-lukittu-muokkaukselta?))
              :uusi-id (inc (count @alustalle-tehdyt-toimet))
              :virheet alustalle-tehdyt-toimet-virheet}
             [{:otsikko "Alku\u00ADtieosa" :nimi :aosa :tyyppi :numero :leveys "10%" :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Alku\u00ADetäisyys" :nimi :aet :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Loppu\u00ADtieosa" :nimi :losa :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Loppu\u00ADetäisyys" :nimi :let :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (tierekisteri/laske-tien-pituus rivi))}
              {:otsikko       "Käsittely\u00ADmenetelmä"
               :nimi          :kasittelymenetelma
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta (fn [rivi]
                                (if rivi
                                  (str (:lyhenne rivi)  " - " (:nimi rivi))
                                  "- Valitse menetelmä -"))
               :valinnat      pot/+alustamenetelmat+
               :leveys        "30%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Käsit\u00ADtely\u00ADpaks. (cm)" :nimi :paksuus :leveys "15%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Verkko\u00ADtyyppi"
               :nimi          :verkkotyyppi
               :tyyppi        :valinta
               :valinta-arvo  :koodi
               :valinta-nayta #(if % (:nimi %) "- Valitse verkkotyyppi -")
               :valinnat      pot/+verkkotyypit+
               :leveys        "25%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko       "Tekninen toimen\u00ADpide"
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
             [{:otsikko       "Päällyste\u00ADtyön tyyppi"
               :nimi          :tyyppi
               :tyyppi        :valinta
               :valinta-arvo  :avain
               :valinta-nayta #(if % (:nimi %) "- Valitse työ -")
               :valinnat      pot/+paallystystyon-tyypit+
               :leveys        "30%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Työ" :nimi :tyo :tyyppi :string :leveys "30%" :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Yks." :nimi :yksikko :tyyppi :string :leveys "10%" :pituus-max 20 :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Tilattu määrä" :nimi :tilattu-maara :tyyppi :numero :leveys "15%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Toteu\u00ADtunut määrä" :nimi :toteutunut-maara :leveys "15%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Ero" :nimi :ero :leveys "15%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (- (:toteutunut-maara rivi) (:tilattu-maara rivi)))}
              {:otsikko "Yks.\u00ADhinta" :nimi :yksikkohinta :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Muutos hintaan" :nimi :muutos-hintaan :leveys "15%" :muokattava? (constantly false) :tyyppi :numero :hae (fn [rivi] (* (- (:toteutunut-maara rivi) (:tilattu-maara rivi)) (:yksikkohinta rivi)))}]
             toteutuneet-maarat]]

           (yhteenveto)
           (tallennus valmis-tallennettavaksi?)])))))

(defn avaa-paallystysilmoitus [paallystyskohteen-id]
  (go
    (let [urakka-id (:id @nav/valittu-urakka)
          [sopimus-id _] @u/valittu-sopimusnumero
          vastaus (<! (paallystys/hae-paallystysilmoitus-paallystyskohteella urakka-id sopimus-id paallystyskohteen-id))]
      (log "Päällystysilmoitus kohteelle " paallystyskohteen-id " => " (pr-str vastaus))
      (if-not (k/virhe? vastaus)
        (reset! paallystysilmoitus-lomakedata vastaus)))))

(defn ilmoitusluettelo
  []
  (komp/luo
   (komp/kuuntelija :avaa-paallystysilmoitus
                    (fn [_ rivi]
                      (avaa-paallystysilmoitus (:paallystyskohde-id rivi))))
                                                     
                      
    (fn []
      [:div
       [grid/grid
        {:otsikko  "Päällystysilmoitukset"
         :tyhja    (if (nil? @paallystystoteumat) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
         :tunniste :kohdenumero}
        [{:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
         {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "50%"}
         {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys "20%"
          :hae (fn [rivi]
                 (nayta-tila (:tila rivi)))}
         {:otsikko "Päätös, tekninen" :nimi :paatos_tekninen_osa :muokattava? (constantly false) :tyyppi :komponentti :leveys "20%"
          :komponentti (fn [rivi]
                         (nayta-paatos (:paatos_tekninen_osa rivi)))}
         {:otsikko "Päätös, taloudel\u00ADlinen" :nimi :paatos_taloudellinen_osa :muokattava? (constantly false) :tyyppi :komponentti :leveys "20%"
          :komponentti (fn [rivi]
                         (nayta-paatos (:paatos_taloudellinen_osa rivi)))}
         {:otsikko     "Päällystys\u00ADilmoitus" :nimi :paallystysilmoitus :muokattava? (constantly false) :leveys "25%" :tyyppi :komponentti
          :komponentti (fn [rivi]
                         (if (:tila rivi)
                           [:button.nappi-toissijainen.nappi-grid
                            {:on-click #(avaa-paallystysilmoitus (:paallystyskohde_id rivi))}
                            [:span (ikonit/eye-open) " Päällystysilmoitus"]]
                           [:button.nappi-toissijainen.nappi-grid {:on-click #(avaa-paallystysilmoitus (:paallystyskohde_id rivi))}
                            [:span "Aloita päällystysilmoitus"]]))}]
        (sort-by
         (juxt (fn [toteuma] (case (:tila toteuma)
                               :lukittu 0
                                :valmis 1
                                :aloitettu 3
                                4))
                (fn [toteuma] (case (:paatos_tekninen_osa toteuma)
                                :hyvaksytty 0
                                :hylatty 1
                                3))
                (fn [toteuma] (case (:paatos_taloudellinen_osa toteuma)
                                :hyvaksytty 0
                                :hylatty 1
                                3)))
          @paallystystoteumat)]])))

(defn paallystysilmoitukset []
  (komp/luo
    (komp/lippu paallystys/paallystysilmoitukset-nakymassa?)

    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @paallystysilmoitus-lomakedata
         [paallystysilmoituslomake]
         [ilmoitusluettelo])])))
