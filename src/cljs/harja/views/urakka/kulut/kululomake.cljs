(ns harja.views.urakka.kulut.kululomake
  (:require [tuck.core :as tuck]
            [reagent.core :as r]
            [goog.string :as gstring]
            [goog.string.format]
            [harja.domain.kulut :as kulut]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kulut :as tiedot]
            [harja.ui.debug :as debug]
            [harja.ui.komponentti :as komp]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.liitteet :as liitteet]
            [harja.ui.kentat :as kentat]
            [clojure.string :as str]
            [harja.asiakas.kommunikaatio :as k]
            [harja.transit :as t]
            [harja.pvm :as pvm]
            [harja.ui.valinnat :as valinnat]
            [harja.fmt :as fmt]))

(def kulu-lukittu-teksti "Hoitokauden välikatselmus on pidetty ja kuluja ei voi enää lisätä tai muokata.")

(defn- kulun-poistovarmistus-modaali
  [{:keys [varmistus-fn koontilaskun-kuukausi laskun-pvm kohdistukset tehtavaryhmat]}]
  [:div#kulun-poisto-modaali
   (for [k kohdistukset]
     ^{:key (gensym "trpoisto")}
     [:<>
      [:div.flex-row
       (str "Tehtäväryhmä: "
         (some #(when (= (:tehtavaryhma k) (:id %))
                  (:tehtavaryhma %))
           tehtavaryhmat))]
      [:div.flex-row (str "Määrä: "
                       (:summa k))]])
   [:div.flex-row (str "Koontilaskun kuukausi: "
                    (let [[kk hv] (str/split koontilaskun-kuukausi #"/")]
                      (str (pvm/kuukauden-nimi (pvm/kuukauden-numero kk) true) " - "
                        (get kulut/hoitovuodet-strs (keyword hv)))))]
   [:div.flex-row (str "Laskun päivämäärä: " laskun-pvm)]
   [:div.flex-row (str "Kokonaissumma: "
                    (reduce
                      (fn [a s]
                        (+ a (tiedot/parsi-summa (:summa s))))
                      0
                      kohdistukset))]
   [:div
    [napit/yleinen-toissijainen "Peruuta" (fn [] (modal/piilota!)) {:vayla-tyyli? true :luokka "suuri"}]
    [napit/poista "Poista tiedot" varmistus-fn {:vayla-tyyli? true :luokka "suuri"}]]])

(defn- liitteen-naytto
  [e! {:keys [liite-id liite-nimi liite-tyyppi liite-koko] :as _liite}]
  [:div.liiterivi
   [:div.liitelista
    [liitteet/liitelinkki {:id liite-id
                           :nimi liite-nimi
                           :tyyppi liite-tyyppi
                           :koko liite-koko} (str liite-nimi)]]
   [:div.liitepoisto
    [napit/poista "" #(e! (tiedot/->PoistaLiite liite-id)) {:vayla-tyyli? true :teksti-nappi? true}]]])

(defn- liitteet [e! kulu-lukittu? lomake]
  (let [liitteet (:liitteet lomake)]
    [:div.palsta
     [:h5 "Liite"]
     [:div.liitelaatikko
      [:div.liiterivit
       (if-not (empty? liitteet)
         (into [:<>] (mapv (r/partial liitteen-naytto e!) liitteet))
         [:div.liitelista "Ei liitteitä"])]
      (when-not kulu-lukittu?
        [:div.liitenappi
         [liitteet/lisaa-liite
          (-> @tila/yleiset :urakka :id)
          {:nayta-lisatyt-liitteet? false
           :liite-ladattu #(e! (tiedot/->LiiteLisatty %))}]])]]))

(defn- kulun-tyyppi->tekstiksi [tyyppi]
  (case tyyppi
    :muukulu "Muu kulu"
    :hankintakulu "Hankintakulu (kiinteä tai määrämitattava)"
    :rahavaraus "Rahavaraukselle kohdistettava kulu"
    :lisatyo "Lisätyö"
    :paatos "Hoitovuoden päätös"
    "Tuntematon"))

(defn- nayta-kohdistuksen-virhe? [lomake nro avain]
  (let [{validoi-fn :validoi} (meta lomake)
        validoitu-lomake (validoi-fn lomake)
        validius (get (:validius (meta validoitu-lomake)) [:kohdistukset nro avain])
        validi? (:validi? validius)]
    ;; Näytä virhe, jos annettu arvo ei ole validi
    (not validi?)))

(defn- hankintakulu-kohdistus [e! lomake kohdistus tehtavaryhmat nro]
  (let [;; Hankintakululla ei saa olla kaikkia mahdollisia tehtäväryhmiä. Siivotaan väärät pois tässä
        tehtavaryhmat (filter
                        (fn [t]
                          (let [sisaltaako?
                                (not (or
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "rahavaraus")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "vahinkojen")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "äkilliset")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "hoidonjohtopalkkio")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "erillishankinnat")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "hallintokorvaus")))]
                            sisaltaako?))
                        tehtavaryhmat)]
    [:div.row
     [:div.col-xs-12.col-md-6
      [:div.label-ja-alasveto {:style {:width "320px"}}
       [:span.alasvedon-otsikko "Tehtäväryhmä*"]
       [yleiset/livi-pudotusvalikko {:vayla-tyyli? true
                                     :muokattu? true
                                     :pakollinen? true
                                     :valinta (:tehtavaryhma kohdistus)
                                     :virhe? (nayta-kohdistuksen-virhe? lomake nro :tehtavaryhma)
                                     :format-fn :tehtavaryhma
                                     :valitse-fn #(e! (tiedot/->ValitseTehtavaryhmaKohdistukselle % nro))}
        tehtavaryhmat]]]]))

(defn- muukulu-kohdistus [e! lomake kohdistus tehtavaryhmat toimenpiteet nro]
  (let [tavoitehinta (r/atom (:tavoitehintainen kohdistus))
        ;; Tietokannassa lisätietoja voi olla vain lisätöillä. Mutta uusimpien muutosten mukaan niitä voi olla myös muilla kuluilla.
        ;; Tässä vaiheessa ei ole vielä muokattu tietokantaa, joten nimeäminen on tässä vaiheessa vielä hieman sekava.
        lisatyon-lisatieto (:lisatyon-lisatieto kohdistus)
        ;; Muu-kululla ei saa olla kaikkia mahdollisia tehtäväryhmiä. Siivotaan väärät pois tässä
        tehtavaryhmat (filter
                        (fn [t]
                          (let [sisaltaako?
                                (not (or
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "erillishankinta")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "rahavaraus")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "vahinkojen")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "äkilliset")
                                       (str/includes? (str/lower-case (:tehtavaryhma t)) "hoidonjohtopalkkio")
                                       #_ (str/includes? (str/lower-case (:tehtavaryhma t)) "hallintokorvaus")))]
                            sisaltaako?))
                        tehtavaryhmat)]
    [:div
     [:div.row
      [:div.col-xs-12.col-md-6
       [:div.label-ja-alasveto
        [:span.alasvedon-otsikko "Tavoitehintaan kuuluminen*"]
        [kentat/tee-kentta {:tyyppi :radio-group
                            :vaihtoehdot [:true :false]
                            :vayla-tyyli? true
                            :nayta-rivina? true
                            :vaihtoehto-nayta {:true "Kuuluu tavoitehintaan"
                                               :false "Ei kuulu tavoitehintaan"}
                            :valitse-fn #(e! (tiedot/->TavoitehintaanKuuluminen % nro))}
         tavoitehinta]]]]

     [:div.row
      (if (= :true @tavoitehinta)
        ;; Tavoitehintaisella muulla kululla on tehtäväryhmä
        [:div.col-xs-12.col-md-4
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Tehtäväryhmä*"]
          [yleiset/livi-pudotusvalikko {:vayla-tyyli? true
                                        :muokattu? true
                                        :pakollinen? true
                                        :valinta (:tehtavaryhma kohdistus)
                                        :virhe? (nayta-kohdistuksen-virhe? lomake nro :tehtavaryhma)
                                        :format-fn :tehtavaryhma
                                        :valitse-fn #(e! (tiedot/->ValitseTehtavaryhmaKohdistukselle % nro))}
           tehtavaryhmat]]]
        ;; Ei tavoitehintaisella muulla kululla on toimenpide
        [:div.col-xs-12.col-md-4
         [:div.label-ja-alasveto
          [:span.alasvedon-otsikko "Toimenpide*"]
          [yleiset/livi-pudotusvalikko {:valinta (:toimenpide kohdistus)
                                        :vayla-tyyli? true
                                        :muokattu? true
                                        :virhe? (nayta-kohdistuksen-virhe? lomake nro :toimenpide)
                                        :format-fn :toimenpide
                                        :valitse-fn #(e! (tiedot/->ValitseToimenpideKohdistukselle % nro))}
           toimenpiteet]]])
      [:div.col-xs-12.col-md-6
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Lisätieto *"
         :luokka "poista-label-top-margin"
         :vayla-tyyli? true
         :otsikon-luokka ""
         :arvo-atom (r/wrap lisatyon-lisatieto
                      #(e! (tiedot/->LisatyonLisatieto % nro)))
         :kentta-params {:tyyppi :string
                         :vayla-tyyli? true
                         :muokattu? true
                         :virhe? (nayta-kohdistuksen-virhe? lomake nro :lisatyon-lisatieto)}}]]]]))

(defn- rahavaraus-kohdistus [e! lomake kohdistus rahavaraukset nro]
  [:div.row
   [:div.col-xs-12.col-md-4
    [:div.label-ja-alasveto
     [:span.alasvedon-otsikko "Rahavaraus*"]
     [yleiset/livi-pudotusvalikko {:valinta (:rahavaraus kohdistus)
                                   :format-fn :nimi
                                   :vayla-tyyli? true
                                   :muokattu? true
                                   :virhe? (nayta-kohdistuksen-virhe? lomake nro :rahavaraus)
                                   :valitse-fn #(e! (tiedot/->ValitseRahavarausKohdistukselle % nro))}
      rahavaraukset]]]
   [:div.col-xs-12.col-md-6
    [:div.label-ja-alasveto
     [:span.alasvedon-otsikko "Tehtäväryhmä*"]
     [yleiset/livi-pudotusvalikko {:valinta (:tehtavaryhma kohdistus)
                                   :format-fn :tehtavaryhma
                                   :vayla-tyyli? true
                                   :muokattu? true
                                   :virhe? (nayta-kohdistuksen-virhe? lomake nro :tehtavaryhma)
                                   :valitse-fn #(e! (tiedot/->ValitseTehtavaryhmaKohdistukselle % nro))}
      (get-in kohdistus [:rahavaraus :tehtavaryhmat])]]]])

(defn- lisatyo-kohdistus [e! lomake kohdistus toimenpiteet nro]
  (let [lisatyon-lisatieto (:lisatyon-lisatieto kohdistus)]
    [:div.row
     [:div.col-xs-12.col-md-4
      [:div.label-ja-alasveto
       [:span.alasvedon-otsikko "Toimenpide*"]
       [yleiset/livi-pudotusvalikko {:valinta (:toimenpide kohdistus)
                                     :vayla-tyyli? true
                                     :format-fn :toimenpide
                                     :muokattu? true
                                     :virhe? (nayta-kohdistuksen-virhe? lomake nro :toimenpide)
                                     :valitse-fn #(e! (tiedot/->ValitseToimenpideKohdistukselle % nro))}
        toimenpiteet]]]
     [:div.col-xs-12.col-md-6
      [kentat/tee-otsikollinen-kentta
       {:otsikko "Lisätieto *"
        :luokka "poista-label-top-margin"
        :vayla-tyyli? true
        :otsikon-luokka ""
        :arvo-atom (r/wrap lisatyon-lisatieto
                     #(e! (tiedot/->LisatyonLisatieto % nro)))
        :kentta-params {:tyyppi :string
                        :vayla-tyyli? true
                        :muokattu? true
                        :virhe? (nayta-kohdistuksen-virhe? lomake nro :lisatyon-lisatieto)}}]]]))

(defn- hoitovuodenpaatos-kohdistus [e! lomake kohdistus nro]
  (let [lisatyon-lisatieto (:lisatyon-lisatieto kohdistus)
        hoitovuoden-paatostyyppi (r/atom (:hoitovuoden-paatostyyppi kohdistus))
        _ (js/console.log "hoitovuodenpaatos-kohdistus :: hoitovuoden-paatostyyppi" (pr-str hoitovuoden-paatostyyppi) )]
    [:div.row
     [:div.col-xs-12.col-md-4
      [:div.label-ja-alasveto
       [:span.alasvedon-otsikko "Hoitovuoden päätöksen tyyppi*"]
       [:p (@hoitovuoden-paatostyyppi tiedot/vuoden-paatoksen-kulun-tyypit)]]]
     [:div.col-xs-12.col-md-6
      [kentat/tee-otsikollinen-kentta
       {:otsikko "Lisätieto *"
        :luokka "poista-label-top-margin"
        :vayla-tyyli? true
        :otsikon-luokka ""
        :arvo-atom (r/wrap lisatyon-lisatieto
                     #(e! (tiedot/->LisatyonLisatieto % nro)))
        :kentta-params {:tyyppi :string
                        :vayla-tyyli? true
                        :disabled? true
                        :muokattu? true
                        :virhe? (nayta-kohdistuksen-virhe? lomake nro :lisatyon-lisatieto)}}]]]))



(defn- nayta-kohdistus [e! lomake nro kohdistus tehtavaryhmat rahavaraukset toimenpiteet urakoitsija-maksaa?]
  (let [kohdistustyyppi (:tyyppi kohdistus)
        ;; Varmistetaan, että tehtäväryhmissä ei ole vääriä juttuja tälle kohdistukselle
        tehtavaryhmat (tiedot/kasittele-tehtavaryhmat tehtavaryhmat (:tehtavaryhma kohdistus))
        ;; Kohdistustyypit vaihtelee sen mukaan, onko hoitovuoden päätöstä valittu. Jos on, niin kulun tyyppiä ei voi vaihtaa
        kohdistustyyppit (if (:vuoden-paatos-valittu? lomake)
                           [:paatos]
                           [:hankintakulu :rahavaraus :lisatyo :muukulu])
        voiko-muokata? (cond
                        ;; Jos kohdistus on hoitovuoden päätös, sitä ei voi muokata
                        (= :paatos kohdistustyyppi) false
                        :else true)]
    [:div {:style {:background-color "#D6D6D6"
                   :padding-bottom "20px"}}
     ;; Otsikko ja poista nappi
     [:div.row
      [:div.col-xs-12.col-md-6 [:h3 (str "Kohdistus " (inc nro))]]
      [:div.col-xs-12.col-md-6 {:style {:float "right"}}
       (when voiko-muokata?
         [napit/poista "Poista kohdistus"
          #(e! (tiedot/->PoistaKohdistus nro))
          {:vayla-tyyli? true
           :teksti-nappi? true
           :luokka "pieni pull-right"}])]]

     [:div.row
      [:div.col-xs-12.col-md-6
       ;; Kulun tyyppi
       [:div.label-ja-alasveto
        [:span.alasvedon-otsikko "Kulun tyyppi*"]
        [yleiset/livi-pudotusvalikko {:valinta kohdistustyyppi
                                      :disabled (not voiko-muokata?)
                                      :format-fn #(kulun-tyyppi->tekstiksi %)
                                      :valitse-fn #(e! (tiedot/->KohdistusTyyppi % nro))}
         kohdistustyyppit]]]]


     ;; Kululle valitaan tehtäväryhmä, rahavaraus tai tavoitehintaan kuuluminen sen perusteella, että minkä tyyppinen kohdistus on kyseessä
     (case kohdistustyyppi
       :muukulu [muukulu-kohdistus e! lomake kohdistus tehtavaryhmat toimenpiteet nro]
       :hankintakulu [hankintakulu-kohdistus e! lomake kohdistus tehtavaryhmat nro]
       :rahavaraus [rahavaraus-kohdistus e! lomake kohdistus rahavaraukset nro]
       :lisatyo [lisatyo-kohdistus e! lomake kohdistus toimenpiteet nro]
       :paatos [hoitovuodenpaatos-kohdistus e! kohdistus nro])

     ;; Kohdistuksen summa
     [:div.row
      [:div.col-xs-12.col-md-2
       [:div {:style {:padding-left "5px"}}
        [kentat/tee-otsikollinen-kentta
         {:otsikko "Määrä € *"
          :luokka #{}
          :arvo-atom (r/wrap (:summa kohdistus) #(e! (tiedot/->KohdistuksenSumma % nro)))
          :kentta-params {:disabled? (or (not voiko-muokata?) (:lukittu? kohdistus))
                          :tyyppi :euro
                          :vaadi-negatiivinen? urakoitsija-maksaa?
                          :vaadi-positiivinen-numero? (not urakoitsija-maksaa?)
                          ;; TODO: Kehitä validointi tähän :virhe? (not (validi-ei-tarkistettu-tai-ei-koskettu? summa-meta))
                          :input-luokka "maara-input"
                          :vayla-tyyli? true}}]]]]

     ]))

(defn kululomake [e! app]
  (let [_ (js/console.log "kululomake 1")
        syottomoodi (:syottomoodi app)
        tehtavaryhmat (:tehtavaryhmat app)
        rahavaraukset (:rahavaraukset app)
        toimenpiteet (:toimenpiteet app)
        lomake (:lomake app)
        erapaiva (:erapaiva lomake)
        kohdistukset (:kohdistukset lomake)
        koontilaskun-kuukausi (:koontilaskun-kuukausi lomake)
        tehtavaryhma (:tehtavaryhma lomake)
        ;; Jos kulun eräpäivä osuu vuodelle, josta on välikatselmus pidetty, kulu lukitaan
        erapaivan-hoitovuosi (when erapaiva
                               (pvm/vuosi (first (pvm/paivamaaran-hoitokausi erapaiva))))
        kulu-lukittu? (when erapaivan-hoitovuosi
                        (some #(and
                                 (= erapaivan-hoitovuosi (:vuosi %))
                                 (:paatos-tehty? %))
                          (:vuosittaiset-valikatselmukset app)))
        ;; Vuoden päätöksen kulut voivatkin olla urakoitsijan maksettavia!
        urakoitsija-maksaa? (and (:vuoden-paatos-valittu? lomake)
                              (=
                                (:id (tiedot/avain->tehtavaryhma tehtavaryhmat :tavoitehinnan-ylitys))
                                (:tehtavaryhma (first (:kohdistukset lomake)))))


        {:keys [alkupvm loppupvm]} (-> @tila/tila :yleiset :urakka)
        alkuvuosi (pvm/vuosi alkupvm)
        loppuvuosi (pvm/vuosi loppupvm)
        hoitokauden-nro-vuodesta (fn [vuosi urakan-alkuvuosi urakan-loppuvuosi]
                                   (when (and (<= urakan-alkuvuosi vuosi) (>= urakan-loppuvuosi vuosi))
                                     (inc (- vuosi urakan-alkuvuosi))))
        hoitokaudet-ilman-valikatselmusta (keep #(when (not= true (:paatos-tehty? %))
                                                   (hoitokauden-nro-vuodesta (:vuosi %) alkuvuosi loppuvuosi))
                                            (:vuosittaiset-valikatselmukset app))
        koontilaskun-kuukaudet (for [hv hoitokaudet-ilman-valikatselmusta
                                     kk tiedot/kuukaudet]
                                 (str (name kk) "/" hv "-hoitovuosi"))

        tarkistukset (:tarkistukset lomake)
        laskun-nro-lukittu? (and (some? (:numerolla-tarkistettu-pvm tarkistukset))
                              (not (false? (:numerolla-tarkistettu-pvm tarkistukset))))
        laskun-nro-virhe? (if (and (some? (:numerolla-tarkistettu-pvm tarkistukset))
                                (not (false? (:numerolla-tarkistettu-pvm tarkistukset)))
                                (or
                                  (nil? (:erapaiva-tilapainen lomake))
                                  (and (some? (:erapaiva-tilapainen lomake))
                                    (not (pvm/sama-pvm? (:erapaiva-tilapainen lomake)
                                           (get-in tarkistukset [:numerolla-tarkistettu-pvm :erapaiva]))))))
                            true
                            false)

        kk-droppari-disabled (or
                               (not= 0 (get-in app [:parametrit :haetaan]))
                               laskun-nro-virhe?
                               laskun-nro-lukittu?)

        ;; Validoidaan koko lomake
        lomake-validi? (tiedot/validoi-lomake lomake)]
    [:div
     [:div.row
      #_ [debug/debug app]]
     [:div.row
      ;; Otsikko
      [:div.col-xs-12.col-md-6
       [napit/takaisin "Takaisin"
        #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
        {:vayla-tyyli? true :teksti-nappi? true :style {:font-size "14px"}}]
       [:h2 (str (if-not (nil? (:id lomake)) "Muokkaa kulua" "Uusi kulu"))]]

      ;; Poista-nappi
      [:div.col-xs-12.col-md-6
       (when (and (not (nil? (:id lomake))) (not kulu-lukittu?))
         [napit/poista "Poista kulu"
          #(modal/nayta! {:otsikko "Haluatko varmasti poistaa kulun?"}
             [kulun-poistovarmistus-modaali {:varmistus-fn (fn []
                                                             (modal/piilota!)
                                                             (e! (tiedot/->PoistaKulu (:id lomake))))
                                             :kohdistukset kohdistukset
                                             :koontilaskun-kuukausi koontilaskun-kuukausi
                                             :tehtavaryhma tehtavaryhma
                                             :laskun-pvm (pvm/pvm erapaiva)
                                             :tehtavaryhmat tehtavaryhmat}])
          {:vayla-tyyli? true
           :teksti-nappi? true
           :style {:font-size "14px"
                   :margin-left "auto"
                   :float "right"}}])]
      ]
     ;; Onko kulu lukittu
     (when kulu-lukittu? [:div.palstat [:div.palsta.punainen-teksti kulu-lukittu-teksti]])

     #_ [debug/debug lomake]
     (map-indexed
       (fn [index kohdistus]
         ^{:key (str "kohdistus-" index)}
         [nayta-kohdistus e! lomake index kohdistus tehtavaryhmat rahavaraukset toimenpiteet urakoitsija-maksaa?])
       kohdistukset)

     (when (not kulu-lukittu?)
       [napit/yleinen-toissijainen "Lisää kohdistus"
        #(e! (tiedot/->LisaaKohdistus lomake))
        {:ikoni [ikonit/plus-sign]
         :vayla-tyyli? true
         :luokka "suuri"
         :teksti-nappi? true}])

     ;; Laskun tiedot
     [:div.row
      [:div.col-xs-12.col-md-6
       [:h5 "Laskun tiedot"]]]
     [:div.row
      [:div.col-xs-12.col-md-6
       [:div.label-ja-alasveto
        [:span.alasvedon-otsikko "Koontilaskun kuukausi*"]
        [yleiset/livi-pudotusvalikko {;; TODO: tee validointi tähän
                                      #_#_:virhe? (and
                                                    (not kk-droppari-disabled)
                                                    (not (validi-ei-tarkistettu-tai-ei-koskettu? koontilaskun-kuukausi-meta)))
                                      :data-cy "koontilaskun-kk-dropdown"
                                      :disabled (or kk-droppari-disabled kulu-lukittu?)
                                      :vayla-tyyli? true
                                      :skrollattava? true
                                      :valinta koontilaskun-kuukausi
                                      :valitse-fn #(e! (tiedot/->KoontilaskunKuukausi %))
                                      :format-fn tiedot/koontilaskun-kk-formatter}
         koontilaskun-kuukaudet]]]]

     [:div.row
      [:div.col-xs-12.col-md-2
       [:div {:style {:padding-left "5px"}}
        [:label "Laskun pvm*"]
        [pvm-valinta/pvm-valintakalenteri-inputilla
         {:valitse #(e! (tiedot/->ValitseErapaiva %)) #_(r/partial paivita-lomakkeen-arvo {:paivitys-fn paivitys-fn
                                                                                           :polku :erapaiva
                                                                                           :optiot {:validoitava? true}})
          #_#_:luokat #{(str "input" (if (or
                                           (validi-ei-tarkistettu-tai-ei-koskettu? erapaiva-meta)
                                           disabled) "" "-error") "-default")
                        "komponentin-input"}
          :paivamaara (or
                        erapaiva
                        (kulut/koontilaskun-kuukausi->pvm
                          koontilaskun-kuukausi
                          (-> @tila/yleiset :urakka :alkupvm)
                          (-> @tila/yleiset :urakka :loppupvm)))
          :pakota-suunta false
          :disabled (or (nil? koontilaskun-kuukausi)
                      kulu-lukittu?)
          :placeholder (when (nil? koontilaskun-kuukausi) "Valitse koontilaskun kuukausi ensin")
          :valittava?-fn (kulut/koontilaskun-kuukauden-sisalla?-fn
                           koontilaskun-kuukausi
                           (-> @tila/yleiset :urakka :alkupvm)
                           (-> @tila/yleiset :urakka :loppupvm))}]]]]

     [:div.row
      [:div.col-xs-12.col-md-2 {:style {:padding-left "20px"}}
       [kentat/tee-otsikollinen-kentta
        {:kentta-params {:tyyppi :string
                         :vayla-tyyli? true
                         :on-blur #(e! (tiedot/->OnkoLaskunNumeroKaytossa (.. % -target -value)))
                         ;:virhe? laskun-nro-virhe?
                         :disabled? kulu-lukittu?}
         :otsikko "Koontilaskun numero"
         :luokka #{}
         :arvo-atom (r/wrap
                      (:laskun-numero lomake)
                      #(e! (tiedot/->KoontilaskunNumero %))
                      #_(r/partial paivita-lomakkeen-arvo
                          {:paivitys-fn paivitys-fn
                           :optiot {:validoitava? true}
                           :polku :laskun-numero}))}]]]
     [:div.row
      [:div.col-xs-12.col-md-6
       (when (or laskun-nro-lukittu? laskun-nro-virhe?)
         [:label (str "Annetulla numerolla on jo olemassa kirjaus, jonka päivämäärä on "
                   (-> tarkistukset
                     :numerolla-tarkistettu-pvm
                     :erapaiva
                     pvm/pvm)
                   ". Yhdellä laskun numerolla voi olla yksi päivämäärä, joten kulu kirjataan samalle päivämäärälle. Jos haluat kirjata laskun eri päivämäärälle, vaihda laskun numero.")])]]

     [:div.row
      [:div.col-xs-12.col-md-4
       [:h5 "Määrä € *"]
       [:h2 (str (reduce + (map :summa kohdistukset)) " €")]]
      [:div.col-xs-12.col-md-6
       [liitteet e! kulu-lukittu? lomake]]]

     [:div.row
      [:div.col-xs-12.col-md-6
       [:div.kulu-napit
        [napit/tallenna "Tallenna" #(e! (tiedot/->TallennaKulu))
         {:vayla-tyyli? true
          :luokka "suuri"
          :disabled (or (not lomake-validi?) kulu-lukittu?)}]
        [napit/peruuta "Peruuta" #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
         {:ikoni [ikonit/remove]
          :luokka "suuri"
          :vayla-tyyli? true}]]]]
     [:div.row
      [:div.col-xs-12.col-md-6
       (when urakoitsija-maksaa? [:div.caption.margin-top-4 "Kulu kirjataan miinusmerkkisenä"])]]]))
