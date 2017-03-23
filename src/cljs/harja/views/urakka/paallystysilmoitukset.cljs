(ns harja.views.urakka.paallystysilmoitukset
  "Urakan päällystysilmoitukset"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<!]]

            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.lomake :as lomake]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.kommentit :as kommentit]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.historia :as historia]

            [harja.domain.paallystysilmoitus :as pot]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]

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
            [harja.views.urakka.yllapitokohteet :as yllapitokohteet]
            [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
            [harja.tiedot.urakka :as urakka]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.yhatuonti :as yha]
            [harja.pvm :as pvm]
            [harja.atom :as atom]
            [harja.tyokalut.vkm :as vkm]

            [harja.ui.debug :refer [debug]]
            [harja.ui.viesti :as viesti]
            [harja.ui.valinnat :as valinnat]
            [cljs-time.core :as t]
            [harja.tiedot.urakka.yllapito :as yllapito-tiedot]
            [harja.views.urakka.valinnat :as u-valinnat])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn laske-hinta [lomakedata-nyt]
  (let [urakkasopimuksen-mukainen-kokonaishinta (:kokonaishinta lomakedata-nyt)
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

(defn asiatarkastus
  "Asiatarkastusosio konsultille."
  [urakka {:keys [tila asiatarkastus] :as lomakedata-nyt}
   lukittu? muokkaa!]
  (log "ASIATARKASTUS " (pr-str asiatarkastus))
  (let [muokattava? (and
                      (oikeudet/on-muu-oikeus? "asiatarkastus"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))
                      (not= tila :lukittu)
                      (false? lukittu?))]

    [:div.pot-asiatarkastus
     [:h3 "Asiatarkastus"]

     [lomake/lomake
      {:otsikko ""
       :muokkaa! (fn [uusi]
                   (muokkaa! assoc :asiatarkastus uusi))
       :voi-muokata? muokattava?}
      [{:otsikko "Tarkastettu"
        :nimi :tarkastusaika
        :tyyppi :pvm
        :validoi [[:ei-tyhja "Anna tarkastuspäivämäärä"]
                  [:pvm-toisen-pvmn-jalkeen (:valmispvm-kohde lomakedata-nyt)
                   "Tarkastus ei voi olla ennen valmistumista"]]}
       {:otsikko "Tarkastaja"
        :nimi :tarkastaja
        :tyyppi :string
        :validoi [[:ei-tyhja "Anna tarkastaja"]]
        :pituus-max 1024}
       {:teksti "Hyväksytty"
        :nimi :hyvaksytty
        :tyyppi :checkbox
        :fmt #(if % "Tekninen osa tarkastettu" "Teknistä osaa ei tarkastettu")}
       {:otsikko "Lisätiedot"
        :nimi :lisatiedot
        :tyyppi :text
        :koko [60 3]
        :pituus-max 4096
        :palstoja 2}]
      asiatarkastus]]))

(defn kasittelytiedot [otsikko muokattava? valmistumispvm osa muokkaa!]
  (let [pvm-validoinnit (if (:paatos osa)
                          [[:ei-tyhja "Anna käsittelypvm"]
                           [:pvm-toisen-pvmn-jalkeen valmistumispvm
                            "Käsittely ei voi olla ennen valmistumista"]]
                          [[:pvm-toisen-pvmn-jalkeen valmistumispvm
                            "Käsittely ei voi olla ennen valmistumista"]])]
    [lomake/lomake
     {:otsikko otsikko
      :muokkaa! muokkaa!
      :voi-muokata? muokattava?}
     [{:otsikko "Käsitelty"
       :nimi :kasittelyaika
       :pakollinen? (when (:paatos osa) true)
       :tyyppi :pvm
       :validoi pvm-validoinnit}

      {:otsikko "Päätös"
       :nimi :paatos
       :tyyppi :valinta
       :valinnat [:hyvaksytty :hylatty]
       :validoi [[:ei-tyhja "Anna päätös"]]
       :valinta-nayta #(cond
                         % (paallystys-ja-paikkaus/kuvaile-paatostyyppi %)
                         muokattava? "- Valitse päätös -"
                         :default "-")
       :palstoja 1}

      (when (:paatos osa)
        {:otsikko "Selitys"
         :nimi :perustelu
         :tyyppi :text
         :koko [60 3]
         :pituus-max 2048
         :palstoja 2
         :validoi [[:ei-tyhja "Anna päätöksen selitys"]]})]
     osa]))

(defn kasittely
  "Ilmoituksen käsittelyosio, kun ilmoitus on valmis.
  Tilaaja voi muokata, urakoitsija voi tarkastella."
  [urakka {:keys [tila tekninen-osa] :as lomakedata-nyt}
   lukittu? muokkaa!]
  (let [muokattava? (and
                      (oikeudet/on-muu-oikeus? "päätös"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))
                      (not= tila :lukittu)
                      (false? lukittu?))
        valmistumispvm (:valmispvm-kohde lomakedata-nyt)]
    [:div.pot-kasittely

     [:h3 "Käsittely"]
     [kasittelytiedot "Tekninen osa" muokattava? valmistumispvm tekninen-osa
      #(muokkaa! assoc :tekninen-osa %)]]))

(defn tallennus
  [urakka {:keys [tekninen-osa tila] :as lomake} valmis-tallennettavaksi? tallennus-onnistui]
  (let [paatos-tekninen-osa (:paatos tekninen-osa)
        huomautusteksti
        (cond (and (not= :lukittu tila)
                   (= :hyvaksytty paatos-tekninen-osa))
              "Päällystysilmoitus hyväksytty, ilmoitus lukitaan tallennuksen yhteydessä."
              :default nil)
        urakka-id (:id urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        vuosi @u/valittu-urakan-vuosi]

    [:div.pot-tallennus
     (when huomautusteksti
       (lomake/yleinen-huomautus huomautusteksti))

     [napit/palvelinkutsu-nappi
      "Tallenna"
      #(let [lahetettava-data (-> lomake
                                  lomake/ilman-lomaketietoja
                                  (grid/poista-idt [:ilmoitustiedot :osoitteet])
                                  (grid/poista-idt [:ilmoitustiedot :alustatoimet]))]
         (log "[PÄÄLLYSTYS] Lomake-data: " (pr-str lomake))
         (log "[PÄÄLLYSTYS] Lähetetään data " (pr-str lahetettava-data))
         (paallystys/tallenna-paallystysilmoitus! {:urakka-id urakka-id
                                                   :sopimus-id sopimus-id
                                                   :lomakedata lahetettava-data
                                                   :vuosi vuosi}))
      {:luokka "nappi-ensisijainen"
       :id "tallenna-paallystysilmoitus"
       :disabled (or (false? valmis-tallennettavaksi?)
                     (not (oikeudet/voi-kirjoittaa?
                            oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                            urakka-id @istunto/kayttaja)))
       :ikoni (ikonit/tallenna)
       :virheviesti "Tallentaminen epäonnistui"
       :kun-onnistuu tallennus-onnistui}]]))

(defn- tr-vali-paakohteen-sisalla? [lomakedata _ rivi]
  (when-not
    (and (<= (:tr-alkuosa lomakedata) (:tr-alkuosa rivi) (:tr-loppuosa lomakedata))
         (<= (:tr-alkuosa lomakedata) (:tr-loppuosa rivi) (:tr-loppuosa lomakedata))
         (if (= (:tr-alkuosa lomakedata) (:tr-loppuosa rivi))
           (>= (:tr-alkuetaisyys rivi) (:tr-alkuetaisyys lomakedata))
           true)
         (if (= (:tr-loppuosa lomakedata) (:tr-loppuosa rivi))
           (<= (:tr-loppuetaisyys rivi) (:tr-loppuetaisyys lomakedata))
           true))
    "Ei pääkohteen sisällä"))

(defn- muokkaus-grid-wrap [lomakedata-nyt muokkaa! polku]
  (r/wrap (zipmap (iterate inc 1) (get-in lomakedata-nyt polku))
          (fn [uusi-arvo]
            (muokkaa!
              #(assoc-in % polku
                         (vec (grid/filteroi-uudet-poistetut uusi-arvo)))))))

(defn paallystysilmoitus-perustiedot [urakka {:keys [tila] :as lomakedata-nyt} lukittu? kirjoitusoikeus? muokkaa!]
  (let [nayta-kasittelyosiot? (or (= tila :valmis) (= tila :lukittu))
        tarkista-takuu-pvm (fn [_ {valmispvm-paallystys :valmispvm-paallystys
                                   takuupvm :takuupvm}]
                             (when (and valmispvm-paallystys
                                        takuupvm
                                        (> valmispvm-paallystys takuupvm))
                               "Takuupvm on yleensä kohteen valmistumisen jälkeen."))]
    [:div.row
     [:div.col-md-6
      [:h3 "Perustiedot"]
      [lomake/lomake {:voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                         (false? lukittu?)
                                         kirjoitusoikeus?)
                      :muokkaa! (fn [uusi]
                                  #_(log "[PÄÄLLYSTYS] Muokataan kohteen tietoja: " (pr-str uusi))
                                  (muokkaa! merge uusi))}
       [{:otsikko "Kohde" :nimi :kohde
         :hae (fn [_]
                (str "#" (:kohdenumero lomakedata-nyt) " " (:kohdenimi lomakedata-nyt)))
         :muokattava? (constantly false)
         :palstoja 2}
        {:otsikko "Työ aloitettu" :nimi :aloituspvm :tyyppi :pvm :palstoja 1 :muokattava? (constantly false)}
        {:otsikko "Takuupvm" :nimi :takuupvm :tyyppi :pvm :palstoja 1
         :varoita [tarkista-takuu-pvm]}
        {:otsikko "Päällystys valmistunut" :nimi :valmispvm-paallystys :tyyppi :pvm :palstoja 1 :muokattava? (constantly false)}
        {:otsikko "Päällystyskohde valmistunut" :nimi :valmispvm-kohde :palstoja 1
         :tyyppi :pvm :muokattava? (constantly false)}
        {:otsikko "Toteutunut hinta" :nimi :toteuman-kokonaishinta
         :hae #(-> % laske-hinta :toteuman-kokonaishinta)
         :fmt fmt/euro-opt :tyyppi :numero
         :muokattava? (constantly false) :palstoja 1}
        (when (and (not= :valmis (:tila lomakedata-nyt))
                   (not= :lukittu (:tila lomakedata-nyt)))
          {:otsikko "Käsittely"
           :teksti "Valmis tilaajan käsiteltäväksi"
           :nimi :valmis-kasiteltavaksi :palstoja 1
           :tyyppi :checkbox})
        (when (or (= :valmis (:tila lomakedata-nyt))
                  (= :lukittu (:tila lomakedata-nyt)))
          {:otsikko "Kommentit" :nimi :kommentit
           :palstoja 2
           :tyyppi :komponentti
           :komponentti (fn [_]
                          [kommentit/kommentit
                           {:voi-kommentoida?
                            (not= :lukittu (:tila lomakedata-nyt))
                            :voi-liittaa false
                            :palstoja 40
                            :placeholder "Kirjoita kommentti..."
                            :uusi-kommentti (r/wrap (:uusi-kommentti lomakedata-nyt)
                                                    #(muokkaa! assoc :uusi-kommentti %))}
                           (:kommentit lomakedata-nyt)])})]
       lomakedata-nyt]]

     [:div.col-md-6
      (when nayta-kasittelyosiot?
        [:div
         [kasittely urakka lomakedata-nyt lukittu? muokkaa!]
         [asiatarkastus urakka lomakedata-nyt lukittu? muokkaa!]])]]))

(defn paallystysilmoitus-tekninen-osa
  [urakka {tie :tr-numero aosa :tr-alkuosa losa :tr-loppuosa :as lomakedata-nyt}
   voi-muokata? grid-wrap wrap-virheet muokkaa!]
  (let [osan-pituus (atom {})]
    (go (reset! osan-pituus (<! (vkm/tieosien-pituudet tie aosa losa))))
    (fn [urakka lomakedata-nyt voi-muokata? alustatoimet-voi-muokata? grid-wrap wrap-virheet muokkaa!]
      (let [tierekisteriosoitteet (get-in lomakedata-nyt [:ilmoitustiedot :osoitteet])
            paallystystoimenpiteet (grid-wrap [:ilmoitustiedot :osoitteet])
            alustalle-tehdyt-toimet (grid-wrap [:ilmoitustiedot :alustatoimet])]
        [:fieldset.lomake-osa
         [:h3 "Tekninen osa"]

         [yllapitokohteet/yllapitokohdeosat
          {:voi-kumota? false
           :muokkaa! (fn [kohteet virheet]
                       (muokkaa! (fn [lomake]
                                   (-> lomake
                                       (assoc-in [:ilmoitustiedot :osoitteet] kohteet)
                                       (assoc-in [:virheet :alikohteet] virheet)))))
           :rivinumerot? true
           :voi-muokata? voi-muokata?
           :virheet (wrap-virheet :alikohteet)}
          urakka tierekisteriosoitteet
          (select-keys lomakedata-nyt
                       #{:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys})
          @osan-pituus]

         [grid/muokkaus-grid
          {:otsikko "Päällystystoimenpiteen tiedot"
           :id "paallystysilmoitus-paallystystoimenpiteet"
           :voi-lisata? false
           :voi-kumota? false
           :voi-poistaa? (constantly false)
           :voi-muokata? voi-muokata?
           :virheet (wrap-virheet :paallystystoimenpide)
           :rivinumerot? true}
          [(assoc paallystys/paallyste-grid-skeema :nimi :toimenpide-paallystetyyppi :leveys 30)
           (assoc paallystys/raekoko-grid-skeema :nimi :toimenpide-raekoko :leveys 10)
           {:otsikko "Massa\u00ADmenek\u00ADki (kg/m²)" :nimi :massamenekki
            :tyyppi :positiivinen-numero :desimaalien-maara 0
            :tasaa :oikea :leveys 10}
           {:otsikko "RC-%" :nimi :rc% :leveys 10 :tyyppi :numero :desimaalien-maara 0
            :tasaa :oikea :pituus-max 100
            :validoi [[:rajattu-numero nil 0 100]]}
           (assoc paallystys/tyomenetelma-grid-skeema :nimi :toimenpide-tyomenetelma :leveys 30
             :validoi [[:ei-tyhja "Valitse päällystysmenetelmä"]])
           {:otsikko "Leveys (m)" :nimi :leveys :leveys 10 :tyyppi :positiivinen-numero
            :tasaa :oikea}
           {:otsikko "Kohteen kokonais\u00ADmassa\u00ADmäärä (t)" :nimi :kokonaismassamaara
            :tyyppi :positiivinen-numero :tasaa :oikea :leveys 10}
           {:otsikko "Pinta-ala (m²)" :nimi :pinta-ala :leveys 10 :tyyppi :positiivinen-numero
            :tasaa :oikea}
           {:otsikko "Kuulamylly"
            :nimi :kuulamylly
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta (fn [rivi]
                             (if rivi
                               (:nimi rivi)
                               "- Valitse kuulamylly -"))
            :valinnat (conj pot/+kuulamyllyt+
                            {:nimi "Ei kuulamyllyä" :lyhenne "Ei kuulamyllyä" :koodi nil})
            :leveys 30}]
          paallystystoimenpiteet]

         [grid/muokkaus-grid
          {:otsikko "Kiviaines ja sideaine"
           :rivinumerot? true
           :voi-lisata? false
           :voi-kumota? false
           :voi-poistaa? (constantly false)
           :voi-muokata? voi-muokata?
           :virheet (wrap-virheet :kiviaines)}
          [{:otsikko "Kiviaines\u00ADesiintymä" :nimi :esiintyma :tyyppi :string :pituus-max 256
            :leveys 30}
           {:otsikko "KM-arvo" :nimi :km-arvo :tyyppi :string :pituus-max 256 :leveys 20}
           {:otsikko "Muoto\u00ADarvo" :nimi :muotoarvo :tyyppi :string :pituus-max 256
            :leveys 20}
           {:otsikko "Sideaine\u00ADtyyppi" :nimi :sideainetyyppi :leveys 30
            :tyyppi :valinta
            :valinta-arvo :koodi
            :valinta-nayta (fn [rivi]
                             (if rivi
                               (:nimi rivi)
                               "- Valitse sideainetyyppi -"))
            :valinnat (conj pot/+sideainetyypit+
                            {:nimi "Ei sideainetyyppi" :lyhenne "Ei sideainetyyppiä" :koodi nil})}
           {:otsikko "Pitoisuus" :nimi :pitoisuus :leveys 20 :tyyppi :numero :desimaalien-maara 2 :tasaa :oikea}
           {:otsikko "Lisä\u00ADaineet" :nimi :lisaaineet :leveys 20 :tyyppi :string
            :pituus-max 256}]
          paallystystoimenpiteet]

         (let [tr-validaattori (partial tr-vali-paakohteen-sisalla? lomakedata-nyt)]
           ;; FIXME: laskee TR pituuden väärin! 3 1 4 2 -> -1
           ;; herjaa yo tiedoille "ei pääkohteen sisällä"
           [:div [grid/muokkaus-grid
                  {:otsikko "Alustalle tehdyt toimet"
                   :voi-muokata? alustatoimet-voi-muokata?
                   :voi-kumota? false
                   :uusi-id (inc (count @alustalle-tehdyt-toimet))
                   :virheet (wrap-virheet :alustalle-tehdyt-toimet)}
                  [{:otsikko "Aosa" :nimi :tr-alkuosa :tyyppi :positiivinen-numero :leveys 10
                    :pituus-max 256 :validoi [[:ei-tyhja "Tieto puuttuu"] tr-validaattori] :tasaa :oikea}
                   {:otsikko "Aet" :nimi :tr-alkuetaisyys :tyyppi :positiivinen-numero :leveys 10
                    :validoi [[:ei-tyhja "Tieto puuttuu"] tr-validaattori] :tasaa :oikea}
                   {:otsikko "Losa" :nimi :tr-loppuosa :tyyppi :positiivinen-numero :leveys 10
                    :validoi [[:ei-tyhja "Tieto puuttuu"] tr-validaattori] :tasaa :oikea}
                   {:otsikko "Let" :nimi :tr-loppuetaisyys :leveys 10 :tyyppi :positiivinen-numero
                    :validoi [[:ei-tyhja "Tieto puuttuu"] tr-validaattori] :tasaa :oikea}
                   {:otsikko "Pituus (m)" :nimi :pituus :leveys 10 :tyyppi :numero :tasaa :oikea
                    :muokattava? (constantly false)
                    :hae (partial tierekisteri-domain/laske-tien-pituus @osan-pituus)
                    :validoi [[:ei-tyhja "Tieto puuttuu"]]}
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
                    :validoi [[:ei-tyhja "Tieto puuttuu"]]}
                   {:otsikko "Käsit\u00ADtely\u00ADpaks. (cm)" :nimi :paksuus :leveys 15
                    :tyyppi :positiivinen-numero :tasaa :oikea
                    :desimaalien-maara 0
                    :validoi [[:ei-tyhja "Tieto puuttuu"]]}
                   {:otsikko "Verkko\u00ADtyyppi"
                    :nimi :verkkotyyppi
                    :tyyppi :valinta
                    :valinta-arvo :koodi
                    :valinta-nayta #(if % (:nimi %) "- Valitse verkkotyyppi -")
                    :valinnat pot/+verkkotyypit+
                    :leveys 25
                    :validoi [[:ei-tyhja "Tieto puuttuu"]]}
                   {:otsikko "Verkon sijainti"
                    :nimi :verkon-sijainti
                    :tyyppi :valinta
                    :valinta-arvo :koodi
                    :valinta-nayta #(if % (:nimi %) "- Valitse verkon sijainti -")
                    :valinnat pot/+verkon-sijainnit+
                    :leveys 25
                    :validoi [[:ei-tyhja "Tieto puuttuu"]]}
                   {:otsikko "Verkon tarkoitus"
                    :nimi :verkon-tarkoitus
                    :tyyppi :valinta
                    :valinta-arvo :koodi
                    :valinta-nayta #(if % (:nimi %) "- Valitse verkon tarkoitus -")
                    :valinnat pot/+verkon-tarkoitukset+
                    :leveys 25
                    :validoi [[:ei-tyhja "Tieto puuttuu"]]}
                   {:otsikko "Tekninen toimen\u00ADpide"
                    :nimi :tekninen-toimenpide
                    :tyyppi :valinta
                    :valinta-arvo :koodi
                    :valinta-nayta #(if % (:nimi %) "- Valitse toimenpide -")
                    :valinnat (conj pot/+tekniset-toimenpiteet+
                                    {:nimi "Ei toimenpidettä" :lyhenne "Ei toimenpidettä" :koodi nil})
                    :leveys 30}]
                  alustalle-tehdyt-toimet]])]))))

(defn paallystysilmoituslomake [urakka {:keys [yllapitokohde-id yllapitokohdetyyppi] :as lomake}
                                _ muokkaa! historia tallennus-onnistui]
  (komp/luo
    (komp/lukko (lukko/muodosta-lukon-id "paallystysilmoitus" yllapitokohde-id))
    (fn [urakka {:keys [virheet tila kirjoitusoikeus?] :as lomakedata-nyt}
         lukko muokkaa! historia tallennus-onnistui]
      (let [lukittu? (lukko/nakyma-lukittu? lukko)
            valmis-tallennettavaksi? (and
                                       (not (= tila :lukittu))
                                       (every? empty? (vals virheet))
                                       (false? lukittu?))

            grid-wrap (partial muokkaus-grid-wrap lomakedata-nyt muokkaa!)

            tekninen-osa-voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                           (not= :hyvaksytty
                                                 (:paatos (:tekninen-osa lomakedata-nyt)))
                                           (false? lukittu?)
                                           kirjoitusoikeus?)
            alustatoimet-voi-muokata? (and tekninen-osa-voi-muokata?
                                           (not (= "sora" yllapitokohdetyyppi)))

            wrap-virheet (fn [virheet-key]
                           (atom/wrap-arvo lomakedata-nyt [:virheet virheet-key] muokkaa!))]
        [:div.paallystysilmoituslomake

         [napit/takaisin "Takaisin ilmoitusluetteloon" #(muokkaa! (constantly nil))]

         (when lukittu?
           (lomake/lomake-lukittu-huomautus lukko))

         [:h2 "Päällystysilmoitus"]

         [paallystysilmoitus-perustiedot urakka lomakedata-nyt lukittu? kirjoitusoikeus? muokkaa!]

         [:div {:style {:float "right"}} [historia/kumoa historia]]
         [paallystysilmoitus-tekninen-osa
          urakka lomakedata-nyt tekninen-osa-voi-muokata? alustatoimet-voi-muokata?
          grid-wrap wrap-virheet muokkaa!]

         [yhteenveto lomakedata-nyt]

         [tallennus urakka lomakedata-nyt valmis-tallennettavaksi? tallennus-onnistui]]))))

(defn avaa-paallystysilmoitus [paallystyskohteen-id]
  (go
    (let [urakka-id (:id @nav/valittu-urakka)
          vastaus (<! (paallystys/hae-paallystysilmoitus-paallystyskohteella urakka-id paallystyskohteen-id))]
      (if (k/virhe? vastaus)
        (viesti/nayta! "Päällystysilmoituksen haku epäonnistui." :warning viesti/viestin-nayttoaika-lyhyt)
        (reset! paallystys/paallystysilmoitus-lomakedata
                (assoc vastaus
                  :kirjoitusoikeus?
                  (oikeudet/voi-kirjoittaa? oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                            (:id @nav/valittu-urakka))))))))

(defn jarjesta-paallystysilmoitukset [paallystysilmoitukset]
  (when paallystysilmoitukset
    (sort-by
      (juxt (fn [toteuma] (case (:tila toteuma)
                            :lukittu 0
                            :valmis 1
                            :aloitettu 3
                            4))
            (fn [toteuma] (case (:paatos-tekninen-osa toteuma)
                            :hyvaksytty 0
                            :hylatty 1
                            3)))
      paallystysilmoitukset)))

(defn- paallystysilmoitukset-taulukko [paallystysilmoitukset]
  [grid/grid
   {:otsikko ""
    :tyhja (if (nil? paallystysilmoitukset) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
    :tunniste hash}
   [{:otsikko "Kohdenumero" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys 14}
    {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys 50}
    {:otsikko "Tila" :nimi :tila :muokattava? (constantly false) :tyyppi :string :leveys 20
     :hae (fn [rivi]
            (paallystys-ja-paikkaus/nayta-tila (:tila rivi)))}
    {:otsikko "Päätös" :nimi :paatos-tekninen-osa :muokattava? (constantly false) :tyyppi :komponentti
     :leveys 20
     :komponentti (fn [rivi]
                    (paallystys-ja-paikkaus/nayta-paatos (:paatos-tekninen-osa rivi)))}
    {:otsikko "Päällystys\u00ADilmoitus" :nimi :paallystysilmoitus :muokattava? (constantly false) :leveys 25 :tyyppi
     :komponentti
     :komponentti (fn [rivi]
                    (if (:tila rivi)
                      [:button.nappi-toissijainen.nappi-grid
                       {:on-click #(avaa-paallystysilmoitus (:paallystyskohde-id rivi))}
                       [:span (ikonit/eye-open) " Päällystysilmoitus"]]
                      [:button.nappi-toissijainen.nappi-grid {:on-click #(avaa-paallystysilmoitus (:paallystyskohde-id rivi))}
                       [:span "Aloita päällystysilmoitus"]]))}]
   paallystysilmoitukset])

(defn- nayta-lahetystiedot [rivi]
  (if (some #(= % (:paallystyskohde-id rivi)) @paallystys/kohteet-yha-lahetyksessa)
    [:span.tila-odottaa-vastausta "Lähetys käynnissä " [yleiset/ajax-loader-pisteet]]
    (if (:lahetetty rivi)
      (if (:lahetys-onnistunut rivi)
        [:span.tila-lahetetty
         (str "Lähetetty onnistuneesti: " (pvm/pvm-aika (:lahetetty rivi)))]
        [:span.tila-virhe
         (str "Lähetys epäonnistunut: " (pvm/pvm-aika (:lahetetty rivi)) ". Virhe: \"" (:lahetysvirhe rivi) "\"")])
      [:span "Ei lähetetty"])))

(defn- yha-lahetykset-taulukko [urakka-id sopimus-id vuosi paallystysilmoitukset]
  [grid/grid
   {:otsikko ""
    :tyhja (if (nil? paallystysilmoitukset) [ajax-loader "Haetaan ilmoituksia..."] "Ei ilmoituksia")
    :tunniste hash}
   [{:otsikko "Kohdenumero" :nimi :kohdenumero :muokattava? (constantly false) :tyyppi :numero :leveys 14}
    {:otsikko "Nimi" :nimi :nimi :muokattava? (constantly false) :tyyppi :string :leveys 50}
    {:otsikko "Edellinen lähetys YHA:n" :nimi :edellinen-lahetys :muokattava? (constantly false) :tyyppi :komponentti
     :leveys 60
     :komponentti (fn [rivi] [nayta-lahetystiedot rivi])}
    {:otsikko "Lähetä YHA:n" :nimi :laheta-yhan :muokattava? (constantly false) :leveys 25 :tyyppi :komponentti
     :komponentti (fn [rivi]
                    [yha/yha-lahetysnappi
                     oikeudet/urakat-kohdeluettelo-paallystyskohteet
                     urakka-id
                     sopimus-id
                     vuosi
                     [rivi]])}]
   paallystysilmoitukset])

(defn- ilmoitusluettelo
  []
  (komp/luo
    (komp/kuuntelija :avaa-paallystysilmoitus
                     (fn [_ rivi]
                       (avaa-paallystysilmoitus (:paallystyskohde-id rivi))))
    (fn []
      (let [urakka-id (:id @nav/valittu-urakka)
            sopimus-id (first @u/valittu-sopimusnumero)
            urakan-vuosi @u/valittu-urakan-vuosi
            paallystysilmoitukset (jarjesta-paallystysilmoitukset @paallystys/paallystysilmoitukset-suodatettu)]
        [:div
         [:h3 "Päällystysilmoitukset"]
         [paallystysilmoitukset-taulukko paallystysilmoitukset]
         [:h3 "YHA-lähetykset"]
         [yleiset/vihje "Ilmoituksen täytyy olla merkitty valmiiksi ja kokonaisuudessaan hyväksytty ennen kuin se voidaan lähettää YHA:n."]
         [yha-lahetykset-taulukko urakka-id sopimus-id urakan-vuosi paallystysilmoitukset]
         [yha/yha-lahetysnappi
          oikeudet/urakat-kohdeluettelo-paallystyskohteet
          urakka-id
          sopimus-id
          urakan-vuosi
          paallystysilmoitukset]]))))

(defn paallystysilmoituslomake-historia [ilmoituslomake]
  (let [historia (historia/historia ilmoituslomake)]
    (komp/luo
      (komp/ulos (historia/kuuntele! historia))
      (fn [ilmoituslomake]
        [paallystysilmoituslomake
         @nav/valittu-urakka
         @ilmoituslomake
         @lukko/nykyinen-lukko
         (partial swap! ilmoituslomake)
         historia
         (fn [vastaus]
           (log "[PÄÄLLYSTYS] Lomake tallennettu, vastaus: " (pr-str vastaus))
           (urakka/lukitse-urakan-yha-sidonta! (:id @nav/valittu-urakka))
           (reset! paallystys/paallystysilmoitukset (:paallystysilmoitukset vastaus))
           (reset! paallystys/yllapitokohteet (:yllapitokohteet vastaus))
           (reset! ilmoituslomake nil))]))))

(defn paallystysilmoitukset [urakka]
  (komp/luo
    (komp/lippu paallystys/paallystysilmoitukset-nakymassa?)

    (fn [urakka]
      [:div.paallystysilmoitukset
       [kartta/kartan-paikka]
       (if @paallystys/paallystysilmoitus-lomakedata
         [paallystysilmoituslomake-historia paallystys/paallystysilmoitus-lomakedata]
         [:div
          [valinnat/vuosi {}
           (t/year (:alkupvm urakka))
           (t/year (:loppupvm urakka))
           urakka/valittu-urakan-vuosi
           urakka/valitse-urakan-vuosi!]
          [u-valinnat/kohdenumero yllapito-tiedot/kohdenumero]
          [u-valinnat/tienumero yllapito-tiedot/tienumero]
          [ilmoitusluettelo]])])))
