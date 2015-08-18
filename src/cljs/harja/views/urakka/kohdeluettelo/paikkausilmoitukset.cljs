(ns harja.views.urakka.kohdeluettelo.paikkausilmoitukset
  "Urakan kohdeluettelon paikkausilmoitukset"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]

            [harja.tiedot.muokkauslukko :as lukko]

            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.ui.lomake :refer [lomake]]
            [harja.tiedot.urakka.kohdeluettelo.paikkaus :as paikkaus]
            [harja.domain.roolit :as roolit]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.yleiset :as yleiset]
            [harja.domain.paikkaus.minipot :as minipot]
            [harja.views.urakka.kohdeluettelo.paallystysilmoitukset :as paallystysilmoitukset])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(def lomakedata (atom nil)) ; Vastaa rakenteeltaan paikkausilmoitus-taulun sisältöä

(def lomake-lukittu-muokkaukselta? (reaction (let [_ @lukko/nykyinen-lukko]
                                               (lukko/nykyinen-nakyma-lukittu?))))

(defonce toteumarivit (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                   [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                   nakymassa? @paikkaus/paikkausilmoitukset-nakymassa?]
                                  (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                    (log "PAI Haetaan paikkausilmoitukset")
                                    (paikkaus/hae-paikkaustoteumat valittu-urakka-id valittu-sopimus-id))))

(defn kuvaile-paatostyyppi [paatos]
  (case paatos
    :hyvaksytty "Hyväksytty"
    :hylatty "Hylätty"))

(defn nayta-tila [tila]
  (case tila
    :aloitettu "Aloitettu"
    :valmis "Valmis"
    :lukittu "Lukittu"
    "-"))

(defn nayta-paatos [tila]
  (case tila
    :hyvaksytty [:span.paikkausilmoitus-hyvaksytty "Hyväksytty"]
    :hylatty [:span.paikkausilmoitus-hylatty "Hylätty"]
    ""))

(defn lisaa-suoritteet-tyhjaan-toteumaan [toteumat]
  (if (or (nil? toteumat) (empty? toteumat))
    (mapv
      (fn [tyo] {:suorite (:koodi tyo)})
      minipot/+paikkaustyot+)
    toteumat))


(defn laske-tyon-alv
  "Ottaa työn hinnan (esim 100) ja arvolisäveron (esim. 24) palauttaa työn hinnan alv:n kera"
  [tyon-hinta alv]
  (* tyon-hinta (+ (/ (double alv) 100) 1)))

(defn laske-kokonaishinta [tyot]
  (reduce +
          (map
            (fn [tyo] (* (:yks_hint_alv_0 tyo) (:maara tyo)))
            tyot)))

(defn kasittely
  "Ilmoituksen käsittelyosio, kun ilmoitus on valmis. Tilaaja voi muokata, urakoitsija voi tarkastella."
  [valmis-kasiteltavaksi?]
  (let [voi-muokata (constantly (and
                                  (roolit/roolissa? roolit/urakanvalvoja)
                                  (not= (:tila @lomakedata) :lukittu)
                                  (false? @lomake-lukittu-muokkaukselta?)))
        paatostiedot (r/wrap {:paatos        (:paatos @lomakedata)
                              :perustelu     (:perustelu @lomakedata)
                              :kasittelyaika (:kasittelyaika @lomakedata)}
                             (fn [uusi-arvo] (reset! lomakedata (-> (assoc @lomakedata :paatos (:paatos uusi-arvo))
                                                                    (assoc :perustelu (:perustelu uusi-arvo))
                                                                    (assoc :kasittelyaika (:kasittelyaika uusi-arvo))))))]
    (when @valmis-kasiteltavaksi?
      [:div.paikkausilmoitus-kasittely
       [:h3 "Käsittely"]
       [lomake/lomake
        {:luokka       :horizontal
         :muokkaa!     (fn [uusi]
                         (reset! paatostiedot uusi))
         :voi-muokata? (voi-muokata)}
        [{:otsikko "Käsitelty"
          :nimi    :kasittelyaika
          :tyyppi  :pvm
          :validoi [[:ei-tyhja "Anna käsittelypäivämäärä"]]}

         {:otsikko       "Päätös"
          :nimi          :paatos
          :tyyppi        :valinta
          :valinnat      [:hyvaksytty :hylatty]
          :validoi       [[:ei-tyhja "Anna päätös"]]
          :valinta-nayta #(if % (kuvaile-paatostyyppi %) (if (voi-muokata) "- Valitse päätös -" "-"))
          :leveys-col    3}

         (when (:paatos @paatostiedot)
           {:otsikko    "Selitys"
            :nimi       :perustelu
            :tyyppi     :text
            :koko       [60 3]
            :pituus-max 2048
            :leveys-col 6
            :validoi    [[:ei-tyhja "Anna päätöksen selitys"]]})]
        @paatostiedot]])))

(defn tallennus
  [valmis-tallennettavaksi?]
  (let [huomautusteksti (reaction (let [valmispvm-kohde (:valmispvm_kohde @lomakedata)
                                        valmispvm-paikkaus (:valmispvm_paikkaus @lomakedata)
                                        paatos (:paatos @lomakedata)
                                        tila (:tila @lomakedata)]
                                    (cond (not (and valmispvm-kohde valmispvm-paikkaus))
                                          "Valmistusmispäivämäärää ei ole annettu, ilmoitus tallennetaan keskeneräisenä."
                                          (and (not= :lukittu tila)
                                               (= :hyvaksytty paatos))
                                          "Ilmoitus on hyväksytty, ilmoitus lukitaan tallennuksen yhteydessä."
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
                                  (grid/poista-idt [:ilmoitustiedot :toteumat]))]
        (log "PAI Lomake-data: " (pr-str @lomakedata))
        (log "PAIK Lähetetään data " (pr-str lahetettava-data))
        (paikkaus/tallenna-paikkausilmoitus urakka-id sopimus-id lahetettava-data))
      {:luokka       "nappi-ensisijainen"
       :disabled     (false? @valmis-tallennettavaksi?)
       :ikoni        (ikonit/tallenna)
       :kun-onnistuu (fn [vastaus]
                       (log "PAI Lomake tallennettu, vastaus: " (pr-str vastaus))
                       (reset! toteumarivit vastaus)
                       (reset! lomakedata nil))}]]))

(defn paikkausilmoituslomake []
  (let [kokonaishinta (reaction (laske-kokonaishinta (get-in @lomakedata [:ilmoitustiedot :toteumat])))
        kohteen-tiedot (r/wrap {:aloituspvm         (:aloituspvm @lomakedata)
                                :valmispvm_kohde    (:valmispvm_kohde @lomakedata)
                                :valmispvm_paikkaus (:valmispvm_paikkaus @lomakedata)}
                               (fn [uusi-arvo]
                                 (reset! lomakedata (-> (assoc @lomakedata :aloituspvm (:aloituspvm uusi-arvo))
                                                        (assoc :valmispvm_kohde (:valmispvm_kohde uusi-arvo))
                                                        (assoc :valmispvm_paikkaus (:valmispvm_paikkaus uusi-arvo))))))

        toteutuneet-osoitteet
        (r/wrap (zipmap (iterate inc 1) (:osoitteet (:ilmoitustiedot @lomakedata)))
                (fn [uusi-arvo] (reset! lomakedata
                                        (assoc-in @lomakedata [:ilmoitustiedot :osoitteet] (grid/filteroi-uudet-poistetut uusi-arvo)))))
        toteutuneet-maarat
        (r/wrap (zipmap (iterate inc 1) (lisaa-suoritteet-tyhjaan-toteumaan (:toteumat (:ilmoitustiedot @lomakedata))))
                (fn [uusi-arvo] (reset! lomakedata
                                        (assoc-in @lomakedata [:ilmoitustiedot :toteumat] (grid/filteroi-uudet-poistetut uusi-arvo)))))

        toteutuneet-osoitteet-virheet (atom {})
        toteutuneet-maarat-virheet (atom {})

        valmis-tallennettavaksi? (reaction
                                   (let [toteutuneet-osoitteet @toteutuneet-osoitteet
                                         toteutuneet-osoitteet-virheet @toteutuneet-osoitteet-virheet
                                         toteutuneet-maarat-virheet @toteutuneet-maarat-virheet
                                         tila (:tila @lomakedata)
                                         lomake-lukittu-muokkaukselta? @lomake-lukittu-muokkaukselta?]
                                     (and
                                       (not (= tila :lukittu))
                                       (not (empty? toteutuneet-osoitteet))
                                       (empty? toteutuneet-osoitteet-virheet)
                                       (empty? toteutuneet-maarat-virheet)
                                       (false? lomake-lukittu-muokkaukselta?))))
        valmis-kasiteltavaksi? (reaction
                                 (let [valmispvm-kohde (:valmispvm_kohde @lomakedata)
                                       tila (:tila @lomakedata)]
                                   (log "PAI valmis käsi " (pr-str valmispvm-kohde) (pr-str tila))
                                   (and tila
                                        valmispvm-kohde
                                        (not (= tila :aloitettu))
                                        (not (nil? valmispvm-kohde)))))]

    (komp/luo
      (komp/lukko (lukko/muodosta-lukon-id "paikkausilmoitus" (:kohdenumero @lomakedata)))
      (fn []

        [:div.paikkausilmoituslomake

         [:button.nappi-toissijainen {:on-click #(reset! lomakedata nil)}
          (ikonit/chevron-left) " Takaisin ilmoitusluetteloon"]

         (when @lomake-lukittu-muokkaukselta?
           (lomake/lomake-lukittu-huomautus))

         [:h2 "Paikkausilmoitus"]

         [:div.row
          [:div.col-md-6
           [:h3 "Perustiedot"]
           [lomake {:luokka       :horizontal
                    :voi-muokata? (and (not= :lukittu (:tila @lomakedata))
                                       (false? @lomake-lukittu-muokkaukselta?))
                    :muokkaa!     (fn [uusi]
                                    (log "PAI Muokataan kohteen tietoja: " (pr-str uusi))
                                    (reset! kohteen-tiedot uusi))}
            [{:otsikko "Kohde" :nimi :kohde :hae (fn [_] (str "#" (:kohdenumero @lomakedata) " " (:kohdenimi @lomakedata))) :muokattava? (constantly false)}
             {:otsikko "Työ aloitettu" :nimi :aloituspvm :tyyppi :pvm}
             {:otsikko "Paikkaus valmistunut" :nimi :valmispvm_paikkaus :tyyppi :pvm}
             {:otsikko "Kohde valmistunut" :nimi :valmispvm_kohde
              :vihje   (when (and
                               (:valmispvm_paikkaus @lomakedata)
                               (:valmispvm_kohde @lomakedata)
                               (= :aloitettu (:tila @lomakedata)))
                         "Kohteen valmistumispäivämäärä annettu, ilmoitus tallennetaan valmiina urakanvalvojan käsiteltäväksi.")
              :tyyppi  :pvm :validoi [[:pvm-annettu-toisen-jalkeen :valmispvm_paikkaus "Kohdetta ei voi merkitä valmistuneeksi ennen kuin paikkaus on valmistunut."]]}
             {:otsikko "Toteutunut hinta" :nimi :hinta :tyyppi :numero :leveys-col 2 :hae #(fmt/euro-opt @kokonaishinta) :muokattava? (constantly false)}
             (when (or (= :valmis (:tila @lomakedata))
                       (= :lukittu (:tila @lomakedata)))
               {:otsikko     "Kommentit" :nimi :kommentit
                :komponentti [kommentit/kommentit {:voi-kommentoida? true
                                                   :voi-liittaa      false
                                                   :leveys-col       40
                                                   :placeholder      "Kirjoita kommentti..."
                                                   :uusi-kommentti   (r/wrap (:uusi-kommentti @lomakedata)
                                                                             #(swap! lomakedata assoc :uusi-kommentti %))}
                              (:kommentit @lomakedata)]})
             ]
            @kohteen-tiedot]]

          [:div.col-md-6
           (kasittely valmis-kasiteltavaksi?)]]

         [:fieldset.lomake-osa
          [:legend "Ilmoitustiedot"]

          [grid/muokkaus-grid
           {:otsikko      "Paikatut tierekisteriosoitteet"
            :tunniste     :tie
            :voi-muokata? (do
                            (log "PAI tila " (pr-str (:tila @lomakedata)) " Päätös: " (pr-str (:paatos_tekninen_osa @lomakedata)))
                            (and (not= :lukittu (:tila @lomakedata))
                                 (not= :hyvaksytty (:paatos @lomakedata))
                                 (false? @lomake-lukittu-muokkaukselta?)))
            :muutos       (fn [g]
                            (reset! toteutuneet-osoitteet-virheet (grid/hae-virheet g)))}
           [{:otsikko "Tie#" :nimi :tie :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
            {:otsikko "Alkutieosa" :nimi :aosa :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
            {:otsikko "Alkuetäisyys" :nimi :aet :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
            {:otsikko "Lopputieosa" :nimi :losa :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
            {:otsikko "Loppuetäisyys" :nimi :let :leveys "10%" :tyyppi :numero :validoi [[:ei-tyhja "Tieto puuttuu"]]}
            {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (paallystysilmoitukset/laske-tien-pituus rivi))}
            {:otsikko "Tienpäällysteen leveys" :nimi :paallysteen_leveys :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
            {:otsikko "Tiepäällysteen neliöt" :nimi :paallysteen_neliot :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
            {:otsikko "Paikkausneliöt" :nimi :paikkausneliot :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
            {:otsikko "Paikkaus-%" :nimi :paikkausprosentti :tyyppi :numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}]
           toteutuneet-osoitteet]

          [grid/muokkaus-grid
           {:otsikko      "Toteutuneet suoritemäärät"
            :voi-muokata? (and (not= :lukittu (:tila @lomakedata))
                               (not= :hyvaksytty (:paatos @lomakedata))
                               (false? @lomake-lukittu-muokkaukselta?))
            :voi-lisata?  false
            :voi-kumota?  false
            :voi-poistaa? (constantly false)
            :muutos       #(reset! toteutuneet-maarat-virheet (grid/hae-virheet %))}
           [{:otsikko "Suorite" :nimi :suorite :tyyppi :string :leveys "10%" :pituus-max 256
             :hae     (fn [rivi] (minipot/hae-paikkaustyo-koodilla (:suorite rivi))) :muokattava? (constantly false)}
            {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys "10%" :pituus-max 256}
            {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys "10%"}
            {:otsikko "Yks.hinta (alv 0%)" :nimi :yks_hint_alv_0 :tyyppi :numero :leveys "10%"}
            {:otsikko "Yks.hinta (alv 24%)" :nimi :yks_hint_alv_24 :leveys "10%" :tyyppi :numero :muokattava? (constantly false) :hae (fn [rivi] (laske-tyon-alv (:yks_hint_alv_0 rivi) 24))}
            {:otsikko "Yht. (alv 0%)" :nimi :yht :leveys "10%" :tyyppi :numero :muokattava? (constantly false)
             :hae     (fn [rivi] (* (:yks_hint_alv_0 rivi) (:maara rivi)))}
            {:otsikko "Takuupvm" :nimi :takuupvm :leveys "10%" :tyyppi :pvm}]
           toteutuneet-maarat]]

         (tallennus valmis-tallennettavaksi?)]))))

(defn ilmoitusluettelo
  []
  (komp/luo
    (fn []
      [:div
       [grid/grid
        {:otsikko  "Paikkausilmoitukset"
         :tyhja    (if (nil? @toteumarivit) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
         :tunniste :kohdenumero}
        [{:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys "10%"}
         {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys "50%"}
         {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys "20%" :hae (fn [rivi]
                                                                                                           (nayta-tila (:tila rivi)))}
         {:otsikko "Päätös" :nimi :paatos :muokattava? (constantly false) :tyyppi :komponentti :leveys "20%" :komponentti (fn [rivi]
                                                                                                                            (nayta-paatos (:paatos rivi)))}
         {:otsikko     "Paikkausilmoitus" :nimi :paikkausilmoitus :muokattava? (constantly false) :leveys "25%" :tyyppi :komponentti
          :komponentti (fn [rivi] (if (:tila rivi) [:button.nappi-toissijainen.nappi-grid {:on-click #(go
                                                                                                       (let [urakka-id (:id @nav/valittu-urakka)
                                                                                                             [sopimus-id _] @u/valittu-sopimusnumero
                                                                                                             vastaus (<! (paikkaus/hae-paikkausilmoitus-paikkauskohteella urakka-id sopimus-id (:paikkauskohde_id rivi)))]
                                                                                                         (log "PAI Rivi: " (pr-str rivi))
                                                                                                         (log "PAI Vastaus: " (pr-str vastaus))
                                                                                                         (if-not (k/virhe? vastaus)
                                                                                                           (reset! lomakedata (-> (assoc vastaus :paikkauskohde-id (:paikkauskohde_id rivi)))))))}
                                                    [:span (ikonit/eye-open) " Paikkausilmoitus"]]
                                                   [:button.nappi-toissijainen.nappi-grid {:on-click #(reset! lomakedata {:kohdenumero      (:kohdenumero rivi)
                                                                                                                          :kohdenimi        (:nimi rivi)
                                                                                                                          :paikkauskohde-id (:paikkauskohde_id rivi)})}
                                                    [:span "Aloita paikkausilmoitus"]]))}]
        (sort-by
          (fn [toteuma] (case (:tila toteuma)
                          :lukittu 0
                          :valmis 1
                          :aloitettu 3
                          4))
          @toteumarivit)]])))

(defn paikkausilmoitukset []
  (komp/luo
    (komp/lippu paikkaus/paikkausilmoitukset-nakymassa?)

    (fn []
      (if @lomakedata
        [paikkausilmoituslomake]
        [ilmoitusluettelo]))))