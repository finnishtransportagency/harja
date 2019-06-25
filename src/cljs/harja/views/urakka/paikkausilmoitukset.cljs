(ns harja.views.urakka.paikkausilmoitukset
  "Urakan paikkausilmoitukset"
  (:require [reagent.core :refer [atom] :as r]
            [harja.ui.grid :as grid]
            [harja.ui.grid.gridin-muokkaus :as gridin-muokkaus]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.muokkauslukko :as lukko]
            [harja.tiedot.navigaatio :as nav]
            [harja.fmt :as fmt]
            [harja.loki :refer [log tarkkaile!]]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.paikkaus :as paikkaus]
            [harja.ui.kommentit :as kommentit]
            [harja.domain.paikkausilmoitus :as minipot]
            [harja.views.kartta :as kartta]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.tierekisteri :as tierekisteri-domain]
            [harja.ui.napit :as napit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t]
            [clojure.set :as set])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn lisaa-suoritteet-tyhjaan-toteumaan [toteumat]
  (if (or (nil? toteumat) (empty? toteumat))
    (mapv
      (fn [tyo] {:suorite (:koodi tyo)})
      minipot/+paikkaustyot+)
    toteumat))


(defn laske-tyon-alv
  "Ottaa työn hinnan (esim. 100) ja arvolisäveron (esim. 24) palauttaa työn hinnan alv:n kera"
  [tyon-hinta alv]
  (* tyon-hinta (+ (/ (double alv) 100) 1)))

(defn laske-paikkausprosentti [paikkausneliot tienpaallysteen-neliot]
  (if (and
        (not (nil? tienpaallysteen-neliot))
        (not= tienpaallysteen-neliot 0))
    (let [tulos (* (/ paikkausneliot tienpaallysteen-neliot) 100)]
      (.toFixed tulos 0))))

(defn laske-tienpaallysteen-neliot [pituus tienpaallysteen-leveys]
  (let [pituus (or pituus 0)
        tienpaallysteen-leveys (or tienpaallysteen-leveys 0)]
    (* pituus tienpaallysteen-leveys)))


(defn kasittely
  "Ilmoituksen käsittelyosio, kun ilmoitus on valmis. Tilaaja voi muokata, urakoitsija voi tarkastella."
  [valmis-kasiteltavaksi?]
  (let [muokattava? (and
                      (oikeudet/on-muu-oikeus? "päätös"
                                               oikeudet/urakat-kohdeluettelo-paikkausilmoitukset
                                               (:id @nav/valittu-urakka)
                                               @istunto/kayttaja)
                      (not= (:tila @paikkaus/paikkausilmoitus-lomakedata) :lukittu)
                      (false? @paikkaus/paikkausilmoituslomake-lukittu?))
        paatostiedot (r/wrap {:paatos (:paatos @paikkaus/paikkausilmoitus-lomakedata)
                              :perustelu (:perustelu @paikkaus/paikkausilmoitus-lomakedata)
                              :kasittelyaika (:kasittelyaika @paikkaus/paikkausilmoitus-lomakedata)}
                             (fn [uusi-arvo] (reset! paikkaus/paikkausilmoitus-lomakedata (-> (assoc @paikkaus/paikkausilmoitus-lomakedata :paatos (:paatos uusi-arvo))
                                                                                              (assoc :perustelu (:perustelu uusi-arvo))
                                                                                              (assoc :kasittelyaika (:kasittelyaika uusi-arvo))))))]
    (when @valmis-kasiteltavaksi?
      [:div.paikkausilmoitus-kasittely
       [:h3 "Käsittely"]
       [lomake/lomake
        {:luokka :horizontal
         :muokkaa! (fn [uusi]
                     (reset! paatostiedot uusi))
         :voi-muokata? muokattava?}
        [{:otsikko "Käsitelty"
          :nimi :kasittelyaika
          :tyyppi :pvm
          :validoi [[:ei-tyhja "Anna käsittelypäivämäärä"]
                    [:pvm-toisen-pvmn-jalkeen (:valmispvm-kohde @paikkaus/paikkausilmoitus-lomakedata) "Käsittely ei voi olla ennen valmistumista"]]}

         {:otsikko "Päätös"
          :nimi :paatos
          :tyyppi :valinta
          :valinnat [:hyvaksytty :hylatty]
          :validoi [[:ei-tyhja "Anna päätös"]]
          :valinta-nayta #(if % (paallystys-ja-paikkaus/kuvaile-paatostyyppi %) (if muokattava? "- Valitse päätös -" "-"))
          :palstoja 1}

         (when (:paatos @paatostiedot)
           {:otsikko "Selitys"
            :nimi :perustelu
            :tyyppi :text
            :koko [60 3]
            :pituus-max 2048
            :palstoja 2
            :validoi [[:ei-tyhja "Anna päätöksen selitys"]]})]
        @paatostiedot]])))

(defn tallennus
  [valmis-tallennettavaksi?]
  (let [huomautusteksti (reaction (let [valmispvm-kohde (:valmispvm-kohde @paikkaus/paikkausilmoitus-lomakedata)
                                        valmispvm-paikkaus (:valmispvm-paikkaus @paikkaus/paikkausilmoitus-lomakedata)
                                        paatos (:paatos @paikkaus/paikkausilmoitus-lomakedata)
                                        tila (:tila @paikkaus/paikkausilmoitus-lomakedata)]
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
      #(let [lomake @paikkaus/paikkausilmoitus-lomakedata
             lahetettava-data (-> (gridin-muokkaus/poista-idt lomake [:ilmoitustiedot :osoitteet])
                                  (gridin-muokkaus/poista-idt [:ilmoitustiedot :toteumat]))]
        (log "PAI Lomake-data: " (pr-str @paikkaus/paikkausilmoitus-lomakedata))
        (log "PAIK Lähetetään data " (pr-str lahetettava-data))
        (paikkaus/tallenna-paikkausilmoitus! urakka-id sopimus-id lahetettava-data))
      {:luokka "nappi-ensisijainen"
       :disabled (false? @valmis-tallennettavaksi?)
       :ikoni (ikonit/tallenna)
       :virheviesti "Tallentaminen epäonnistui"
       :kun-onnistuu (fn [vastaus]
                       (log "PAI Lomake tallennettu, vastaus: " (pr-str vastaus))
                       (urakka/lukitse-urakan-yha-sidonta! urakka-id)
                       (reset! paikkaus/paikkausilmoitukset vastaus)
                       (reset! paikkaus/paikkausilmoitus-lomakedata nil))}]]))

(defn paikkausilmoituslomake []
  (let [lomake-kirjoitusoikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paikkausilmoitukset
                                                          (:id @nav/valittu-urakka))
        kokonaishinta (reaction (minipot/laske-kokonaishinta (get-in @paikkaus/paikkausilmoitus-lomakedata [:ilmoitustiedot :toteumat])))]

    (komp/luo
      (komp/lukko (lukko/muodosta-lukon-id "paikkausilmoitus" (:kohdenumero @paikkaus/paikkausilmoitus-lomakedata)))
      (fn []
        (let [kohteen-tiedot (r/wrap {:aloituspvm (:aloituspvm @paikkaus/paikkausilmoitus-lomakedata)
                                      :valmispvm-kohde (:valmispvm-kohde @paikkaus/paikkausilmoitus-lomakedata)
                                      :valmispvm-paikkaus (:valmispvm-paikkaus @paikkaus/paikkausilmoitus-lomakedata)}
                                     (fn [uusi-arvo]
                                       (reset! paikkaus/paikkausilmoitus-lomakedata (-> (assoc @paikkaus/paikkausilmoitus-lomakedata :aloituspvm (:aloituspvm uusi-arvo))
                                                                                        (assoc :valmispvm-kohde (:valmispvm-kohde uusi-arvo))
                                                                                        (assoc :valmispvm-paikkaus (:valmispvm-paikkaus uusi-arvo))))))

              toteutuneet-osoitteet
              (r/wrap (zipmap (iterate inc 1) (:osoitteet (:ilmoitustiedot @paikkaus/paikkausilmoitus-lomakedata)))
                      (fn [uusi-arvo]
                        (reset! paikkaus/paikkausilmoitus-lomakedata
                                              (assoc-in @paikkaus/paikkausilmoitus-lomakedata [:ilmoitustiedot :osoitteet]
                                                        ;; Sortataan ensisijaisesti tien mukaan, jos kaikki kohat tielle on annettu. Muuten sortataan id:n mukaan
                                                        (sort-by #(let [funktio (apply juxt (if (set/subset? #{:tie :aosa :aet :losa :let} (into #{} (keys %)))
                                                                                              [:tie :aosa :aet :losa :let] ;; Tämä tulee aina ylemmäksi kuin :id vertailu (alla), koska pienempi vecotri saa arvoksi 1 compare funktiolla
                                                                                              [:id :tie :aosa :aet :losa :let]))]
                                                                    (funktio %))
                                                                 (gridin-muokkaus/filteroi-uudet-poistetut uusi-arvo))))))
              toteutuneet-maarat
              (r/wrap (zipmap (iterate inc 1) (lisaa-suoritteet-tyhjaan-toteumaan (:toteumat (:ilmoitustiedot @paikkaus/paikkausilmoitus-lomakedata))))
                      (fn [uusi-arvo] (reset! paikkaus/paikkausilmoitus-lomakedata
                                              (assoc-in @paikkaus/paikkausilmoitus-lomakedata [:ilmoitustiedot :toteumat] (gridin-muokkaus/filteroi-uudet-poistetut uusi-arvo)))))

              toteutuneet-osoitteet-virheet (atom {})
              toteutuneet-maarat-virheet (atom {})

              valmis-tallennettavaksi? (reaction
                                         (let [toteutuneet-osoitteet-virheet @toteutuneet-osoitteet-virheet
                                               toteutuneet-maarat-virheet @toteutuneet-maarat-virheet
                                               tila (:tila @paikkaus/paikkausilmoitus-lomakedata)
                                               lomake-lukittu-muokkaukselta? @paikkaus/paikkausilmoituslomake-lukittu?]
                                           (and
                                             (not (= tila :lukittu))
                                             (empty? toteutuneet-osoitteet-virheet)
                                             (empty? toteutuneet-maarat-virheet)
                                             (false? lomake-lukittu-muokkaukselta?))))
              valmis-kasiteltavaksi? (reaction
                                       (let [valmispvm-kohde (:valmispvm-kohde @paikkaus/paikkausilmoitus-lomakedata)
                                             tila (:tila @paikkaus/paikkausilmoitus-lomakedata)]
                                         (log "PAI valmis käsi " (pr-str valmispvm-kohde) (pr-str tila))
                                         (and tila
                                              valmispvm-kohde
                                              (not (= tila :aloitettu))
                                              (not (nil? valmispvm-kohde)))))
              grid-kirjoitusoikeus? (and (not= :lukittu (:tila @paikkaus/paikkausilmoitus-lomakedata))
                                         (not= :hyvaksytty (:paatos @paikkaus/paikkausilmoitus-lomakedata))
                                         (false? @paikkaus/paikkausilmoituslomake-lukittu?)
                                         lomake-kirjoitusoikeus?)]
          [:div.paikkausilmoituslomake
           [napit/takaisin "Takaisin ilmoitusluetteloon" #(reset! paikkaus/paikkausilmoitus-lomakedata nil)]

           (when @paikkaus/paikkausilmoituslomake-lukittu?
             (lomake/lomake-lukittu-huomautus @lukko/nykyinen-lukko))

           [:h2 "Paikkausilmoitus"]

           [:div.row
            [:div.col-md-6
             [:h3 "Perustiedot"]
             [lomake/lomake {:luokka :horizontal
                             :voi-muokata? (and (not= :lukittu (:tila @paikkaus/paikkausilmoitus-lomakedata))
                                                (false? @paikkaus/paikkausilmoituslomake-lukittu?)
                                                lomake-kirjoitusoikeus?)
                             :muokkaa! (fn [uusi]
                                         (log "PAI Muokataan kohteen tietoja: " (pr-str uusi))
                                         (reset! kohteen-tiedot uusi))}
              [{:otsikko "Kohde" :nimi :kohde :hae (fn [_] (str "#" (:kohdenumero @paikkaus/paikkausilmoitus-lomakedata) " " (:kohdenimi @paikkaus/paikkausilmoitus-lomakedata))) :muokattava? (constantly false)}
               {:otsikko "Työ aloitettu" :nimi :aloituspvm :tyyppi :pvm}
               {:otsikko "Paikkaus valmistunut" :nimi :valmispvm-paikkaus :tyyppi :pvm}
               {:otsikko "Kohde valmistunut" :nimi :valmispvm-kohde
                :vihje (when (and
                               (:valmispvm-paikkaus @paikkaus/paikkausilmoitus-lomakedata)
                               (:valmispvm-kohde @paikkaus/paikkausilmoitus-lomakedata)
                               (= :aloitettu (:tila @paikkaus/paikkausilmoitus-lomakedata)))
                         "Kohteen valmistumispäivämäärä annettu, ilmoitus tallennetaan valmiina urakanvalvojan käsiteltäväksi.")
                :tyyppi :pvm :validoi [[:toinen-arvo-annettu-ensin :valmispvm-paikkaus "Kohdetta ei voi merkitä valmistuneeksi ennen kuin paikkaus on valmistunut."]]}
               {:otsikko "Toteutunut hinta" :nimi :hinta :tyyppi :string :palstoja 1
                :hae #(if @kokonaishinta
                        (fmt/euro-opt @kokonaishinta)
                        0)
                :muokattava? (constantly false)}
               (when (or (= :valmis (:tila @paikkaus/paikkausilmoitus-lomakedata))
                         (= :lukittu (:tila @paikkaus/paikkausilmoitus-lomakedata)))
                 {:otsikko "Kommentit" :nimi :kommentit
                  :tyyppi :komponentti
                  :komponentti (fn [_]
                                 [kommentit/kommentit {:voi-kommentoida? (not= :lukittu (:tila @paikkaus/paikkausilmoitus-lomakedata))
                                                      :voi-liittaa? false
                                                      :leveys-col 40
                                                      :placeholder "Kirjoita kommentti..."
                                                      :uusi-kommentti (r/wrap (:uusi-kommentti @paikkaus/paikkausilmoitus-lomakedata)
                                                                              #(swap! paikkaus/paikkausilmoitus-lomakedata assoc :uusi-kommentti %))}
                                 (:kommentit @paikkaus/paikkausilmoitus-lomakedata)])})
               ]
              @kohteen-tiedot]]

            [:div.col-md-6
             (kasittely valmis-kasiteltavaksi?)]]

           [:fieldset.lomake-osa
            [:h3 "Ilmoitustiedot"]

            [grid/muokkaus-grid
             {:otsikko "Paikatut tierekisteriosoitteet"
              :tunniste :tie
              :voi-muokata? grid-kirjoitusoikeus?
              :virheet toteutuneet-osoitteet-virheet
              :uusi-id (inc (count @toteutuneet-osoitteet))}
             [{:otsikko "Tie#" :nimi :tie :tyyppi :positiivinen-numero :leveys "10%"
               :validoi [[:ei-tyhja "Tieto puuttuu"]] :tasaa :oikea}
              {:otsikko "Aosa" :nimi :aosa :leveys "10%" :tyyppi :positiivinen-numero
               :validoi [[:ei-tyhja "Tieto puuttuu"]] :tasaa :oikea}
              {:otsikko "Aet" :nimi :aet :leveys "10%" :tyyppi :positiivinen-numero
               :validoi [[:ei-tyhja "Tieto puuttuu"]] :tasaa :oikea}
              {:otsikko "Losa" :nimi :losa :leveys "10%" :tyyppi :positiivinen-numero
               :validoi [[:ei-tyhja "Tieto puuttuu"]] :tasaa :oikea}
              {:otsikko "Let" :nimi :let :leveys "10%" :tyyppi :positiivinen-numero
               :validoi [[:ei-tyhja "Tieto puuttuu"]] :tasaa :oikea}
              {:otsikko "Pituus (m)" :nimi :pituus :leveys "10%" :tasaa :oikea
               :tyyppi :positiivinen-numero :muokattava? (constantly false) :hae (fn [rivi]
                                                                                   (tierekisteri-domain/laske-tien-pituus rivi))}
              {:otsikko "Tiepääl\u00ADlysteen leveys" :nimi :paallysteen-leveys :tasaa :oikea
               :tyyppi :positiivinen-numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Tiepääl\u00ADlysteen neliöt" :nimi :paallysteen-neliot :tasaa :oikea
               :tyyppi :positiivinen-numero :leveys "10%" :muokattava? (constantly false) :hae (fn [rivi]
                                                                                                 (laske-tienpaallysteen-neliot (tierekisteri-domain/laske-tien-pituus rivi) (:paallysteen-leveys rivi)))}
              {:otsikko "Paik\u00ADkaus\u00ADneliöt" :nimi :paikkausneliot :tasaa :oikea
               :tyyppi :positiivinen-numero :leveys "10%" :validoi [[:ei-tyhja "Tieto puuttuu"]]}
              {:otsikko "Paik\u00ADkaus-%" :nimi :paikkausprosentti
               :tyyppi :string :leveys "10%" :muokattava? (constantly false) :hae (fn [rivi]
                                                                                    (laske-paikkausprosentti (:paikkausneliot rivi)
                                                                                                             (laske-tienpaallysteen-neliot (tierekisteri-domain/laske-tien-pituus rivi) (:paallysteen-leveys rivi))))}]
             toteutuneet-osoitteet]

            [grid/muokkaus-grid
             {:otsikko "Toteutuneet suoritemäärät"
              :voi-muokata? grid-kirjoitusoikeus?
              :voi-lisata? false
              :voi-kumota? false
              :voi-poistaa? (constantly false)
              :virheet toteutuneet-maarat-virheet
              :uusi-id (inc (count @toteutuneet-maarat))}
             [{:otsikko "Suorite" :nimi :suorite :tyyppi :string :leveys "10%" :pituus-max 256
               :hae (fn [rivi] (minipot/hae-paikkaustyo-koodilla (:suorite rivi))) :muokattava? (constantly false)}
              {:otsikko "Yksikkö" :nimi :yksikko :tyyppi :string :leveys "10%" :pituus-max 256 }
              {:otsikko "Määrä" :nimi :maara :tyyppi :positiivinen-numero :leveys "10%" :kokonaisosan-maara 6
               :tasaa :oikea}
              {:otsikko "Yks.hinta (alv 0%)" :nimi :yks-hint-alv-0
               :tyyppi :positiivinen-numero :leveys "10%" :kokonaisosan-maara 4 :tasaa :oikea}
              {:otsikko "Yks.hinta (alv 24%)" :nimi :yks-hint-alv-24
               :leveys "10%" :tyyppi :positiivinen-numero :muokattava? (constantly false)
               :hae (fn [rivi] (laske-tyon-alv (:yks-hint-alv-0 rivi) 24)) :tasaa :oikea}
              {:otsikko "Yht. (alv 0%)" :nimi :yht :leveys "10%" :tyyppi :positiivinen-numero
               :muokattava? (constantly false) :tasaa :oikea
               :hae (fn [rivi] (* (:yks-hint-alv-0 rivi) (:maara rivi)))}
              {:otsikko "Takuupvm" :nimi :takuupvm :leveys "10%" :tyyppi :pvm}]
             toteutuneet-maarat]]

           (tallennus valmis-tallennettavaksi?)])))))

(defn avaa-paikkausilmoitus [paikkauskohteen-id]
  (go
    (let [urakka-id (:id @nav/valittu-urakka)
          [sopimus-id _] @u/valittu-sopimusnumero
          vastaus (<! (paikkaus/hae-paikkausilmoitus-paikkauskohteella urakka-id sopimus-id paikkauskohteen-id))]
      (log "Paikkausilmoitus kohteelle " paikkauskohteen-id " => " (pr-str vastaus))
      (if-not (k/virhe? vastaus)
        (reset! paikkaus/paikkausilmoitus-lomakedata vastaus)))))

(defn ilmoitusluettelo
  []
  (komp/luo
    (komp/kuuntelija :avaa-paikkausilmoitus
                     (fn [_ rivi]
                       (avaa-paikkausilmoitus (:paikkauskohde-id rivi))))
    (fn []
      [:div
       [:h3 "Paikkausilmoitukset"]
       [grid/grid
        {:otsikko ""
         :tyhja (if (nil? @paikkaus/paikkausilmoitukset) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
         :tunniste :kohdenumero}
        [{:otsikko "#" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys 10}
         {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys 50}
         {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys 20 :hae (fn [rivi]
                                                                                                        (paallystys-ja-paikkaus/kuvaile-ilmoituksen-tila (:tila rivi)))}
         {:otsikko "Päätös" :nimi :paatos :muokattava? (constantly false) :tyyppi :komponentti :leveys 20 :komponentti (fn [rivi]
                                                                                                                         [paallystys-ja-paikkaus/nayta-paatos (:paatos rivi)])}
         {:otsikko "Paikkaus\u00ADilmoitus" :nimi :paikkausilmoitus :muokattava? (constantly false) :leveys 25 :tyyppi :komponentti
          :komponentti (fn [rivi] (if (:tila rivi) [:button.nappi-toissijainen.nappi-grid {:on-click #(avaa-paikkausilmoitus (:paikkauskohde-id rivi))}
                                                    [:span (ikonit/eye-open) " Paikkausilmoitus"]]
                                                   [:button.nappi-toissijainen.nappi-grid {:on-click #(avaa-paikkausilmoitus (:paikkauskohde-id rivi))}
                                                    [:span "Aloita paikkausilmoitus"]]))}]
        (sort-by
          (juxt (fn [toteuma] (case (:tila toteuma)
                                :lukittu 0
                                :valmis 1
                                :aloitettu 3
                                4))
                (fn [toteuma] (case (:paatos toteuma)
                                :hyvaksytty 0
                                :hylatty 1
                                3)))
          @paikkaus/paikkausilmoitukset)]])))

(defn paikkausilmoitukset [urakka]
  (komp/luo
    (komp/lippu paikkaus/paikkausilmoitukset-nakymassa?)

    (fn [urakka]
      [:span.paikkausilmoitukset
       [kartta/kartan-paikka]
       [valinnat/vuosi {}
        (t/year (:alkupvm urakka))
        (t/year (:loppupvm urakka))
        urakka/valittu-urakan-vuosi
        urakka/valitse-urakan-vuosi!]
       (if @paikkaus/paikkausilmoitus-lomakedata
         [paikkausilmoituslomake]
         [ilmoitusluettelo])])))
