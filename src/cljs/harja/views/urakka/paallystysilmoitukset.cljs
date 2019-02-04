(ns harja.views.urakka.paallystysilmoitukset
  "Urakan päällystysilmoitukset"
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]
            [cljs-time.core :as t]
            [goog.events.EventType :as event-type]

            [harja.ui.grid :as grid]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje]]
            [harja.ui.komponentti :as komp]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.historia :as historia]
            [harja.ui.kentat :as kentat]

            [harja.domain.paallystysilmoitus :as pot]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]

            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.muokkauslukko :as lukko]

            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]

            [harja.asiakas.kommunikaatio :as k]
            [harja.views.kartta :as kartta]
            [harja.domain.tierekisteri :as tierekisteri-domain]
            [harja.ui.napit :as napit]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.tierekisteri :as tr]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.pvm :as pvm]
            [harja.atom :as atom]
            [harja.tyokalut.vkm :as vkm]

            [harja.ui.debug :refer [debug]]
            [harja.ui.viesti :as viesti]
            [harja.ui.valinnat :as valinnat]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.views.urakka.valinnat :as u-valinnat]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.modal :as modal]
            [harja.ui.validointi :as v])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn laske-hinta [lomakedata-nyt]
  (let [urakkasopimuksen-mukainen-kokonaishinta (:kokonaishinta-ilman-maaramuutoksia lomakedata-nyt)
        muutokset-kokonaishintaan (:maaramuutokset lomakedata-nyt)
        toteuman-kokonaishinta (+ urakkasopimuksen-mukainen-kokonaishinta muutokset-kokonaishintaan)]
    {:urakkasopimuksen-mukainen-kokonaishinta urakkasopimuksen-mukainen-kokonaishinta
     :muutokset-kokonaishintaan muutokset-kokonaishintaan
     :toteuman-kokonaishinta toteuman-kokonaishinta}))

(defn yhteenveto [lomakedata-nyt]
  (let [{:keys [urakkasopimuksen-mukainen-kokonaishinta
                muutokset-kokonaishintaan
                toteuman-kokonaishinta]}
        (laske-hinta lomakedata-nyt)]
    [yleiset/taulukkotietonakyma {}
     "Urakkasopimuksen mukainen kokonaishinta: "
     (fmt/euro-opt (or urakkasopimuksen-mukainen-kokonaishinta 0))

     (str "Määrämuutosten vaikutus kokonaishintaan"
          (when (:maaramuutokset-ennustettu? lomakedata-nyt) " (ennustettu)")
          ": ")
     (fmt/euro-opt (or muutokset-kokonaishintaan 0))

     "Yhteensä: "
     (fmt/euro-opt toteuman-kokonaishinta)]))

(defn pakollinen-kentta?
  [pakolliset-kentat kentta]
  (if (ifn? pakolliset-kentat)
    (not (nil? (pakolliset-kentat kentta)))
    false))

(defn asiatarkastus
  "Asiatarkastusosio konsultille."
  [urakka {:keys [tila asiatarkastus] :as perustiedot-nyt}
   lukittu? muokkaa! {{{:keys [tarkastusaika tarkastaja] :as asiatarkastus-validointi} :asiatarkastus} :perustiedot}]
  (log "ASIATARKASTUS " (pr-str asiatarkastus))
  (let [muokattava? (and
                      (oikeudet/on-muu-oikeus? "asiatarkastus"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))
                      (not= tila :lukittu)
                      (false? lukittu?))
        pakolliset-kentat (-> asiatarkastus-validointi meta :pakolliset)]

    [:div.pot-asiatarkastus
     [:h3 "Asiatarkastus"]

     [lomake/lomake
      {:otsikko ""
       :muokkaa! (fn [uusi]
                   (muokkaa! assoc-in [:perustiedot :asiatarkastus] uusi))
       :validoi-alussa? true
       :validoitavat-avaimet #{:pakollinen :validoi}
       :voi-muokata? muokattava?
       :data-cy "paallystysilmoitus-asiatarkastus"}
      [{:otsikko "Tarkastettu"
        :nimi :tarkastusaika
        :pakollinen? (pakollinen-kentta? pakolliset-kentat :tarkastusaika)
        :tyyppi :pvm
        :huomauta tarkastusaika}
       {:otsikko "Tarkastaja"
        :nimi :tarkastaja
        :pakollinen? (pakollinen-kentta? pakolliset-kentat :tarkastaja)
        :tyyppi :string
        :huomauta tarkastaja
        :pituus-max 1024}
       {:teksti "Hyväksytty"
        :nimi :hyvaksytty
        :tyyppi :checkbox
        :fmt #(if % "Tekninen osa tarkastettu" "Teknistä osaa ei tarkastettu")}
       {:otsikko "Lisätiedot"
        :nimi :lisatiedot
        :pakollinen? (pakollinen-kentta? pakolliset-kentat :lisatiedot)
        :tyyppi :text
        :koko [60 3]
        :pituus-max 4096
        :palstoja 2}]
      asiatarkastus]]))

(defn kasittely
  "Ilmoituksen käsittelyosio, kun ilmoitus on valmis.
  Tilaaja voi muokata, urakoitsija voi tarkastella."
  [urakka {:keys [tila tekninen-osa] :as perustiedot-nyt}
   lukittu? muokkaa! {{{:keys [kasittelyaika paatos perustelu] :as tekninen-osa-validointi} :tekninen-osa} :perustiedot}]
  (let [muokattava? (and
                      (oikeudet/on-muu-oikeus? "päätös"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))
                      (not= tila :lukittu)
                      (false? lukittu?))]
    [:div.pot-kasittely

     [:h3 "Käsittely"]
     [lomake/lomake
      {:otsikko "Tekninen osa"
       :muokkaa! #(muokkaa! assoc-in [:perustiedot :tekninen-osa] %)
       :validoi-alussa? true
       :validoitavat-avaimet #{:pakollinen :validoi}
       :voi-muokata? muokattava?
       :data-cy "paallystysilmoitus-kasittelytiedot"}
      [{:otsikko "Käsitelty"
        :nimi :kasittelyaika
        :tyyppi :pvm
        :huomauta kasittelyaika}

       {:otsikko "Päätös"
        :nimi :paatos
        :tyyppi :valinta
        :valinnat [:hyvaksytty :hylatty]
        :huomauta paatos
        :valinta-nayta #(cond
                          % (paallystys-ja-paikkaus/kuvaile-paatostyyppi %)
                          muokattava? "- Valitse päätös -"
                          :default "-")
        :palstoja 1}

       (when (:paatos tekninen-osa)
         {:otsikko "Selitys"
          :nimi :perustelu
          :tyyppi :text
          :koko [60 3]
          :pituus-max 2048
          :palstoja 2
          :huomauta perustelu})]
      tekninen-osa]]))

(defn tallennus
  [e! {:keys [tekninen-osa tila]} kayttaja {urakka-id :id :as urakka} valmis-tallennettavaksi?]
  (let [paatos-tekninen-osa (:paatos tekninen-osa)
        huomautusteksti
        (cond (and (not= :lukittu tila)
                   (= :hyvaksytty paatos-tekninen-osa))
              "Päällystysilmoitus hyväksytty, ilmoitus lukitaan tallennuksen yhteydessä."
              :default nil)]

    [:div.pot-tallennus
     (when huomautusteksti
       (lomake/yleinen-huomautus huomautusteksti))

     [napit/palvelinkutsu-nappi
      "Tallenna"
      ;; Palvelinkutsunappi olettaa saavansa kanavan. Siksi go.
      #(go
         (e! (paallystys/->TallennaPaallystysilmoitus)))
      {:luokka "nappi-ensisijainen"
       :data-cy "pot-tallenna"
       :id "tallenna-paallystysilmoitus"
       :disabled (or (false? valmis-tallennettavaksi?)
                     (not (oikeudet/voi-kirjoittaa?
                            oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                            urakka-id kayttaja)))
       :ikoni (ikonit/tallenna)
       :virheviesti "Tallentaminen epäonnistui"}]]))

(defn tarkista-takuu-pvm [_ {valmispvm-paallystys :valmispvm-paallystys takuupvm :takuupvm}]
  (when (and valmispvm-paallystys
             takuupvm
             (> valmispvm-paallystys takuupvm))
    "Takuupvm on yleensä kohteen valmistumisen jälkeen."))


(defn tr-kentta [{:keys [muokkaa-lomaketta data]} e!
                 {{:keys [tr-numero tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys]} :perustiedot
                  ohjauskahvat :ohjauskahvat}]
  (let [muuta! (fn [kentta]
                 #(do
                    (.preventDefault %)
                    (let [v (-> % .-target .-value)]
                      (when (re-matches #"\d*" v)
                        (let [arvo (if (= "" v)
                                     nil
                                     (js/parseInt v))]
                          (muokkaa-lomaketta (-> data
                                                 (assoc-in [:tr-osoite kentta] arvo)
                                                 (assoc kentta arvo)))
                          (grid/validoi-grid (:tierekisteriosoitteet ohjauskahvat))
                          (grid/validoi-grid (:alustalle-tehdyt-toimet ohjauskahvat)))))))]
    [:table
     [:thead
      [:tr
       [:th "Tie"]
       [:th "Aosa"]
       [:th "Aet"]
       [:th "Losa"]
       [:th "Let"]]]
     [:tbody
      [:tr
       [:td
        [kentat/tr-kentan-elementti true muuta! nil
         "Tie" tr-numero :tr-numero true ""]]
       [:td
        [kentat/tr-kentan-elementti true muuta! nil
         "Aosa" tr-alkuosa :tr-alkuosa false ""]]
       [:td
        [kentat/tr-kentan-elementti true muuta! nil
         "Aet" tr-alkuetaisyys :tr-alkuetaisyys false ""]]
       [:td
        [kentat/tr-kentan-elementti true muuta! nil
         "Losa" tr-loppuosa :tr-loppuosa false ""]]
       [:td
        [kentat/tr-kentan-elementti true muuta! nil
         "Let" tr-loppuetaisyys :tr-loppuetaisyys false ""]]]]]))

(defn paallystysilmoitus-perustiedot [e! {{:keys [tila kohdenumero tunnus kohdenimi tr-numero tr-ajorata tr-kaista
                                                  tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                                                  uusi-kommentti kommentit takuupvm] :as perustiedot-nyt}
                                          :perustiedot kirjoitusoikeus? :kirjoitusoikeus?
                                          ohjauskahvat :ohjauskahvat :as paallystysilmoituksen-osa} urakka
                                      lukittu?
                                      muokkaa!
                                      validoinnit huomautukset]
  (let [nayta-kasittelyosiot? (or (= tila :valmis) (= tila :lukittu))
        muokattava? (boolean (and (not= :lukittu tila)
                                  (false? lukittu?)
                                  kirjoitusoikeus?))]
    [:div.row
     [:div.col-sm-12.col-md-6
      [:h3 "Perustiedot"]
      [lomake/lomake {:voi-muokata? muokattava?
                      :muokkaa! (fn [uusi]
                                  (log "[PÄÄLLYSTYS] Muokataan kohteen tietoja: " (pr-str uusi))
                                  (muokkaa! update :perustiedot (fn [vanha]
                                                                  (merge vanha uusi))))
                      :validoi-alussa? true
                      :data-cy "paallystysilmoitus-perustiedot"}
       [{:otsikko "Kohde" :nimi :kohde
         :hae (fn [_]
                (str "#" kohdenumero " " tunnus " " kohdenimi))
         :muokattava? (constantly false)
         ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"}
        (merge
          {:nimi :tr-osoite
           ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-12 col-lg-6"}
          (if muokattava?
            {:tyyppi :reagent-komponentti
             :piilota-label? true
             :komponentti tr-kentta
             :komponentti-args [e! paallystysilmoituksen-osa]
             :validoi (get-in validoinnit [:perustiedot :tr-osoite])
             :sisallon-leveys? true}
            {:otsikko "Tierekisteriosoite"
             :hae identity
             :fmt tierekisteri-domain/tierekisteriosoite-tekstina
             :muokattava? (constantly false)}))
        (when (or tr-ajorata tr-kaista)
          {:otsikko "Ajorata" :nimi :tr-ajorata :tyyppi :string
           ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6" :muokattava? (constantly false)})

        (when (or tr-ajorata tr-kaista)
          {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :string
           ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6" :muokattava? (constantly false)})
        {:otsikko "Työ aloitettu" :nimi :aloituspvm :tyyppi :pvm
         ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6" :muokattava? (constantly false)}
        {:otsikko "Takuupvm" :nimi :takuupvm :tyyppi :pvm
         ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"
         :varoita [tarkista-takuu-pvm]}
        {:otsikko "Päällystys valmistunut" :nimi :valmispvm-paallystys :tyyppi :pvm
         ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6" :muokattava? (constantly false)}
        {:otsikko "Päällystyskohde valmistunut" :nimi :valmispvm-kohde
         ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"
         :tyyppi :pvm :muokattava? (constantly false)}
        {:otsikko "Toteutunut hinta" :nimi :toteuman-kokonaishinta
         :hae #(-> % laske-hinta :toteuman-kokonaishinta)
         :fmt fmt/euro-opt :tyyppi :numero
         :muokattava? (constantly false)
         ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"}
        (when (and (not= :valmis tila)
                   (not= :lukittu tila))
          {:otsikko "Käsittely"
           :teksti "Valmis tilaajan käsiteltäväksi"
           :nimi :valmis-kasiteltavaksi
           :nayta-rivina? true
           ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"
           :tyyppi :checkbox})
        (when (or (= :valmis tila)
                  (= :lukittu tila))
          {:otsikko "Kommentit" :nimi :kommentit
           ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"
           :tyyppi :komponentti
           :komponentti (fn [_]
                          [kommentit/kommentit
                           {:voi-kommentoida?
                            (not= :lukittu tila)
                            :voi-liittaa? false
                            :palstoja 40
                            :placeholder "Kirjoita kommentti..."
                            :uusi-kommentti (r/wrap uusi-kommentti
                                                    #(muokkaa! assoc-in [:perustiedot :uusi-kommentti] %))}
                           kommentit])})]
       perustiedot-nyt]]

     [:div.col-md-6
      (when nayta-kasittelyosiot?
        [:div
         [kasittely urakka perustiedot-nyt lukittu? muokkaa! huomautukset]
         [asiatarkastus urakka perustiedot-nyt lukittu? muokkaa! huomautukset]])]]))

(defn poista-lukitus [e! urakka]
  (let [paatosoikeus? (oikeudet/on-muu-oikeus? "päätös"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))]
    [:div
     [:div "Tämä ilmoitus on lukittu. Urakanvalvoja voi avata lukituksen."]
     [napit/palvelinkutsu-nappi
      "Avaa lukitus"
      #(go
         (e! (paallystys/->AvaaPaallystysilmoituksenLukitus)))
      {:luokka "nappi-kielteinen avaa-lukitus-nappi"
       :id "poista-paallystysilmoituksen-lukitus"
       :disabled (not paatosoikeus?)
       :ikoni (ikonit/livicon-wrench)
       :virheviesti "Lukituksen avaaminen epäonnistui"}]]))

(defn tayta-fn [avain]
  (fn [toistettava-rivi tama-rivi]
    (assoc tama-rivi avain (avain toistettava-rivi))))

(defn paallystystoimenpiteen-tiedot
  [yllapitokohdeosat-tila paallystystoimenpiteen-tiedot-ohjauskahva false-fn muokkaus-mahdollista?
   paallystystoimenpide-virhe tekninen-osa-voi-muokata? tietojen-validointi]
  (let [toimenpide-paallystetyyppi-valinta-nayta (fn [rivi]
                                                   (if (:koodi rivi)
                                                     (str (:lyhenne rivi) " - " (:nimi rivi))
                                                     (:nimi rivi)))
        toimenpide-tyomenetelma-valinta-nayta (fn [rivi]
                                                (if (:koodi rivi)
                                                  (str (:lyhenne rivi) " - " (:nimi rivi))
                                                  (:nimi rivi)))
        kuulamylly-valinta-nayta #(:nimi %)
        tayta-toimenpide-paallystetyyppi (tayta-fn :toimenpide-paallystetyyppi)
        tayta-raekoko (tayta-fn :toimenpide-raekoko)
        tayta-massamenekki (tayta-fn :massamenekki)
        tayta-rc (tayta-fn :rc%)
        tayta-toimenpide-tyomenetelma (tayta-fn :toimenpide-tyomenetelma)
        tayta-leveys (tayta-fn :leveys)
        tayta-kokonaismassamaara (tayta-fn :kokonaismassamaara)
        tayta-pinta-ala (tayta-fn :pinta-ala)
        tayta-kuulamylly (tayta-fn :kuulamylly)
        tayta-alas?-fn #(not (nil? %))]
    (fn [yllapitokohdeosat-tila paallystystoimenpiteen-tiedot-ohjauskahva false-fn muokkaus-mahdollista?
         paallystystoimenpide-virhe tekninen-osa-voi-muokata? tietojen-validointi]
      [grid/muokkaus-grid
       {:otsikko "Päällystystoimenpiteen tiedot"
        :id "paallystysilmoitus-paallystystoimenpiteet"
        :data-cy "paallystystoimenpiteen-tiedot"
        :ohjaus paallystystoimenpiteen-tiedot-ohjauskahva
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? false-fn
        :voi-muokata? muokkaus-mahdollista?
        :disable-input? true
        :virheet paallystystoimenpide-virhe
        :virhe-viesti (when (and (not muokkaus-mahdollista?)
                                 tekninen-osa-voi-muokata?)
                        "Tarkista kohteen tr-osoite ennen tallentamista")
        :rivinumerot? true
        :jarjesta-avaimen-mukaan identity
        :rivin-avaimet #{:toimenpide-paallystetyyppi :toimenpide-raekoko :massamenekki :rc% :toimenpide-tyomenetelma
                         :leveys :kokonaismassamaara :pinta-ala :kuulamylly}}
       [(assoc paallystys/paallyste-grid-skeema
               :nimi :toimenpide-paallystetyyppi
               :fokus-klikin-jalkeen? true
               :leveys 30
               :tayta-alas? tayta-alas?-fn
               :tayta-fn tayta-toimenpide-paallystetyyppi
               :tayta-sijainti :ylos
               :tayta-tooltip "Kopioi sama toimenpide alla oleville riveille"
               :tayta-alas-toistuvasti? tayta-alas?-fn
               :kentta-arity-3? true
               :valinta-nayta toimenpide-paallystetyyppi-valinta-nayta
               :tayta-toistuvasti-fn tayta-toimenpide-paallystetyyppi)
        (assoc paallystys/raekoko-grid-skeema
               :validoi (:toimenpide-raekoko tietojen-validointi)
               :nimi :toimenpide-raekoko :leveys 10
               :tayta-alas? tayta-alas?-fn
               :tayta-fn tayta-raekoko
               :tayta-sijainti :ylos
               :tayta-tooltip "Kopioi sama raekoko alla oleville riveille"
               :tayta-alas-toistuvasti? tayta-alas?-fn
               :tayta-toistuvasti-fn tayta-raekoko)
        {:otsikko "Massa\u00ADmenek\u00ADki (kg/m²)"
         :nimi :massamenekki
         :tyyppi :positiivinen-numero :desimaalien-maara 0
         :tasaa :oikea :leveys 10
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-massamenekki
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama massamenekki alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "RC-%" :nimi :rc% :leveys 10 :tyyppi :numero :desimaalien-maara 0
         :tasaa :oikea :pituus-max 100
         :validoi (:rc tietojen-validointi)
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-rc
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama RC-% alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        (assoc paallystys/tyomenetelma-grid-skeema
               :nimi :toimenpide-tyomenetelma
               :fokus-klikin-jalkeen? true
               :leveys 30
               :tayta-alas? tayta-alas?-fn
               :tayta-fn tayta-toimenpide-tyomenetelma
               :tayta-sijainti :ylos
               :tayta-tooltip "Kopioi sama työmenetelmä alla oleville riveille"
               :tayta-alas-toistuvasti? tayta-alas?-fn
               :valinta-nayta toimenpide-tyomenetelma-valinta-nayta)
        {:otsikko "Leveys (m)" :nimi :leveys :leveys 10 :tyyppi :positiivinen-numero
         :tasaa :oikea
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-leveys
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama leveys alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "Kohteen kokonais\u00ADmassa\u00ADmäärä (t)" :nimi :kokonaismassamaara
         :tyyppi :positiivinen-numero :tasaa :oikea :leveys 10
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-kokonaismassamaara
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama kokonaismassamäärä alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "Pinta-ala (m²)" :nimi :pinta-ala :leveys 10 :tyyppi :positiivinen-numero
         :tasaa :oikea
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-pinta-ala
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama pinta-ala alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "Kuulamylly"
         :nimi :kuulamylly
         :fokus-klikin-jalkeen? true
         :tyyppi :valinta
         :kentta-arity-3? true
         :valinta-arvo :koodi
         :valinta-nayta kuulamylly-valinta-nayta
         :valinnat pot/+kyylamyllyt-ja-nil+
         :leveys 30
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-kuulamylly
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama kuulamylly alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}]
       yllapitokohdeosat-tila])))

(defn kiviaines-ja-sideaine
  [yllapitokohdeosat-tila kiviaines-ja-sideaine-ohjauskahva false-fn muokkaus-mahdollista? tekninen-osa-voi-muokata? kiviaines-virhe]
  (let [tayta-esiintyma (tayta-fn :esiintyma)
        tayta-km-arvo (tayta-fn :km-arvo)
        tayta-muotoarvo (tayta-fn :muotoarvo)
        tayta-sideainetyyppi (tayta-fn :sideainetyyppi)
        tayta-pitoisuus (tayta-fn :pitoisuus)
        tayta-lisaaineet (tayta-fn :lisaaineet)
        tayta-alas?-fn #(not (nil? %))]
    (fn [yllapitokohdeosat-tila kiviaines-ja-sideaine-ohjauskahva false-fn muokkaus-mahdollista? tekninen-osa-voi-muokata? kiviaines-virhe]
      [grid/muokkaus-grid
       {:otsikko "Kiviaines ja sideaine"
        :data-cy "kiviaines-ja-sideaine"
        :ohjaus kiviaines-ja-sideaine-ohjauskahva
        :rivinumerot? true
        :voi-lisata? false
        :voi-kumota? false
        :voi-poistaa? false-fn
        :voi-muokata? muokkaus-mahdollista?
        :disable-input? true
        :virhe-viesti (when (and (not muokkaus-mahdollista?)
                                 tekninen-osa-voi-muokata?)
                        "Tarkista kohteen tr-osoite ennen tallentamista")
        :virheet kiviaines-virhe
        :jarjesta-avaimen-mukaan identity
        :rivin-avaimet #{:esiintyma :km-arvo :muotoarvo :sideainetyyppi :pitoisuus :lisaaineet}}
       [{:otsikko "Kiviaines\u00ADesiintymä" :nimi :esiintyma :tyyppi :string :pituus-max 256
         :leveys 30
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-esiintyma
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama esiintymä alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "KM-arvo" :nimi :km-arvo :tyyppi :string :pituus-max 256 :leveys 20
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-km-arvo
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama KM-arvo alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "Muoto\u00ADarvo" :nimi :muotoarvo :tyyppi :string :pituus-max 256
         :leveys 20
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-muotoarvo
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama muotoarvo alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "Sideaine\u00ADtyyppi" :nimi :sideainetyyppi :leveys 30
         :tyyppi :valinta
         :fokus-klikin-jalkeen? true
         :valinta-arvo :koodi
         :valinta-nayta #(:nimi %)
         :valinnat pot/+sideainetyypit-ja-nil+
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-sideainetyyppi
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama sideainetyyppi alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "Pitoisuus" :nimi :pitoisuus :leveys 20 :tyyppi :numero :desimaalien-maara 2 :tasaa :oikea
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-pitoisuus
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama pitoisuus alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}
        {:otsikko "Lisä\u00ADaineet" :nimi :lisaaineet :leveys 20 :tyyppi :string
         :pituus-max 256
         :tayta-alas? tayta-alas?-fn
         :tayta-fn tayta-lisaaineet
         :tayta-sijainti :ylos
         :tayta-tooltip "Kopioi sama tieto alla oleville riveille"
         :tayta-alas-toistuvasti? tayta-alas?-fn}]
       yllapitokohdeosat-tila])))

(defn alustalle-tehdyt-toimet
  [alustan-toimet-tila alustalle-tehdyt-toimet-ohjauskahva alustatoimet-voi-muokata? alustan-toimet-virheet
   alustatoimien-validointi tr-osien-pituudet false-fn]
  [:div
   [debug @alustan-toimet-tila {:otsikko "Alustatoimenpiteet"}]
   [:div [grid/muokkaus-grid
          {:otsikko "Alustalle tehdyt toimet"
           :data-cy "alustalle-tehdyt-toimet"
           :ohjaus alustalle-tehdyt-toimet-ohjauskahva
           :jarjesta-avaimen-mukaan identity
           :voi-muokata? alustatoimet-voi-muokata?
           :voi-kumota? false
           :uusi-id (inc (count @alustan-toimet-tila))
           :virheet alustan-toimet-virheet}
          [{:otsikko "Tie" :nimi :tr-numero :tyyppi :positiivinen-numero :leveys 10
            :pituus-max 256
            :validoi (:tr-numero alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Ajorata" :nimi :tr-ajorata :tyyppi :positiivinen-numero :leveys 10
            :pituus-max 256
            :validoi (:tr-ajorata alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :positiivinen-numero :leveys 10
            :pituus-max 256
            :validoi (:tr-kaista alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Aosa" :nimi :tr-alkuosa :tyyppi :positiivinen-numero :leveys 10
            :pituus-max 256
            :validoi (:tr-alkuosa alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Aet" :nimi :tr-alkuetaisyys :tyyppi :positiivinen-numero :leveys 10
            :validoi (:tr-alkuetaisyys alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Losa" :nimi :tr-loppuosa :tyyppi :positiivinen-numero :leveys 10
            :validoi (:tr-loppuosa alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Let" :nimi :tr-loppuetaisyys :leveys 10 :tyyppi :positiivinen-numero
            :validoi (:tr-loppuetaisyys alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Pituus (m)" :nimi :pituus :leveys 10 :tyyppi :numero :tasaa :oikea
            :muokattava? false-fn
            :hae (fn [rivi]
                   (tierekisteri-domain/laske-tien-pituus (get tr-osien-pituudet (:tr-numero rivi)) rivi))
            :validoi (:pituus alustatoimien-validointi)}
           {:otsikko "Käsittely\u00ADmenetelmä"
            :nimi :kasittelymenetelma
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta (fn [rivi]
                             (if rivi
                               (str (:lyhenne rivi) " - " (:nimi rivi))
                               "- Valitse menetelmä -"))
            :valinnat pot/+alustamenetelmat+
            :leveys 30
            :validoi (:kasittelymenetelma alustatoimien-validointi)}
           {:otsikko "Käsit\u00ADtely\u00ADpaks. (cm)" :nimi :paksuus :leveys 15
            :tyyppi :positiivinen-numero :tasaa :oikea
            :desimaalien-maara 0
            :validoi (:paksuus alustatoimien-validointi)}
           {:otsikko "Verkko\u00ADtyyppi"
            :nimi :verkkotyyppi
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta #(:nimi %)
            :valinnat pot/+verkkotyypit-ja-nil+
            :leveys 25}
           {:otsikko "Verkon sijainti"
            :nimi :verkon-sijainti
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta #(:nimi %)
            :valinnat pot/+verkon-sijainnit-ja-nil+
            :leveys 25}
           {:otsikko "Verkon tarkoitus"
            :nimi :verkon-tarkoitus
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta #(:nimi %)
            :valinnat pot/+verkon-tarkoitukset-ja-nil+
            :leveys 25}
           {:otsikko "Tekninen toimen\u00ADpide"
            :nimi :tekninen-toimenpide
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta #(:nimi %)
            :valinnat pot/+tekniset-toimenpiteet-ja-nil+
            :leveys 30}]
          alustan-toimet-tila]]])

(defn paallystysilmoitus-tekninen-osa
  [e! lomakedata-nyt urakka
   muokkaa! tekninen-osa-voi-muokata? alustatoimet-voi-muokata? validoinnit]
  (let [tr-osoite-muokkaus! (fn [uusi-arvo]
                              ;; Ei haluta tallentaa historiaa, kun ollaan kumoamassa historiaa
                              (when-not (:ei-historiaa? (meta uusi-arvo))
                                (e! (paallystys/->TallennaHistoria [:paallystysilmoitus-lomakedata :ilmoitustiedot :osoitteet])))
                              (muokkaa! assoc-in [:ilmoitustiedot :osoitteet]
                                        (if (:ei-historiaa? (meta uusi-arvo))
                                          (vary-meta uusi-arvo dissoc :ei-historiaa?)
                                          uusi-arvo)))
        tr-osoite-virheet-muokkaus! (fn [uusi-arvo]
                                      (muokkaa! assoc-in [:ilmoitustiedot :virheet :alikohteet]
                                                uusi-arvo))
        paallystystoimenpide-virhe-muokkaus! (fn [uusi-arvo]
                                               (muokkaa! assoc-in [:ilmoitustiedot :virheet :paallystystoimenpide]
                                                         uusi-arvo))
        kiviaines-virhe-muokkaus! (fn [uusi-arvo]
                                    (muokkaa! assoc-in [:ilmoitustiedot :virheet :kiviaines]
                                              uusi-arvo))
        alustan-muokkaus! (fn [uusi-arvo]
                            (when-not (:ei-historiaa? (meta uusi-arvo))
                              (e! (paallystys/->TallennaHistoria [:paallystysilmoitus-lomakedata :ilmoitustiedot :alustatoimet])))
                            (muokkaa! assoc-in [:ilmoitustiedot :alustatoimet]
                                      (if (:ei-historiaa? (meta uusi-arvo))
                                        (vary-meta uusi-arvo dissoc :ei-historiaa?)
                                        uusi-arvo)))
        alustan-virheet-muokkaus! (fn [uusi-arvo]
                                    (muokkaa! assoc-in [:ilmoitustiedot :virheet :alustatoimet]
                                              uusi-arvo))
        ;; Grid olettaa saavansa atomin. Siksi näin.
        yllapitokohdeosat-tila (atom (with-meta (get-in lomakedata-nyt [:ilmoitustiedot :osoitteet])
                                                {:jarjesta-gridissa true}))
        yllapitokohdeosat-virhe (atom (get-in lomakedata-nyt [:ilmoitustiedot :virheet :alikohteet]))
        paallystystoimenpide-virhe (atom (get-in lomakedata-nyt [:ilmoitustiedot :virheet :paallystystoimenpide]))
        kiviaines-virhe (atom (get-in lomakedata-nyt [:ilmoitustiedot :virheet :kiviaines]))
        alustan-toimet-tila (atom (get-in lomakedata-nyt [:ilmoitustiedot :alustatoimet]))
        alustan-toimet-virheet (atom (get-in lomakedata-nyt [:ilmoitustiedot :virheet :alustatoimet]))

        paallystystoimenpiteen-tiedot-ohjauskahva (grid/grid-ohjaus)
        kiviaines-ja-sideaine-ohjauskahva (grid/grid-ohjaus)
        alustalle-tehdyt-toimet-ohjauskahva (grid/grid-ohjaus)

        false-fn (constantly false)
        ohjauskahvan-asetus-fn #(e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :tierekisteriosoitteet] %))
        hae-tr-osien-pituudet #(e! (paallystys/->HaeTrOsienPituudet % nil nil))
        muokattava-ajorata-ja-kaista?-fn (fn [rivi]
                                           (let [{:keys [tr-numero tr-ajorata tr-kaista]} (-> @paallystys/tila :paallystysilmoitus-lomakedata :perustiedot)
                                                 paakohteella-ajorata-ja-kaista? (boolean (and tr-ajorata
                                                                                               tr-kaista))
                                                 osan-tie-paakohteella? (= (:tr-numero rivi) tr-numero)]
                                             (if paakohteella-ajorata-ja-kaista?
                                               ;; Pääkohteella ajorata ja kaista, saman tien kohdeosien täytyy siis olla
                                               ;; samalla ajoradalla ja kaistalla. Muiden teiden kohdeosat saa määrittää
                                               ;; vapaasti.
                                               (if osan-tie-paakohteella?
                                                 false
                                                 true)
                                               ;; Pääkohteella ei ajorataa & kaistaa, saa muokata kohdeosille vapaasti
                                               true)))]


    (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :paallystystoimenpiteen-tiedot] paallystystoimenpiteen-tiedot-ohjauskahva))
    (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :kiviaines-ja-sideaine] kiviaines-ja-sideaine-ohjauskahva))
    (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :alustalle-tehdyt-toimet] alustalle-tehdyt-toimet-ohjauskahva))
    (komp/luo
      (komp/sisaan-ulos #(do
                           (add-watch yllapitokohdeosat-tila :pot-yllapitokohdeosat
                                      (fn [_ _ _ uusi-arvo]
                                        (tr-osoite-muokkaus! uusi-arvo)))
                           (add-watch yllapitokohdeosat-virhe :pot-yllapitokohdeosat-virhe
                                      (fn [_ _ _ uusi-arvo]
                                        (tr-osoite-virheet-muokkaus! uusi-arvo)))
                           (add-watch paallystystoimenpide-virhe :pot-paallystystoimenpide-virhe
                                      (fn [_ _ _ uusi-arvo]
                                        (paallystystoimenpide-virhe-muokkaus! uusi-arvo)))
                           (add-watch kiviaines-virhe :pot-kiviaines-virhe
                                      (fn [_ _ _ uusi-arvo]
                                        (kiviaines-virhe-muokkaus! uusi-arvo)))
                           (add-watch alustan-toimet-tila :pot-alustan-toimet-tila
                                      (fn [_ _ _ uusi-arvo]
                                        (alustan-muokkaus! uusi-arvo)))
                           (add-watch alustan-toimet-virheet :pot-alustan-toimet-virheet
                                      (fn [_ _ _ uusi-arvo]
                                        (alustan-virheet-muokkaus! uusi-arvo))))
                        #(do
                           (remove-watch yllapitokohdeosat-tila :pot-yllapitokohdeosat)
                           (remove-watch yllapitokohdeosat-virhe :pot-yllapitokohdeosat-virhe)
                           (remove-watch paallystystoimenpide-virhe :pot-paallystystoimenpide-virhe)
                           (remove-watch kiviaines-virhe :pot-kiviaines-virhe)
                           (remove-watch alustan-toimet-tila :pot-alustan-toimet-tila)
                           (remove-watch alustan-toimet-virheet :pot-alustan-toimet-virheet)))
      (fn [e! {{:keys [tr-osien-pituudet tr-numero tr-ajorata tr-kaista]} :perustiedot
               tr-osien-pituudet :tr-osien-pituudet
               :as lomakedata-nyt}
           urakka
           muokkaa! tekninen-osa-voi-muokata? alustatoimet-voi-muokata?
           {{tietojen-validointi :paallystystoimenpiteen-tiedot alustatoimien-validointi :alustatoimenpiteet} :tekninen-osa :as validoinnit}]
        (let [muokkaus-mahdollista? (and tekninen-osa-voi-muokata?
                                         (empty? (keep #(let [rivin-virheviestit (flatten (vals %))]
                                                          (when-not (empty? rivin-virheviestit)
                                                            rivin-virheviestit))
                                                       (vals (get-in lomakedata-nyt [:ilmoitustiedot :virheet :alikohteet])))))
              lomakedatan-osoitteet (get-in lomakedata-nyt [:ilmoitustiedot :osoitteet])]
          [:fieldset.lomake-osa
           [leijuke/otsikko-ja-vihjeleijuke 3 "Tekninen osa"
            {:otsikko "Päällystysilmoituksen täytön vihjeet"}
            [leijuke/multipage-vihjesisalto
             [:div
              [:h6 "Arvon kopiointi alaspäin"]
              [:figure
               [:img {:src "images/pot_taytto1.gif"
                      ;; Kuva ei lataudu heti -> leijukkeen korkeus määrittyy väärin -> avautumissuunta määrittyy väärin -> asetetaan height
                      :style {:height "260px"}}]
               [:figcaption
                [:p "Voit kopioida kentän arvon alaspäin erillisellä napilla, joka ilmestyy aina kun kenttää ollaan muokkaamassa. Seuraavien rivien arvojen on oltava tyhjiä."]]]]
             [:div
              [:h6 "Arvojen toistaminen alaspäin"]
              [:figure
               [:img {:src "images/pot_taytto2.gif"
                      :style {:height "260px"}}]
               [:figcaption
                [:p "Voit toistaa kentän edelliset arvot alaspäin erillisellä napilla, joka ilmestyy aina kun kenttää ollaan muokkaamassa. Seuraavien rivien arvojen on oltava tyhjiä."]]]]]]
           [yllapitokohteet/yllapitokohdeosat-tuck lomakedata-nyt urakka
            {:rivinumerot? true
             :voi-muokata? tekninen-osa-voi-muokata?
             :validoinnit {:tr-osoitteet (-> validoinnit :tekninen-osa :tr-osoitteet (dissoc :taulukko :rivi))
                           :taulukko (-> validoinnit :tekninen-osa :tr-osoitteet :taulukko)
                           :rivi (-> validoinnit :tekninen-osa :tr-osoitteet :rivi)}
             :vain-nama-validoinnit? true
             :hae-tr-osien-pituudet hae-tr-osien-pituudet
             :muokattava-tie? false-fn
             :kohdeosat yllapitokohdeosat-tila
             :kohdeosat-virheet yllapitokohdeosat-virhe
             :ohjauskahvan-asetus ohjauskahvan-asetus-fn
             :muokattava-ajorata-ja-kaista? muokattava-ajorata-ja-kaista?-fn
             :otsikko "Tierekisteriosoitteet"
             :jarjesta-kun-kasketaan first}]

           [paallystystoimenpiteen-tiedot yllapitokohdeosat-tila paallystystoimenpiteen-tiedot-ohjauskahva false-fn muokkaus-mahdollista?
            paallystystoimenpide-virhe tekninen-osa-voi-muokata? tietojen-validointi]

           [kiviaines-ja-sideaine yllapitokohdeosat-tila kiviaines-ja-sideaine-ohjauskahva false-fn muokkaus-mahdollista? tekninen-osa-voi-muokata? kiviaines-virhe]
           [alustalle-tehdyt-toimet alustan-toimet-tila alustalle-tehdyt-toimet-ohjauskahva alustatoimet-voi-muokata? alustan-toimet-virheet
            alustatoimien-validointi tr-osien-pituudet false-fn]])))))

(defn kumoa
  "Hyvinkin pitkälti sama, kuin harja.ui.historia/kumoa. Ei vain käytetä erikoisatomia"
  [e! historia ohjauskahvat]
  (komp/luo
    (komp/dom-kuuntelija js/window event-type/KEYDOWN
                         (fn [event]
                           (when (and (= "z" (.-key event))
                                      (or (.-ctrlKey event)
                                          (.-metaKey event)))
                             (.stopPropagation event)
                             (.preventDefault event)
                             (e! (paallystys/->HoidaCtrl+Z)))))
    (fn [e! historia]
      [:button.nappi-toissijainen.kumoa-nappi
       {:disabled (empty? historia)
        :on-click #(let [muokattu-osoite (-> historia ffirst last)
                         uusi-app-tila (e! (paallystys/->KumoaHistoria))]
                     (.stopPropagation %)
                     (.preventDefault %)
                     (case muokattu-osoite
                       :osoitteet (let [ohjauskahva (get-in ohjauskahvat [:tierekisteriosoitteet])]
                                    (grid/aseta-muokkaustila! ohjauskahva (with-meta
                                                                            (get-in uusi-app-tila [:paallystysilmoitus-lomakedata :ilmoitustiedot :osoitteet])
                                                                            {:ei-historiaa? true}))
                                    (grid/validoi-grid ohjauskahva)
                                    (grid/validoi-grid (get-in ohjauskahvat [:paallystystoimenpiteen-tiedot]))
                                    (grid/validoi-grid (get-in ohjauskahvat [:kiviaines-ja-sideaine])))
                       :alustatoimet (let [ohjauskahva (get-in ohjauskahvat [:alustalle-tehdyt-toimet])]
                                       (grid/aseta-muokkaustila! ohjauskahva (with-meta
                                                                               (get-in uusi-app-tila [:paallystysilmoitus-lomakedata :ilmoitustiedot :alustatoimet])
                                                                               {:ei-historiaa? true}))
                                       (grid/validoi-grid ohjauskahva))
                       nil))}
       [ikonit/ikoni-ja-teksti [ikonit/kumoa] " Kumoa"]])))


(defn paallystysilmoitus [e! {yllapitokohde-id :yllapitokohde-id
                              {:keys [tr-alkuosa tr-loppuosa tr-numero]} :perustiedot}
                          lukko urakka kayttaja]
  (let [lukon-id (lukko/muodosta-lukon-id "paallystysilmoitus" yllapitokohde-id)
        validoi-kohteen-paallekkaisyys (fn [rivi-indeksi rivi taulukko]
                                         (when (and (:tr-alkuosa rivi) (:tr-alkuetaisyys rivi)
                                                    (:tr-loppuosa rivi) (:tr-loppuetaisyys rivi))
                                           (let [rivit-joilla-tarvittava-tieto (keep (fn [[indeksi kohdeosa]]
                                                                                       (when (and (:tr-alkuosa kohdeosa) (:tr-alkuetaisyys kohdeosa)
                                                                                                  (:tr-loppuosa kohdeosa) (:tr-loppuetaisyys kohdeosa))
                                                                                         (assoc kohdeosa :valiaikainen-id indeksi)))
                                                                                     taulukko)
                                                 paallekkaiset-osat (tr/kohdeosat-keskenaan-paallekkain rivit-joilla-tarvittava-tieto
                                                                                                        :valiaikainen-id
                                                                                                        rivi-indeksi)]
                                             (when-not (empty? paallekkaiset-osat)
                                               (apply str (interpose ", "
                                                                     (map :viesti paallekkaiset-osat)))))))
        paakohteen-sisalla-validointi (fn [rivi taulukko]
                                        ;; Tila pitää dereffata, sillä muuten tähän jää ensimmäinen perustietojen tila
                                        ;; eikä muutokset ilmenny täällä. Ei auta vaikka tämän määrittelisi tuossa vähän
                                        ;; alempana reagent-render fnktiossa, johtuen muokkaus-gridin toteutuksesta.
                                        (let [perustiedot (get-in @paallystys/tila [:paallystysilmoitus-lomakedata :perustiedot])
                                              yllapitokohde (select-keys perustiedot [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys])]
                                          (when (and (= (:tr-numero yllapitokohde) (:tr-numero rivi))
                                                     (every? #(not (nil? %)) (vals (select-keys rivi [:tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys])))
                                                     (not (tr/tr-vali-paakohteen-sisalla? yllapitokohde rivi)))
                                            "Alikohde ei voi olla pääkohteen ulkopuolella")))
        validoi-kohde (fn [viesti-fn]
                        (fn [_ rivi _ tr-osien-pituudet]
                          (let [oikamuotoisuus-virheet (yllapitokohde-domain/oikean-muotoinen-tr rivi)
                                pituus-virheet (yllapitokohde-domain/validoi-tr-osan-pituus tr-osien-pituudet rivi)]
                            (when-not (and (empty? oikamuotoisuus-virheet)
                                           (empty? pituus-virheet))
                              (viesti-fn oikamuotoisuus-virheet pituus-virheet rivi)))))
        validoi-kohteen-alkuosa (validoi-kohde (fn [{tr-alkuosa-muoto-virhe :tr-alkuosa} {tr-alkuosa-pituus-virhe :tr-alkuosa}
                                                    {:keys [tr-numero tr-alkuosa tr-loppuosa]}]
                                                 (when (or tr-alkuosa-muoto-virhe tr-alkuosa-pituus-virhe)
                                                   (cond-> []
                                                           (and tr-alkuosa-pituus-virhe tr-alkuosa) (conj (str "Tiellä " tr-numero " ei ole osaa " tr-alkuosa))
                                                           (and (contains? tr-alkuosa-muoto-virhe :vaara-muotoinen)
                                                                (empty? (:vaara-muotoinen tr-alkuosa-muoto-virhe))) (conj "An\u00ADna al\u00ADku\u00ADo\u00ADsa")
                                                           (and tr-loppuosa (:liian-iso tr-alkuosa-muoto-virhe)) (conj "Al\u00ADku\u00ADo\u00ADsa ei voi olla lop\u00ADpu\u00ADo\u00ADsan jäl\u00ADkeen")))))
        validoi-kohteen-loppuosa (validoi-kohde (fn [{tr-loppuosa-muoto-virhe :tr-loppuosa} {tr-loppuosa-pituus-virhe :tr-loppuosa}
                                                     {:keys [tr-numero tr-loppuosa tr-alkuosa]}]
                                                  (when (or tr-loppuosa-pituus-virhe tr-loppuosa-muoto-virhe)
                                                    (cond-> []
                                                            (and tr-loppuosa-pituus-virhe tr-loppuosa) (conj (str "Tiellä " tr-numero " ei ole osaa " tr-loppuosa))
                                                            (and (contains? tr-loppuosa-muoto-virhe :vaara-muotoinen)
                                                                 (empty? (:vaara-muotoinen tr-loppuosa-muoto-virhe))) (conj "An\u00ADna lop\u00ADpu\u00ADo\u00ADsa")
                                                            (and tr-alkuosa (:liian-pieni tr-loppuosa-muoto-virhe)) (conj "Lop\u00ADpu\u00ADosa ei voi olla al\u00ADku\u00ADo\u00ADsaa ennen")))))
        validoi-kohteen-alkuetaisyys (validoi-kohde (fn [{tr-alkuetaisyys-muoto-vrihe :tr-alkuetaisyys} {tr-alkuetaisyys-pituus-virhe :tr-alkuetaisyys}
                                                         {:keys [tr-alkuosa tr-alkuetaisyys tr-loppuetaisyys]}]
                                                      (when (or tr-alkuetaisyys-pituus-virhe tr-alkuetaisyys-muoto-vrihe)
                                                        (cond-> []
                                                                (and tr-alkuetaisyys-pituus-virhe tr-alkuetaisyys) (conj (str "Osan " tr-alkuosa " maksimietäisyys on " (last (:liian-iso tr-alkuetaisyys-pituus-virhe))))
                                                                (and (contains? tr-alkuetaisyys-muoto-vrihe :vaara-muotoinen)
                                                                     (empty? (:vaara-muotoinen tr-alkuetaisyys-muoto-vrihe))) (conj "An\u00ADna al\u00ADku\u00ADe\u00ADtäi\u00ADsyys")
                                                                (and tr-loppuetaisyys (:liian-iso tr-alkuetaisyys-muoto-vrihe)) (conj "Alku\u00ADe\u00ADtäi\u00ADsyys ei voi olla lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyy\u00ADden jäl\u00ADkeen")))))
        validoi-kohteen-loppuetaisyys (validoi-kohde (fn [{tr-loppuetaisyys-muoto-virhe :tr-loppuetaisyys} {tr-loppuetaisyys-pituus-virhe :tr-loppuetaisyys}
                                                          {:keys [tr-loppuosa tr-loppuetaisyys tr-alkuetaisyys]}]
                                                       (when (or tr-loppuetaisyys-pituus-virhe tr-loppuetaisyys-muoto-virhe)
                                                         (cond-> []
                                                                 (and tr-loppuetaisyys-pituus-virhe tr-loppuetaisyys) (str "Osan " tr-loppuosa " maksimietäisyys on " (last (:liian-iso tr-loppuetaisyys-pituus-virhe)))
                                                                 (and (contains? tr-loppuetaisyys-muoto-virhe :vaara-muotoinen)
                                                                      (empty? (:vaara-muotoinen tr-loppuetaisyys-muoto-virhe))) (conj "An\u00ADna lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys")
                                                                 (and tr-alkuetaisyys (:liian-pieni tr-loppuetaisyys-muoto-virhe)) (conj "Lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys ei voi olla enn\u00ADen al\u00ADku\u00ADe\u00ADtäi\u00ADsyyt\u00ADtä")))))
        muokkaa! (fn [f & args]
                   (e! (paallystys/->PaivitaTila [:paallystysilmoitus-lomakedata] (fn [vanha-arvo]
                                                                                    (apply f vanha-arvo args)))))]
    (komp/luo
      ;; Tässä ilmoituksessa on lukko, jotta vain yksi käyttäjä voi muokata yhtä ilmoitusta kerralla.
      (komp/lukko lukon-id)
      (komp/sisaan #(do
                      (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :historia] '()))
                      (e! (paallystys/->HaeTrOsienPituudet tr-numero tr-alkuosa tr-loppuosa))))
      (fn [e! {:keys [ilmoitustiedot kirjoitusoikeus? yllapitokohdetyyppi perustiedot tr-osien-pituudet historia
                      ohjauskahvat] :as lomakedata-nyt}
           lukko urakka kayttaja]
        (let [{:keys [tila yllapitokohdetyyppi tekninen-osa asiatarkastus
                      valmispvm-kohde]} perustiedot
              lukittu? (lukko/nakyma-lukittu? lukko)
              virheet (conj []
                            (-> perustiedot :tekninen-osa ::lomake/virheet)
                            (-> perustiedot :asiatarkastus ::lomake/virheet)
                            (-> perustiedot ::lomake/virheet)
                            (reduce (fn [kaikki-virheet [taulukon-avain taulukon-virheet]]
                                      (let [taulukon-virheviestit (apply concat
                                                                         (keep #(let [rivin-virheviestit (flatten (vals %))]
                                                                                  (when-not (empty? rivin-virheviestit)
                                                                                    rivin-virheviestit))
                                                                               (vals taulukon-virheet)))]
                                        (if-not (empty? taulukon-virheviestit)
                                          (assoc kaikki-virheet taulukon-avain taulukon-virheviestit)
                                          kaikki-virheet)))
                                    {} (:virheet ilmoitustiedot)))
              tekninen-osa-voi-muokata? (and (not= :lukittu tila)
                                             (not= :hyvaksytty
                                                   (:paatos tekninen-osa))
                                             (false? lukittu?)
                                             kirjoitusoikeus?)
              alustatoimet-voi-muokata? (and tekninen-osa-voi-muokata?
                                             (not (= "sora" yllapitokohdetyyppi)))
              tr-validaattori (partial tierekisteri-domain/tr-vali-paakohteen-sisalla-validaattori lomakedata-nyt)
              ;; Koko POT-lomakkeen validoinnit ja huomautukset on selkeämpää pitää yhdessä paikassa, joten pidetään ne tässä.

              ;; TODO: nämä validoinnit eivät pelkästään validoi vaan tekevät jonku sivueffektin samalla. Yleensä
              ;; palauttavat tekstin, joka näytetään UI:lla.
              ;; Kaikki validoinnit olisi hyvä saada cljc filuun, josta samoja validointeja käyttäisi sekä frontti, back, että API kutsujen
              ;; validointi.
              validoinnit {:tekninen-osa {:tr-osoitteet {:taulukko [{:fn validoi-kohteen-paallekkaisyys
                                                                     :sarakkeet #{:tr-ajorata :tr-kaista :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys}}]
                                                         :rivi [{:fn paakohteen-sisalla-validointi
                                                                 :sarakkeet #{:tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys}}]
                                                         :tr-numero [[:ei-tyhja "Anna tienumero"]]
                                                         :tr-ajorata [[:ei-tyhja "Anna ajorata"]]
                                                         :tr-kaista [[:ei-tyhja "Anna kaista"]]
                                                         :tr-alkuosa [{:fn validoi-kohteen-alkuosa
                                                                       :args [tr-osien-pituudet]}
                                                                      yllapitokohde-domain/alkuosa-ei-lopun-jalkeen]
                                                         :tr-alkuetaisyys [{:fn validoi-kohteen-alkuetaisyys
                                                                            :args [tr-osien-pituudet]}
                                                                           yllapitokohde-domain/alkuetaisyys-ei-lopun-jalkeen]
                                                         :tr-loppuosa [{:fn validoi-kohteen-loppuosa
                                                                        :args [tr-osien-pituudet]}
                                                                       yllapitokohde-domain/loppuosa-ei-alkua-ennen]
                                                         :tr-loppuetaisyys [{:fn validoi-kohteen-loppuetaisyys
                                                                             :args [tr-osien-pituudet]}
                                                                            yllapitokohde-domain/loppuetaisyys-ei-alkua-ennen]}
                                          :paallystystoimenpiteen-tiedot {:rc [[:rajattu-numero 0 100]]
                                                                          :toimenpide-raekoko [[:rajattu-numero 0 99]]}
                                          :alustatoimenpiteet
                                          ;; TODO HAR-7831 Korvaa nykyinen tr_validaattori: tsekkaa, että jonkun kohdeosan sisällä.
                                          {:tr-numero [[:ei-tyhja "Tienumero puuttuu"] #_tr-validaattori]
                                           :tr-ajorata [[:ei-tyhja "Ajorata puuttuu"] #_tr-validaattori]
                                           :tr-kaista [[:ei-tyhja "Kaista puuttuu"] #_tr-validaattori]
                                           :tr-alkuosa [[:ei-tyhja "Alkuosa puuttuu"] #_tr-validaattori]
                                           :tr-alkuetaisyys [[:ei-tyhja "Alkuetäisyys puuttuu"] #_tr-validaattori]
                                           :tr-loppuosa [[:ei-tyhja "Loppuosa puuttuu"] #_tr-validaattori]
                                           :tr-loppuetaisyys [[:ei-tyhja "Loppuetäisyys puuttuu"] #_tr-validaattori]
                                           :pituus [[:ei-tyhja "Tieto puuttuu"]]
                                           :kasittelymenetelma [[:ei-tyhja "Tieto puuttuu"]]
                                           :paksuus [[:ei-tyhja "Tieto puuttuu"]]}}
                           :perustiedot {:tr-osoite [{:fn validoi-kohteen-alkuosa
                                                      :args [tr-osien-pituudet]}
                                                     {:fn validoi-kohteen-alkuetaisyys
                                                      :args [tr-osien-pituudet]}
                                                     {:fn validoi-kohteen-loppuosa
                                                      :args [tr-osien-pituudet]}
                                                     {:fn validoi-kohteen-loppuetaisyys
                                                      :args [tr-osien-pituudet]}]}}
              ;; Tarkista pitäisikö näiden olla ihan virheitä
              huomautukset {:perustiedot {:tekninen-osa {:kasittelyaika (if (:paatos tekninen-osa)
                                                                          [[:ei-tyhja "Anna käsittelypvm"]
                                                                           [:pvm-toisen-pvmn-jalkeen valmispvm-kohde
                                                                            "Käsittely ei voi olla ennen valmistumista"]]
                                                                          [[:pvm-toisen-pvmn-jalkeen valmispvm-kohde
                                                                            "Käsittely ei voi olla ennen valmistumista"]])
                                                         :paatos [[:ei-tyhja "Anna päätös"]]
                                                         :perustelu [[:ei-tyhja "Anna päätöksen selitys"]]}
                                          :asiatarkastus {:tarkastusaika [[:ei-tyhja "Anna tarkastuspäivämäärä"]
                                                                          [:pvm-toisen-pvmn-jalkeen valmispvm-kohde
                                                                           "Tarkastus ei voi olla ennen valmistumista"]]
                                                          :tarkastaja [[:ei-tyhja "Anna tarkastaja"]]}}}
              valmis-tallennettavaksi? (and
                                         (not (= tila :lukittu))
                                         (empty? (flatten (keep vals virheet)))
                                         (false? lukittu?))
              perustiedot-app (select-keys lomakedata-nyt #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})]
          [:div.paallystysilmoituslomake

           [napit/takaisin "Takaisin ilmoitusluetteloon" #(e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata] nil))]

           (when lukittu?
             [lomake/lomake-lukittu-huomautus lukko])

           [:h2 "Päällystysilmoitus"]
           (when (= :lukittu tila)
             [poista-lukitus e! urakka])

           [dom/lataus-komponentille {:viesti "Perustietoja ladataan..."} paallystysilmoitus-perustiedot e! perustiedot-app urakka lukittu? muokkaa! validoinnit huomautukset]

           [:div {:style {:float "right"}}
            [kumoa e! historia ohjauskahvat]]
           [dom/lataus-komponentille {:viesti "Teknisiätietoja ladataan..."} paallystysilmoitus-tekninen-osa e! lomakedata-nyt urakka muokkaa! tekninen-osa-voi-muokata? alustatoimet-voi-muokata? validoinnit]

           [yhteenveto lomakedata-nyt]

           [tallennus e! lomakedata-nyt kayttaja urakka valmis-tallennettavaksi?]])))))

;;;; PAALLYSTYSILMOITUKSET "PÄÄNÄKYMÄ" ;;;;;;;;

(defn- tayta-takuupvm [lahtorivi tama-rivi]
  ;; jos kohteella ei vielä ole POT:ia, ei kopioida takuupvm:ääkään
  (if (:id tama-rivi)
    (assoc tama-rivi :takuupvm (:takuupvm lahtorivi))
    tama-rivi))

(defn- paallystysilmoitukset-taulukko [e! {:keys [urakka paallystysilmoitukset] :as app}]
  (let [urakka-id (:id urakka)]
    [grid/grid
     {:otsikko ""
      :tunniste :paallystyskohde-id
      :tyhja (if (nil? paallystysilmoitukset) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
      :tallenna (fn [rivit]
                  ;; Tässä käytetään go-blockia koska gridi olettaa saavansa kanavan. Paluu arvolla ei tehdä mitään.
                  ;; 'takuupvm-tallennus-kaynnissa-kanava' käytetään sen takia, että gridi pitää 'tallenna' nappia
                  ;; disaploituna niin kauan kuin go-block ei palauta arvoa.
                  (go
                    (let [takuupvm-tallennus-kaynnissa-kanava (chan)]
                      (e! (paallystys/->TallennaPaallystysilmoitustenTakuuPaivamaarat rivit takuupvm-tallennus-kaynnissa-kanava))
                      (<! takuupvm-tallennus-kaynnissa-kanava))))
      :voi-lisata? false
      :voi-kumota? false
      :voi-poistaa? (constantly false)
      :voi-muokata? true
      :data-cy "paallystysilmoitukset-grid"}
     [{:otsikko "Kohde\u00ADnumero" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys 14}
      {:otsikko "Tunnus" :nimi :tunnus :muokattava? (constantly false) :tyyppi :string :leveys 14}
      {:otsikko "YHA-id" :nimi :yhaid :muokattava? (constantly false) :tyyppi :numero :leveys 15}
      {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys 50}
      {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys 20
       :hae (fn [rivi]
              (paallystys-ja-paikkaus/kuvaile-ilmoituksen-tila (:tila rivi)))}
      {:otsikko "Takuupäivämäärä" :nimi :takuupvm :tyyppi :pvm :leveys 20 :muokattava? (fn [t] (not (nil? (:id t))))
       :fmt pvm/pvm-opt
       :tayta-alas? #(not (nil? %))
       :tayta-fn tayta-takuupvm
       :tayta-tooltip "Kopioi sama takuupvm alla oleville kohteille"}
      {:otsikko "Päätös" :nimi :paatos-tekninen-osa :muokattava? (constantly true) :tyyppi :komponentti
       :leveys 20
       :komponentti (fn [rivi]
                      [paallystys-ja-paikkaus/nayta-paatos (:paatos-tekninen-osa rivi)])}
      {:otsikko "Päällystys\u00ADilmoitus" :nimi :paallystysilmoitus :muokattava? (constantly true) :leveys 25
       :tyyppi :komponentti
       :komponentti (fn [rivi]
                      (if (:tila rivi)
                        [:button.nappi-toissijainen.nappi-grid
                         {:on-click #(e! (paallystys/->AvaaPaallystysilmoitus (:paallystyskohde-id rivi)))}
                         [:span (ikonit/eye-open) " Päällystysilmoitus"]]
                        [:button.nappi-toissijainen.nappi-grid {:on-click #(e! (paallystys/->AvaaPaallystysilmoitus (:paallystyskohde-id rivi)))}
                         [:span "Aloita päällystysilmoitus"]]))}]
     paallystysilmoitukset]))

(defn- nayta-lahetystiedot [rivi {kohteet-yha-lahetyksessa :kohteet-yha-lahetyksessa}]
  (if (some #(= % (:paallystyskohde-id rivi)) kohteet-yha-lahetyksessa)
    [:span.tila-odottaa-vastausta "Lähetys käynnissä " [yleiset/ajax-loader-pisteet]]
    (if (:lahetetty rivi)
      (if (:lahetys-onnistunut rivi)
        [:span.tila-lahetetty
         (str "Lähetetty onnistuneesti: " (pvm/pvm-aika (:lahetetty rivi)))]
        [:span.tila-virhe
         (str "Lähetys epäonnistunut: " (pvm/pvm-aika (:lahetetty rivi)))])
      [:span "Ei lähetetty"])))

(defn- yha-lahetykset-taulukko [e! {urakka :urakka {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
                                    paallystysilmoitukset :paallystysilmoitukset :as app}]
  [grid/grid
   {:otsikko ""
    :tyhja (if (nil? paallystysilmoitukset) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
    :tunniste hash}
   [{:otsikko "Kohde\u00ADnumero" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys 12}
    {:otsikko "Tunnus" :nimi :tunnus :muokattava? (constantly false) :tyyppi :string :leveys 14}
    {:otsikko "YHA-id" :nimi :yhaid :muokattava? (constantly false) :tyyppi :numero :leveys 15}
    {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys 45}
    {:otsikko "Edellinen lähetys YHAan" :nimi :edellinen-lahetys :muokattava? (constantly false) :tyyppi :komponentti
     :leveys 45
     :komponentti (fn [rivi] [nayta-lahetystiedot rivi app])}
    {:otsikko "Lähetä YHAan" :nimi :laheta-yhan :muokattava? (constantly false) :leveys 20 :tyyppi :komponentti
     :komponentti (fn [rivi]
                    [yha/yha-lahetysnappi {:oikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet :urakka-id (:id urakka) :sopimus-id (first valittu-sopimusnumero)
                                           :vuosi valittu-urakan-vuosi :paallystysilmoitukset [rivi] :lahetys-kaynnissa-fn #(e! (paallystys/->MuutaTila [:kohteet-yha-lahetyksessa] %))
                                           :kun-onnistuu #(e! (paallystys/->YHAVientiOnnistui %)) :kun-epaonnistuu #(e! (paallystys/->YHAVientiEpaonnistui %))}])}]
   paallystysilmoitukset])

(defn- ilmoitusluettelo
  [e! app]
  (komp/luo
    (fn [e! {urakka :urakka {:keys [valittu-sopimusnumero valittu-urakan-vuosi]} :urakka-tila
             paallystysilmoitukset :paallystysilmoitukset :as app}]
      (let [urakka-id (:id urakka)
            sopimus-id (first valittu-sopimusnumero)]
        [:div
         [:h3 "Päällystysilmoitukset"]
         [paallystysilmoitukset-taulukko e! app]
         [:h3 "YHA-lähetykset"]
         [yleiset/vihje "Ilmoituksen täytyy olla merkitty valmiiksi ja kokonaisuudessaan hyväksytty ennen kuin se voidaan lähettää YHAan."]
         #_[yha/yha-lahetysnappi {:oikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet :urakka-id urakka-id :sopimus-id sopimus-id
                                  :vuosi valittu-urakan-vuosi :paallystysilmoitukset paallystysilmoitukset :lahetys-kaynnissa-fn #(e! (paallystys/->MuutaTila [:kohteet-yha-lahetyksessa] %))
                                  :kun-onnistuu #(e! (paallystys/->YHAVientiOnnistui %)) :kun-epaonnistuu #(e! (paallystys/->YHAVientiEpaonnistui %))}]
         [yha-lahetykset-taulukko e! app]]))))

(defn valinnat [e! {:keys [urakka pot-jarjestys]}]
  [:div
   [valinnat/vuosi {}
    (t/year (:alkupvm urakka))
    (t/year (:loppupvm urakka))
    urakka/valittu-urakan-vuosi
    #(do
       (urakka/valitse-urakan-vuosi! %)
       (e! (paallystys/->HaePaallystysilmoitukset)))]
   [u-valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero (fn [valittu-arvo]
                                                                         ;; Tämänkin voi ottaa pois, jos koko ylläpidon saa
                                                                         ;; joskus refaktoroitua
                                                                         (reset! yllapito-tiedot/kohdenumero valittu-arvo)
                                                                         (e! (paallystys/->SuodataYllapitokohteet)))]
   [u-valinnat/tienumero yllapito-tiedot/tienumero (fn [valittu-arvo]
                                                     ;; Tämänkin voi ottaa pois, jos koko ylläpidon saa
                                                     ;; joskus refaktoroitua
                                                     (reset! yllapito-tiedot/tienumero valittu-arvo)
                                                     (e! (paallystys/->SuodataYllapitokohteet)))]
   [yleiset/pudotusvalikko
    "Järjestä kohteet"
    {:valinta pot-jarjestys
     :valitse-fn #(e! (paallystys/->JarjestaYllapitokohteet %))
     :format-fn {:tila "Tilan mukaan"
                 :kohdenumero "Kohdenumeron mukaan"
                 :muokkausaika "Muokkausajan mukaan"}}
    [:tila :kohdenumero :muokkausaika]]])

(defn paallystysilmoitukset
  [e! app]
  (komp/luo
    (komp/lippu paallystys/paallystysilmoitukset-tai-kohteet-nakymassa?)
    (komp/sisaan-ulos
      (fn []
        (e! (paallystys/->MuutaTila [:paallystysilmoitukset-tai-kohteet-nakymassa?] true))
        (e! (paallystys/->HaePaallystysilmoitukset)))
      (fn []
        (e! (paallystys/->MuutaTila [:paallystysilmoitukset-tai-kohteet-nakymassa?] false))))
    (fn [e! {:keys [paallystysilmoitus-lomakedata lukko urakka kayttaja] :as app}]
      [:div.paallystysilmoitukset
       [kartta/kartan-paikka]
       [debug app {:otsikko "TUCK STATE"}]
       (if paallystysilmoitus-lomakedata
         [paallystysilmoitus e! paallystysilmoitus-lomakedata lukko urakka kayttaja]
         [:div
          [valinnat e! (select-keys app #{:urakka :pot-jarjestys})]
          [ilmoitusluettelo e! app]])])))
