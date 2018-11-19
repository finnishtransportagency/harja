(ns harja.views.urakka.paallystysilmoitukset
  "Urakan päällystysilmoitukset"
  (:require [reagent.core :refer [atom] :as r]
            [tuck.core :as tuck]
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
            [harja.views.urakka.valinnat :as u-valinnat]
            [harja.ui.leijuke :as leijuke]
            [harja.ui.modal :as modal])
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
                                  ;; POT-lomake tallentuu kantaan JSONina, eikä se tarvitse id-tietoja.
                                  (grid/poista-idt [:ilmoitustiedot :osoitteet])
                                  (grid/poista-idt [:ilmoitustiedot :alustatoimet])
                                  ;; Poistetaan poistetut elementit
                                  (grid/poista-poistetut [:ilmoitustiedot :osoitteet])
                                  (grid/poista-poistetut [:ilmoitustiedot :alustatoimet])

                                  (update-in [:ilmoitustiedot :osoitteet] (fn [osoitteet]
                                                                            (map (fn [osoite]
                                                                                   (dissoc osoite :jarjestys-gridissa))
                                                                                 osoitteet))))]
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

(defn- muokkaus-grid-wrap [lomakedata-nyt muokkaa! polku]
  ;; Huom. datan järjestyksen täytyy konformoida gridin id:ten kanssa.
  (let [polun-meta (meta (get-in lomakedata-nyt polku))
        polun-data (into (sorted-map)
                         (map (fn [data i]
                                (if (contains? data :id)
                                  ;; Jos rivillä on olemassa id, otetaan se käyttöön tunnisteeksi gridin datassa
                                  [(:id data) data]
                                  ;; Muussa tapauksessa käytetään järjestysnumeroa
                                  [i data]))
                              (if (:jarjestetty-kerran? polun-meta)
                                (get-in lomakedata-nyt polku)
                                (yllapitokohde-domain/jarjesta-yllapitokohteet (get-in lomakedata-nyt polku)))
                              (iterate inc 1)))]
    (r/wrap (with-meta polun-data polun-meta)
            (fn [uusi-arvo]
              (muokkaa!
                #(assoc-in % polku
                           (with-meta (vec (grid/filteroi-uudet-poistetut uusi-arvo))
                                      (meta uusi-arvo))))))))

(defn tarkista-takuu-pvm [_ {valmispvm-paallystys :valmispvm-paallystys takuupvm :takuupvm}]
  (when (and valmispvm-paallystys
             takuupvm
             (> valmispvm-paallystys takuupvm))
    "Takuupvm on yleensä kohteen valmistumisen jälkeen."))

(defn paallystysilmoitus-perustiedot [urakka {:keys [tila] :as lomakedata-nyt} lukittu? kirjoitusoikeus? muokkaa!]
  (let [nayta-kasittelyosiot? (or (= tila :valmis) (= tila :lukittu))]
    [:div.row
     [:div.col-md-6
      [:h3 "Perustiedot"]
      [lomake/lomake {:voi-muokata? (and (not= :lukittu (:tila lomakedata-nyt))
                                         (false? lukittu?)
                                         kirjoitusoikeus?)
                      :muokkaa! (fn [uusi]
                                  (log "[PÄÄLLYSTYS] Muokataan kohteen tietoja: " (pr-str uusi))
                                  (muokkaa! merge uusi))}
       [{:otsikko "Kohde" :nimi :kohde
         :hae (fn [_]
                (str "#" (:kohdenumero lomakedata-nyt) " " (:tunnus lomakedata-nyt) " " (:kohdenimi lomakedata-nyt)))
         :muokattava? (constantly false)
         :palstoja 1}
        {:otsikko "Tierekisteriosoite"
         :nimi :tr-osoite
         :hae identity
         :fmt tierekisteri-domain/tierekisteriosoite-tekstina
         :muokattava? (constantly false)
         :palstoja 1}
        (when (or (:tr-ajorata lomakedata-nyt) (:tr-kaista lomakedata-nyt))
          {:otsikko "Ajorata" :nimi :tr-ajorata :tyyppi :string :palstoja 1 :muokattava? (constantly false)})

        (when (or (:tr-ajorata lomakedata-nyt) (:tr-kaista lomakedata-nyt))
          {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :string :palstoja 1 :muokattava? (constantly false)})
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
                            :voi-liittaa? false
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

(defn poista-lukitus [urakka lomakedata-nyt]
  (let [paatosoikeus? (oikeudet/on-muu-oikeus? "päätös"
                                               oikeudet/urakat-kohdeluettelo-paallystysilmoitukset
                                               (:id urakka))
        lahetettava-data {:urakka-id (:id urakka)
                          :kohde-id (:paallystyskohde-id lomakedata-nyt)
                          :tila :valmis}]
    [:div
     [:div "Tämä ilmoitus on lukittu. Urakanvalvoja voi avata lukituksen."]
     [napit/palvelinkutsu-nappi
      "Avaa lukitus"
      #(when paatosoikeus?
         (paallystys/avaa-paallystysilmoituksen-lukitus! lahetettava-data))
      {:luokka "nappi-kielteinen avaa-lukitus-nappi"
       :id "poista-paallystysilmoituksen-lukitus"
       :disabled (not paatosoikeus?)
       :ikoni (ikonit/livicon-wrench)
       :virheviesti "Lukituksen avaaminen epäonnistui"
       :kun-onnistuu (fn [vastaus]
                       (swap! paallystys/paallystysilmoitus-lomakedata assoc :tila (:tila vastaus))
                       (harja.atom/paivita! paallystys/paallystysilmoitukset))}]]))


(defn paallystysilmoitus-tekninen-osa
  [{urakka :urakka {tie :tr-numero aosa :tr-alkuosa losa :tr-loppuosa :as lomakedata-nyt} :lomakedata-nyt
    voi-muokata? :tekninen-osa-voi-muokata? alustatoimet-voi-muokata? :alustatoimet-voi-muokata? grid-wrap
    :grid-wrap wrap-virheet :wrap-virheet muokkaa! :muokkaa!}]
  (let [osan-pituus (atom {})]
    (go (reset! osan-pituus (<! (vkm/tieosien-pituudet tie aosa losa))))
    (fn [{:keys [urakka lomakedata-nyt tekninen-osa-voi-muokata? alustatoimet-voi-muokata? grid-wrap wrap-virheet muokkaa!]}]
      (let [paallystystoimenpiteet (grid-wrap [:ilmoitustiedot :osoitteet])
            alustalle-tehdyt-toimet (grid-wrap [:ilmoitustiedot :alustatoimet])
            yllapitokohde-virheet (wrap-virheet :alikohteet)
            muokkaus-mahdollista? (and tekninen-osa-voi-muokata?
                                       ;; Jostain osassa tilanteita, kentät tulevat virheiden mukana, mutta niissä ei
                                       ;; ole virheitä. Tällöin pitää erikseen tarkitaa kenttäkohtaisesti, ovatko
                                       ;; kenttien virheet tyhjät.
                                       (or (empty? @yllapitokohde-virheet)
                                           (empty? (flatten (map vals (vals @yllapitokohde-virheet))))))
            jarjestys-fn :id
            paakohteella-ajorata-ja-kaista? (boolean (and (:tr-ajorata lomakedata-nyt)
                                                          (:tr-kaista lomakedata-nyt)))]
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

         (println "-----> FOO: " lomakedata-nyt)
         [yllapitokohteet/yllapitokohdeosat
          {:urakka urakka
           :yllapitokohde (select-keys lomakedata-nyt [:tr-numero :tr-kaista :tr-ajorata :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys])
           :rivinumerot? true
           :muokattava-tie? (fn [rivi]
                              false
                              ;; Alempi olisi luultavasti parempi, mutta aiheuttaa käytännössä ongelmia jos
                              ;; tienumeroksi muokataan sama tie kuin pääkohteella --> kenttä disabloituu
                              ;; Voitaneen ehkä hyväksyä, että kohdeosien tien muokkaus tapahtuu kohdeluettelossa.
                              #_(let [osan-tie-paakohteella? (= (:tr-numero rivi) tie)]
                                (if osan-tie-paakohteella?
                                  false
                                  true)))
           :muokattava-ajorata-ja-kaista? (fn [rivi]
                                            (let [osan-tie-paakohteella? (= (:tr-numero rivi) tie)]
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
           :kohdeosat-atom paallystystoimenpiteet
           :jarjesta-kun-kasketaan first
           :voi-muokata? muokkaus-mahdollista?
           :kohdetyyppi (keyword (:yllapitokohdetyyppi lomakedata-nyt))
           :virheet-atom yllapitokohde-virheet}]

         [debug @paallystystoimenpiteet {:otsikko "Päällystystoimenpiteet"}]

         [grid/muokkaus-grid
          {:otsikko "Päällystystoimenpiteen tiedot"
           :id "paallystysilmoitus-paallystystoimenpiteet"
           :voi-lisata? false
           :voi-kumota? false
           :voi-poistaa? (constantly false)
           :voi-muokata? muokkaus-mahdollista?
           :virheet (wrap-virheet :paallystystoimenpide)
           :virhe-viesti (when (and (not muokkaus-mahdollista?)
                                    tekninen-osa-voi-muokata?)
                           "Tierekisterikohteet taulukko on virheellisessä tilassa")
           :rivinumerot? true
           :jarjesta jarjestys-fn}
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
            :validoi [[:rajattu-numero 0 100]]
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
          paallystystoimenpiteet]

         [grid/muokkaus-grid
          {:otsikko "Kiviaines ja sideaine"
           :rivinumerot? true
           :voi-lisata? false
           :voi-kumota? false
           :voi-poistaa? (constantly false)
           :voi-muokata? muokkaus-mahdollista?
           :virhe-viesti (when (and (not muokkaus-mahdollista?)
                                    tekninen-osa-voi-muokata?)
                           "Tierekisterikohteet taulukko on virheellisessä tilassa")
           :virheet (wrap-virheet :kiviaines)
           :jarjesta jarjestys-fn}
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
          paallystystoimenpiteet]

         (let [tr-validaattori (partial tierekisteri-domain/tr-vali-paakohteen-sisalla-validaattori lomakedata-nyt)]
           [:div
            [debug @alustalle-tehdyt-toimet {:otsikko "Alustatoimenpiteet"}]
            [:div [grid/muokkaus-grid
                   {:otsikko "Alustalle tehdyt toimet"
                    :jarjesta jarjestys-fn
                    :voi-muokata? alustatoimet-voi-muokata?
                    :voi-kumota? false
                    :uusi-id (inc (count @alustalle-tehdyt-toimet))
                    :virheet (wrap-virheet :alustalle-tehdyt-toimet)}
                   [{:otsikko "Tie" :nimi :tr-numero :tyyppi :positiivinen-numero :leveys 10
                     :pituus-max 256
                     ;; TODO HAR-7831 Korvaa nykyinen tr_validaattori: tsekkaa, että jonkun kohdeosan sisällä.
                     :validoi [[:ei-tyhja "Tienumero puuttuu"] #_tr-validaattori]
                     :tasaa :oikea
                     :kokonaisluku? true}
                    {:otsikko "Ajorata" :nimi :tr-ajorata :tyyppi :positiivinen-numero :leveys 10
                     :pituus-max 256
                     :validoi [[:ei-tyhja "Ajorata puuttuu"] #_tr-validaattori]
                     :tasaa :oikea
                     :kokonaisluku? true}
                    {:otsikko "Kaista" :nimi :tr-kaista :tyyppi :positiivinen-numero :leveys 10
                     :pituus-max 256
                     :validoi [[:ei-tyhja "Kaista puuttuu"] #_tr-validaattori]
                     :tasaa :oikea
                     :kokonaisluku? true}
                    {:otsikko "Aosa" :nimi :tr-alkuosa :tyyppi :positiivinen-numero :leveys 10
                     :pituus-max 256
                     :validoi [[:ei-tyhja "Alkuosa puuttuu"] #_tr-validaattori]
                     :tasaa :oikea
                     :kokonaisluku? true}
                    {:otsikko "Aet" :nimi :tr-alkuetaisyys :tyyppi :positiivinen-numero :leveys 10
                     :validoi [[:ei-tyhja "Alkuetäisyys puuttuu"] #_tr-validaattori]
                     :tasaa :oikea
                     :kokonaisluku? true}
                    {:otsikko "Losa" :nimi :tr-loppuosa :tyyppi :positiivinen-numero :leveys 10
                     :validoi [[:ei-tyhja "Loppuosa puuttuu"] #_tr-validaattori]
                     :tasaa :oikea
                     :kokonaisluku? true}
                    {:otsikko "Let" :nimi :tr-loppuetaisyys :leveys 10 :tyyppi :positiivinen-numero
                     :validoi [[:ei-tyhja "Loppuetäisyys puuttuu"] #_tr-validaattori]
                     :tasaa :oikea
                     :kokonaisluku? true}
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
                   alustalle-tehdyt-toimet]]])]))))

(defn paallystysilmoituslomake [urakka {:keys [yllapitokohde-id yllapitokohdetyyppi] :as lomake}
                                _ muokkaa! historia tallennus-onnistui]
  (komp/luo
    (komp/lukko (lukko/muodosta-lukon-id "paallystysilmoitus" yllapitokohde-id))
    (fn [urakka {:keys [virheet tila kirjoitusoikeus?] :as lomakedata-nyt}
         lukko muokkaa! historia tallennus-onnistui]
      (let [lukittu? (lukko/nakyma-lukittu? lukko)
            valmis-tallennettavaksi? (and
                                       (not (= tila :lukittu))
                                       ;; Jostain osassa tilanteita, kentät tulevat virheiden mukana, mutta niissä ei
                                       ;; ole virheitä. Tällöin pitää erikseen tarkitaa kenttäkohtaisesti, ovatko
                                       ;; kenttien virheet tyhjät.
                                       (or (every? empty? (vals virheet))
                                           (empty? (reduce (fn [tehty [_ virhe-map]]
                                                             (concat tehty (flatten (map vals (vals virhe-map)))))
                                                           [] virheet)))
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
         (when (= :lukittu (:tila lomakedata-nyt))
           [poista-lukitus urakka lomakedata-nyt])

         [paallystysilmoitus-perustiedot urakka lomakedata-nyt lukittu? kirjoitusoikeus? muokkaa!]

         [:div {:style {:float "right"}} [historia/kumoa historia]]
         [paallystysilmoitus-tekninen-osa {:urakka urakka :lomakedata-nyt lomakedata-nyt :tekninen-osa-voi-muokata? tekninen-osa-voi-muokata?
                                           :alustatoimet-voi-muokata? alustatoimet-voi-muokata? :grid-wrap grid-wrap :wrap-virheet wrap-virheet
                                           :muokkaa! muokkaa!}]

         [yhteenveto lomakedata-nyt]

         [tallennus urakka lomakedata-nyt valmis-tallennettavaksi? tallennus-onnistui]]))))

(defn jarjesta-paallystysilmoitukset [paallystysilmoitukset jarjestys]
  (when paallystysilmoitukset
    (case jarjestys
      :kohdenumero
      (sort-by #(yllapitokohde-domain/kohdenumero-str->kohdenumero-vec (:kohdenumero %)) paallystysilmoitukset)

      :muokkausaika
      ;; Muokkausajalliset ylimmäksi, ei-muokatut sen jälkeen kohdenumeron mukaan
      (concat (sort-by :muokattu (filter #(some? (:muokattu %)) paallystysilmoitukset))
              (sort-by #(yllapitokohde-domain/kohdenumero-str->kohdenumero-vec (:kohdenumero %))
                       (filter #(nil? (:muokattu %)) paallystysilmoitukset)))

      :tila
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
        paallystysilmoitukset))))

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
                  (go (let [paallystysilmoitukset (mapv #(do
                                                           {::pot/id (:id %)
                                                            ::pot/paallystyskohde-id (:paallystyskohde-id %)
                                                            ::pot/takuupvm (:takuupvm %)})
                                                        rivit)
                            vastaus (<! (paallystys/tallenna-paallystysilmoitusten-takuupvmt
                                          urakka-id
                                          paallystysilmoitukset))]
                        (harja.atom/paivita! paallystys/paallystysilmoitukset)
                        (when (k/virhe? vastaus)
                          (viesti/nayta! "Päällystysilmoitusten tallennus epäonnistui"
                                         :warning
                                         viesti/viestin-nayttoaika-keskipitka)))))
      :voi-lisata? false
      :voi-kumota? false
      :voi-poistaa? (constantly false)
      :voi-muokata? true}
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
                      (paallystys-ja-paikkaus/nayta-paatos (:paatos-tekninen-osa rivi)))}
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

(defn- nayta-lahetystiedot [rivi]
  (if (some #(= % (:paallystyskohde-id rivi)) @paallystys/kohteet-yha-lahetyksessa)
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
     :komponentti (fn [rivi] [nayta-lahetystiedot rivi])}
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
         [yha/yha-lahetysnappi {:oikeus oikeudet/urakat-kohdeluettelo-paallystyskohteet :urakka-id urakka-id :sopimus-id sopimus-id
                                :vuosi valittu-urakan-vuosi :paallystysilmoitukset paallystysilmoitukset :lahetys-kaynnissa-fn #(e! (paallystys/->MuutaTila [:kohteet-yha-lahetyksessa] %))
                                :kun-onnistuu #(e! (paallystys/->YHAVientiOnnistui %)) :kun-epaonnistuu #(e! (paallystys/->YHAVientiEpaonnistui %))}]
         [yha-lahetykset-taulukko e! app]]))))

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
           (if (:validointivirheet vastaus)
             (modal/nayta!
               {:otsikko "Päällystysilmoituksen tallennus epäonnistui!"
                :otsikko-tyyli :virhe}
               [:div
                [:p "Virheet:"]
                (into [:ul] (mapv (fn [virhe]
                                    [:li virhe])
                                  (:validointivirheet vastaus)))])
             (do (urakka/lukitse-urakan-yha-sidonta! (:id @nav/valittu-urakka))
                 (reset! paallystys/paallystysilmoitukset (:paallystysilmoitukset vastaus))
                 (reset! paallystys/yllapitokohteet (:yllapitokohteet vastaus))
                 (reset! ilmoituslomake nil))))]))))

(defn valinnat [e! {:keys [urakka] :as app}]
  [:div
   [valinnat/vuosi {}
    (t/year (:alkupvm urakka))
    (t/year (:loppupvm urakka))
    urakka/valittu-urakan-vuosi
    #(do
       (urakka/valitse-urakan-vuosi! %)
       (e! (paallystys/->HaePaallystysilmoitukset)))]
   [u-valinnat/yllapitokohteen-kohdenumero yllapito-tiedot/kohdenumero #(e! (paallystys/->SuodataYllapitokohteet))]
   [u-valinnat/tienumero yllapito-tiedot/tienumero #(e! (paallystys/->SuodataYllapitokohteet))]
   [yleiset/pudotusvalikko
    "Järjestä kohteet"
    {:valinta @yllapito-tiedot/kohdejarjestys
     :valitse-fn #(reset! yllapito-tiedot/kohdejarjestys %)
     :format-fn {:tila "Tilan mukaan"
                 :kohdenumero "Kohdenumeron mukaan"
                 :muokkausaika "Muokkausajan mukaan"}}
    [:tila :kohdenumero :muokkausaika]]])

#_(defn paallystysilmoitukset [urakka]
  (komp/luo
    (komp/lippu paallystys/paallystysilmoitukset-tai-kohteet-nakymassa?)

    (fn [urakka]
      [:div.paallystysilmoitukset
       [kartta/kartan-paikka]
       (if @paallystys/paallystysilmoitus-lomakedata
         [paallystysilmoituslomake-historia paallystys/paallystysilmoitus-lomakedata]
         [:div
          [valinnat urakka]
          [ilmoitusluettelo]])])))

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
       [debug app "TUCK STATE"]
       (if paallystysilmoitus-lomakedata
         [paallystysilmoituslomake-historia paallystys/paallystysilmoitus-lomakedata]
         [:div
          [valinnat e! app]
          [ilmoitusluettelo e! app]])])))
