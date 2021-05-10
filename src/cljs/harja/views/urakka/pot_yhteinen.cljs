(ns harja.views.urakka.pot-yhteinen

  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.domain.tierekisteri :as tr]

            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.ui.debug :refer [debug]]
            [harja.ui.komponentti :as komp]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]

            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.kentat :as kentat]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.tiedot.urakka.pot2.pot2-tiedot :as pot2-tiedot])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn tallenna
  [e! {:keys [tekninen-osa tila versio]}
   {:keys [kayttaja urakka-id valmis-tallennettavaksi?]}]
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

     [napit/palvelinkutsu-nappi
      "Tallenna"
      ;; Palvelinkutsunappi olettaa saavansa kanavan. Siksi go.
      #(go
         (if (= 2 versio)
           (e! (pot2-tiedot/->TallennaPot2Tiedot))
           (e! (paallystys/->TallennaPaallystysilmoitus))))
      {:luokka "nappi-ensisijainen"
       :data-cy "pot-tallenna"
       :id "tallenna-paallystysilmoitus"
       :disabled (or (false? valmis-tallennettavaksi?)
                     (not (oikeudet/voi-kirjoittaa?
                            oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                            urakka-id kayttaja)))
       :ikoni (ikonit/tallenna)
       :virheviesti "Tallentaminen epäonnistui"}]]))

(defn poista-lukitus [e! urakka]
  (let [paatosoikeus? (oikeudet/on-muu-oikeus? "päätös"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))]
    [:div
     [yleiset/vihje "Ilmoitus lukittu. Urakanvalvoja voi tarvittaessa avata lukituksen."]
     [napit/palvelinkutsu-nappi
      "Avaa lukitus"
      #(when paatosoikeus?
         (go
           (e! (paallystys/->AvaaPaallystysilmoituksenLukitus))))
      {:luokka "nappi-toissijainen avaa-lukitus-nappi"
       :id "poista-paallystysilmoituksen-lukitus"
       :disabled (not paatosoikeus?)
       :virheviesti "Lukituksen avaaminen epäonnistui"}]]))

(defn tarkista-takuu-pvm [_ {valmispvm-paallystys :valmispvm-paallystys takuupvm :takuupvm}]
  (when (and valmispvm-paallystys
             takuupvm
             (> valmispvm-paallystys takuupvm))
    "Takuupvm on yleensä kohteen valmistumisen jälkeen."))

(defn tr-kentta [{:keys [muokkaa-lomaketta data]} e!
                 {{:keys [tr-numero tr-ajorata tr-kaista tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                          yha-tr-osoite] :as perustiedot} :perustiedot
                  ohjauskahvat :ohjauskahvat}]
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
       [:tr
        [:td
         [kentat/tr-kentan-elementti true muuta! nil
          "Tie" tr-numero :tr-numero true ""]]
        (when tr-ajorata
          [:td
           [kentat/tr-kentan-elementti true muuta! nil
            "Ajorata" tr-ajorata :tr-ajorata false ""]])
        (when tr-kaista
          [:td
           [kentat/tr-kentan-elementti true muuta! nil
            "Kaista" tr-kaista :tr-kaista false ""]])
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
          "Let" tr-loppuetaisyys :tr-loppuetaisyys false ""]]]]]
     (when-not osoite-sama-kuin-yhasta-tuodessa?
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

(defn asiatarkastus
  "Asiatarkastusosio konsultille."
  [urakka {:keys [tila asiatarkastus versio] :as perustiedot-nyt}
   lukittu? muokkaa! {{{:keys [tarkastusaika tarkastaja] :as asiatarkastus-validointi} :asiatarkastus} :perustiedot}]
  (let [pot-tila-lukittu? (= :lukittu tila)
        asiatarkastus-sis-tietoja? (some #(some? (val %))
                                         (lomake/ilman-lomaketietoja asiatarkastus))
        muokattava? (and
                      (oikeudet/on-muu-oikeus? "asiatarkastus"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))
                      (not pot-tila-lukittu?)
                      (false? lukittu?))
        pakolliset-kentat (-> asiatarkastus-validointi meta :pakolliset)]
    [:span
     (when-not (or (= 1 versio) pot-tila-lukittu? asiatarkastus-sis-tietoja?)
       [kentat/tee-kentta {:tyyppi :checkbox
                           :teksti "Näytä asiatarkastus"} tee-asiatarkastus?])
     (when (or (= 1 versio) @tee-asiatarkastus? pot-tila-lukittu? asiatarkastus-sis-tietoja?)
       [:div.pot-asiatarkastus
        [:h6 "Asiatarkastus"]
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
           :fmt #(when-not % "Asiatarkastusta ei hyväksytty")}
          {:otsikko "Lisätiedot"
           :nimi :lisatiedot
           :pakollinen? (pakollinen-kentta? pakolliset-kentat :lisatiedot)
           :tyyppi :text
           :koko [60 3]
           :pituus-max 4096
           :palstoja 2}]
         asiatarkastus]])]))

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
     [:h6 "Käsittely, tekninen osa"]
     [lomake/lomake
      {:otsikko ""
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

(defn paallystyskohteen-fmt
  [{:keys [kohdenumero tunnus kohdenimi]}]
  (str "#" kohdenumero " " tunnus " " kohdenimi))

(defn paallystysilmoitus-perustiedot [e! paallystysilmoituksen-osa urakka
                                      lukittu?
                                      muokkaa!
                                      validoinnit huomautukset]
  (let [false-fn (constantly false)
        muokkaa-fn (fn [uusi]
                     (log "[PÄÄLLYSTYS] Muokataan kohteen tietoja: " (pr-str uusi))
                     (muokkaa! update :perustiedot (fn [vanha]
                                                     (merge vanha uusi))))
        toteuman-kokonaishinta-hae-fn #(-> % laske-hinta :toteuman-kokonaishinta)
        kommentit-komponentti (fn [lomakedata]
                                (let [uusi-kommentti-atom (atom (get-in lomakedata [:data :uusi-kommentti]))]
                                  (komp/luo
                                    (komp/sisaan-ulos #(add-watch uusi-kommentti-atom :pot-perustiedot-uusi-kommentti
                                                                  (fn [_ _ _ uusi-arvo]
                                                                    (muokkaa! assoc-in [:perustiedot :uusi-kommentti] uusi-arvo)))
                                                      #(remove-watch uusi-kommentti-atom :pot-perustiedot-uusi-kommentti))
                                    (fn [{muokkaa-lomaketta :muokkaa-lomaketta
                                          {:keys [tila uusi-kommentti kommentit]} :data}]
                                      [kommentit/kommentit
                                       {:voi-kommentoida?
                                        (not= :lukittu tila)
                                        :voi-liittaa? false
                                        :palstoja 40
                                        :placeholder "Kirjoita kommentti..."
                                        :uusi-kommentti uusi-kommentti-atom}
                                       kommentit]))))]
    (fn [e! {{:keys [tila kohdenumero tunnus kohdenimi tr-numero tr-ajorata tr-kaista
                     tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                     takuupvm versio] :as perustiedot-nyt}
             :perustiedot kirjoitusoikeus? :kirjoitusoikeus?
             ohjauskahvat :ohjauskahvat :as paallystysilmoituksen-osa} urakka
         lukittu?
         muokkaa!
         validoinnit huomautukset]
      (let [pot2? (= 2 versio)
            nayta-kasittelyosiot? (or (= tila :valmis) (= tila :lukittu))
            muokattava? (boolean (and (not= :lukittu tila)
                                      (false? lukittu?)
                                      kirjoitusoikeus?))]
        [:div.row.pot-perustiedot
         [:div.col-sm-12.col-md-6
          [:h6 "Perustiedot"]
          [lomake/lomake {:voi-muokata? muokattava?
                          :muokkaa! muokkaa-fn
                          :kutsu-muokkaa-renderissa? true
                          :validoi-alussa? true
                          :data-cy "paallystysilmoitus-perustiedot"}
           [{:otsikko "Kohde" :nimi :kohde
             :hae paallystyskohteen-fmt
             :muokattava? false-fn
             ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"}
            (merge
              {:nimi :tr-osoite
               ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-12 col-lg-6"}
              (if muokattava?
                {:tyyppi :reagent-komponentti
                 :otsikko "Tierekisteriosoite"
                 :komponentti tr-kentta
                 :komponentti-args [e! paallystysilmoituksen-osa]
                 :validoi (get-in validoinnit [:perustiedot :tr-osoite])
                 :sisallon-leveys? true}
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
            {:otsikko "Työ aloitettu" :nimi :aloituspvm :tyyppi :pvm
             ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6" :muokattava? false-fn}
            {:otsikko "Takuupvm" :nimi :takuupvm :tyyppi :pvm
             ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"
             :varoita [tarkista-takuu-pvm]}
            {:otsikko "Päällystyskohde valmistunut" :nimi :valmispvm-kohde
             ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"
             :tyyppi :pvm :muokattava? false-fn}
            (when-not pot2?
              {:otsikko "Toteutunut hinta" :nimi :toteuman-kokonaishinta
               :hae toteuman-kokonaishinta-hae-fn
               :fmt fmt/euro-opt :tyyppi :numero
               :muokattava? false-fn
               ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"})
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
               :tyyppi :reagent-komponentti
               :komponentti kommentit-komponentti})]
           perustiedot-nyt]]

         [:div.col-md-6
          (when nayta-kasittelyosiot?
            [:div
             [kasittely urakka perustiedot-nyt lukittu? muokkaa! huomautukset]
             [asiatarkastus urakka perustiedot-nyt lukittu? muokkaa! huomautukset]])]]))))