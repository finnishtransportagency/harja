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
        [{:otsikko "Tarkastettu"
          :nimi :tarkastusaika  ::lomake/col-luokka "col-sm-6"
          :pakollinen? (pakollinen-kentta? pakolliset-kentat :tarkastusaika)
          :tyyppi :pvm
          :huomauta tarkastusaika}
         {:otsikko "Tarkastaja"
          :nimi :tarkastaja  ::lomake/col-luokka "col-sm-6"
          :pakollinen? (pakollinen-kentta? pakolliset-kentat :tarkastaja)
          :tyyppi :string
          :huomauta tarkastaja
          :pituus-max 1024}
         {:otsikko "Lisätiedot"
          :nimi :lisatiedot
          :pakollinen? (pakollinen-kentta? pakolliset-kentat :lisatiedot)
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
      [(lomake/rivi
         {:otsikko "Käsitelty"
          :nimi :kasittelyaika
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
          :palstoja 1})
       {:otsikko "Selitys" :nimi :perustelu
        :tyyppi :text :koko [60 3] :pituus-max 2048
        ::lomake/col-luokka "col-sm-12" :huomauta perustelu}]
      tekninen-osa]]))

(defn paallystysilmoitus-perustiedot [e! paallystysilmoituksen-osa urakka lukittu?
                                      muokkaa! validoinnit huomautukset]
  (let [false-fn (constantly false)
        muokkaa-fn (fn [uusi]
                     (log "[PÄÄLLYSTYS] Muokataan kohteen tietoja: " (pr-str uusi))
                     (muokkaa! update :perustiedot (fn [vanha]
                                                     (merge vanha uusi))))
        toteuman-kokonaishinta-hae-fn #(-> % laske-hinta :toteuman-kokonaishinta)]
    (fn [e! {{:keys [tila kohdenumero tunnus kohdenimi tr-numero tr-ajorata tr-kaista
                     tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                     takuupvm versio] :as perustiedot-nyt}
             :perustiedot kirjoitusoikeus? :kirjoitusoikeus?
             ohjauskahvat :ohjauskahvat :as paallystysilmoituksen-osa} urakka
         lukittu?
         muokkaa!
         validoinnit huomautukset]
      (let [pot2? (= 2 versio)
            muokattava? (boolean (and (not= :lukittu tila)
                                      (false? lukittu?)
                                      kirjoitusoikeus?))]
        [:div.row.pot-perustiedot
         [:div.col-sm-12.col-md-6
          [:h5 "Perustiedot"]
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
               ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"})]
           perustiedot-nyt]]]))))

(defn kasittely [e! {:keys [perustiedot] :as app} urakka lukittu?
                 muokkaa! validoinnit huomautukset]
  (let [{:keys [tila asiatarkastus versio]} perustiedot
        nayta-kasittelyosiot? (#{:valmis :lukittu} tila)
        asiatarkastus-sis-tietoja? (some #(some? (val %))
                                         (lomake/ilman-lomaketietoja asiatarkastus))]

    (fn [e! {:keys [perustiedot] :as app} urakka lukittu?
         muokkaa! validoinnit huomautukset]
      [:div
       [:h5 "Käsittely"]
       (if-not nayta-kasittelyosiot?
         [kentat/tee-kentta {:tyyppi :checkbox
                             :teksti "Merkitse valmiiksi tarkistusta varten"}
          (r/wrap (get-in app [:perustiedot :valmis-kasiteltavaksi])
                  (fn [uusi]
                    (e! (paallystys/->AsetaKasiteltavaksi uusi))))]
         [:span.asiatarkastus-checkbox
          (when-not (or (= :lukittu tila) asiatarkastus-sis-tietoja?)
            [kentat/tee-kentta {:tyyppi :checkbox
                                :teksti "Täytä asiatarkastuskin"} tee-asiatarkastus?])
          [:div.pot-kasittely
           [kasittely-asiatarkastus urakka perustiedot lukittu? muokkaa! huomautukset asiatarkastus-sis-tietoja?]
           [kasittely-tekninen-osa urakka perustiedot lukittu? muokkaa! huomautukset]]])])))