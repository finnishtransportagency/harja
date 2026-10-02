(ns harja.views.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden näkymä"
  (:require
    [tuck.core :as tuck]
    [cljs-time.core :as t]

    [harja.pvm :as pvm]
    [harja.ui.grid :as grid]
    [harja.ui.napit :as napit]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.yleiset :as yleiset]
    [harja.ui.komponentti :as komp]
    [harja.tiedot.urakka :as urakka]
    [harja.ui.valinnat :as valinnat]
    [harja.domain.urakka :as u-domain]
    [harja.domain.oikeudet :as oikeudet]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.domain.valitavoite :as vt-domain]
    [harja.tiedot.urakka.valitavoitteet :as tiedot]
    [harja.domain.yllapitokohde :as yllapitokohde-domain]
    [harja.tiedot.raportit :as raportit]
    [harja.ui.upotettu-raportti :as upotettu-raportti]
    [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]
    [harja.tiedot.hallinta.valtakunnalliset-valitavoitteet :as vvt-tiedot]))

(defn- suodata-valitavoitteet-hoitokaudella
  "Suodattaa välitavoitteet valitun hoitokauden perusteella.
   Hoitokausi on vektori [alku loppu]."
  [valitavoitteet valittu-hoitokausi]
  (if (nil? valittu-hoitokausi)
    valitavoitteet
    (let [[hoitokausi-alku hoitokausi-loppu] valittu-hoitokausi]
      (filterv #(when-let [takaraja (:takaraja %)]
                  (pvm/valissa? takaraja hoitokausi-alku hoitokausi-loppu))
        valitavoitteet))))

(defn onko-tulevia?
  [urakan-valitavoitteet [hoitokausi-alku hoitokausi-loppu]]
  (boolean
    (some (fn [{:keys [takaraja]}]
            (and takaraja (pos? (compare takaraja hoitokausi-loppu))))
      urakan-valitavoitteet)))

(defn sarake-yllapitokohde [urakka yllapitokohteet]
  {:otsikko (case (:tyyppi urakka)
              :paallystys "Pääl\u00ADlystys\u00ADkohde"
              :paikkaus "Paik\u00ADkaus\u00ADkohde"
              :tiemerkinta "Tie\u00ADmerkintä\u00ADkohde")
   :leveys 20
   :nimi :yllapitokohde-id
   :fmt (fn [kohde-id]
          (if kohde-id
            (let [valittu-kohde (first (filter #(= (:id %) kohde-id) yllapitokohteet))]
              (yllapitokohde-domain/yllapitokohde-tekstina valittu-kohde))
            "Ei kohdetta"))
   :tyyppi :valinta
   :valinnat (concat [nil] (map :id yllapitokohteet))
   :valinta-nayta (fn [kohde-id _]
                    (if kohde-id
                      (let [valittu-kohde (first (filter #(= (:id %) kohde-id) yllapitokohteet))]
                        (yllapitokohde-domain/yllapitokohde-tekstina
                          valittu-kohde
                          {:osoite {:tr-numero (:tr-numero valittu-kohde)
                                    :tr-alkuosa (:tr-alkuosa valittu-kohde)
                                    :tr-alkuetaisyys (:tr-alkuetaisyys valittu-kohde)
                                    :tr-loppuosa (:tr-loppuosa valittu-kohde)
                                    :tr-loppuetaisyys (:tr-loppuetaisyys valittu-kohde)}}))
                      "Ei kohdetta"))})

(defn urakan-omat-valitavoitteet-grid
  [e! {:keys [urakka urakan-valitavoitteet valittu-hoitokausi yllapitokohteet ladataan?] :as app}]
  (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
        voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))
        vesivaylaurakka? (u-domain/vesivaylaurakka? urakka)
        ladataan-kohteita? (and @urakka/yllapitokohdeurakka? (nil? yllapitokohteet))]
    (if ladataan-kohteita?
      [yleiset/ajax-loader "Ladataan..."]
      [grid/grid
       {:otsikko "Urakkakohtaiset määräaikaan mennessä tehtävät työt"
        :tyhja (if (and
                     ladataan?
                     (nil? urakan-valitavoitteet))
                 [yleiset/ajax-loader "Tavoitteita haetaan..."]
                 "Ei urakkakohtaisia määräajassa tehtäviä töitä.")
        :tallenna (if voi-muokata?
                    #(tuck-apurit/e-kanavalla! e! tiedot/->TallennaValitavoitteet %)
                    :ei-mahdollinen)
        :tallennus-ei-mahdollinen-tooltip
        (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-valitavoitteet)}

       [{:otsikko "Nimi" :leveys 25 :nimi :nimi :tyyppi :string :pituus-max 256}
        (when vesivaylaurakka?
          {:otsikko "Aloituspäivä" :leveys 20 :tyyppi :pvm
           :nimi :aloituspvm
           :fmt #(if %
                   (pvm/pvm-opt %)
                   "-")})

        (when @urakka/yllapitokohdeurakka?
          (sarake-yllapitokohde urakka yllapitokohteet))
        {:otsikko "Taka\u00ADraja" :leveys 20 :nimi :takaraja
         :fmt #(if %
                 (pvm/pvm-opt %)
                 "Ei takarajaa")
         :validoi [[:urakan-aikana]]
         :tyyppi :pvm}
        {:otsikko "Tila" :leveys 20 :tyyppi :string :muokattava? (constantly false)
         :nimi :valmiustila :hae identity :fmt vt-domain/valmiustilan-kuvaus}
        {:otsikko "Valmis\u00ADtumis\u00ADpäivä" :leveys 20 :tyyppi :pvm
         :muokattava? (constantly voi-merkita-valmiiksi?)
         :nimi :valmispvm
         :fmt #(if %
                 (pvm/pvm-opt %)
                 "-")}
        {:otsikko "Kom\u00ADmentti val\u00ADmis\u00ADtu\u00ADmi\u00ADses\u00ADta"
         :leveys 35 :tyyppi :string :muokattava? #(and voi-merkita-valmiiksi?
                                                    (:valmispvm %))
         :nimi :valmis-kommentti}
        {:otsikko "Valmiiksi\u00ADmerkitsijä" :leveys 20 :tyyppi :string :muokattava? (constantly false)
         :nimi :merkitsija :hae (fn [rivi]
                                  (str (:valmis-merkitsija-etunimi rivi) " " (:valmis-merkitsija-sukunimi rivi)))}]
       (suodata-valitavoitteet-hoitokaudella urakan-valitavoitteet valittu-hoitokausi)])))

(defn urakan-omat-ja-valtakunnalliset-valitavoitteet-grid
  "Tässä gridissä näytetään sekä urakan omat että valtakunnallisten välitavoitteiden pohjalta urakkaan liitetyt
   välitavoitteet"
  [e! {:keys [urakka valittu-hoitokausi yllapitokohteet urakan-valitavoitteet valtakunnalliset-valitavoitteet ladataan?] :as app}]
  (let [yhdistetyt (into urakan-valitavoitteet valtakunnalliset-valitavoitteet)
        muokattava-fn (fn [rivi]
                        (boolean
                          (not
                            (some? (:valtakunnallinen-id rivi)))))
        voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
        voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))]

    [grid/grid
     {:otsikko "Urakkakohtaiset määräaikaan mennessä tehtävät työt"
      :voi-poistaa? muokattava-fn
      :tyhja (if (and
                   ladataan?
                   (nil? yhdistetyt))
               [yleiset/ajax-loader "Tavoitteita haetaan..."]
               "Ei urakkakohtaisia määräajassa tehtäviä töitä.")
      :tallenna (if voi-muokata?
                  #(tuck-apurit/e-kanavalla! e! tiedot/->TallennaValitavoitteet %)
                  :ei-mahdollinen)
      :tallennus-ei-mahdollinen-tooltip
      (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-valitavoitteet)}

     [{:otsikko "Nimi"
       :muokattava? muokattava-fn
       :leveys 25 :nimi :nimi :tyyppi :string :pituus-max 128}
      (when @urakka/yllapitokohdeurakka?
        (merge
          {:muokattava? muokattava-fn}
          (sarake-yllapitokohde urakka yllapitokohteet)))
      {:otsikko "Taka\u00ADraja"
       :muokattava? muokattava-fn
       :leveys 20 :nimi :takaraja :fmt #(if %
                                          (pvm/pvm-opt %)
                                          "Ei takarajaa")
       :validoi [[:urakan-aikana]]
       :tyyppi :pvm}
      {:otsikko "Tila"
       :muokattava? (constantly false)
       :leveys 20 :tyyppi :string
       :nimi :valmiustila :hae identity :fmt vt-domain/valmiustilan-kuvaus}
      {:otsikko "Valmis\u00ADtumis\u00ADpäivä"
       :muokattava? (constantly voi-merkita-valmiiksi?)
       :leveys 20 :tyyppi :pvm
       :nimi :valmispvm
       :fmt #(if %
               (pvm/pvm-opt %)
               "-")}
      {:otsikko "Kom\u00ADmentti val\u00ADmis\u00ADtu\u00ADmi\u00ADses\u00ADta"
       :leveys 35 :tyyppi :string
       :muokattava? #(and
                       voi-merkita-valmiiksi?
                       (:valmispvm %))
       :nimi :valmis-kommentti}
      {:otsikko "Merkit\u00ADsijä"
       :leveys 20 :tyyppi :string :muokattava? (constantly false)
       :nimi :merkitsija :hae (fn [rivi]
                                (str (:valmis-merkitsija-etunimi rivi) " " (:valmis-merkitsija-sukunimi rivi)))}]
     (suodata-valitavoitteet-hoitokaudella yhdistetyt valittu-hoitokausi)]))

(defn takaraja-poikkeaa-valtakunnallisesta? [{:keys [takaraja valtakunnallinen-takaraja
                                                     valtakunnallinen-takarajan-toistopaiva
                                                     valtakunnallinen-takarajan-toistokuukausi]}]
  (boolean
    (or
      ;; Kertaluontoinen takaraja poikkeaa
      (and valtakunnallinen-takaraja
        (not= takaraja valtakunnallinen-takaraja))
      ;; Toistuva takaraja poikkeaa
      (and valtakunnallinen-takarajan-toistopaiva
        valtakunnallinen-takarajan-toistokuukausi
        (or (nil? takaraja)
          (not= valtakunnallinen-takarajan-toistopaiva
            (t/day takaraja))
          (not= valtakunnallinen-takarajan-toistokuukausi
            (t/month takaraja)))))))

(defn ainakin-yksi-tavoite-muutettu-urakkaan? [rivit]
  (boolean (some #(or (takaraja-poikkeaa-valtakunnallisesta? %)
                    ;; Välitavoitteen nimi poikkeaa
                    (not= (:valtakunnallinen-nimi %) (:nimi %)))
             rivit)))

(defn valtakunnalliset-valitavoitteet-grid
  [e! {:keys [urakka valtakunnalliset-valitavoitteet valittu-hoitokausi] :as app}]
  (let [voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))
        voi-tehda-tarkennuksen? voi-merkita-valmiiksi? ; Toistaiseksi oletetaan nämä oikeudet samaksi
        ;; Mitään taulukon kenttää ei voi muokata ilman oikeutta merkitä valmiiksi tai tehdä tarkennuksia
        voi-muokata? (and (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
                       (or voi-merkita-valmiiksi?
                         voi-tehda-tarkennuksen?))]
    [:div
     [grid/grid
      {:otsikko "Kaikissa urakoissa määräaikaan mennessä tehtävät työt"
       :tyhja (if (nil? valtakunnalliset-valitavoitteet)
                [yleiset/ajax-loader "Tavoitteita haetaan..."]
                "Ei valtakunnallisia määräajassa tehtäviä töitä")
       :tallenna (if voi-muokata?
                   #(tuck-apurit/e-kanavalla! e! tiedot/->TallennaValitavoitteet %)
                   :ei-mahdollinen)
       :tallennus-ei-mahdollinen-tooltip
       (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-valitavoitteet)

       :voi-lisata? false
       :voi-poistaa? (constantly false)}

      [{:otsikko "Työn ku\u00ADva\u00ADus"
        :leveys 25
        :nimi :valtakunnallinen-nimi :tyyppi :string :pituus-max 128
        :muokattava? (constantly false) :hae #(str (:valtakunnallinen-nimi %))}
       {:otsikko "U\u00ADrak\u00ADka\u00ADkoh\u00ADtai\u00ADset tar\u00ADken\u00ADnuk\u00ADset"
        :leveys 25 :nimi :nimi :tyyppi :string :pituus-max 128
        :solun-luokka
        (fn [_ rivi]
          (when-not (= (:valtakunnallinen-nimi rivi) (:nimi rivi))
            "grid-solu-varoitus"))
        :muokattava? (constantly voi-tehda-tarkennuksen?)}
       {:otsikko "Valta\u00ADkunnal\u00ADlinen taka\u00ADraja"
        :leveys 20
        :nimi :valtakunnallinen-takaraja
        :hae #(cond
                (:valtakunnallinen-takaraja %)
                (pvm/pvm-opt (:valtakunnallinen-takaraja %))

                (and (:valtakunnallinen-takarajan-toistopaiva %)
                  (:valtakunnallinen-takarajan-toistokuukausi %))
                (str "Vuosittain "
                  (:valtakunnallinen-takarajan-toistopaiva %)
                  "."
                  (:valtakunnallinen-takarajan-toistokuukausi %))

                :else
                "Ei takarajaa")
        :tyyppi :pvm
        :muokattava? (constantly false)}
       {:otsikko "Taka\u00ADraja ura\u00ADkassa"
        :leveys 20
        :nimi :takaraja
        :fmt pvm/pvm-opt
        :solun-luokka
        (fn [_ rivi]
          (let [poikkeava "grid-solu-varoitus"]
            (when (takaraja-poikkeaa-valtakunnallisesta? rivi)
              poikkeava)))
        :tyyppi :pvm
        :muokattava? (constantly voi-tehda-tarkennuksen?)}
       {:otsikko "Tila" :leveys 20 :tyyppi :string :muokattava? (constantly false)
        :nimi :valmiustila :hae identity :fmt vt-domain/valmiustilan-kuvaus}
       {:otsikko "Valmistumispäivä" :leveys 20 :tyyppi :pvm
        :muokattava? (constantly voi-merkita-valmiiksi?)
        :nimi :valmispvm
        :fmt #(if %
                (pvm/pvm-opt %)
                "-")}
       {:otsikko "Kom\u00ADmentti val\u00ADmis\u00ADtu\u00ADmi\u00ADses\u00ADta"
        :leveys 35 :tyyppi :string :muokattava? #(and voi-merkita-valmiiksi?
                                                   (:valmispvm %))
        :nimi :valmis-kommentti}
       {:otsikko "Merkitsijä" :leveys 20 :tyyppi :string :muokattava? (constantly false)
        :nimi :merkitsija :hae (fn [rivi]
                                 (str (:valmis-merkitsija-etunimi rivi) " " (:valmis-merkitsija-sukunimi rivi)))}]
      (suodata-valitavoitteet-hoitokaudella valtakunnalliset-valitavoitteet valittu-hoitokausi)]

     (when (ainakin-yksi-tavoite-muutettu-urakkaan? valtakunnalliset-valitavoitteet)
       [yleiset/vihje-elementti [:span
                                 [:span "Urakkakohtaisten tarkennukset värjätty "]
                                 [:span.grid-solu-varoitus "punaisella"]
                                 [:span "."]]])]))

(defn- valitavoitteet*
  "Sisäinen komponentti joka saa app-tilan Tuck:lta"
  [e! app ur]
  (komp/luo
    (komp/sisaan #(do
                    (when (urakka/koko-urakkakausi-valittuna?) (urakka/valitse-kuluva-hk!))
                    (e! (tiedot/->NakymaAvattu))))
    (komp/ulos #(e! (tiedot/->NakymaSuljettu)))
    (fn [e! {:keys [valittu-hoitokausi urakan-hoitokaudet ladataan?
                    urakan-valitavoitteet valtakunnalliset-valitavoitteet] :as app} ur]
      (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id ur))
            nayta-yhdistetty-grid? (and (boolean (#{:tiemerkinta} (:tyyppi ur)))
                                     (vvt-tiedot/valtakunnalliset-valitavoitteet-kaytossa? (:tyyppi ur)))
            nayta-valtakunnalliset-grid? (and (not nayta-yhdistetty-grid?)
                                           (vvt-tiedot/valtakunnalliset-valitavoitteet-kaytossa? (:tyyppi ur)))
            nayta-urakkakohtaiset-grid? (not nayta-yhdistetty-grid?)
            ;; Lisää urakka app-tilaan grid-funktioita varten
            app (assoc app :urakka ur)
            tulevaisuudessa-arvoja? (onko-tulevia? urakan-valitavoitteet valittu-hoitokausi)
            haun-alkupvm (or (first valittu-hoitokausi) (ffirst urakan-hoitokaudet))
            haun-loppupvm (or (second valittu-hoitokausi) (last (last urakan-hoitokaudet)))
            parametrit (raportit/urakkaraportin-parametrit
                         (:id ur)
                         :valitavoiteraportti
                         {:alkupvm haun-alkupvm
                          :loppupvm haun-loppupvm})]

        (if ladataan?
          [:div.valitavoitteet
           [:h1 "Välitavoitteet"]
           [yleiset/ajax-loader "Ladataan välitavoitteita..."]]

          [:div.valitavoitteet
           [:div.flex-row
            [:h1 "Välitavoitteet"]
            [:div
             [upotettu-raportti/raportin-vientimuodot
              (assoc parametrit
                :otsikko "Tallenna Excel"
                :kasittelija :excel)
              (assoc parametrit
                :otsikko "Tallenna PDF"
                :kasittelija :pdf)]]]

           [:div.valinnat.margin-bottom-16
            [valinnat/urakan-hoitokausi-tuck
             valittu-hoitokausi
             urakan-hoitokaudet
             #(e! (tiedot/->HoitokausiVaihdettu %))
             {:wrapper-luokka "label-ja-alasveto hoitokausi"
              :kaikki-valinta? true}]

            (when (and
                    ;; "Kaikki" ei ole valittuna
                    valittu-hoitokausi
                    ;; Älä näytä yhdistetylle
                    nayta-urakkakohtaiset-grid?)
              [napit/yleinen-toissijainen "Kopioi urakkakohtaiset välitavoitteet tuleville hoitovuosille"
               (fn []
                 (if tulevaisuudessa-arvoja?
                   (varmista-kayttajalta/varmista-kayttajalta
                     {:otsikko "Tulevilla hoitovuosilla on jo tietoja"
                      :sisalto (str "Tulevilla hoitovuosilla on jo tietoja. Ylikirjoitetaanko tiedot? Ylikirjoitetut tiedot menetetään pysyvästi.")
                      :hyvaksy "Ylikirjoita"
                      :toiminto-fn #(e! (tiedot/->KopioiValitavoitteet))})
                   (e! (tiedot/->KopioiValitavoitteet))))
               {:disabled ladataan?
                :luokka "ikoni-16"
                :ikoni (ikonit/action-copy)}])]

           (when nayta-urakkakohtaiset-grid?
             [urakan-omat-valitavoitteet-grid e! app])

           (when nayta-valtakunnalliset-grid?
             [valtakunnalliset-valitavoitteet-grid e! app])

           (when nayta-yhdistetty-grid?
             [urakan-omat-ja-valtakunnalliset-valitavoitteet-grid e! app])

           (when nayta-valtakunnalliset-grid?
             [yleiset/vihje (str
                              "Järjestelmävastaava hallinnoi listaa valtakunnallisista, määräaikaan mennessä tehtävistä töistä."
                              " "
                              (when voi-muokata?
                                "Voit kuitenkin tehdä tavoitteisiin urakkakohtaisia muokkauksia."))])])))))

(defn valitavoitteet [ur]
  [tuck/tuck tiedot/valitavoitteet-app-tila
   (fn [e! app] [valitavoitteet* e! app ur])])
