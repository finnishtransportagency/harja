(ns harja.views.urakka.pot1-lomake

  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
            [cljs.core.async :refer [<! chan]]
            [cljs.spec.alpha :as s]
            [cljs-time.core :as t]
            [goog.events.EventType :as event-type]

            [harja.ui.grid :as grid]
            [harja.ui.debug :refer [debug]]
            [harja.ui.dom :as dom]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.komponentti :as komp]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko virheen-ohje] :as yleiset]

            [harja.domain.paallystysilmoitus :as pot]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.tierekisteri :as tr]

            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.muokkauslukko :as lukko]
            [harja.fmt :as fmt]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.views.urakka.pot-yhteinen :as pot-yhteinen]
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(defn yhteenveto [lomakedata-nyt]
  (let [{:keys [urakkasopimuksen-mukainen-kokonaishinta
                muutokset-kokonaishintaan
                toteuman-kokonaishinta]}
        (pot-yhteinen/laske-hinta lomakedata-nyt)]
    [yleiset/taulukkotietonakyma {}
     "Urakkasopimuksen mukainen kokonaishinta: "
     (fmt/euro-opt (or urakkasopimuksen-mukainen-kokonaishinta 0))

     (str "Määrämuutosten vaikutus kokonaishintaan"
          (when (:maaramuutokset-ennustettu? lomakedata-nyt) " (ennustettu)")
          ": ")
     (fmt/euro-opt (or muutokset-kokonaishintaan 0))

     "Yhteensä: "
     (fmt/euro-opt toteuman-kokonaishinta)]))


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
                         :leveys :kokonaismassamaara :pinta-ala :kuulamylly}
        :validoi-alussa? true}
       [(assoc paallystys/paallyste-grid-skeema
          :nimi :toimenpide-paallystetyyppi :elementin-id "pt-paallyste"
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
         :tyyppi :positiivinen-numero :desimaalien-maara 2
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
          :nimi :toimenpide-tyomenetelma :elementin-id "pt-tyomenetelma"
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
        :validoi-alussa? true
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
           :rivi-validointi (:rivi alustatoimien-validointi)
           :taulukko-validointi (:taulukko alustatoimien-validointi)
           :ohjaus alustalle-tehdyt-toimet-ohjauskahva
           :jarjesta-avaimen-mukaan identity
           :voi-muokata? alustatoimet-voi-muokata?
           :voi-kumota? false
           :validoi-alussa? true
           :uusi-id (inc (count @alustan-toimet-tila))
           :virheet alustan-toimet-virheet}
          [{:otsikko "Tie" :nimi :tr-numero :tyyppi :positiivinen-numero :leveys 10
            :pituus-max 256
            :validoi (:tr-numero alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Ajorata" :nimi :tr-ajorata :tyyppi :valinta :leveys 10
            :valinnat pot/+ajoradat-numerona+
            :valinta-arvo :koodi
            :valinta-nayta (fn [rivi]
                             (if rivi
                               (:nimi rivi)
                               "- Valitse Ajorata -"))
            :pituus-max 256 :elementin-id "alustan-ajorata"
            :validoi (:tr-ajorata alustatoimien-validointi)
            :tasaa :oikea
            :kokonaisluku? true}
           {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :valinta :leveys 10
            :pituus-max 256 :elementin-id "alustan-kaista"
            :valinnat pot/+kaistat+
            :valinta-arvo :koodi
            :valinta-nayta (fn [rivi]
                             (if rivi
                               (:nimi rivi)
                               "- Valitse kaista -"))
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
                   (tr/laske-tien-pituus (into {}
                                               (map (juxt key (comp :pituus val)))
                                               (get tr-osien-pituudet (:tr-numero rivi)))
                                         rivi))
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
                              ;; Muokataan itse dataa
                              (muokkaa! assoc-in [:ilmoitustiedot :osoitteet]
                                        (if (:ei-historiaa? (meta uusi-arvo))
                                          (vary-meta uusi-arvo dissoc :ei-historiaa?)
                                          uusi-arvo))
                              ;; Validoidaan alustatoimenpiteet
                              (when-let [ohjauskahva (get-in @paallystys/tila [:paallystysilmoitus-lomakedata :ohjauskahvat :alustalle-tehdyt-toimet])]
                                (grid/validoi-grid ohjauskahva)))
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
      (komp/piirretty (fn [_]
                        (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :validoi-lomake?] true))))
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


(defn pot1-lomake [e! {yllapitokohde-id :yllapitokohde-id
                       {:keys [tr-alkuosa tr-loppuosa tr-numero]} :perustiedot}
                   lukko urakka kayttaja]
  (let [lukon-id (lukko/muodosta-lukon-id "paallystysilmoitus" yllapitokohde-id)
        muokkaa! (fn [f & args]
                   (e! (paallystys/->PaivitaTila [:paallystysilmoitus-lomakedata] (fn [vanha-arvo]
                                                                                    (apply f vanha-arvo args)))))
        alikohteen-validointi (fn [rivi taulukko]
                                (let [{:keys [perustiedot vuodet tr-osien-tiedot]} (:paallystysilmoitus-lomakedata @paallystys/tila)
                                      paakohde (select-keys perustiedot tr/paaluvali-avaimet)
                                      vuosi (first vuodet)
                                      ;; Kohteiden päällekkyys keskenään validoidaan taulukko tasolla, jotta rivin päivittämine oikeaksi korjaa
                                      ;; myös toisilla riveillä olevat validoinnit.
                                      validoitu (if (= (:tr-numero paakohde) (:tr-numero rivi))
                                                  (yllapitokohde-domain/validoi-alikohde paakohde rivi [] (get tr-osien-tiedot (:tr-numero rivi)) vuosi)
                                                  (yllapitokohde-domain/validoi-muukohde paakohde rivi [] (get tr-osien-tiedot (:tr-numero rivi)) vuosi))]
                                  (yllapitokohde-domain/validoitu-kohde-tekstit (dissoc validoitu :alikohde-paallekkyys :muukohde-paallekkyys) false)))
        kohde-toisten-kanssa-paallekkain-validointi (fn [alikohde? _ rivi taulukko]
                                                      (let [toiset-alikohteet (keep (fn [[indeksi kohdeosa]]
                                                                                      (when (and (:tr-alkuosa kohdeosa) (:tr-alkuetaisyys kohdeosa)
                                                                                                 (:tr-loppuosa kohdeosa) (:tr-loppuetaisyys kohdeosa)
                                                                                                 (not= kohdeosa rivi))
                                                                                        kohdeosa))
                                                                                    taulukko)
                                                            paallekkyydet (filter #(yllapitokohde-domain/tr-valit-paallekkain? rivi %)
                                                                                  toiset-alikohteet)]
                                                        (if alikohde?
                                                          (yllapitokohde-domain/validoitu-kohde-tekstit {(if (= (:tr-numero rivi) (-> @paallystys/tila :paallystysilmoitus-lomakedata :perustiedot :tr-numero))
                                                                                                           :alikohde-paallekkyys
                                                                                                           :muukohde-paallekkyys)
                                                                                                         paallekkyydet}
                                                                                                        (not alikohde?))
                                                          (yllapitokohde-domain/validoi-alustatoimenpide-teksti {:alustatoimenpide-paallekkyys paallekkyydet}))))
        alustatoimen-validointi (fn [rivi taulukko]
                                  (let [{:keys [ilmoitustiedot vuodet tr-osien-tiedot]} (:paallystysilmoitus-lomakedata @paallystys/tila)
                                        alikohteet (vals (:osoitteet ilmoitustiedot))
                                        vuosi (first vuodet)
                                        validoitu (yllapitokohde-domain/validoi-alustatoimenpide alikohteet [] rivi [] (get tr-osien-tiedot (:tr-numero rivi)) [[]] vuosi)]
                                    (yllapitokohde-domain/validoi-alustatoimenpide-teksti (dissoc validoitu :alustatoimenpide-paallekkyys))))
        arvo-valilta (fn [min-arvo max-arvo data _ _]
                       (when-not (<= min-arvo data max-arvo)
                         (str "Anna arvo välillä " min-arvo " - " max-arvo "")))]
    (komp/luo
      ;; Tässä ilmoituksessa on lukko, jotta vain yksi käyttäjä voi muokata yhtä ilmoitusta kerralla.
      (komp/lukko lukon-id)
      (komp/sisaan #(do
                      (nav/vaihda-kartan-koko! :S)
                      (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :historia] '()))
                      (e! (paallystys/->HaeTrOsienPituudet tr-numero tr-alkuosa tr-loppuosa))
                      (e! (paallystys/->HaeTrOsienTiedot tr-numero tr-alkuosa tr-loppuosa))))
      (fn [e! {:keys [ilmoitustiedot kirjoitusoikeus? yllapitokohdetyyppi perustiedot tr-osien-pituudet historia
                      ohjauskahvat validoi-lomake?] :as lomakedata-nyt}
           lukko urakka kayttaja]
        (when validoi-lomake?
          (when-let [ohjauskahva (:tierekisteriosoitteet ohjauskahvat)]
            (grid/validoi-grid ohjauskahva))
          (when-let [ohjauskahva (:paallystystoimenpiteen-tiedotin ohjauskahvat)]
            (grid/validoi-grid ohjauskahva))
          (when-let [ohjauskahva (:kiviaines-ja-sideaine ohjauskahvat)]
            (grid/validoi-grid ohjauskahva))
          (when-let [ohjauskahva (:alustalle-tehdyt-toimet ohjauskahvat)]
            (grid/validoi-grid ohjauskahva))
          (e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata :validoi-lomake?] false)))
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
              tr-validaattori (partial tr/tr-vali-paakohteen-sisalla-validaattori lomakedata-nyt)
              ;; Koko POT-lomakkeen validoinnit ja huomautukset on selkeämpää pitää yhdessä paikassa, joten pidetään ne tässä.

              validoinnit {:tekninen-osa {:tr-osoitteet {:rivi [{:fn alikohteen-validointi
                                                                 :sarakkeet {:tr-numero :tr-numero
                                                                             :tr-ajorata :tr-ajorata
                                                                             :tr-kaista :tr-kaista
                                                                             :tr-alkuosa :tr-alkuosa
                                                                             :tr-alkuetaisyys :tr-alkuetaisyys
                                                                             :tr-loppuosa :tr-loppuosa
                                                                             :tr-loppuetaisyys :tr-loppuetaisyys}}]
                                                         :taulukko [{:fn (r/partial kohde-toisten-kanssa-paallekkain-validointi true)
                                                                     :sarakkeet {:tr-numero :tr-numero
                                                                                 :tr-ajorata :tr-ajorata
                                                                                 :tr-kaista :tr-kaista
                                                                                 :tr-alkuosa :tr-alkuosa
                                                                                 :tr-alkuetaisyys :tr-alkuetaisyys
                                                                                 :tr-loppuosa :tr-loppuosa
                                                                                 :tr-loppuetaisyys :tr-loppuetaisyys}}]}
                                          :paallystystoimenpiteen-tiedot {:rc [{:fn (r/partial arvo-valilta 0 100)}]
                                                                          :toimenpide-raekoko [{:fn (r/partial arvo-valilta 0 99)}]}
                                          :alustatoimenpiteet {:rivi [{:fn alustatoimen-validointi
                                                                       :sarakkeet {:tr-numero :tr-numero
                                                                                   :tr-ajorata :tr-ajorata
                                                                                   :tr-kaista :tr-kaista
                                                                                   :tr-alkuosa :tr-alkuosa
                                                                                   :tr-alkuetaisyys :tr-alkuetaisyys
                                                                                   :tr-loppuosa :tr-loppuosa
                                                                                   :tr-loppuetaisyys :tr-loppuetaisyys
                                                                                   :kasittelymenetelma :kasittelymenetelma
                                                                                   :paksuus :paksuus}}]
                                                               :taulukko [{:fn (r/partial kohde-toisten-kanssa-paallekkain-validointi false)
                                                                           :sarakkeet {:tr-numero :tr-numero
                                                                                       :tr-ajorata :tr-ajorata
                                                                                       :tr-kaista :tr-kaista
                                                                                       :tr-alkuosa :tr-alkuosa
                                                                                       :tr-alkuetaisyys :tr-alkuetaisyys
                                                                                       :tr-loppuosa :tr-loppuosa
                                                                                       :tr-loppuetaisyys :tr-loppuetaisyys}}]}}
                           :perustiedot paallystys/perustietojen-validointi}

              huomautukset (paallystys/perustietojen-huomautukset tekninen-osa valmispvm-kohde)
              valmis-tallennettavaksi? (and
                                         (not (= tila :lukittu))
                                         (empty? (flatten (keep vals virheet)))
                                         (false? lukittu?))
              perustiedot-app (select-keys lomakedata-nyt #{:perustiedot :kirjoitusoikeus? :ohjauskahvat})]
          [:div.paallystysilmoituslomake

           [napit/takaisin "Takaisin ilmoitusluetteloon" #(e! (paallystys/->MuutaTila [:paallystysilmoitus-lomakedata] nil))]

           (when lukittu?
             [lomake/lomake-lukittu-huomautus lukko])

           [:h1 "Päällystysilmoitus"]
           (when (= :lukittu tila)
             [pot-yhteinen/poista-lukitus e! urakka])

           [dom/lataus-komponentille {:viesti "Perustietoja ladataan..."} pot-yhteinen/paallystysilmoitus-perustiedot e! perustiedot-app urakka lukittu? muokkaa! validoinnit huomautukset]

           [:div {:style {:float "right"}}
            [kumoa e! historia ohjauskahvat]]
           [dom/lataus-komponentille {:viesti "Teknisiätietoja ladataan..."} paallystysilmoitus-tekninen-osa e! lomakedata-nyt urakka muokkaa! tekninen-osa-voi-muokata? alustatoimet-voi-muokata? validoinnit]

           [yhteenveto lomakedata-nyt]

           [debug virheet]
           [tallennus e! lomakedata-nyt kayttaja urakka valmis-tallennettavaksi?]])))))