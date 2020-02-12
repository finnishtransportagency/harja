(ns harja.views.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.valitavoitteet :as tiedot]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [cljs.core.async :refer [<!]]
            [harja.domain.oikeudet :as oikeudet]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.hallinta.valtakunnalliset-valitavoitteet :as vvt-tiedot]
            [harja.tiedot.urakka :as urakka]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.domain.urakka :as u-domain]
            [harja.domain.valitavoite :as vt-domain]
            [harja.domain.yllapitokohde :as yllapitokohde-domain])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(defn- suodata-valitavoitteet-urakkavuodella [valitavoitteet valittu-urakan-vuosi]
  (filterv #(or
              (= valittu-urakan-vuosi :kaikki)
              (and (some? (:takaraja %))
                   (= (pvm/vuosi (:takaraja %)) valittu-urakan-vuosi)))
           valitavoitteet))

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
   :valinta-nayta (fn [kohde-id muokattava?]
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

(defn urakan-omat-valitavoitteet
  [{:keys [urakka kaikki-valitavoitteet-atom urakan-valitavoitteet valittu-urakan-vuosi]}]
  (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
        voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))
        vesivaylaurakka? (u-domain/vesivaylaurakka? urakka)
        yllapitokohteet @tiedot/urakan-yllapitokohteet-lomakkeelle]
    (if (and @urakka/yllapitokohdeurakka? (nil? @tiedot/urakan-yllapitokohteet-lomakkeelle))
      [y/ajax-loader "Ladataan..."]
      [grid/grid
       {:otsikko "Urakkakohtaiset määräaikaan mennessä tehtävät työt"
        :tyhja (if (nil? urakan-valitavoitteet)
                 [y/ajax-loader "Tavoitteita haetaan..."]
                 "Ei urakkakohtaisia määräajassa tehtäviä töitä.")
        :tallenna (if voi-muokata?
                    #(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet! (:id urakka) %))]
                           (if (k/virhe? vastaus)
                             (viesti/nayta! "Tallentaminen epäonnistui"
                                            :warning viesti/viestin-nayttoaika-lyhyt)
                             (reset! kaikki-valitavoitteet-atom vastaus))))
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
         :validoi [[:pvm-kentan-jalkeen :aloituspvm
                    "Takaraja ei voi olla ennen aloituspäivää."]]
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
       (suodata-valitavoitteet-urakkavuodella urakan-valitavoitteet valittu-urakan-vuosi)])))

(defn urakan-omat-ja-valtakunnalliset-valitavoitteet
  "Tässä gridissä näytetään sekä urakan omat että valtakunnallisten välitavoitteiden pohjalta urakkaan liitetyt
   välitavoitteet"
  [{:keys [urakka kaikki-valitavoitteet-atom valittu-urakan-vuosi]}]
  (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
        voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))
        yllapitokohteet @tiedot/urakan-yllapitokohteet-lomakkeelle]
    [grid/grid
     {:otsikko "Urakkakohtaiset määräaikaan mennessä tehtävät työt"
      :tyhja (if (nil? @kaikki-valitavoitteet-atom)
               [y/ajax-loader "Tavoitteita haetaan..."]
               "Ei urakkakohtaisia määräajassa tehtäviä töitä.")
      :tallenna (if voi-muokata?
                  #(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet! (:id urakka) %))]
                         (if (k/virhe? vastaus)
                           (viesti/nayta! "Tallentaminen epäonnistui"
                                          :warning viesti/viestin-nayttoaika-lyhyt)
                           (reset! kaikki-valitavoitteet-atom vastaus))))
                  :ei-mahdollinen)
      :tallennus-ei-mahdollinen-tooltip
      (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-valitavoitteet)}

     [{:otsikko "Nimi" :leveys 25 :nimi :nimi :tyyppi :string :pituus-max 128}
      (when @urakka/yllapitokohdeurakka?
        (sarake-yllapitokohde urakka yllapitokohteet))
      {:otsikko "Taka\u00ADraja" :leveys 20 :nimi :takaraja :fmt #(if %
                                                                    (pvm/pvm-opt %)
                                                                    "Ei takarajaa")
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
     (suodata-valitavoitteet-urakkavuodella @kaikki-valitavoitteet-atom valittu-urakan-vuosi)]))

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

(defn valtakunnalliset-valitavoitteet
  [{:keys [urakka kaikki-valitavoitteet-atom valtakunnalliset-valitavoitteet valittu-urakan-vuosi]}]
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
                [y/ajax-loader "Tavoitteita haetaan..."]
                "Ei valtakunnallisia määräajassa tehtäviä töitä")
       :tallenna (if voi-muokata?
                   #(go (let [vastaus (<! (tiedot/tallenna-valitavoitteet! (:id urakka) %))]
                          (if (k/virhe? vastaus)
                            (viesti/nayta! "Tallentaminen epäonnistui"
                                           :warning viesti/viestin-nayttoaika-lyhyt)
                            (reset! kaikki-valitavoitteet-atom vastaus))))
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

                :default
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
      (suodata-valitavoitteet-urakkavuodella valtakunnalliset-valitavoitteet valittu-urakan-vuosi)]

     (when (ainakin-yksi-tavoite-muutettu-urakkaan? valtakunnalliset-valitavoitteet)
       [yleiset/vihje-elementti [:span
                                 [:span "Urakkakohtaisten tarkennukset värjätty "]
                                 [:span.grid-solu-varoitus "punaisella"]
                                 [:span "."]]])]))

(defn valitavoitteet
  "Urakan välitavoitteet näkymä. Ottaa parametrinä urakan ja hakee välitavoitteet sille."
  [ur]
  (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id ur))
        nayta-yhdistetty-grid? (and (boolean (#{:tiemerkinta} (:tyyppi ur)))
                                    (vvt-tiedot/valtakunnalliset-valitavoitteet-kaytossa? (:tyyppi ur)))
        nayta-valtakunnalliset-grid? (and (not nayta-yhdistetty-grid?)
                                          (vvt-tiedot/valtakunnalliset-valitavoitteet-kaytossa? (:tyyppi ur)))
        nayta-urakkakohtaiset-grid? (not nayta-yhdistetty-grid?)]
    (komp/luo
      (komp/lippu tiedot/nakymassa?)
      (komp/ulos #(when (= @urakka/valittu-urakan-vuosi :kaikki)
                    ;; Muut näkymät eivät tue vuosivalintaa "Kaikki",
                    ;; joten resetoidaan valinta
                    (urakka/valitse-urakan-oletusvuosi! ur)))
      (fn [ur]
        [:div.valitavoitteet
         [valinnat/urakan-vuosi ur {:kaikki-valinta? true}]

         (when nayta-urakkakohtaiset-grid?
           [urakan-omat-valitavoitteet
            {:urakka ur
             :kaikki-valitavoitteet-atom tiedot/valitavoitteet
             :urakan-valitavoitteet @tiedot/urakan-valitavoitteet
             :valittu-urakan-vuosi @urakka/valittu-urakan-vuosi}])

         (when nayta-valtakunnalliset-grid?
           [valtakunnalliset-valitavoitteet
            {:urakka ur
             :kaikki-valitavoitteet-atom tiedot/valitavoitteet
             :valtakunnalliset-valitavoitteet @tiedot/valtakunnalliset-valitavoitteet
             :valittu-urakan-vuosi @urakka/valittu-urakan-vuosi}])

         (when nayta-yhdistetty-grid?
           [urakan-omat-ja-valtakunnalliset-valitavoitteet
            {:urakka ur
             :kaikki-valitavoitteet-atom tiedot/valitavoitteet
             :valittu-urakan-vuosi @urakka/valittu-urakan-vuosi}])

         (when nayta-valtakunnalliset-grid?
           [yleiset/vihje (str
                            "Järjestelmävastaava hallinnoi listaa valtakunnallisista, määräaikaan mennessä tehtävistä töistä."
                            " "
                            (when voi-muokata?
                              "Voit kuitenkin tehdä tavoitteisiin urakkakohtaisia muokkauksia."))])]))))
