(ns harja.views.urakka.kulut.kululomake
  (:require [reagent.core :as r]
            [goog.string.format]
            [goog.string :as gstring]
            [clojure.string :as str]

            [harja.fmt :as fmt]
            [harja.domain.muutos-domain :as muutos-domain]
            [harja.domain.kulut :as kulut]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.tiedot.urakka.kulut.mhu-kulut :as tiedot]
            [harja.tiedot.urakka.siirtymat :as siirtymat]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.pvm :as pvm-valinta]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.ui.modal :as modal]
            [harja.ui.liitteet :as liitteet]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.kentat :as kentat]
            [harja.pvm :as pvm]
            [harja.asiakas.kommunikaatio :as k]))

(def kulu-lukittu-teksti "Hoitokauden välikatselmuksen tavoitehintaan liittyvät päätökset on tehty, joten kuluja ei voi enää lisätä tai muokata.")

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
   [:hr]
   [:div.kulu-poisto-footer
    [napit/poista "Poista tiedot" varmistus-fn]
    [napit/peruuta "Peruuta" (fn [] (modal/piilota!))]]])

(defn- liitteen-naytto
  [e! {:keys [liite-id liite-nimi liite-tyyppi liite-koko] :as _liite}]
  [:div.liiterivi
   [:div.liitelista
    [liitteet/liitelinkki {:id liite-id
                           :nimi liite-nimi
                           :tyyppi liite-tyyppi
                           :koko liite-koko} (str liite-nimi)]]
   [:div.liitepoisto
    [napit/poista "" #(e! (tiedot/->PoistaLiite liite-id))
     {:vayla-tyyli? true :teksti-nappi? false :luokka "napiton-nappi pelkka-ikoni"}]]])

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
    :jjh-muutos (:jjh-muutos muutos-domain/+muutos-kulu-tyypit+)
    :erillisrahoitettu-muutos (:erillisrahoitettu-muutos muutos-domain/+muutos-kulu-tyypit+)
    "Tuntematon"))

(defn- nayta-kohdistuksen-virhe? [lomake nro avain]
  (let [{validoi-fn :validoi} (meta lomake)
        validoitu-lomake (validoi-fn lomake)
        validius (get (:validius (meta validoitu-lomake)) [:kohdistukset nro avain])
        validi? (:validi? validius)]
    ;; Näytä virhe, jos annettu arvo ei ole validi
    (not validi?)))

(defn tehtavan-valinta [{:keys [valitse-fn disabled virhe? valinta format-fn tehtava-haku-menossa]} tehtavat]
  [:<>
   (when (or (seq tehtavat) valinta)
     [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
      [:div.label-ja-alasveto {:style {:width "320px"}}
       [:span.alasvedon-otsikko "Tehtävä*"]
       [yleiset/livi-pudotusvalikko
        {:data-cy "tehtava-dropdown"
         :placeholder "Valitse tehtävä"
         :valinta valinta
         :format-fn format-fn
         :pakollinen? true
         :valitse-fn valitse-fn
         :disabled disabled
         :virhe? virhe?
         :vayla-tyyli? true}
        tehtavat]]])
   (when tehtava-haku-menossa
     [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
      [:div.margin-top-32
       [yleiset/ajax-loader-pieni "Ladataan mahdollisia tehtäviä..."]]])])

(defn lisatieto [e! lisatieto lomake nro]
  [:div.col-xs-12.col-md-3
   [kentat/tee-otsikollinen-kentta
    {:otsikko "Lisätieto"
     :vayla-tyyli? true
     :otsikon-luokka ""
     :arvo-atom (r/wrap lisatieto
                  #(e! (tiedot/->KohdistuksenLisatieto % nro)))
     :kentta-params {:tyyppi :string
                     :vayla-tyyli? true
                     :aputeksti "Kirjoita tähän halutessasi lisätietoa"
                     :virhe? (nayta-kohdistuksen-virhe? lomake nro :lisatyon-lisatieto)}}]])

(defn- hankintakulu-kohdistus [e! lomake kohdistus tehtavaryhmat nro]
  (let [;; Hankintakululla ei saa olla kaikkia mahdollisia tehtäväryhmiä. Siivotaan väärät pois tässä
        kielletyt-tehtavaryhmat #{"rahavaraus" "vahinkojen" "äkilliset" "hoidonjohtopalkkio"
                                  "erillishankinnat" "hallintokorvaus" "t4"}
        tehtavaryhmat (remove (fn [tr]
                                (some
                                  (fn [kielletty-tr]
                                    (str/includes? (str/lower-case (:tehtavaryhma tr)) kielletty-tr))
                                  kielletyt-tehtavaryhmat))
                        tehtavaryhmat)
        tehtavat (:tehtavaryhman-tehtavat kohdistus)
        ;; Jos tehtävistä löytyy "Muu tehtävä" id -1, ja kohdistuksen "muu-tehtava-kaytossa" = true, niin aseta se käyttöön
        ;; Kyseessä on ns. dummy tehtävä, jota ei voi valita normaalisti, mutta joka on olemassa tietokannassa, jotta käyttöliittymässä voi näyttää "Muu tehtävä" valinnan
        trlla-on-muu-tehtava? (some #(= -1 (:id %)) tehtavat)
        muu-tehtava-kaytossa? (:muu-tehtava-kaytossa kohdistus)
        kohdistus (if (and trlla-on-muu-tehtava? muu-tehtava-kaytossa?)
                    (assoc kohdistus :tehtava tiedot/muu-tehtava)
                    kohdistus)
        tehtava-haku-menossa (:tehtava-haku-menossa kohdistus)]
    [:div
     [:div.row
      [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
       [:div.label-ja-alasveto {:style {:width "320px"}}
        [:span.alasvedon-otsikko "Tehtäväryhmä*"]
        [yleiset/livi-pudotusvalikko {:data-cy "hankintakulu-tehtavaryhma-dropdown"
                                      :vayla-tyyli? true
                                      :muokattu? true
                                      :pakollinen? true
                                      :valinta (:tehtavaryhma kohdistus)
                                      :skrollattava? true
                                      :virhe? (nayta-kohdistuksen-virhe? lomake nro :tehtavaryhma)
                                      :format-fn :tehtavaryhma
                                      :valitse-fn #(do
                                                     ;; Hankintakulut on tavoitehintaisia 
                                                     (e! (tiedot/->TavoitehintaanKuuluminen :true nro))
                                                     (e! (tiedot/->ValitseTehtavaryhmaKohdistukselle % nro)))}
         tehtavaryhmat]]]
      [tehtavan-valinta {:valitse-fn #(e! (tiedot/->ValitseTehtavaKohdistukselle % nro))
                         :disabled tehtava-haku-menossa
                         :virhe? (nayta-kohdistuksen-virhe? lomake nro :tehtava)
                         :valinta (:tehtava kohdistus)
                         :format-fn :nimi
                         :tehtava-haku-menossa tehtava-haku-menossa}
       tehtavat]
      [lisatieto e! (:lisatyon-lisatieto kohdistus) lomake nro]]]))



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
                                       #_(str/includes? (str/lower-case (:tehtavaryhma t)) "hallintokorvaus")))]
                            sisaltaako?))
                        tehtavaryhmat)
        tehtavat (:tehtavaryhman-tehtavat kohdistus)
        tehtava-haku-menossa (:tehtava-haku-menossa kohdistus)]
    [:div
     [:div.row
      [:div.col-xs-12.col-md-6
       [:div.label-ja-alasveto {:style {:width "320px"}}
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
        [:<>
         [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
          [:div.label-ja-alasveto {:style {:width "320px"}}
           [:span.alasvedon-otsikko "Tehtäväryhmä*"]
           [yleiset/livi-pudotusvalikko {:vayla-tyyli? true
                                         :muokattu? true
                                         :pakollinen? true
                                         :valinta (:tehtavaryhma kohdistus)
                                         :virhe? (nayta-kohdistuksen-virhe? lomake nro :tehtavaryhma)
                                         :format-fn :tehtavaryhma
                                         :valitse-fn #(do
                                                        (e! (tiedot/->ValitseTehtavaryhmaKohdistukselle % nro)))}
            tehtavaryhmat]]]
         [tehtavan-valinta {:valitse-fn #(e! (tiedot/->ValitseTehtavaKohdistukselle % nro))
                            :disabled tehtava-haku-menossa
                            :virhe? (nayta-kohdistuksen-virhe? lomake nro :tehtava)
                            :valinta (:tehtava kohdistus)
                            :format-fn :nimi
                            :tehtava-haku-menossa tehtava-haku-menossa}
          tehtavat]]
        ;; Ei tavoitehintaisella muulla kululla on toimenpide
        [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
         [:div.label-ja-alasveto {:style {:width "320px"}}
          [:span.alasvedon-otsikko "Toimenpide*"]
          [yleiset/livi-pudotusvalikko {:valinta (:toimenpide kohdistus)
                                        :vayla-tyyli? true
                                        :muokattu? true
                                        :virhe? (nayta-kohdistuksen-virhe? lomake nro :toimenpide)
                                        :format-fn :toimenpide
                                        :valitse-fn #(e! (tiedot/->ValitseToimenpideKohdistukselle % nro))}
           toimenpiteet]]])
      [:div.col-xs-12.col-md-6 {:style {:max-width "350px"}}
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Lisätieto *"
         :vayla-tyyli? true
         :tyylit {:width "150px"}
         :otsikon-luokka ""
         :arvo-atom (r/wrap lisatyon-lisatieto
                      #(e! (tiedot/->KohdistuksenLisatieto % nro)))
         :kentta-params {:tyyppi :string
                         :vayla-tyyli? true
                         :muokattu? true
                         :virhe? (nayta-kohdistuksen-virhe? lomake nro :lisatyon-lisatieto)}}]]]]))

(defn- rahavaraus-kohdistus [e! lomake kohdistus rahavaraukset nro]
  (let [tehtavaryhmat (sort-by :jarjestys (get-in kohdistus [:rahavaraus :tehtavaryhmat]))]
    [:div.row
     [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
      [:div.label-ja-alasveto {:style {:width "320px"}}
       [:span.alasvedon-otsikko "Rahavaraus*"]
       [yleiset/livi-pudotusvalikko {:valinta (:rahavaraus kohdistus)
                                     :format-fn :nimi
                                     :vayla-tyyli? true
                                     :muokattu? true
                                     :virhe? (nayta-kohdistuksen-virhe? lomake nro :rahavaraus)
                                     :valitse-fn #(do
                                                    ;; Rahavaraukset on tavoitehintaisia 
                                                    (e! (tiedot/->TavoitehintaanKuuluminen :true nro))
                                                    (e! (tiedot/->ValitseRahavarausKohdistukselle % nro)))}
        rahavaraukset]]]
     [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
      [:div.label-ja-alasveto {:style {:width "320px"}}
       [:span.alasvedon-otsikko "Tehtäväryhmä*"]
       [yleiset/livi-pudotusvalikko {:valinta (:tehtavaryhma kohdistus)
                                     :format-fn :tehtavaryhma
                                     :vayla-tyyli? true
                                     :muokattu? true
                                     :virhe? (nayta-kohdistuksen-virhe? lomake nro :tehtavaryhma)
                                     :valitse-fn #(e! (tiedot/->ValitseTehtavaryhmaKohdistukselle % nro))}
        tehtavaryhmat]]]

     [lisatieto e! (:lisatyon-lisatieto kohdistus) lomake nro]]))

(defn- lisatyo-kohdistus [e! lomake kohdistus toimenpiteet nro]
  (let [lisatyon-lisatieto (:lisatyon-lisatieto kohdistus)]
    [:div.row
     [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
      [:div.label-ja-alasveto {:style {:width "320px"}}
       [:span.alasvedon-otsikko "Toimenpide*"]
       [yleiset/livi-pudotusvalikko {:valinta (:toimenpide kohdistus)
                                     :vayla-tyyli? true
                                     :format-fn :toimenpide
                                     :muokattu? true
                                     :virhe? (nayta-kohdistuksen-virhe? lomake nro :toimenpide)
                                     :valitse-fn #(e! (tiedot/->ValitseToimenpideKohdistukselle % nro))}
        toimenpiteet]]]
     [:div.col-xs-12.col-md-6 {:style {:width "350px"}}
      [kentat/tee-otsikollinen-kentta
       {:otsikko "Lisätieto *"
        :vayla-tyyli? true
        :otsikon-luokka ""
        :arvo-atom (r/wrap lisatyon-lisatieto
                     #(e! (tiedot/->KohdistuksenLisatieto % nro)))
        :kentta-params {:tyyppi :string
                        :vayla-tyyli? true
                        :muokattu? true
                        :virhe? (nayta-kohdistuksen-virhe? lomake nro :lisatyon-lisatieto)}}]]]))

(defn- hoitovuodenpaatos-kohdistus [e! lomake kohdistus nro]
  (let [tavoitehinta (r/atom (:tavoitehintainen kohdistus))
        lisatyon-lisatieto (:lisatyon-lisatieto kohdistus)
        hoitovuoden-paatostyyppi (r/atom (:hoitovuoden-paatostyyppi kohdistus))]
    [:div.row
     [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
      [:div.label-ja-alasveto {:style {:width "320px"}}
       [:span.alasvedon-otsikko "Hoitovuoden päätöksen tyyppi*"]
       [:p (@hoitovuoden-paatostyyppi tiedot/vuoden-paatoksen-kulun-tyypit)]]]
     [:div.col-xs-12.col-md-3
      [:div.label-ja-alasveto {:style {:width "320px"}}
       [:span.alasvedon-otsikko "Tavoitehintaan kuuluminen*"]
       [kentat/tee-kentta {:tyyppi :radio-group
                           :vaihtoehdot [:true :false]
                           :vayla-tyyli? true
                           :nayta-rivina? true
                           :disabloitu? true
                           :vaihtoehto-nayta {:true "Kuuluu tavoitehintaan"
                                              :false "Ei kuulu tavoitehintaan"}
                           :valitse-fn #(e! (tiedot/->TavoitehintaanKuuluminen % nro))}
        tavoitehinta]]]
     [:div.col-xs-12.col-md-6 {:style {:width "350px"}}
      [kentat/tee-otsikollinen-kentta
       {:otsikko "Lisätieto *"
        :vayla-tyyli? true
        :otsikon-luokka ""
        :arvo-atom (r/wrap lisatyon-lisatieto
                     #(e! (tiedot/->KohdistuksenLisatieto % nro)))
        :kentta-params {:tyyppi :string
                        :vayla-tyyli? true
                        :disabled? true
                        :muokattu? true
                        :virhe? (nayta-kohdistuksen-virhe? lomake nro :lisatyon-lisatieto)}}]]]))

(defn- erillisrahoitettu-muutostyo-kohdistus [e! lomake urakan-muutostyot kohdistus toimenpiteet nro]
  (let [lisatyon-lisatieto (:lisatyon-lisatieto kohdistus)]
    [:<>
     [:div.row
      [:div.col-xs-12.col-md-3 {:style {:width "350px"}}
       [:div.label-ja-alasveto {:style {:width "320px"}}
        [:span.alasvedon-otsikko "Muutostyö*"]
        [yleiset/livi-pudotusvalikko {:valinta (:valittu-muutostyo kohdistus)
                                      ;; Jos muutostöitä ei urakalle ole ollenkaan tehty, näytä "Ei muutostöitä."
                                      :format-fn #(if (seq urakan-muutostyot) (:nimi %) "Ei muutostöitä.")
                                      :vayla-tyyli? true
                                      :muokattu? true
                                      :virhe? (nayta-kohdistuksen-virhe? lomake nro :valittu-muutostyo)
                                      :valitse-fn #(do
                                                     ;; Erillisrahoitetut muutostyöt ovat tavoitehintaisia 
                                                     (e! (tiedot/->TavoitehintaanKuuluminen :true nro))
                                                     (e! (tiedot/->ValitseMuutostyoKohdistukselle % nro)))}
         (vec
           ;; Halutaan näyttää pelkästään erillisrahoitetut
           (filter #(= "erillisrahoitus" (:alityyppi %)) urakan-muutostyot))]]

       [:div.label-ja-alasveto {:style {:width "320px"}}
        [:span.alasvedon-otsikko "Toimenpide*"]
        [yleiset/livi-pudotusvalikko {:valinta (:toimenpide kohdistus)
                                      :vayla-tyyli? true
                                      :format-fn :toimenpide
                                      :muokattu? true
                                      :virhe? (nayta-kohdistuksen-virhe? lomake nro :toimenpide)
                                      :valitse-fn #(e! (tiedot/->ValitseToimenpideKohdistukselle % nro))}
         toimenpiteet]]]]

     [:div.row
      [:div.col-xs-12.lomakeryhman-rivi-tausta {:style {:width "350px"}}
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Lisätieto *"
         :vayla-tyyli? true
         :otsikon-luokka ""
         :arvo-atom (r/wrap lisatyon-lisatieto
                      #(e! (tiedot/->KohdistuksenLisatieto % nro)))
         :kentta-params {:tyyppi :text
                         :palstoja 2
                         :koko [90 4]
                         :pituus-max 1000
                         :uusi-rivi? true
                         :virhe? (nayta-kohdistuksen-virhe? lomake nro :lisatyon-lisatieto)}}]]]]))

(defn- nayta-kohdistus [e! lomake nro kohdistus tehtavaryhmat rahavaraukset toimenpiteet urakoitsija-maksaa? urakan-muutostyot]
  (let [kohdistustyyppi (:tyyppi kohdistus)
        ;; Varmistetaan, että tehtäväryhmissä ei ole vääriä juttuja tälle kohdistukselle
        tehtavaryhmat (tiedot/kasittele-tehtavaryhmat tehtavaryhmat (:tehtavaryhma kohdistus))
        ;; Kohdistustyypit vaihtelee sen mukaan, onko hoitovuoden päätöstä valittu. Jos on, niin kulun tyyppiä ei voi vaihtaa
        kaikki-kohdistustyypit (if (istunto/ominaisuus-kaytossa? :mhu-muutokset)
                                 [:hankintakulu :rahavaraus :lisatyo :muukulu :erillisrahoitettu-muutos]
                                 [:hankintakulu :rahavaraus :lisatyo :muukulu])
        kohdistustyyppit (if (:vuoden-paatos-valittu? lomake)
                           [:paatos]
                           kaikki-kohdistustyypit)
        voiko-muokata? (cond
                         ;; Jos kohdistus on hoitovuoden päätös, sitä ei voi muokata
                         (= :paatos kohdistustyyppi) false
                         :else true)]
    [:div {:style {:background-color "#F5F5F5"
                   :padding-bottom "20px"
                   :margin-bottom "10px"}}
     ;; Otsikko ja poista nappi
     [:div.row
      [:div.col-xs-6.col-sm-6.col-md-6 [:h3 (str "Kohdistus " (inc nro))]]
      [:div.col-xs-6.col-sm-6.col-md-6 {:style {:float "right"}}
       (when voiko-muokata?
         [napit/poista "Poista kohdistus"
          #(e! (tiedot/->PoistaKohdistus nro))
          {:vayla-tyyli? true
           :teksti-nappi? true
           :luokka "pieni pull-right"}])]]

     [:div.row
      [:div.col-xs-12.col-md-6
       ;; Kulun tyyppi
       [:div.label-ja-alasveto {:style {:width "320px"}}
        [:span.alasvedon-otsikko "Kulun tyyppi*"]
        [yleiset/livi-pudotusvalikko {:vayla-tyyli? true
                                      :valinta kohdistustyyppi
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
       :paatos [hoitovuodenpaatos-kohdistus e! lomake kohdistus nro]
       :erillisrahoitettu-muutos [erillisrahoitettu-muutostyo-kohdistus e! lomake urakan-muutostyot kohdistus toimenpiteet nro]
       :jjh-muutos nil

       ;; Default
       (do
         ;; Ei blokkaa mitään, mutta halutaan tästä jokin punainen valo heittää
         (js/console.error (str
                             "Kohdistustyyppiä " kohdistustyyppi " "
                             "ei ole käsitelty kululomakkeella."))
         nil))

     ;; Kohdistuksen summa
     [:div.row
      [:div.col-xs-12.col-md-2 {:style {:width "142px"}}
       [:div
        [kentat/tee-otsikollinen-kentta
         {:otsikko "Määrä € *"
          :otsikon-tag "span"
          :arvo-atom (r/wrap (:summa kohdistus) #(e! (tiedot/->KohdistuksenSumma % nro)))
          :kentta-params {:elementin-id (str "kohdistuksen-summa-" nro)
                          :disabled? (or (not voiko-muokata?) (:lukittu? kohdistus))
                          :tyyppi :euro
                          :tyylit {:width "110px" :height "34px"}
                          :vaadi-negatiivinen? urakoitsija-maksaa?
                          :vaadi-positiivinen-numero? (not urakoitsija-maksaa?)
                          ;; TODO: Kehitä validointi tähän :virhe? (not (validi-ei-tarkistettu-tai-ei-koskettu? summa-meta))
                          :input-luokka "maara-input"
                          :vayla-tyyli? true}}]]]]]))

(defn testausvalinnat [e! app]
  (when (k/kehitysymparistossa?)
    [:<>
     [:h3 "Testausta varten"]
     [:span.nykyhetki.label-ja-kentta
      [:span.kentan-otsikko "Aseta nykyhetki"]
      [:div.kentta
       [kentat/tee-kentta
        {:tyyppi :pvm}
        (r/wrap
          (:nykyhetki app)
          #(e! (tiedot/->AsetaNykyhetki %)))]]]
     ;; [debug/debug app {:otsikko "KULUJEN TUCK STATE"}]
     ]))

(defn kululomake [e! app]
  (let [syottomoodi (:syottomoodi app)
        tehtavaryhmat (:tehtavaryhmat app)
        rahavaraukset (:rahavaraukset app)
        toimenpiteet (:toimenpiteet app)
        lomake (:lomake app)
        erapaiva (:erapaiva lomake)
        paivia-kulunut-erapaivasta (pvm/paivia-valissa erapaiva (pvm/nyt))
        erapaivasta-yli-2kk? (if (> paivia-kulunut-erapaivasta 59) true false)
        kohdistukset (:kohdistukset lomake)
        koontilaskun-kuukausi (:koontilaskun-kuukausi lomake)
        tehtavaryhma (:tehtavaryhma lomake)
        paatos-tehty? (:paatos-tehty? lomake)
        urakan-muutostyot (:urakan-muutostyot app)
        ;; Jos kulun eräpäivä osuu vuodelle, josta on välikatselmus pidetty, kulu lukitaan
        erapaivan-hoitovuosi (when erapaiva
                               (pvm/vuosi (first (pvm/paivamaaran-hoitokausi erapaiva))))
        haku-menossa (boolean (get-in app [:parametrit :haku-menossa]))
        tehtava-haku-menossa? (boolean (boolean (some :tehtava-haku-menossa kohdistukset)))
        kulu-lukittu? (or haku-menossa
                        (when erapaivan-hoitovuosi
                          (some #(and
                                   (= erapaivan-hoitovuosi (:vuosi %))
                                   (:paatos-tehty? %))
                            (:vuosittaiset-valikatselmukset app)))
                        erapaivasta-yli-2kk?)
        ;; Vuoden päätöksen kulut voivatkin olla urakoitsijan maksettavia!
        urakoitsija-maksaa? (and (:vuoden-paatos-valittu? lomake)
                              (=
                                (:id (tiedot/avain->tehtavaryhma tehtavaryhmat :tavoitehinnan-ylitys))
                                (:tehtavaryhma (first (:kohdistukset lomake)))))

        koontilaskun-kuukaudet (tiedot/palauta-urakan-mahdolliset-koontilaskun-kuukaudet app (-> @tila/tila :yleiset :urakka))

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

        kk-droppari-disabled (or laskun-nro-virhe?
                               laskun-nro-lukittu?)
        ;; Validoidaan koko lomake
        lomake-validi? (tiedot/validoi-lomake lomake)
        summa-yht (reduce + (map :summa kohdistukset))
        lopullinen-summa (if (neg? summa-yht)
                           (str "-" (fmt/euro (* -1 summa-yht)))
                           (fmt/euro (or summa-yht 0)))]
    [:div.kululomake
     [:div.row
      ;; Otsikko
      [:div.col-xs-12.col-md-6
       [napit/takaisin "Takaisin"
        #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
        {:vayla-tyyli? true :teksti-nappi? true :style {:font-size "14px" :padding-right "16px"}}]
       [:h2 (str (if-not (nil? (:id lomake)) "Muokkaa kulua" "Uusi kulu"))]]

      (when (and (pvm/onko-hoitovuosi-loppunut?) (not paatos-tehty?))
        [yleiset/info-laatikko :vahva-ilmoitus
         [:<>
          [:div
           (str (gstring/unescapeEntities "&ensp;&#x2022;&ensp;")
             "Hoitovuoden " (pvm/kuluva-hoitovuosi)
             " kulujen kirjauksen määräpäivä on " (pvm/kulujen-kirjauksen-maarapaiva))]
          [:div.info-laatikko-kohta-vali
           (str (gstring/unescapeEntities "&ensp;&#x2022;&ensp;")
             "Olethan syöttänyt kuluihin liittyvät toteumat?")
           [:div.info-laatikko-sisennetty-kohta
            (gstring/unescapeEntities "&ensp;&#x2022;&ensp;")
            [:span.info-laatikko-teksti-normaali
             "Urakoisijan velvollisuuksiin kuuluu ilmoittaa toimenpiteiden toteumat. Voit syöttää tiedot "]
            [yleiset/linkki "Toteumat" #(siirtymat/avaa-toteumat)]
            [:span.info-laatikko-teksti-normaali
             " sivulla."]]]]
         nil
         "100%"
         {:luokka "ala-margin-16 max-width-full max-width-full"}])]

     ;; Onko kulu lukittu
     (when (and kulu-lukittu? (not haku-menossa)) [:div.palstat [:div.palsta.punainen-teksti kulu-lukittu-teksti]])

     #_[debug/debug lomake]
     (map-indexed
       (fn [index kohdistus]
         ^{:key (str "kohdistus-" index)}
         [nayta-kohdistus e! lomake index kohdistus tehtavaryhmat rahavaraukset toimenpiteet urakoitsija-maksaa? urakan-muutostyot])
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
      [:div.col-xs-12.col-md-4 [:h5 "Laskun tiedot"]]
      [:div.col-xs-12.col-md-6 [:h5 "Lisätiedot"]]]

     [:div.row
      [:div.col-xs-12.col-md-4
       [:div.label-ja-alasveto
        [:span.alasvedon-otsikko "Koontilaskun kuukausi*"]
        [yleiset/livi-pudotusvalikko {:data-cy "koontilaskun-kk-dropdown"
                                      :disabled kk-droppari-disabled
                                      :tyylit {:margin-left "0px"}
                                      :vayla-tyyli? true
                                      :skrollattava? true
                                      :valinta koontilaskun-kuukausi
                                      :format-fn tiedot/koontilaskun-kk-formatter
                                      :itemit-komponentteja? true}
         ;; Ongelma: livi-pudotusvalikko
         ;; -> komp/klikattu-ulkopuolelle
         ; Kutsutaan randomisti 3-5 kertaa klikillä
         ; Jos tätä kutsutaan parillinen määrä, alasveto jää auki
         (mapv (fn [kk]
                 [:div.koontilaskun-kuukaudet-komp {:on-click #(e! (tiedot/->KoontilaskunKuukausi kk))}
                  [:div (tiedot/koontilaskun-kk-formatter kk)]
                  [:div (when (tiedot/paatos-tehty-rivin-vuodelle? app kk) [ikonit/locked-svg])]])
           koontilaskun-kuukaudet)]

        (when (and kulu-lukittu? paatos-tehty?)
          [:div.vihje-paatoksia-tehty
           [yleiset/keltainen-vihjelaatikko
            [:div
             [:strong "Urakalle on tehty päätöksiä välikatselmuksessa ko. hoitovuodelle. "]
             "Ota yhteyttä urakanvalvojaan, jos haluat kirjata kuluja ko. hoitovuodelle."] :info]])]]
      [:div.col-xs-12.col-md-6.lisatiedot
       [kentat/tee-otsikollinen-kentta
        {:otsikko "Lisätieto"
         :arvo-atom (r/wrap (:lisatieto lomake)
                      #(e! (tiedot/->KulunLisatieto %)))
         :kentta-params {:tyyppi :text
                         :koko [50 2]
                         :vayla-tyyli? true
                         :aputeksti "Kirjoita tähän halutessasi lisätietoa"
                         :disabled? (or haku-menossa kulu-lukittu?)}}]]]

     [:div.row
      [:div.col-xs-12.col-md-2
       [:div.label-ja-alasveto {:style {:max-width "250px"}}
        [:label "Laskun pvm*"]
        [pvm-valinta/pvm-valintakalenteri-inputilla
         {:valitse #(e! (tiedot/->ValitseErapaiva %))
          :paivamaara (or
                        erapaiva
                        (kulut/koontilaskun-kuukausi->pvm
                          koontilaskun-kuukausi
                          (-> @tila/yleiset :urakka :alkupvm)
                          (-> @tila/yleiset :urakka :loppupvm)))
          :pakota-suunta false
          :disabled (or (nil? koontilaskun-kuukausi)
                      kulu-lukittu?)
          :luokat #{"input-default"}
          :placeholder (when (nil? koontilaskun-kuukausi) "Valitse koontilaskun kuukausi ensin")
          :valittava?-fn (kulut/koontilaskun-kuukauden-sisalla?-fn
                           koontilaskun-kuukausi
                           (-> @tila/yleiset :urakka :alkupvm)
                           (-> @tila/yleiset :urakka :loppupvm))}]]]]

     [:div.row
      [:div.col-xs-12.col-md-2 {:style {:max-width "280px"}}
       [kentat/tee-otsikollinen-kentta
        {:kentta-params {:tyyppi :string
                         :vayla-tyyli? true
                         :on-blur #(e! (tiedot/->OnkoLaskunNumeroKaytossa (.. % -target -value)))
                         :disabled? kulu-lukittu?
                         :elementin-id "koontilaskun-numero-input"}
         :otsikko "Koontilaskun numero"
         :arvo-atom (r/wrap
                      (:laskun-numero lomake)
                      #(e! (tiedot/->KoontilaskunNumero %)))}]]]
     [:div.row
      [:div.col-xs-12.col-md-6 {:style {:padding-left "0px"}}
       (when (or laskun-nro-lukittu? laskun-nro-virhe?)
         [:label (str "Annetulla numerolla on jo olemassa kirjaus, jonka päivämäärä on "
                   (-> tarkistukset
                     :numerolla-tarkistettu-pvm
                     :erapaiva
                     pvm/pvm)
                   ". Yhdellä laskun numerolla voi olla yksi päivämäärä, joten kulu kirjataan samalle päivämäärälle. Jos haluat kirjata laskun eri päivämäärälle, vaihda laskun numero.")])]]

     [:div.row
      [:div.col-xs-12.col-md-4 {:style {:padding-left "20px"}} [:h5 "Määrä € *"]
       [:h2 lopullinen-summa]]
      [:div.col-xs-12.col-md-6
       [liitteet e! kulu-lukittu? lomake]]]

     [:div.row.kulu-valistys-top
      [:div.col-xs-12.col-md-6
       [:div.kulu-napit

        [:span.kulu-valistys-oikea
         [napit/tallenna
          "Tallenna"
          #(e! (tiedot/->TallennaKulu))
          {:disabled (or (not lomake-validi?) kulu-lukittu? haku-menossa tehtava-haku-menossa?)}]]

        [:span
         [napit/peruuta
          "Peruuta"
          #(e! (tiedot/->KulujenSyotto (not syottomoodi)))
          {:disabled haku-menossa}]]

        (when (and (not haku-menossa) (not tehtava-haku-menossa?) (not (nil? (:id lomake))))
          [:span
           [napit/poista "Poista kulu"
            #(modal/nayta! {:otsikko "Haluatko varmasti poistaa kulun?" :otsikon-alle-komp (fn [_] [:hr])}
               [kulun-poistovarmistus-modaali {:varmistus-fn (fn []
                                                               (modal/piilota!)
                                                               (e! (tiedot/->PoistaKulu (:id lomake))))
                                               :kohdistukset kohdistukset
                                               :koontilaskun-kuukausi koontilaskun-kuukausi
                                               :tehtavaryhma tehtavaryhma
                                               :laskun-pvm (pvm/pvm erapaiva)
                                               :tehtavaryhmat tehtavaryhmat}])
            {:style {:margin-left "1rem"}}]])

        (when haku-menossa
          [:span.kulu-ladataan
           [yleiset/ajax-loader-pieni "Ladataan..."]])]]]

     [:div.row
      [:div.col-xs-12.col-md-6
       (when urakoitsija-maksaa? [:div.caption.margin-top-4 "Kulu kirjataan miinusmerkkisenä"])]]]))
