(ns harja.views.urakka.pot-yhteinen

  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.tierekisteri :as tr]

            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.ui.debug :refer [debug]]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]

            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.kentat :as kentat]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot]
            [harja.pvm :as pvm]
            [harja.tiedot.urakka.siirtymat :as siirtymat])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn tallenna
  [e! {:keys [tekninen-osa tila versio]}
   {:keys [kayttaja urakka-id valmis-tallennettavaksi? tallennus-kaynnissa?]}]
  (let [paatos-tekninen-osa (:paatos tekninen-osa)
        huomautusteksti
        (cond
          (= :lukittu tila)
          "Päällystysilmoitus lukittu, tietoja ei voi muokata."

          (and (not= :lukittu tila)
               (= :hyvaksytty paatos-tekninen-osa))
          "Päällystysilmoitus hyväksytty, ilmoitus lukitaan tallennuksen yhteydessä."

          :default nil)]

    [:div.pot-tallennus
     (when huomautusteksti
       [:div {:style {:margin-bottom "24px"}}
        [yleiset/vihje huomautusteksti]])

     [napit/tallenna
      "Tallenna"
      #(do
         (e! (pot2-tiedot/->AsetaTallennusKaynnissa))
         (if (= 2 versio)
           (e! (pot2-tiedot/->TallennaPot2Tiedot))
           (e! (paallystys/->TallennaPaallystysilmoitus))))
      {:luokka "nappi-ensisijainen"
       :data-attributes {:data-cy "pot-tallenna"}
       :id "tallenna-paallystysilmoitus"
       :disabled (or tallennus-kaynnissa?
                   (false? valmis-tallennettavaksi?)
                   (not (oikeudet/voi-kirjoittaa?
                          oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                          urakka-id kayttaja)))
       :ikoni (ikonit/tallenna)}]
     (when tallennus-kaynnissa?
       [yleiset/ajax-loader-pieni "Tallennus käynnissä"])]))

(defn paallystyskohteen-fmt
  [{:keys [kohdenumero tunnus kohdenimi]}]
  (str "#" kohdenumero " " tunnus " " kohdenimi))

(defn poista-lukitus [e! urakka]
  (let [paatosoikeus? (oikeudet/on-muu-oikeus? "päätös" oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))]
    [:div
     [napit/palvelinkutsu-nappi
      "Avaa lukitus"
      #(when paatosoikeus?
         (go
           (e! (paallystys/->AvaaPaallystysilmoituksenLukitus))))
      {:luokka "nappi-toissijainen avaa-lukitus-nappi"
       :id "poista-paallystysilmoituksen-lukitus"
       :disabled (not paatosoikeus?)
       :virheviesti "Lukituksen avaaminen epäonnistui"}]]))

(defn otsikkotiedot [e! {:keys [tila] :as perustiedot} urakka]
  [:span
   [:h1 (str "Päällystysilmoitus - "
             (paallystyskohteen-fmt perustiedot))]
   [:div
    [:div.inline-block
     [:div.inline-block.pot-tila {:class (when tila (name tila))}
      (paallystys-ja-paikkaus/kuvaile-ilmoituksen-tila tila)]
     (when (= :lukittu tila)
       [:div.inline-block
        [yleiset/vihje "Ilmoitus lukittu. Urakanvalvoja voi tarvittaessa avata lukituksen."]])]
    (when (= :lukittu tila)
      [poista-lukitus e! urakka])]])

(defn lahetys-virhe-teksti [{:keys [velho-lahetyksen-aika velho-lahetyksen-vastaus
                                    velho-lahetyksen-tila velho-rivi-lahetyksen-tila
                                    lahetysaika lahetetty lahetys-onnistunut lahetysvirhe] :as lahetyksen-tila}]
  (let [pre-tyyli {:style {:background-color "inherit" :padding-bottom "16px" ;; padding bottom tarpeen koska horizontal scroll bar muuten peittää
                           :max-height "100px" :overflow-y "auto" :border-style "none"}}]
    (when (or (contains? #{"epaonnistunut" "osittain-onnistunut"} velho-lahetyksen-tila)
              (contains? #{"epaonnistunut"} velho-rivi-lahetyksen-tila)
              (and (some? lahetys-onnistunut) (false? lahetys-onnistunut) (some? lahetysvirhe)))
      [:div
       (when (some? lahetysvirhe)
         [:div
          (when lahetetty
            [:p (str "Edellisen kerran lähetetty " (fmt/pvm lahetetty))])
          [:pre pre-tyyli lahetysvirhe]])
       (when (some? velho-lahetyksen-vastaus)
         [:pre pre-tyyli velho-lahetyksen-vastaus])])))

(defn tarkista-takuu-pvm [_ {valmispvm-paallystys :valmispvm-paallystys takuupvm :takuupvm}]
  (when (and valmispvm-paallystys
             takuupvm
             (> valmispvm-paallystys takuupvm))
    "Takuupvm on yleensä kohteen valmistumisen jälkeen."))

(defn tr-kentta [{:keys [muokkaa-lomaketta data]} e!
                 {{:keys [tr-numero tr-ajorata tr-kaista tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                          yha-tr-osoite paikkauskohde-id] :as perustiedot} :perustiedot
                  ohjauskahvat :ohjauskahvat
                  {:keys [vayla-tyyli?]} :optiot}]
  (let [osoite-sama-kuin-yhasta-tuodessa? (tr/sama-tr-osoite? perustiedot yha-tr-osoite)
        muuta!  (fn [kentta]
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
                           (e! (paallystys/->HaeTrOsienTiedot (if (= :tr-numero kentta)
                                                                arvo tr-numero)
                                                              (if (= :tr-alkuosa kentta)
                                                                arvo tr-alkuosa)
                                                              (if (= :tr-loppuosa kentta)
                                                                arvo tr-loppuosa)))
                           ;; when-kääreet koska kyseessä POT1-spesifiset validoinnit
                           (when (:tierekisteriosoitteet ohjauskahvat)
                             (grid/validoi-grid (:tierekisteriosoitteet ohjauskahvat)))
                           (when (:alustalle-tehdyt-toimet ohjauskahvat)
                             (grid/validoi-grid (:alustalle-tehdyt-toimet ohjauskahvat))))))))]
    [:div
     [:table
      [:thead
       [:tr
        [:th "Tie"]
        (when tr-ajorata
          [:th "Ajorata"])
        (when tr-kaista
          [:th "Kaista"])
        [:th "Aosa"]
        [:th "Aet"]
        [:th "Losa"]
        [:th "Let"]]]
      [:tbody
       [:tr (merge {} (when vayla-tyyli? {:class "solujen-valistys-8"}))
        [:td
         [kentat/tr-kentan-elementti true muuta! nil
          "Tie" tr-numero :tr-numero true ""]]
        (when tr-ajorata
          [:td
           [kentat/tr-kentan-elementti true muuta! nil
            "Ajorata" tr-ajorata :tr-ajorata false (or vayla-tyyli? false)]])
        (when tr-kaista
          [:td
           [kentat/tr-kentan-elementti true muuta! nil
            "Kaista" tr-kaista :tr-kaista false (or vayla-tyyli? false)]])
        [:td
         [kentat/tr-kentan-elementti true muuta! nil
          "Aosa" tr-alkuosa :tr-alkuosa false (or vayla-tyyli? false)]]
        [:td
         [kentat/tr-kentan-elementti true muuta! nil
          "Aet" tr-alkuetaisyys :tr-alkuetaisyys false (or vayla-tyyli? false)]]
        [:td
         [kentat/tr-kentan-elementti true muuta! nil
          "Losa" tr-loppuosa :tr-loppuosa false (or vayla-tyyli? false)]]
        [:td
         [kentat/tr-kentan-elementti true muuta! nil
          "Let" tr-loppuetaisyys :tr-loppuetaisyys false (or vayla-tyyli? false)]]]]]
     ;; relevantti vain päällystyskohteissa, halutaan nähdä alkuperäinen YHA TR-osoite
     (when (and (not paikkauskohde-id)
                (not osoite-sama-kuin-yhasta-tuodessa?))
       [:div {:style {:margin-top "4px"}}
        [:label.kentan-label "Alkuperäinen suunniteltu TR-osoite:"]
        [:div {:style {}}
         (tr/tierekisteriosoite-tekstina yha-tr-osoite)]])]))

(defn laske-hinta [lomakedata-nyt]
  (let [urakkasopimuksen-mukainen-kokonaishinta (:kokonaishinta-ilman-maaramuutoksia lomakedata-nyt)
        muutokset-kokonaishintaan (:maaramuutokset lomakedata-nyt)
        toteuman-kokonaishinta (+ urakkasopimuksen-mukainen-kokonaishinta muutokset-kokonaishintaan)]
    {:urakkasopimuksen-mukainen-kokonaishinta urakkasopimuksen-mukainen-kokonaishinta
     :muutokset-kokonaishintaan muutokset-kokonaishintaan
     :toteuman-kokonaishinta toteuman-kokonaishinta}))

(defn pakollinen-kentta?
  [pakolliset-kentat kentta]
  (if (ifn? pakolliset-kentat)
    (not (nil? (pakolliset-kentat kentta)))
    false))

(def tee-asiatarkastus? (atom false))

(defn kasittely-asiatarkastus
  "Asiatarkastusosio konsultille."
  [urakka {:keys [tila asiatarkastus versio] :as perustiedot-nyt}
   lukittu? muokkaa! {{{:keys [tarkastusaika tarkastaja] :as asiatarkastus-validointi} :asiatarkastus} :perustiedot}
   asiatarkastus-sis-tietoja?]
  (let [pot-tila-lukittu? (= :lukittu tila)
        muokattava? (and
                      (oikeudet/on-muu-oikeus? "asiatarkastus"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))
                      (not pot-tila-lukittu?)
                      (false? lukittu?))
        pakolliset-kentat (-> asiatarkastus-validointi meta :pakolliset)]
    (when (or @tee-asiatarkastus? asiatarkastus-sis-tietoja?)
      [:div.pot-asiatarkastus.inline-block
       [lomake/lomake
        {:otsikko "Asiatarkastus"
         :muokkaa! (fn [uusi]
                     (muokkaa! assoc-in [:perustiedot :asiatarkastus] uusi))
         :validoi-alussa? true
         :validoitavat-avaimet #{:pakollinen :validoi}
         :voi-muokata? muokattava?
         :data-cy "paallystysilmoitus-asiatarkastus"}
        [{:otsikko "Tarkastettu" :kaariva-luokka "tarkastusaika"
          :nimi :tarkastusaika  ::lomake/col-luokka "col-sm-6"
          :tyyppi :pvm
          :huomauta tarkastusaika}
         {:otsikko "Tarkastaja" :kaariva-luokka :tarkastaja
          :nimi :tarkastaja  ::lomake/col-luokka "col-sm-6"
          :tyyppi :string
          :huomauta tarkastaja
          :pituus-max 1024}
         {:otsikko "Lisätiedot" :kaariva-luokka "lisatiedot"
          :nimi :lisatiedot
          :tyyppi :text :koko [60 3] :pituus-max 4096
          ::lomake/col-luokka "col-sm-12"}]
        asiatarkastus]])))

(defn kasittely-tekninen-osa
  "Ilmoituksen käsittelyosio, kun ilmoitus on valmis.
  Tilaaja voi muokata, urakoitsija voi tarkastella."
  [urakka {:keys [tila tekninen-osa] :as perustiedot-nyt}
   lukittu? muokkaa! {{{:keys [kasittelyaika paatos perustelu] :as tekninen-osa-validointi} :tekninen-osa} :perustiedot}]
  (let [muokattava? (and
                      (oikeudet/on-muu-oikeus? "päätös"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))
                      (not= tila :lukittu)
                      (false? lukittu?))
        nayta-kasittelyosiot? (#{:valmis :lukittu} tila)]

    [:div.pot-kasittely-tekninen.inline-block
     [lomake/lomake
      {:otsikko "Päätös"
       :muokkaa! #(muokkaa! assoc-in [:perustiedot :tekninen-osa] %)
       :validoi-alussa? true
       :validoitavat-avaimet #{:pakollinen :validoi}
       :voi-muokata? muokattava?
       :data-cy "paallystysilmoitus-kasittelytiedot"}
      [{:otsikko "Käsitelty"
        :nimi :kasittelyaika :kaariva-luokka "kasittelyaika"
        :tyyppi :pvm ::lomake/col-luokka "col-sm-6"
        :huomauta kasittelyaika}
       {:otsikko "Päätös"
        :nimi :paatos
        :tyyppi :valinta ::lomake/col-luokka "col-sm-6"
        :valinnat [:hyvaksytty :hylatty]
        :huomauta paatos
        :valinta-nayta #(cond
                          % (paallystys-ja-paikkaus/kuvaile-paatostyyppi %)
                          muokattava? "- Valitse päätös -"
                          :default "-")
        :palstoja 1}
       {:otsikko "Selitys" :nimi :perustelu :kaariva-luokka "perustelu"
        :tyyppi :text :koko [60 3] :pituus-max 2048
        ::lomake/col-luokka "col-sm-12" :huomauta perustelu}]
      tekninen-osa]]))

(def teksti-hintatiedot-puuttuvat-otsikko
  "Hintatiedot puuttuvat")

(def teksti-hintatiedot-puuttuvat-paallystys
  "Päällystysilmoitusta ei voida merkitä valmiiksi ennen kuin kohteen kokonaishinta on kirjattu Päällystyskohteet-välilehdelle. ")
(def teksti-hintatiedot-puuttuvat-paikkaus
  "Päällystysilmoitusta ei voida merkitä valmiiksi ennen kuin kohteen toteutunut hinta on kirjattu Paikkauskohteet-välilehden kautta. ")

(def teksti-kirjaa-hintatiedot-linkki
  "Kirjaa hintatiedot.")

(defn- toteuman-kokonaishinta-hae-fn
  [tiedot]
  (-> tiedot laske-hinta :toteuman-kokonaishinta))

(defn- hintatiedot-puuttuvat-komp
  [e! toast? paikkauskohde?]
  (let [komponentti  [:span {:class (when toast? "pot2-hintatiedon-toast")}
                      [:span.otsikko {:class (if toast?
                                               "bold inline-block"
                                               "punainen-teksti")}
                       (str teksti-hintatiedot-puuttuvat-otsikko
                            (when-not toast? ". "))]
                      [(if toast? :div :span) {:class "hintatiedot-info"}
                       (if paikkauskohde?
                         teksti-hintatiedot-puuttuvat-paikkaus
                         teksti-hintatiedot-puuttuvat-paallystys)
                       (when-not paikkauskohde?
                         [yleiset/linkki teksti-kirjaa-hintatiedot-linkki
                          #(do
                             ;; Usein käyttäjän flow menee siten, että päällystysilmoitukselta puuttuu kustannustieto. Hän klikkaa tässä handlerissä itsensä syöttämään hintatiedon. POT-lomake jäisi muuten auki vanhalla datalla (ilman kustannustietoa), joten se on suljettava tässä Tuck-eventillä. Kuitenkin siirtymä-funktio on kutsuttava tästä, eikä päällystys-tiedot ns:stä, koska muuten tulisi circular-dependency.
                             (e! (paallystys/->SuljePaallystysilmoitus))
                             (siirtymat/paallystysten-kohdeluetteloon))])]]]
    [:div {:class (when toast? "hintatiedot-puuttuvat-container")}
     (if toast?
       [yleiset/toast-viesti komponentti "varoitus"]
       komponentti)]))

(defn paallystysilmoitus-perustiedot [e! paallystysilmoituksen-osa urakka lukittu?
                                      muokkaa! validoinnit huomautukset paikkauskohteet?]
  (let [false-fn (constantly false)
        muokkaa-fn (fn [uusi ohjauskahva]
                     (log "[PÄÄLLYSTYS] Muokataan kohteen tietoja: " (pr-str uusi))
                     (muokkaa! update :perustiedot (fn [vanha]
                                                     (merge vanha uusi)))
                     ;; päällystekerroksen (alikohteiden) validointi tehtävä uudestaan kun pääkohde muuttuu
                     (when ohjauskahva
                       (grid/validoi-grid ohjauskahva)))]
    (fn [e! {{:keys [tila kohdenumero tunnus kohdenimi tr-numero tr-ajorata tr-kaista
                     tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                     takuupvm versio valmispvm-kohde kokonaishinta-ilman-maaramuutoksia maaramuutokset
                     paikkauskohde-toteutunut-hinta] :as perustiedot-nyt}
             :perustiedot kirjoitusoikeus? :kirjoitusoikeus?
             ohjauskahvat :ohjauskahvat :as paallystysilmoituksen-osa} urakka
         lukittu? muokkaa! validoinnit huomautukset paikkauskohteet?]
      (let [pot2? (= 2 versio)
            muokattava? (boolean (and (not= :lukittu tila)
                                      (false? lukittu?)
                                      kirjoitusoikeus?))]
        [:div.row.pot-perustiedot
         [:div.col-sm-12.col-md-6
          [:h5 "Perustiedot"]
          [lomake/lomake {:voi-muokata? muokattava?
                          :muokkaa! #(muokkaa-fn % (:paallystekerros ohjauskahvat))
                          :kutsu-muokkaa-renderissa? true
                          :validoi-alussa? true
                          :data-cy "paallystysilmoitus-perustiedot"}
           [{:otsikko "Kohde" :nimi :kohde
             :label-ja-kentta-samalle-riville? true
             :hae paallystyskohteen-fmt
             :muokattava? false-fn
             ::lomake/col-luokka "col-xs-12"}
            (merge
              {:nimi :tr-osoite
               :label-ja-kentta-samalle-riville? true
               ::lomake/col-luokka "col-xs-12"}
              (if muokattava?
                {:tyyppi :reagent-komponentti
                 :otsikko "Tierekisteriosoite"
                 :komponentti tr-kentta
                 :komponentti-args [e! (merge paallystysilmoituksen-osa {:optiot {:vayla-tyyli? true}})]
                 :validoi (get-in validoinnit [:perustiedot :tr-osoite])}
                {:otsikko "Tierekisteriosoite"
                 :hae identity
                 :fmt tr/tierekisteriosoite-tekstina
                 :muokattava? false-fn}))
            (when (or tr-ajorata tr-kaista)
              {:otsikko "Ajorata" :nimi :tr-ajorata :tyyppi :string
               ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6" :muokattava? false-fn})

            (when (or tr-ajorata tr-kaista)
              {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :string
               ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6" :muokattava? false-fn})
            (when pot2?
              {:otsikko "Hintatiedot kirjattu" :nimi :kokonaishinta
               :label-ja-kentta-samalle-riville? true
               :hae #(if-not paikkauskohteet?
                       (toteuman-kokonaishinta-hae-fn {:kokonaishinta-ilman-maaramuutoksia kokonaishinta-ilman-maaramuutoksia
                                                       :maaramuutokset maaramuutokset})
                       paikkauskohde-toteutunut-hinta)
               :fmt (fn [hinta]
                      (if (and hinta (> hinta 0))
                        "Kyllä"
                        [hintatiedot-puuttuvat-komp e! false paikkauskohteet?]))
               :tyyppi :numero
               :muokattava? false-fn
               ::lomake/col-luokka "col-xs-12"})
            (when-not pot2?
              {:otsikko "Toteutunut hinta" :nimi :kokonaishinta
               :hae #(toteuman-kokonaishinta-hae-fn {:kokonaishinta-ilman-maaramuutoksia kokonaishinta-ilman-maaramuutoksia
                                                     :maaramuutokset maaramuutokset})
               :fmt fmt/euro-opt
               :tyyppi :numero :muokattava? false-fn
               ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"})
            {:otsikko "Työ aloitettu" :tyyppi :pvm :nimi :aloituspvm
             :label-ja-kentta-samalle-riville? true
             :ikoni-sisaan? true :vayla-tyyli? true :pakollinen? true
             ::lomake/col-luokka "col-xs-12"}
            {:otsikko "Kohde valmistunut" :tyyppi :pvm :nimi :valmispvm-kohde :pakollinen? true
             :label-ja-kentta-samalle-riville? true :ikoni-sisaan? true :vayla-tyyli? true
             :validoi [[:pvm-kentan-jalkeen :aloituspvm "Valmistumisen pitää olla työn alkamisen jälkeen"]]
             ::lomake/col-luokka "col-xs-12"}
            (if paikkauskohteet?
              {:otsikko "Takuuaika" :tyyppi :valinta :nimi :takuuaika :label-ja-kentta-samalle-riville? true
               :valinnat {0 "Ei takuuaikaa"
                          1 "1 vuosi"
                          2 "2 vuotta"
                          3 "3 vuotta"}
               :valinta-arvo first :valinta-nayta second :vayla-tyyli? true :pakollinen? true
               ::lomake/col-luokka "col-xs-12"}
              {:otsikko "Takuuaika" :nimi :takuupvm :tyyppi :valinta :vayla-tyyli? true
               :valinnat (paallystys/takuupvm-valinnat takuupvm)
               :valinta-nayta (fn [valinta]
                                (if muokattava?
                                  (:fmt valinta)
                                  (pvm/pvm (:pvm valinta))))
               :valinta-arvo :pvm
               :label-ja-kentta-samalle-riville? true
               :tarkenne (fn [valinta]
                           (when valinta
                             [:span.takuupvm-tarkenne (pvm/pvm (:pvm valinta))]))
               ::lomake/col-luokka "col-xs-12"
               :varoita [tarkista-takuu-pvm]})]
           perustiedot-nyt]]]))))


(defn kasittely [e! {:keys [perustiedot] :as app} urakka lukittu?
                 muokkaa! validoinnit huomautukset]
  (let [{:keys [tila asiatarkastus versio]} perustiedot
        nayta-kasittelyosiot? (#{:valmis :lukittu} tila)
        asiatarkastus-sis-tietoja? (some #(some? (val %))
                                         (lomake/ilman-lomaketietoja asiatarkastus))]
    (fn [e! {:keys [perustiedot] :as app} urakka lukittu?
         muokkaa! validoinnit huomautukset]
      (let [kokonaishinta (toteuman-kokonaishinta-hae-fn perustiedot)
            paikkauskohde? (boolean (:paikkauskohde-id perustiedot))
            paikkauskohde-toteutunut-hinta (:paikkauskohde-toteutunut-hinta perustiedot)
            hinta-puuttuu? (if paikkauskohde?
                             (not (and paikkauskohde-toteutunut-hinta (> paikkauskohde-toteutunut-hinta 0)))
                             (not (and kokonaishinta (> kokonaishinta 0))))]
        [:div.kasittelyosio
         (when hinta-puuttuu?
           [hintatiedot-puuttuvat-komp e! true paikkauskohde?])
         (if-not nayta-kasittelyosiot?
           [kentat/tee-kentta {:tyyppi :checkbox
                               :teksti "Merkitse valmiiksi tarkistusta varten"
                               :disabled? hinta-puuttuu?}
            (r/wrap (get-in app [:perustiedot :valmis-kasiteltavaksi])
                    (fn [uusi]
                      (e! (paallystys/->AsetaKasiteltavaksi uusi))))]
           [:span
            [:h5 "Käsittely"]
            [:span.asiatarkastus-checkbox
             (when-not (or (= :lukittu tila) asiatarkastus-sis-tietoja?)
               [kentat/tee-kentta {:tyyppi :checkbox
                                   :teksti "Kaksi tarkastajaa (asiatarkastus erikseen)"} tee-asiatarkastus?])
             [:div.pot-kasittely
              [kasittely-asiatarkastus urakka perustiedot lukittu? muokkaa! huomautukset asiatarkastus-sis-tietoja?]
              [kasittely-tekninen-osa urakka perustiedot lukittu? muokkaa! huomautukset]]]])]))))
