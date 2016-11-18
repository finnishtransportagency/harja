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
            [harja.ui.yleiset :as yleiset]
            [harja.tiedot.hallinta.valtakunnalliset-valitavoitteet :as tiedot])
  (:require-macros [reagent.ratom :refer [reaction run!]]
                   [cljs.core.async.macros :refer [go]]))

;; Valtakunnallisille välitavoitteille on haluttu eri urakkatyypeissä käyttää hieman eri nimitystä
(def valtakunnalliset-otsikko {:tiemerkinta "Tiemerkinnän välitavoitepohjista luodut välitavoitteet"})
(def valtakunnallinen-sarake {:tiemerkinta "Väli\u00ADtavoite\u00ADpohja"})
(def valtakunnallinen-takaraja-sarake {:tiemerkinta "Väli\u00ADtavoite\u00ADpohjan taka\u00ADraja"})
(def valtakunnalliset-tavoitteet-vihje {:tiemerkinta "Tiemerkinnän välitavoitepohjat ovat järjestelmävastaavan hallinnoimia."})

(defn valmiustilan-kuvaus [{:keys [valmispvm takaraja]}]
  (cond (nil? takaraja)
        "Uusi"

        (and takaraja valmispvm)
        "Valmistunut"

        (and takaraja (nil? valmispvm) (pvm/sama-tai-ennen? (pvm/nyt) takaraja))
        (let [paivia-valissa (pvm/paivia-valissa (pvm/nyt) takaraja)]
          (str "Ei valmis" (when (pos? paivia-valissa)
                             (str " (" (fmt/kuvaile-paivien-maara paivia-valissa) " jäljellä)"))))

        (and takaraja (nil? valmispvm) (t/after? (pvm/nyt) takaraja))
        (let [paivia-valissa (pvm/paivia-valissa takaraja (pvm/nyt))]
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
      :tallenna (if voi-muokata?
                  #(go (let [vastaus (<! (vt/tallenna-valitavoitteet! (:id urakka) %))]
                         (if (k/virhe? vastaus)
                           (viesti/nayta! "Tallentaminen epäonnistui"
                                          :warning viesti/viestin-nayttoaika-lyhyt)
                           (reset! kaikki-valitavoitteet-atom vastaus))))
                  :ei-mahdollinen)
      :tallennus-ei-mahdollinen-tooltip
      (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-valitavoitteet)}

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

(defn ainakin-yksi-tavoite-muutettu-urakkaan [rivit]
  (some #(or
          ;; Kertaluontoinen takaraja poikkeaa
          (and (:valtakunnallinen-takaraja %)
               (not= (:takaraja %) (:valtakunnallinen-takaraja %)))

          ;; Toistuva takaraja poikkeaa
          (and (:valtakunnallinen-takarajan-toistopaiva %)
               (:valtakunnallinen-takarajan-toistokuukausi %)
               (or (not= (:valtakunnallinen-takarajan-toistopaiva %)
                         (t/day (:takaraja %)))
                   (not= (:valtakunnallinen-takarajan-toistokuukausi %)
                         (t/month (:takaraja %)))))

          ;; Välitavoitteen nimi poikkeaa
          (not= (:valtakunnallinen-nimi %) (:nimi %)))
        rivit))

(defn- valtakunnalliset-valitavoitteet [urakka kaikki-valitavoitteet-atom valtakunnalliset-valitavoitteet-atom]
  (let [voi-merkita-valmiiksi? (oikeudet/on-muu-oikeus? "valmis" oikeudet/urakat-valitavoitteet (:id urakka))
        voi-tehda-tarkennuksen? voi-merkita-valmiiksi? ; Toistaiseksi oletetaan nämä oikeudet samaksi
        ;; Mitään taulukon kenttää ei voi muokata ilman oikeutta merkitä valmiiksi tai tehdä tarkennuksia
        voi-muokata? (and (oikeudet/voi-kirjoittaa? oikeudet/urakat-valitavoitteet (:id urakka))
                          (or voi-merkita-valmiiksi?
                              voi-tehda-tarkennuksen?))]
    [:div
     [grid/grid
      {:otsikko (or (valtakunnalliset-otsikko (:tyyppi urakka))
                    "Valtakunnalliset välitavoitteet")
       :tyhja (if (nil? @valtakunnalliset-valitavoitteet-atom)
                [y/ajax-loader "Välitavoitteita haetaan..."]
                "Ei välitavoitteita")
       :tallenna (if voi-muokata?
                   #(go (let [vastaus (<! (vt/tallenna-valitavoitteet! (:id urakka) %))]
                          (if (k/virhe? vastaus)
                            (viesti/nayta! "Tallentaminen epäonnistui"
                                           :warning viesti/viestin-nayttoaika-lyhyt)
                            (reset! kaikki-valitavoitteet-atom vastaus))))
                   :ei-mahdollinen)
       :tallennus-ei-mahdollinen-tooltip
       (oikeudet/oikeuden-puute-kuvaus :kirjoitus oikeudet/urakat-valitavoitteet)

       :voi-lisata? false
       :voi-poistaa? (constantly false)}

      [{:otsikko (or (valtakunnallinen-sarake (:tyyppi urakka))
                     "Valta\u00ADkunnal\u00ADlinen väli\u00ADtavoite")
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
       {:otsikko (or (valtakunnallinen-takaraja-sarake (:tyyppi urakka))
                     "Valta\u00ADkunnal\u00ADlinen taka\u00ADraja")
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
            (when (or
                    (and (:valtakunnallinen-takaraja rivi)
                         (not= (:takaraja rivi) (:valtakunnallinen-takaraja rivi)))
                    (and (:valtakunnallinen-takarajan-toistopaiva rivi)
                         (:valtakunnallinen-takarajan-toistokuukausi rivi)
                         (or (not= (:valtakunnallinen-takarajan-toistopaiva rivi)
                                   (t/day (:takaraja rivi)))
                             (not= (:valtakunnallinen-takarajan-toistokuukausi rivi)
                                   (t/month (:takaraja rivi))))))
                    poikkeava)))
        :tyyppi :pvm
        :muokattava? (constantly voi-tehda-tarkennuksen?)}
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
     (when (ainakin-yksi-tavoite-muutettu-urakkaan @valtakunnalliset-valitavoitteet-atom)
       [yleiset/vihje-elementti [:span
                                 [:span "Urakkakohtaisten tarkennukset värjätty "]
                                 [:span.grid-solu-varoitus "punaisella"]
                                 [:span "."]]])
     [yleiset/vihje (str
                      (or (valtakunnalliset-tavoitteet-vihje (:tyyppi urakka))
                          "Valtakunnalliset välitavoitteet ovat järjestelmävastaavan hallinnoimia.")
                      " "
                      (when voi-muokata?
                        "Voit kuitenkin tehdä tavoitteisiin urakkakohtaisia muokkauksia."))]]))

(defn valitavoitteet
  "Urakan välitavoitteet näkymä. Ottaa parametrinä urakan ja hakee välitavoitteet sille."
  [ur]
  (let [tallennus-kaynnissa (atom false)
        nayta-valtakunnalliset? (some? (tiedot/valtakunnalliset-valitavoitteet-kaytossa
                                   (:tyyppi ur)))]
    (komp/luo
      (komp/lippu vt/nakymassa?)
      (fn [ur]
        [:div.valitavoitteet {:style {:position "relative"}}
         (when @tallennus-kaynnissa (y/lasipaneeli (y/keskita (y/ajax-loader))))
         [urakan-valitavoitteet
          ur
          vt/valitavoitteet
          vt/urakan-valitavoitteet]
         (when nayta-valtakunnalliset?
           [valtakunnalliset-valitavoitteet
            ur
            vt/valitavoitteet
            vt/valtakunnalliset-valitavoitteet])]))))
