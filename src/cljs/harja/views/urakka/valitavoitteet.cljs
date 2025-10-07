(ns harja.views.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden näkymä"
  (:require
   [cljs-time.core :as t]
   [harja.domain.oikeudet :as oikeudet]
   [harja.domain.urakka :as u-domain]
   [harja.domain.valitavoite :as vt-domain]
   [harja.domain.yllapitokohde :as yllapitokohde-domain]
   [harja.pvm :as pvm]
   [harja.tiedot.hallinta.valtakunnalliset-valitavoitteet :as vvt-tiedot]
   [harja.tiedot.urakka :as urakka]
   [harja.tiedot.urakka.valitavoitteet :as tiedot]
   [harja.tyokalut.tuck :as tuck-apurit]
   [harja.ui.debug :as debug]
   [harja.ui.grid :as grid]
   [harja.ui.komponentti :as komp]
   [harja.ui.valinnat :as valinnat]
   [harja.ui.yleiset :as yleiset]
   [tuck.core :as tuck]))



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
  [e! app {:keys [urakka urakan-valitavoitteet valittu-hoitokausi]}]
  (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
        voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))
        vesivaylaurakka? (u-domain/vesivaylaurakka? urakka)
        yllapitokohteet (:yllapitokohteet app)
        ladataan-kohteita? (and @urakka/yllapitokohdeurakka? (nil? yllapitokohteet))]
    (if ladataan-kohteita?
      [yleiset/ajax-loader "Ladataan..."]
      [grid/grid
       {:otsikko "Urakkakohtaiset määräaikaan mennessä tehtävät työt"
        :tyhja (if (nil? urakan-valitavoitteet)
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
  [e! app {:keys [urakka valittu-hoitokausi]}]
  (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
        voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))
        yllapitokohteet (:yllapitokohteet app)
        kaikki-valitavoitteet (:valitavoitteet app)]
    [grid/grid
     {:otsikko "Urakkakohtaiset määräaikaan mennessä tehtävät työt"
      :tyhja (if (nil? kaikki-valitavoitteet)
               [yleiset/ajax-loader "Tavoitteita haetaan..."]
               "Ei urakkakohtaisia määräajassa tehtäviä töitä.")
      :tallenna (if voi-muokata?
                  #(tuck-apurit/e-kanavalla! e! tiedot/->TallennaValitavoitteet %)
                  :ei-mahdollinen)
      :tallennus-ei-mahdollinen-tooltip
      (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-valitavoitteet)}

     [{:otsikko "Nimi" :leveys 25 :nimi :nimi :tyyppi :string :pituus-max 128}
      (when @urakka/yllapitokohdeurakka?
        (sarake-yllapitokohde urakka yllapitokohteet))
      {:otsikko "Taka\u00ADraja" :leveys 20 :nimi :takaraja :fmt #(if %
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
      {:otsikko "Merkit\u00ADsijä" :leveys 20 :tyyppi :string :muokattava? (constantly false)
       :nimi :merkitsija :hae (fn [rivi]
                                (str (:valmis-merkitsija-etunimi rivi) " " (:valmis-merkitsija-sukunimi rivi)))}]
     (suodata-valitavoitteet-hoitokaudella kaikki-valitavoitteet valittu-hoitokausi)]))

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
  [e! app {:keys [urakka valtakunnalliset-valitavoitteet valittu-hoitokausi]}]
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
            nayta-urakkakohtaiset-grid? (not nayta-yhdistetty-grid?)]
        
        (if ladataan?
          [:div.valitavoitteet
           [yleiset/ajax-loader "Ladataan välitavoitteita..."]]
          
          [:div.valitavoitteet
           [:div.flex-row.margin-bottom-16
            [valinnat/urakan-hoitokausi-tuck
             valittu-hoitokausi
             urakan-hoitokaudet
             #(e! (tiedot/->HoitokausiVaihdettu %))
             {:wrapper-luokka "label-ja-alasveto hoitokausi"
              :kaikki-valinta? true}]] 

           (when nayta-urakkakohtaiset-grid?
             [urakan-omat-valitavoitteet-grid
              e! app
              {:urakka ur
               :urakan-valitavoitteet urakan-valitavoitteet
               :valittu-hoitokausi valittu-hoitokausi}])

           (when nayta-valtakunnalliset-grid?
             [valtakunnalliset-valitavoitteet-grid
              e! app
              {:urakka ur
               :valtakunnalliset-valitavoitteet valtakunnalliset-valitavoitteet
               :valittu-hoitokausi valittu-hoitokausi}])
           
           (when nayta-yhdistetty-grid?
             [urakan-omat-ja-valtakunnalliset-valitavoitteet-grid
              e! app
              {:urakka ur
               :valittu-hoitokausi valittu-hoitokausi}])

           (when nayta-valtakunnalliset-grid?
             [yleiset/vihje (str
                              "Järjestelmävastaava hallinnoi listaa valtakunnallisista, määräaikaan mennessä tehtävistä töistä."
                              " "
                              (when voi-muokata?
                                "Voit kuitenkin tehdä tavoitteisiin urakkakohtaisia muokkauksia."))])
           [debug/debug app]])))))


(defn valitavoitteet [ur]
  [tuck/tuck tiedot/valitavoitteet-app-tila
   (fn [e! app] [valitavoitteet* e! app ur])])

