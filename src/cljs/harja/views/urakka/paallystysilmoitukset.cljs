(ns harja.views.urakka.paallystysilmoitukset
  "Urakan päällystysilmoitukset"
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]
            [cljs-time.core :as t]
            [goog.events.EventType :as event-type]

            [harja.ui.grid :as grid]
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
                      (false? lukittu?))
        pakolliset-kentat (-> tekninen-osa-validointi meta :pakolliset)]
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
        :pakollinen? (pakollinen-kentta? pakolliset-kentat :kasittelyaika)
        :tyyppi :pvm
        :huomauta kasittelyaika}

       {:otsikko "Päätös"
        :nimi :paatos
        :pakollinen? (pakollinen-kentta? pakolliset-kentat :paatos)
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
          :pakollinen? (pakollinen-kentta? pakolliset-kentat :perustelu)
          :tyyppi :text
          :koko [60 3]
          :pituus-max 2048
          :palstoja 2
          :huomauta perustelu})]
      tekninen-osa]]))

(defn tallennus
  [e! {kayttaja :kayttaja {urakka-id :id :as urakka} :urakka {:keys [tekninen-osa tila]} :paallystysilmoitus-lomakedata :as app} valmis-tallennettavaksi?]
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

(defn paallystysilmoitus-perustiedot [e! {{perustiedot :perustiedot} :paallystysilmoitus-lomakedata}
                                      lukittu?
                                      muokkaa!
                                      validoinnit huomautukset]
  (fn [e! {urakka :urakka
           {{:keys [tila kohdenumero tunnus kohdenimi tr-numero tr-ajorata tr-kaista
                    tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys
                    uusi-kommentti kommentit takuupvm] :as perustiedot-nyt}
            :perustiedot kirjoitusoikeus? :kirjoitusoikeus?
            ohjauskahvat :ohjauskahvat :as paallystysilmoitus-nyt} :paallystysilmoitus-lomakedata}
       lukittu?
       muokkaa!
       validoinnit huomautukset]
    (let [nayta-kasittelyosiot? (or (= tila :valmis) (= tila :lukittu))
          muokattava? (boolean (and (not= :lukittu tila)
                                    (false? lukittu?)
                                    kirjoitusoikeus?))]
      [:div.row
       [:div.col-md-6
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
             ::lomake/col-luokka "col-xs-12 col-sm-6 col-md-6 col-lg-6"}
            (if muokattava?
              {:tyyppi :reagent-komponentti
               :komponentti tr-kentta
               :komponentti-args [e! paallystysilmoitus-nyt]
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
           [asiatarkastus urakka perustiedot-nyt lukittu? muokkaa! huomautukset]])]])))

(defn poista-lukitus [e! {urakka :urakka :as app}]
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

(defn paallystysilmoitus-tekninen-osa
  [e! {lomakedata-nyt :paallystysilmoitus-lomakedata :as app}
   muokkaa! tekninen-osa-voi-muokata? alustatoimet-voi-muokata? validoinnit]
  (let [tr-osoite-muokkaus! (fn [uusi-arvo]
                              ;; Ei haluta tallentaa historiaa, kun ollaan kumoamassa historiaa
                              (when-not (:ei-historiaa? (meta uusi-arvo))
                                (e! (paallystys/->TallennaHistoria [:paallystysilmoitus-lomakedata :ilmoitustiedot :osoitteet])))
                              (muokkaa! assoc-in [:ilmoitustiedot :osoitteet]
                                        uusi-arvo))
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
                                      uusi-arvo))
        alustan-virheet-muokkaus! (fn [uusi-arvo]
                                    (muokkaa! assoc-in [:ilmoitustiedot :virheet :alustatoimet]
                                              uusi-arvo))
        paallystystoimenpiteen-tiedot-ohjauskahva (grid/grid-ohjaus)
        kiviaines-ja-sideaine-ohjauskahva (grid/grid-ohjaus)
        alustalle-tehdyt-toimet-ohjauskahva (grid/grid-ohjaus)]
    (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :paallystystoimenpiteen-tiedot] paallystystoimenpiteen-tiedot-ohjauskahva))
    (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :kiviaines-ja-sideaine] kiviaines-ja-sideaine-ohjauskahva))
    (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :alustalle-tehdyt-toimet] alustalle-tehdyt-toimet-ohjauskahva))
    (fn [e! {urakka :urakka
             {{:keys [tr-osien-pituudet tr-numero tr-ajorata tr-kaista]} :perustiedot
              tr-osien-pituudet :tr-osien-pituudet
              :as lomakedata-nyt} :paallystysilmoitus-lomakedata :as app}
         muokkaa! tekninen-osa-voi-muokata? alustatoimet-voi-muokata?
         {{tietojen-validointi :paallystystoimenpiteen-tiedot alustatoimien-validointi :alustatoimenpiteet} :tekninen-osa :as validoinnit}]
      (let [muokkaus-mahdollista? (and tekninen-osa-voi-muokata?
                                       (empty? (keep #(let [rivin-virheviestit (flatten (vals %))]
                                                        (when-not (empty? rivin-virheviestit)
                                                          rivin-virheviestit))
                                                     (vals (get-in lomakedata-nyt [:ilmoitustiedot :virheet :alikohteet])))))
            paakohteella-ajorata-ja-kaista? (boolean (and tr-ajorata
                                                          tr-kaista))
            lomakedatan-osoitteet (get-in lomakedata-nyt [:ilmoitustiedot :osoitteet])
            ;; Grid olettaa saavansa atomin. Tämän takia pitää tehdä tämmöiset wrapit.
            yllapitokohdeosat-tila (r/wrap (if (nil? (meta lomakedatan-osoitteet))
                                             (with-meta lomakedatan-osoitteet
                                                        {:jarjesta-gridissa true})
                                             lomakedatan-osoitteet)
                                           (fn [uusi-arvo]
                                             (tr-osoite-muokkaus! uusi-arvo)))
            yllapitokohdeosat-virhe (r/wrap (get-in lomakedata-nyt [:ilmoitustiedot :virheet :alikohteet])
                                            (fn [uusi-arvo]
                                              (tr-osoite-virheet-muokkaus! uusi-arvo)))
            paallystystoimenpide-virhe (r/wrap (get-in lomakedata-nyt [:ilmoitustiedot :virheet :paallystystoimenpide])
                                               (fn [uusi-arvo]
                                                 (paallystystoimenpide-virhe-muokkaus! uusi-arvo)))
            kiviaines-virhe (r/wrap (get-in lomakedata-nyt [:ilmoitustiedot :virheet :kiviaines])
                                    (fn [uusi-arvo]
                                      (kiviaines-virhe-muokkaus! uusi-arvo)))
            alustan-toimet-tila (r/wrap (get-in lomakedata-nyt [:ilmoitustiedot :alustatoimet])
                                        (fn [uusi-arvo]
                                          (alustan-muokkaus! uusi-arvo)))
            alustan-toimet-virheet (r/wrap (get-in lomakedata-nyt [:ilmoitustiedot :virheet :alustatoimet])
                                           (fn [uusi-arvo]
                                             (alustan-virheet-muokkaus! uusi-arvo)))]
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
         [yllapitokohteet/yllapitokohdeosat-tuck app {:rivinumerot? true
                                                      :voi-muokata? tekninen-osa-voi-muokata?
                                                      :validoinnit {:tr-osoitteet (-> validoinnit :tekninen-osa :tr-osoitteet (dissoc :taulukko :rivi))
                                                                    :taulukko (-> validoinnit :tekninen-osa :tr-osoitteet :taulukko)
                                                                    :rivi (-> validoinnit :tekninen-osa :tr-osoitteet :rivi)}
                                                      :vain-nama-validoinnit? true
                                                      :hae-tr-osien-pituudet #(e! (paallystys/->HaeTrOsienPituudet % nil nil))
                                                      :muokattava-tie? (fn [rivi]
                                                                         false)
                                                      :kohdeosat yllapitokohdeosat-tila
                                                      :kohdeosat-virheet yllapitokohdeosat-virhe
                                                      :ohjauskahvan-asetus #(e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :ohjauskahvat :tierekisteriosoitteet] %))
                                                      :muokattava-ajorata-ja-kaista? (fn [rivi]
                                                                                       (let [osan-tie-paakohteella? (= (:tr-numero rivi) tr-numero)]
                                                                                         (if paakohteella-ajorata-ja-kaista?
                                                                                           ;; Pääkohteella ajorata ja kaista, saman tien kohdeosien täytyy siis olla
                                                                                           ;; samalla ajoradalla ja kaistalla. Muiden teiden kohdeosat saa määrittää
                                                                                           ;; vapaasti.
                                                                                           (if osan-tie-paakohteella?
                                                                                             false
                                                                                             true)
                                                                                           ;; Pääkohteella ei ajorataa & kaistaa, saa muokata kohdeosille vapaasti
                                                                                           true)))
                                                      :otsikko "Tierekisteriosoitteet"
                                                      :jarjesta-kun-kasketaan first}]

         [grid/muokkaus-grid
          {:otsikko "Päällystystoimenpiteen tiedot"
           :id "paallystysilmoitus-paallystystoimenpiteet"
           :data-cy "paallystystoimenpiteen-tiedot"
           :ohjaus paallystystoimenpiteen-tiedot-ohjauskahva
           :voi-lisata? false
           :voi-kumota? false
           :voi-poistaa? (constantly false)
           :voi-muokata? muokkaus-mahdollista?
           :virheet paallystystoimenpide-virhe
           :virhe-viesti (when (and (not muokkaus-mahdollista?)
                                    tekninen-osa-voi-muokata?)
                           "Tierekisterikohteet taulukko on virheellisessä tilassa")
           :rivinumerot? true
           :jarjesta-avaimen-mukaan identity}
          [(assoc paallystys/paallyste-grid-skeema
                  :nimi :toimenpide-paallystetyyppi
                  :leveys 30
                  :tayta-alas? #(not (nil? %))
                  :tayta-fn (fn [lahtorivi tama-rivi]
                              (assoc tama-rivi :toimenpide-paallystetyyppi (:toimenpide-paallystetyyppi lahtorivi)))
                  :tayta-sijainti :ylos
                  :tayta-tooltip "Kopioi sama toimenpide alla oleville riveille"
                  :tayta-alas-toistuvasti? #(not (nil? %))
                  :tayta-toistuvasti-fn
                  (fn [toistettava-rivi tama-rivi]
                    (assoc tama-rivi :toimenpide-paallystetyyppi (:toimenpide-paallystetyyppi toistettava-rivi))))
           (assoc paallystys/raekoko-grid-skeema
                  :validoi (:toimenpide-raekoko tietojen-validointi)
                  :nimi :toimenpide-raekoko :leveys 10
                  :tayta-alas? #(not (nil? %))
                  :tayta-fn (fn [lahtorivi tama-rivi]
                              (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko lahtorivi)))
                  :tayta-sijainti :ylos
                  :tayta-tooltip "Kopioi sama raekoko alla oleville riveille"
                  :tayta-alas-toistuvasti? #(not (nil? %))
                  :tayta-toistuvasti-fn
                  (fn [toistettava-rivi tama-rivi]
                    (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko toistettava-rivi))))
           {:otsikko "Massa\u00ADmenek\u00ADki (kg/m²)"
            :nimi :massamenekki
            :tyyppi :positiivinen-numero :desimaalien-maara 0
            :tasaa :oikea :leveys 10
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :massamenekki (:massamenekki lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama massamenekki alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :massamenekki (:massamenekki toistettava-rivi)))}
           {:otsikko "RC-%" :nimi :rc% :leveys 10 :tyyppi :numero :desimaalien-maara 0
            :tasaa :oikea :pituus-max 100
            :validoi (:rc tietojen-validointi)
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :rc% (:rc% lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama RC-% alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko toistettava-rivi)))}
           (assoc paallystys/tyomenetelma-grid-skeema
                  :nimi :toimenpide-tyomenetelma
                  :leveys 30
                  :tayta-alas? #(not (nil? %))
                  :tayta-fn (fn [lahtorivi tama-rivi]
                              (assoc tama-rivi :toimenpide-tyomenetelma (:toimenpide-tyomenetelma lahtorivi)))
                  :tayta-sijainti :ylos
                  :tayta-tooltip "Kopioi sama työmenetelmä alla oleville riveille"
                  :tayta-alas-toistuvasti? #(not (nil? %))
                  :tayta-toistuvasti-fn
                  (fn [toistettava-rivi tama-rivi]
                    (assoc tama-rivi :toimenpide-tyomenetelma (:toimenpide-tyomenetelma toistettava-rivi))))
           {:otsikko "Leveys (m)" :nimi :leveys :leveys 10 :tyyppi :positiivinen-numero
            :tasaa :oikea
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :leveys (:leveys lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama leveys alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :leveys (:leveys toistettava-rivi)))}
           {:otsikko "Kohteen kokonais\u00ADmassa\u00ADmäärä (t)" :nimi :kokonaismassamaara
            :tyyppi :positiivinen-numero :tasaa :oikea :leveys 10
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :kokonaismassamaara (:kokonaismassamaara lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama kokonaismassamäärä alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :kokonaismassamaara (:kokonaismassamaara toistettava-rivi)))}
           {:otsikko "Pinta-ala (m²)" :nimi :pinta-ala :leveys 10 :tyyppi :positiivinen-numero
            :tasaa :oikea
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :pinta-ala (:pinta-ala lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama pinta-ala alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :pinta-ala (:pinta-ala toistettava-rivi)))}
           {:otsikko "Kuulamylly"
            :nimi :kuulamylly
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta #(:nimi %)
            :valinnat pot/+kyylamyllyt-ja-nil+
            :leveys 30
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :kuulamylly (:kuulamylly lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama kuulamylly alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :kuulamylly (:kuulamylly toistettava-rivi)))}]
          yllapitokohdeosat-tila]

         [grid/muokkaus-grid
          {:otsikko "Kiviaines ja sideaine"
           :data-cy "kiviaines-ja-sideaine"
           :ohjaus kiviaines-ja-sideaine-ohjauskahva
           :rivinumerot? true
           :voi-lisata? false
           :voi-kumota? false
           :voi-poistaa? (constantly false)
           :voi-muokata? muokkaus-mahdollista?
           :virhe-viesti (when (and (not muokkaus-mahdollista?)
                                    tekninen-osa-voi-muokata?)
                           "Tierekisterikohteet taulukko on virheellisessä tilassa")
           :virheet kiviaines-virhe
           :jarjesta-avaimen-mukaan identity}
          [{:otsikko "Kiviaines\u00ADesiintymä" :nimi :esiintyma :tyyppi :string :pituus-max 256
            :leveys 30
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :esiintyma (:esiintyma lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama esiintymä alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko toistettava-rivi)))}
           {:otsikko "KM-arvo" :nimi :km-arvo :tyyppi :string :pituus-max 256 :leveys 20
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :km-arvo (:km-arvo lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama KM-arvo alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko toistettava-rivi)))}
           {:otsikko "Muoto\u00ADarvo" :nimi :muotoarvo :tyyppi :string :pituus-max 256
            :leveys 20
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :muotoarvo (:muotoarvo lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama muotoarvo alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko toistettava-rivi)))}
           {:otsikko "Sideaine\u00ADtyyppi" :nimi :sideainetyyppi :leveys 30
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta #(:nimi %)
            :valinnat pot/+sideainetyypit-ja-nil+
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :sideainetyyppi (:sideainetyyppi lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama sideainetyyppi alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko toistettava-rivi)))}
           {:otsikko "Pitoisuus" :nimi :pitoisuus :leveys 20 :tyyppi :numero :desimaalien-maara 2 :tasaa :oikea
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :pitoisuus (:pitoisuus lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama pitoisuus alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko toistettava-rivi)))}
           {:otsikko "Lisä\u00ADaineet" :nimi :lisaaineet :leveys 20 :tyyppi :string
            :pituus-max 256
            :tayta-alas? #(not (nil? %))
            :tayta-fn (fn [lahtorivi tama-rivi]
                        (assoc tama-rivi :lisaaineet (:lisaaineet lahtorivi)))
            :tayta-sijainti :ylos
            :tayta-tooltip "Kopioi sama tieto alla oleville riveille"
            :tayta-alas-toistuvasti? #(not (nil? %))
            :tayta-toistuvasti-fn
            (fn [toistettava-rivi tama-rivi]
              (assoc tama-rivi :toimenpide-raekoko (:toimenpide-raekoko toistettava-rivi)))}]
          yllapitokohdeosat-tila]
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
                   :muokattava? (constantly false)
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
                 alustan-toimet-tila]]]]))))

(defn kumoa
  "Hyvinkin pitkälti sama, kuin harja.ui.historia/kumoa. Ei vain käytetä erikoisatomia"
  [e! app]
  (komp/luo
    (komp/dom-kuuntelija js/window event-type/KEYDOWN
                         (fn [event]
                           (when (and (= 90 (.-keyCode event))
                                      (or (.-ctrlKey event)
                                          (.-metaKey event)))
                             (.stopPropagation event)
                             (.preventDefault event)
                             (e! (paallystys/->HoidaCtrl+Z)))))
    (fn [e! {{historia :historia} :paallystysilmoitus-lomakedata :as app}]
      [:button.nappi-toissijainen.kumoa-nappi
       {:disabled (empty? historia)
        :on-click #(let [muokattu-osoite (-> app (get-in [:paallystysilmoitus-lomakedata :historia]) ffirst last)
                         uusi-app-tila (e! (paallystys/->KumoaHistoria))]
                     (.stopPropagation %)
                     (.preventDefault %)
                     (case muokattu-osoite
                       :osoitteet (let [ohjauskahva (get-in app [:paallystysilmoitus-lomakedata :ohjauskahvat :tierekisteriosoitteet])]
                                    (grid/aseta-muokkaustila! ohjauskahva (with-meta
                                                                            (get-in uusi-app-tila [:paallystysilmoitus-lomakedata :ilmoitustiedot :osoitteet])
                                                                            {:ei-historiaa? true}))
                                    (grid/validoi-grid ohjauskahva)
                                    (grid/validoi-grid (get-in app [:paallystysilmoitus-lomakedata :ohjauskahvat :paallystystoimenpiteen-tiedot]))
                                    (grid/validoi-grid (get-in app [:paallystysilmoitus-lomakedata :ohjauskahvat :kiviaines-ja-sideaine])))
                       :alustatoimet (let [ohjauskahva (get-in app [:paallystysilmoitus-lomakedata :ohjauskahvat :alustalle-tehdyt-toimet])]
                                       (grid/aseta-muokkaustila! ohjauskahva (with-meta
                                                                               (get-in uusi-app-tila [:paallystysilmoitus-lomakedata :ilmoitustiedot :alustatoimet])
                                                                               {:ei-historiaa? true}))
                                       (grid/validoi-grid ohjauskahva))
                       nil))}
       [ikonit/ikoni-ja-teksti [ikonit/kumoa] " Kumoa"]])))


(defn paallystysilmoitus [e! {{yllapitokohde-id :yllapitokohde-id
                               {:keys [tr-alkuosa tr-loppuosa tr-numero]} :perustiedot} :paallystysilmoitus-lomakedata :as app}]
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
        tyhja-tr-arvo (fn [kentta viesti tr rivi taulukko]
                        (when (nil? (kentta tr))
                          viesti))]
    (komp/luo
      ;; Tässä ilmoituksessa on lukko, jotta vain yksi käyttäjä voi muokata yhtä ilmoitusta kerralla.
      (komp/lukko lukon-id)
      (komp/sisaan #(do
                      (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :historia] '()))
                      (e! (paallystys/->HaeTrOsienPituudet tr-numero tr-alkuosa tr-loppuosa))))
      (fn [e! {lukko :lukko urakka :urakka
               {:keys [ilmoitustiedot kirjoitusoikeus? yllapitokohdetyyppi perustiedot tr-osien-pituudet] :as lomakedata-nyt} :paallystysilmoitus-lomakedata :as app}]
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
              muokkaa! (fn [f & args]
                         (e! (paallystys/->PaivitaTila [:paallystysilmoitus-lomakedata] (fn [vanha-arvo]
                                                                                          (apply f vanha-arvo args)))))
              validoi-kohteen-osoite (fn [kentta _ rivi _]
                                       (when (kentta rivi)
                                         (yllapitokohde-domain/validoi-yllapitokohteen-osoite tr-osien-pituudet kentta rivi)))
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
                                                         :tr-alkuosa [(partial validoi-kohteen-osoite :tr-alkuosa)
                                                                      [:ei-tyhja "An\u00ADna al\u00ADku\u00ADo\u00ADsa"]
                                                                      yllapitokohde-domain/alkuosa-ei-lopun-jalkeen]
                                                         :tr-alkuetaisyys [(partial validoi-kohteen-osoite :tr-alkuetaisyys)
                                                                           [:ei-tyhja "An\u00ADna al\u00ADku\u00ADe\u00ADtäi\u00ADsyys"]
                                                                           yllapitokohde-domain/alkuetaisyys-ei-lopun-jalkeen]
                                                         :tr-loppuosa [(partial validoi-kohteen-osoite :tr-loppuosa)
                                                                       [:ei-tyhja "An\u00ADna lop\u00ADpu\u00ADo\u00ADsa"]
                                                                       yllapitokohde-domain/loppuosa-ei-alkua-ennen]
                                                         :tr-loppuetaisyys [(partial validoi-kohteen-osoite :tr-loppuetaisyys)
                                                                            [:ei-tyhja "An\u00ADna lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys"]
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
                           :perustiedot {:tr-osoite [(partial validoi-kohteen-osoite :tr-alkuosa)
                                                     (partial validoi-kohteen-osoite :tr-alkuetaisyys)
                                                     (partial validoi-kohteen-osoite :tr-loppuosa)
                                                     (partial validoi-kohteen-osoite :tr-loppuetaisyys)
                                                     (partial tyhja-tr-arvo :tr-alkuosa "An\u00ADna al\u00ADku\u00ADo\u00ADsa")
                                                     (partial tyhja-tr-arvo :tr-alkuetaisyys "An\u00ADna al\u00ADku\u00ADe\u00ADtäi\u00ADsyys")
                                                     (partial tyhja-tr-arvo :tr-loppuosa "An\u00ADna lop\u00ADpu\u00ADo\u00ADsa")
                                                     (partial tyhja-tr-arvo :tr-loppuetaisyys "An\u00ADna lop\u00ADpu\u00ADe\u00ADtäi\u00ADsyys")]}}
              ;; Tarkista pitäisikö näiden olla ihan virheitä
              huomautukset {:perustiedot {:tekninen-osa (with-meta
                                                          {:kasittelyaika (if (:paatos tekninen-osa)
                                                                            [[:ei-tyhja "Anna käsittelypvm"]
                                                                             [:pvm-toisen-pvmn-jalkeen valmispvm-kohde
                                                                              "Käsittely ei voi olla ennen valmistumista"]]
                                                                            [[:pvm-toisen-pvmn-jalkeen valmispvm-kohde
                                                                              "Käsittely ei voi olla ennen valmistumista"]])
                                                           :paatos [[:ei-tyhja "Anna päätös"]]
                                                           :perustelu [[:ei-tyhja "Anna päätöksen selitys"]]}
                                                          {:pakolliset #{(when (:paatos tekninen-osa)
                                                                           :kasittelyaika)}})
                                          :asiatarkastus {:tarkastusaika [[:ei-tyhja "Anna tarkastuspäivämäärä"]
                                                                          [:pvm-toisen-pvmn-jalkeen valmispvm-kohde
                                                                           "Tarkastus ei voi olla ennen valmistumista"]]
                                                          :tarkastaja [[:ei-tyhja "Anna tarkastaja"]]}}}
              valmis-tallennettavaksi? (and
                                         (not (= tila :lukittu))
                                         (empty? (flatten (keep vals virheet)))
                                         (false? lukittu?))]
          [:div.paallystysilmoituslomake

           [napit/takaisin "Takaisin ilmoitusluetteloon" #(e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata] nil))]

           (when lukittu?
             [lomake/lomake-lukittu-huomautus lukko])

           [:h2 "Päällystysilmoitus"]
           (when (= :lukittu tila)
             [poista-lukitus e! app])

           [paallystysilmoitus-perustiedot e! app lukittu? muokkaa! validoinnit huomautukset]

           [:div {:style {:float "right"}}
            [kumoa e! app]]
           [paallystysilmoitus-tekninen-osa e! app muokkaa! tekninen-osa-voi-muokata? alustatoimet-voi-muokata? validoinnit]

           [yhteenveto lomakedata-nyt]

           [tallennus e! app valmis-tallennettavaksi?]])))))

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
    (komp/kuuntelija :avaa-paallystysilmoitus
                     (fn [_ rivi]
                       (e! (paallystys/->AvaaPaallystysilmoitus (:paallystyskohde-id rivi)))))
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

(defn valinnat [e! {:keys [urakka] :as app}]
  [:div
   [valinnat/vuosi {}
    (t/year (:alkupvm urakka))
    (t/year (:loppupvm urakka))
    urakka/valittu-urakan-vuosi
    #(do
       (urakka/valitse-urakan-vuosi! %)
       (e! (paallystys/->HaePaallystysilmoitukset)))]
   [u-valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero (fn [_]
                                                                         (e! (paallystys/->SuodataYllapitokohteet)))]
   [u-valinnat/tienumero yllapito-tiedot/tienumero (fn [_]
                                                     (e! (paallystys/->SuodataYllapitokohteet)))]
   [yleiset/pudotusvalikko
    "Järjestä kohteet"
    {:valinta @yllapito-tiedot/kohdejarjestys
     :valitse-fn #(reset! yllapito-tiedot/kohdejarjestys %)
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
    (fn [e! {:keys [paallystysilmoitus-lomakedata] :as app}]
      [:div.paallystysilmoitukset
       [kartta/kartan-paikka]
       [debug app {:otsikko "TUCK STATE"}]
       (if paallystysilmoitus-lomakedata
         [paallystysilmoitus e! app]
         [:div
          [valinnat e! app]
          [ilmoitusluettelo e! app]])])))
