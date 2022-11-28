(ns harja.views.urakka.laadunseuranta.sanktiot-ja-bonukset-nakyma
  "Sanktioiden ja bonusten välilehti"
  (:require [reagent.core :refer [atom] :as r]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.loki :refer [log]]

            [harja.tiedot.urakka.laadunseuranta.sanktiot :as tiedot]
            [harja.tiedot.urakka.laadunseuranta :as laadunseuranta]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as tiedot-urakka]
            
            [harja.ui.grid :as grid]
            [harja.ui.komponentti :as komp]
            [harja.ui.napit :as napit]
            [harja.ui.yleiset  :refer [ajax-loader livi-pudotusvalikko] :as yleiset]
            [harja.ui.sivupalkki :as sivupalkki]
            [harja.ui.viesti :as viesti]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.kentat :as kentat]

            [harja.domain.oikeudet :as oikeudet]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.urakka :as u-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain]
            [harja.domain.tierekisteri :as tierekisteri]
            
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.views.urakka.laadunseuranta.sanktiot-lomake :as sanktiot-lomake]))

(defn laji->teksti
  [laji]
  (case laji
    :A "A-ryhmä (tehtäväkohtainen sanktio)"
    :B "B-ryhmä (vakava laiminlyönti)"
    :C "C-ryhmä (määräpäivän ylitys, hallinnollinen laiminlyönti jne.)"
    :muistutus "Muistutus"
    :vaihtosanktio "Vastuuhenkilön vaihto"
    :testikeskiarvo-sanktio "Vastuuhenkilön testipistemäärän alentuminen"
    :tenttikeskiarvo-sanktio "Vastuuhenkilön tenttipistemäärän alentuminen"
    :arvonvahennyssanktio "Arvonvähennys"
    :pohjavesisuolan_ylitys "Pohjavesialueen suolankäytön ylitys"
    :talvisuolan_ylitys "Talvisuolan kokonaiskäytön ylitys"
    :lupaussanktio "Lupaussanktio"
    :yllapidon_muistutus "Muistutus"
    :yllapidon_sakko "Sakko"
    :yllapidon_bonus "Bonus"
    :vesivayla_muistutus "Muistutus"
    :vesivayla_sakko "Sakko"
    :vesivayla_bonus "Bonus"

    :lupausbonus "Lupausbonus"
    :alihankintabonus "Alihankintasopimusten maksuehtobonus"
    :asiakastyytyvaisyysbonus "Asiakastyytyväisyysbonus"
    :muu-bonus "Muu bonus (vahingonkorvaus, liikennevahingot jne.)"
    "- valitse laji -"))

(defn- lajisuodatin-valinnat [lajisuodattimet]
  [:div.lajisuodattimet
   [kentat/tee-otsikollinen-kentta
    {:otsikko "Näytä lajit"
     :otsikon-tag :div
     :kentta-params {:tyyppi :checkbox-group
                     :vaihtoehdot lajisuodattimet
                     :vaihtoehto-nayta #(:teksti (tiedot/lajisuodatin-tiedot %))
                     :label-luokka "margin-right-16"
                     :nayta-rivina? true}
     :arvo-atom tiedot/sanktio-bonus-suodattimet}]])

(defn- suodattimet-ja-toiminnot [valittu-urakka auki? lajisuodattimet]
  [:div.flex-row.tasaa-alkuun
   [valinnat/urakkavalinnat {:urakka valittu-urakka}
    ^{:key "urakkavalinnat"}
    [urakka-valinnat/urakan-hoitokausi-ja-kuukausi valittu-urakka {:kuukausi-otsikko "Käsittelykuukausi"}]]

   [lajisuodatin-valinnat lajisuodattimet]
   (let [oikeus? (oikeudet/voi-kirjoittaa? oikeudet/urakat-laadunseuranta-sanktiot
                   (:id valittu-urakka))]
     (yleiset/wrap-if
       (not oikeus?)
       [yleiset/tooltip {} :%
        (oikeudet/oikeuden-puute-kuvaus :kirjoitus
          oikeudet/urakat-laadunseuranta-sanktiot)]
       ^{:key "Lisää uusi"}
       [:div.lisaa-nappi
        [napit/uusi "Lisää uusi"
         #(do
            (reset! auki? true)
            (reset! tiedot/valittu-sanktio (tiedot/uusi-sanktio (:tyyppi valittu-urakka))))
         {:disabled (not oikeus?)}]]))])


(defn valitse-sanktio! [rivi sanktio-atom]
  (reset! sanktio-atom rivi)
  (if (= :virhe (tiedot/hae-sanktion-liitteet! (get-in rivi [:laatupoikkeama :urakka])
                                               (get-in rivi [:laatupoikkeama :id])
                                               sanktio-atom))
    (viesti/nayta-toast! "Sanktion liitteiden hakeminen epäonnistui" :warning)
    (log "Liitteet haettiin onnistuneesti.")))

(defn- sanktion-tai-bonuksen-kuvaus [{:keys [suorasanktio laatupoikkeama] :as sanktio-tai-bonus}]
  ;; Bonuksilla ei tällä hetkellä ole kuvausta.
  ;; Näytetään sanktion kode, mikäli kyseessä on suorasanktio, eli sanktio on tehty sanktiolomakkeella.
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

(defn sanktiolistaus
  [optiot valittu-urakka]
  (let [sanktiot (->> @tiedot/haetut-sanktiot-ja-bonukset
                   tiedot/suodata-sanktiot-ja-bonukset
                   (sort-by :perintapvm)
                   reverse)
        {:keys [yllapito? auki?]} optiot
        yllapitokohdeurakka? @tiedot-urakka/yllapitokohdeurakka?]
    [:div.sanktiot
     [:h1 (if yllapito? "Sakot ja bonukset" "Sanktiot, bonukset ja arvonvähennykset")]
     [suodattimet-ja-toiminnot valittu-urakka auki? @tiedot/urakan-lajisuodattimet]
     [grid/grid
      {:tyhja (if @tiedot/haetut-sanktiot-ja-bonukset "Ei löytyneitä tietoja" [ajax-loader "Haetaan sanktioita."])
       :rivi-klikattu #(do
                         (reset! auki? true)
                         (valitse-sanktio! % tiedot/valittu-sanktio))
       :rivi-jalkeen-fn #(let [yhteensa-summat (reduce + 0 (map :summa %))
                               yhteensa-indeksit (reduce + 0 (map :indeksikorjaus %))]
                           [{:teksti "Yht." :luokka "lihavoitu"}
                            {:teksti (str (count %) " kpl") :sarakkeita 4 :luokka "lihavoitu"}
                            {:teksti (str (fmt/euro-opt false yhteensa-summat)) :tasaa :oikea :luokka "lihavoitu"}
                            {:teksti (str (fmt/euro-opt false yhteensa-indeksit))
                             :tasaa :oikea :luokka "lihavoitu"}])}
      [{:otsikko "Käsitelty" :nimi :perintapvm :fmt pvm/pvm :leveys 1.5}
       {:otsikko "Laji" :nimi :laji :hae :laji :leveys 3 :fmt laji->teksti}
       (when yllapitokohdeurakka?
         {:otsikko "Kohde" :nimi :kohde :leveys 2
          :hae (fn [rivi]
                 (if (get-in rivi [:yllapitokohde :id])
                   (yllapitokohde-domain/yllapitokohde-tekstina {:kohdenumero (get-in rivi [:yllapitokohde :numero])
                                                                 :nimi (get-in rivi [:yllapitokohde :nimi])})
                   "Ei liity kohteeseen"))})
       (if yllapito?
         {:otsikko "Kuvaus" :nimi :vakiofraasi
          :hae #(sanktio-domain/yllapidon-sanktiofraasin-nimi (:vakiofraasi %)) :leveys 3}
         {:otsikko "Tyyppi" :nimi :sanktiotyyppi :hae (comp :nimi :tyyppi)
          :leveys 3 :fmt #(cond
                            (and % (= "Ei tarvita sanktiotyyppiä" %)) "–"
                            (and % (not= "Ei tarvita sanktiotyyppiä" %)) %
                            :else "–")})
       (when (not yllapito?) {:otsikko "Tapah\u00ADtuma\u00ADpaik\u00ADka/kuvaus" :nimi :tapahtumapaikka
                              :tyyppi :komponentti :komponentti sanktion-tai-bonuksen-kuvaus :leveys 3})
       {:otsikko "Perustelu" :nimi :perustelu :leveys 3.5
        :tyyppi :komponentti :komponentti sanktion-tai-bonuksen-perustelu}
       {:otsikko "Määrä (€)" :nimi :summa :leveys 1.5 :tyyppi :numero :tasaa :oikea
        :hae #(or (fmt/euro-opt false (:summa %))
                "Muistutus")}
       {:otsikko "Indeksi (€)" :nimi :indeksikorjaus :tasaa :oikea :tyyppi :numero :leveys 1.5}]
      sanktiot]
     (when yllapito?
       (yleiset/vihje "Huom! Sakot ovat miinusmerkkisiä ja bonukset plusmerkkisiä."))]))

(defn sanktiot-ja-bonukset [optiot]
  (let [auki? (r/atom false)]
    (komp/luo
      (komp/lippu tiedot/nakymassa?)
      (komp/sisaan-ulos #(reset! tiedot-urakka/default-hoitokausi {:ylikirjoita? true
                                                                   :default nil})
        #(reset! tiedot-urakka/default-hoitokausi {:ylikirjoita? false}))
      (fn []
        (let [optiot (merge optiot
                       {:yllapitokohteet @laadunseuranta/urakan-yllapitokohteet-lomakkeelle
                        :yllapito? @tiedot-urakka/yllapitourakka?
                        :vesivayla? (u-domain/vesivaylaurakka? @nav/valittu-urakka)})]
          [:div.laadunseuranta
           (when @auki?
             [sivupalkki/oikea
              {:leveys "600px" :sulku-fn #(do
                                            (reset! auki? false)
                                            (reset! tiedot/valittu-sanktio nil))}
              [sanktiot-lomake/sivupaneeli (assoc optiot :auki? auki?)]])
           [sanktiolistaus (assoc optiot :auki? auki?) @nav/valittu-urakka]])))))
