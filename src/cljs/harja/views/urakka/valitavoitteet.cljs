(ns harja.views.urakka.valitavoitteet
  "Ylläpidon urakoiden välitavoitteiden näkymä"
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt]]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.urakka.valitavoitteet :as vt]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :as y]
            [harja.pvm :as pvm]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.fmt :as fmt]
            [cljs-time.core :as t]
            [cljs.core.async :refer [<!]]
            [harja.tiedot.navigaatio :as nav]
            [harja.domain.oikeudet :as oikeudet]
            [harja.asiakas.kommunikaatio :as k]
            [harja.ui.viesti :as viesti]
            [harja.ui.yleiset :as yleiset])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

(def tallennus-kaynnissa? (atom false))

(defn valmiustilan-kuvaus [{:keys [valmispvm takaraja]}]
  (cond (nil? takaraja)
        "Uusi"

        (and takaraja valmispvm)
        "Valmistunut"

        (and takaraja (nil? valmispvm) (pvm/sama-tai-ennen? (pvm/nyt) takaraja))
        (let [paivia-valissa (t/in-days (t/interval (t/now) takaraja))]
              (str "Ei valmis" (when (pos? paivia-valissa)
                                 (str " (" (fmt/kuvaile-paivien-maara paivia-valissa) " jäljellä)"))))

        (and takaraja (nil? valmispvm) (t/after? (pvm/nyt) takaraja))
        (let [paivia-valissa (t/in-days (t/interval takaraja (t/now)))]
              (str "Myöhässä" (when (pos? paivia-valissa)
                                (str " (" (fmt/kuvaile-paivien-maara paivia-valissa) ")"))))))

(defn- urakan-valitavoitteet [urakka kaikki-valitavoitteet-atom urakan-valitavoitteet-atom]
  (log "Listalla: " (pr-str urakan-valitavoitteet-atom))
  (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
        voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))]
    [grid/grid
     {:otsikko "Urakan välitavoitteet"
      :tyhja (if (nil? @urakan-valitavoitteet-atom)
               [y/ajax-loader "Välitavoitteita haetaan..."]
               "Ei välitavoitteita")
      :tallenna (when voi-muokata?
                  #(go (reset! tallennus-kaynnissa? true)
                       (go
                         (let [vastaus (<! (vt/tallenna-valitavoitteet! (:id urakka) %))]
                           (if (k/virhe? vastaus)
                             (viesti/nayta! "Tallentaminen epäonnistui"
                                            :warning viesti/viestin-nayttoaika-lyhyt)
                             (reset! kaikki-valitavoitteet-atom vastaus)))
                         (reset! tallennus-kaynnissa? false))))}

     [{:otsikko "Nimi" :leveys 25 :nimi :nimi :tyyppi :string :pituus-max 128}
      {:otsikko "Taka\u00ADraja" :leveys 20 :nimi :takaraja :fmt #(if %
                                                                   (pvm/pvm-opt %)
                                                                   "Ei takarajaa")
       :tyyppi :pvm}
      {:otsikko "Tila" :leveys 20 :tyyppi :string :muokattava? (constantly false)
       :nimi :valmiustila :hae identity :fmt valmiustilan-kuvaus}
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
     @urakan-valitavoitteet-atom]))

(defn- valtakunnalliset-valitavoitteet [urakka kaikki-valitavoitteet-atom valtakunnalliset-valitavoitteet-atom]
  (let [voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
        voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))]
    [:div
     [grid/grid
      {:otsikko "Valtakunnalliset välitavoitteet"
       :tyhja (if (nil? @valtakunnalliset-valitavoitteet-atom)
                [y/ajax-loader "Välitavoitteita haetaan..."]
                "Ei välitavoitteita")
       :tallenna (when voi-muokata?
                   #(go (reset! tallennus-kaynnissa? true)
                        (go
                          (let [vastaus (<! (vt/tallenna-valitavoitteet! (:id urakka) %))]
                            (if (k/virhe? vastaus)
                              (viesti/nayta! "Tallentaminen epäonnistui"
                                             :warning viesti/viestin-nayttoaika-lyhyt)
                              (reset! kaikki-valitavoitteet-atom vastaus)))
                          (reset! tallennus-kaynnissa? false))))
       :voi-lisata? false
       :voi-poistaa? (constantly false)}

      [{:otsikko "Valta\u00ADkunnal\u00ADlinen väli\u00ADtavoite" :leveys 25
        :nimi :valtakunnallinen-nimi :tyyppi :string :pituus-max 128
        :muokattava? (constantly false) :hae #(str (:valtakunnallinen-nimi %))}
       {:otsikko "Väli\u00ADtavoite ura\u00ADkassa" :leveys 25 :nimi :nimi :tyyppi :string :pituus-max 128}
       {:otsikko "Valta\u00ADkunnal\u00ADlinen taka\u00ADraja" :leveys 20
        :nimi :valtakunnallinen-takaraja :hae #(cond
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
       {:otsikko "Taka\u00ADraja ura\u00ADkassa" :leveys 20 :nimi :takaraja :fmt pvm/pvm-opt :tyyppi :pvm}
       {:otsikko "Tila" :leveys 20 :tyyppi :string :muokattava? (constantly false)
        :nimi :valmiustila :hae identity :fmt valmiustilan-kuvaus}
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
      @valtakunnalliset-valitavoitteet-atom]
     [yleiset/vihje (str "Valtakunnalliset välitavoitteet ovat järjestelmävastaavan hallinnoimia. "
                         (when voi-muokata?
                           "Voit kuitenkin tehdä tavoitteisiin urakkakohtaisia muokkauksia."))]]))

(defn valitavoitteet
  "Urakan välitavoitteet näkymä. Ottaa parametrinä urakan ja hakee välitavoitteet sille."
  [ur]
  (let [tallennus-kaynnissa (atom false)]
    (komp/luo
      (komp/lippu vt/nakymassa?)
      (fn [ur]
        [:div.valitavoitteet {:style {:position "relative"}}
         (when @tallennus-kaynnissa (y/lasipaneeli (y/keskita (y/ajax-loader))))
         [urakan-valitavoitteet
          ur
          vt/valitavoitteet
          vt/urakan-valitavoitteet]
         [valtakunnalliset-valitavoitteet
          ur
          vt/valitavoitteet
          vt/valtakunnalliset-valitavoitteet]

         ;; PENDING Kommentoidaan toistaiseksi tämä ylläpidon demo pois hämmentämästä
         ;; Ylläpidon välitavoitteita pohditaan myöhemmin
         #_(when (and (= (:sopimustyyppi @nav/valittu-urakka) :kokonaisurakka)
                      (or (= (:tyyppi @nav/valittu-urakka) :paallystys)
                          (= (:tyyppi @nav/valittu-urakka) :paikkaus)))
             [grid/grid
              {:otsikko "Kohteiden välitavoitteet"
               :tunniste :yha-id
               :tallenna nil
               :vetolaatikot (into {}
                                   (map
                                     (juxt :id
                                           (partial valitavoite-lomake
                                                    {:aseta-tavoitteet #(reset! vt/valitavoitteet %)} ur)))
                                   @vt/valitavoitteet)
               }

              [{:tyyppi :vetolaatikon-tila :leveys "5%"}
               {:otsikko "YHA-ID" :leveys "15%" :nimi :yha-id :tyyppi :string :pituus-max 128 :muokattava? (constantly false)}
               {:otsikko "Kohde" :leveys "60%" :nimi :kohde :tyyppi :string :muokattava? (constantly false)}
               {:otsikko "Tila" :leveys "20%" :tyyppi :string :muokattava? (constantly false)
                :nimi :tila}]
              [{:yha-id 1 :kohde "Mt 22 Ruohonjuuren pätkä" :tila "Kaikki valmiina"}
               {:yha-id 2 :kohde "Mt 22 Terilän silta" :tila "Kaikki valmiina"}
               {:yha-id 3 :kohde "Mt 22 Matulan  pätkä" :tila "Kohde kesken"}
               {:yha-id 4 :kohde "Mt 22 koskenlaskijan kuru" :tila "Kohde kesken"}
               {:yha-id 5 :kohde "Mt 22 rampit" :tila "Kaikki valmiina"}]])]))))
