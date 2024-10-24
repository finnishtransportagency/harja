(ns harja.views.urakka.laadunseuranta.sanktiot-ja-bonukset-nakyma
  "Sanktioiden ja bonusten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]
            [harja.transit :as t]

            [harja.asiakas.kommunikaatio :as k]

            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.urakka.laadunseuranta.bonukset :as bonukset-tiedot]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]

            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.ui.viesti :as viesti]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.domain.tierekisteri :as tierekisteri]

            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.views.urakka.laadunseuranta.sanktiot-lomake :as sanktiot-lomake]
            [harja.views.urakka.laadunseuranta.bonukset-lomake :as bonukset-lomake]
            [harja.ui.ikonit :as ikonit]))

;; --- Sivupaneeli sanktio- ja bonuslomakkeille ---

(defn bonus-sanktio-valikko
  [tila]
  [:<>
   [kentat/tee-kentta {:tyyppi :radio-group
                       :vaihtoehdot [:sanktiot :bonukset]
                       :vayla-tyyli? true
                       :nayta-rivina? true
                       :vaihtoehto-nayta {:sanktiot "Sanktio"
                                          :bonukset "Bonus"}
                       :valitse-fn (fn [valinta]
                                     ;; Alusta sanktio/bonus joka kerta kun valinta vaihdetaan, jotta uudelle tyhjälle
                                     ;; lomakkeelle ei jää aiemman lomakkeen dataa.
                                     ;; Note: Tätä ei tarvitse tehdä, kun saadaan myös sanktiolomake ja s&b listaus
                                     ;;       kunnolla tuck tilanhallinnan piiriin.
                                     ;;       Tällöin lomaketta avatessa voidaan alustaa helpommin tila halutuksi.
                                     (case valinta
                                       :sanktiot
                                       (reset! tiedot/valittu-sanktio (tiedot/uusi-sanktio (:tyyppi @nav/valittu-urakka)))
                                       :bonukset
                                       (reset! tiedot/valittu-sanktio (bonukset-tiedot/uusi-bonus))
                                       nil))}
    tila]
   [:hr]])


(defn sivupaneeli
  [sivupaneeli-auki?-atom]
  (let [tila (atom {:lukutila true :lomake :sanktiot})]
    (komp/luo
      (fn [sivupaneeli-auki?-atom]
        (let [muokattu (atom @tiedot/valittu-sanktio)
              _ (when (and
                        (true? (:bonus @muokattu))
                        (not= :bonukset (:lomake @tila)))
                  (swap! tila assoc :lomake :bonukset))
              oikeus-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                                (:id @nav/valittu-urakka))
              muokataan-vanhaa? (some? (:id @muokattu))
              suorasanktio? (:suorasanktio @muokattu)
              lupaus? (some #{:lupaussanktio :lupausbonus} #{(:laji @muokattu)})
              lukutila? (if (not muokataan-vanhaa?) false (:lukutila @tila))
              bonusten-syotto? (= :bonukset (:lomake @tila))]
          [:div.padding-16.ei-sulje-sivupaneelia
           [:h2 (cond
                  (and lukutila? muokataan-vanhaa?)
                  (str (sanktio-domain/sanktiolaji->teksti (:laji @muokattu)))

                  (and muokataan-vanhaa? (not bonusten-syotto?))
                  "Muokkaa sanktiota"

                  (and muokataan-vanhaa? bonusten-syotto?)
                  "Muokkaa bonusta"

                  :else
                  "Lisää uusi")]

           (when-not muokataan-vanhaa?
             [bonus-sanktio-valikko (r/cursor tila [:lomake])])

           (when (and lukutila? muokataan-vanhaa?)
             [:div.flex-row.alkuun.valistys16
              [napit/yleinen-reunaton "Muokkaa" #(swap! tila update :lukutila not)
               ;; Estä muokkaus-nappulan käyttö laatupoikkeaman kautta tehdyille sanktioille
               ;; ja urakan_paatos-taulusta haetuille sanktioille ja bonuksille
               ;; TODO: Jos/kun lupaussanktio ja lupausbonus sanktio/bonus lajeille tehdään muokkausmahdollisuus tälle lomakkeelle
               ;;       niin, poista "lupaus?" ehto.
               ;;       Tällä hetkellä lupaussanktio ja lupausbonus haetaan urakka_paatos-taulusta, eikä niiden tallentamiselle/poistamiselle
               ;;       ole polkua sanktio/bonus lomakkeilla.
               {:disabled (or (not suorasanktio?) lupaus?)}]
              (cond
                (not suorasanktio?)
                [yleiset/vihje "Lukitun laatupoikkeaman sanktiota ei voi enää muokata." nil 18]
                lupaus?
                [yleiset/vihje "Lupaussanktiota tai lupausbonusta ei voi muokata tällä lomakkeella" nil 18])])


           (if bonusten-syotto?
             ;; Bonus-lomake
             [bonukset-lomake/bonus-lomake sivupaneeli-auki?-atom @muokattu
              ;; Kun bonuksen tallennus tai poisto onnistuu, niin haetaan S&B-listauksen tiedot uudelleen.
              #(tiedot/paivita-sanktiot-ja-bonukset!)
              lukutila? oikeus-muokata?]

             ;;Sanktio-lomake
             [sanktiot-lomake/sanktio-lomake sivupaneeli-auki?-atom lukutila? oikeus-muokata?])])))))


;; --- Sanktioiden listaus ---

(defn- lajisuodatin-valinnat [lajisuodattimet]
  [:div.lajisuodattimet
   [kentat/tee-otsikollinen-kentta
    {:otsikko "Näytä lajit"
     :otsikon-tag :div
     :luokka ""
     :kentta-params {:tyyppi :checkbox-group
                     :vaihtoehdot lajisuodattimet
                     :vaihtoehto-nayta #(:teksti (tiedot/lajisuodatin-tiedot %))
                     :label-luokka "margin-right-16"
                     :nayta-rivina? true}
     :arvo-atom tiedot/sanktio-bonus-suodattimet}]])

(defn- suodattimet-ja-toiminnot [valittu-urakka sivupaneeli-auki?-atom lajisuodattimet]
  [:div.flex-row
   [:div
    [valinnat/urakkavalinnat {:urakka valittu-urakka}
       ^{:key "urakkavalinnat"}
     [urakka-valinnat/urakan-hoitokausi valittu-urakka]]]

   [:div {:style {:flex-grow 2}}
    [lajisuodatin-valinnat lajisuodattimet]]
   [:div {:style {:flex-grow 1}}
    (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                    (:id valittu-urakka))]
      (yleiset/wrap-if
        (not oikeus?)
        [yleiset/tooltip {} :%
         (oikeudet/oikeuden-puute-kuvaus :kirjoitus
           oikeudet/urakat-laadunseuranta-sanktiot)]
        ^{:key "Lisää uusi"}
        [:div.lisaa-nappi {:style {:float "right"}}
         [napit/uusi "Lisää uusi"
          #(do
             (reset! sivupaneeli-auki?-atom true)
             (reset! tiedot/valittu-sanktio (tiedot/uusi-sanktio (:tyyppi valittu-urakka))))
          {:disabled (not oikeus?)}]]))]])


(defn valitse-sanktio-tai-bonus! [rivi sanktio-atom]
  (reset! sanktio-atom rivi)
  ;; TODO: Tässä on jotakin sanktioiden liitteiden hakua valinnan yhteydessä?
  ;;       Pitääkö tunnistaa lisäksi onko bonus valittu ja hakea myös bonuksen liitteet?
  (if (= :virhe (tiedot/hae-sanktion-liitteet! (get-in rivi [:laatupoikkeama :urakka])
                                               (get-in rivi [:laatupoikkeama :id])
                                               sanktio-atom))
    (viesti/nayta-toast! "Sanktion liitteiden hakeminen epäonnistui" :warning)
    (log "Liitteet haettiin onnistuneesti.")))

(defn- sanktion-tai-bonuksen-kuvaus [{:keys [suorasanktio laatupoikkeama] :as sanktio-tai-bonus}]
  ;; Bonuksilla ei tällä hetkellä ole kuvausta.
  ;; Näytetään sanktion kohde, mikäli kyseessä on suorasanktio, eli sanktio on tehty sanktiolomakkeella.
  ;; Jos kyse on laatupoikkeaman kautta tehdystä sanktiosta, näytetään kohteen kuvaus ja mahdollinen TR-osoite.
  (let [kohde (:kohde laatupoikkeama)]
    (if suorasanktio
      (or kohde "–")
      [:span
       (str "Laatupoikkeama: " kohde)
       [:br]
       (str (when (get-in laatupoikkeama [:tr :numero])
              (str " (" (tierekisteri/tierekisteriosoite-tekstina (:tr laatupoikkeama) {:teksti-tie? true}) ")")))])))

(defn- sanktion-tai-bonuksen-perustelu [{:keys [bonus] :as sanktio-tai-bonus}]
  ;; Bonuksille näytetään pelkästään lisätieto
  (if bonus
    [:span (:lisatieto sanktio-tai-bonus)]

    ;; Sanktioilla on kaksi vaihtoehtoista tekstiä:
    ;; Jos sanktio on ns. suorasanktio, eli tehty suoraan sanktiolomakkeella, näytetään perustelu laatupoikkeamasta.
    ;; Jos sanktio on tehty laatupoikkeamat-välilehden kautta, niin näytetään perustelun lisäksi kuvaus.
    (let [perustelu (get-in sanktio-tai-bonus [:laatupoikkeama :paatos :perustelu])
          kuvaus (get-in sanktio-tai-bonus [:laatupoikkeama :kuvaus])]
      (if (:suorasanktio sanktio-tai-bonus)
        [:span
         perustelu]

        [:<>
         (str "Laatupoikkeaman kuvaus: " kuvaus)
         [:br]
         [:br]
         (str "Päätöksen selitys: " perustelu)]))))

(defn fmt-laskutuskuukausi
  "Muokataan päivämäärästä -> Syyskuu 2023 (3. hoitovuosi) tyyppinen string"
  [laskutuskuukausi urakan-alkupaiva]
  (let [kuukausi-vuosi (pvm/koko-kuukausi-ja-vuosi laskutuskuukausi true)
        hoitovuoden-nro (pvm/paivamaara->mhu-hoitovuosi-nro urakan-alkupaiva laskutuskuukausi)]
    (str kuukausi-vuosi " (" hoitovuoden-nro ". hoitokausi)")))

(defn sanktiot-ja-bonukset-listaus
  [sivupaneeli-auki?-atom valittu-urakka]
  (let [;; TODO: Onko tämä käytännössä sama asia kuin alempi "yllapitokohdeurakka?". Ylläpitourakakka?:ssa on mukana lisäksi :valaistus-urakkatyypi
        ;;       Jos yllapitourakka? on OK, niin "yllapitokohdeurakka?" voi poistaa ja korvata viittaukset siihen "yllapitourakka?"-symbolilla.
        yllapitourakka? @tiedot-urakka/yllapitourakka?
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?

        sanktiot (->> @tiedot/haetut-sanktiot-ja-bonukset
                   tiedot/suodata-sanktiot-ja-bonukset
                   (sort-by :kasittelyaika)
                   reverse)
        hoitokauden-alku (first @tiedot-urakka/valittu-hoitokausi)
        hoitokauden-loppu (second @tiedot-urakka/valittu-hoitokausi)
        urakan-alkupaiva (:alkupvm @nav/valittu-urakka)
        urakka-id (when valittu-urakka (:id valittu-urakka))
        urakka-nimi (when valittu-urakka (:nimi valittu-urakka))]

    [:div.sanktiot
     #_[harja.ui.debug/debug sanktiot]

     [:div.header-rivi
      [:div.laadunseuranta-otsikko
       [:h1 {:style {:width "545px"}} (if yllapitourakka? "Sakot ja bonukset" "Sanktiot, bonukset ja arvonvähennykset")]]
      [:div.header-export
       [:div
        ^{:key "raporttixls"}
        [:form {:style {:margin-left "auto"}
                :target "_blank" :method "POST"
                :action (k/excel-url :bonukset-ja-sanktiot)}
         [:input {:type "hidden" :name "parametrit"
                  :value (t/clj->transit {:urakka-id urakka-id
                                          :urakka-nimi urakka-nimi
                                          :alku hoitokauden-alku
                                          :loppu hoitokauden-loppu
                                          :suodattimet @tiedot/sanktio-bonus-suodattimet})}]
         [:button {:type "submit"
                   :class #{"button-secondary-default" "suuri"}}
          [ikonit/ikoni-ja-teksti [ikonit/livicon-download] "Tallenna Excel"]]]]
       [:div
        ^{:key "raporttipdf"}
        [:form {:style {:margin-left "16px"}
                :target "_blank" :method "POST"
                :action (k/pdf-url :bonukset-ja-sanktiot)}
         [:input {:type "hidden" :name "parametrit"
                  :value (t/clj->transit {:urakka-id urakka-id
                                          :urakka-nimi urakka-nimi
                                          :alku hoitokauden-alku
                                          :loppu hoitokauden-loppu
                                          :suodattimet @tiedot/sanktio-bonus-suodattimet})}] ;#{:muistutukset :sanktiot :bonukset :arvonvahennykset}
         [:button {:type "submit"
                   :class #{"button-secondary-default" "suuri"}}
          [ikonit/ikoni-ja-teksti [ikonit/livicon-download] "Tallenna PDF"]]]]]]
     [suodattimet-ja-toiminnot valittu-urakka sivupaneeli-auki?-atom @tiedot/urakan-lajisuodattimet]

     [grid/grid
      {:tyhja (if @tiedot/haetut-sanktiot-ja-bonukset "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
       :rivi-klikattu #(do
                         (reset! sivupaneeli-auki?-atom true)
                         (valitse-sanktio-tai-bonus! % tiedot/valittu-sanktio))
       :rivi-jalkeen-fn #(let [yhteensa-summat (reduce + 0 (map :summa %))
                               yhteensa-indeksit (reduce + 0 (map :indeksikorjaus %))]
                           [{:teksti "Yht." :luokka "lihavoitu"}
                            {:teksti (str (count %) " kpl") :sarakkeita 5 :luokka "lihavoitu"}
                            {:teksti (str (fmt/euro-opt false yhteensa-summat)) :tasaa :oikea :luokka "lihavoitu"}
                            {:teksti (str (fmt/euro-opt false yhteensa-indeksit))
                             :tasaa :oikea :luokka "lihavoitu"}])}
      [{:otsikko "Käsitelty" :nimi :kasittelyaika :fmt pvm/pvm-opt :leveys 1.3}
       {:otsikko "Laskutuskuukausi" :nimi :perintapvm :fmt #(fmt-laskutuskuukausi % urakan-alkupaiva) :leveys 1.5}
       {:otsikko "Laji" :nimi :laji :hae :laji :leveys 2.5 :fmt sanktio-domain/sanktiolaji->teksti}
       (when yllapitokohdeurakka?
         {:otsikko "Kohde" :nimi :kohde :leveys 2
          :hae (fn [rivi]
                 (if (get-in rivi [:yllapitokohde :id])
                   (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                                 :nimi (get-in rivi [:yllapitokohde :nimi])})
                   "Ei liity kohteeseen"))})
       (if yllapitourakka?
         {:otsikko "Kuvaus" :nimi :vakiofraasi
          :hae #(sanktio-domain/yllapidon-sanktiofraasin-nimi (:vakiofraasi %)) :leveys 3}
         {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi)
          :leveys 2.5 :fmt #(cond
                            (and % (= "Ei tarvita sanktiotyyppiä" %)) "–"
                            (and % (not= "Ei tarvita sanktiotyyppiä" %)) %
                            :else "–")})
       (when (not yllapitourakka?)
         {:otsikko "Tapah\u00ADtuma\u00ADpaik\u00ADka/kuvaus" :nimi :tapahtumapaikka
          :tyyppi :komponentti :komponentti sanktion-tai-bonuksen-kuvaus :leveys 3})
       {:otsikko "Perustelu" :nimi :perustelu :leveys 3
        :tyyppi :komponentti :komponentti sanktion-tai-bonuksen-perustelu}
       {:otsikko "Määrä (€)" :nimi :summa :leveys 1.2 :tyyppi :numero :tasaa :oikea
        :hae #(or (fmt/euro-opt false (:summa %))
                "Muistutus")}
       {:otsikko "Indeksi (€)" :nimi :indeksikorjaus :fmt #(fmt/euro-opt false %) :tasaa :oikea :tyyppi :numero :leveys 1.2}]
      sanktiot]
     (when yllapitourakka?
       (yleiset/vihje "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."))]))

(defn sanktiot-ja-bonukset []
  (let [sivupaneeli-auki? (r/atom false)]
    (komp/luo
      (komp/lippu tiedot/nakymassa?)
      (komp/sisaan-ulos #(reset! tiedot-urakka/default-hoitokausi {:ylikirjoita? true
                                                                   :default nil})
        #(reset! tiedot-urakka/default-hoitokausi {:ylikirjoita? false}))
      (fn []
        [:div.laadunseuranta
         (when @sivupaneeli-auki?
           [sivupalkki/oikea
            {:leveys "600px" :sulku-fn #(do
                                          (reset! sivupaneeli-auki? false)
                                          (reset! tiedot/valittu-sanktio nil))}
            [sivupaneeli sivupaneeli-auki?]])
         [sanktiot-ja-bonukset-listaus sivupaneeli-auki? @nav/valittu-urakka]]))))
