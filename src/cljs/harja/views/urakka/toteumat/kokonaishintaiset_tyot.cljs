(ns harja.views.urakka.toteumat.kokonaishintaiset-tyot
  "Urakan 'Toteumat' välilehden 'Kokonaishintaiset työt' osio"
  (:require [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan timeout]]
            [harja.atom :refer [paivita!] :refer-macros [reaction<!]]
            [harja.ui.grid :as grid]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.tiedot.urakka.toteumat.kokonaishintaiset-tyot :as tiedot]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.views.kartta :as kartta]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.views.urakka.valinnat :as urakka-valinnat]
            [harja.ui.komponentti :as komp]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.napit :as napit]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.ui.yleiset :as yleiset]
            [clojure.string :as str])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.makrot :refer [defc fnc]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction-writable]]))

(defn tehtavan-paivakohtaiset-tiedot [urakka-id pvm toimenpidekoodi jarjestelmanlisaama]
  (let [avain [urakka-id pvm toimenpidekoodi jarjestelmanlisaama]
        tiedot (get @tiedot/toteumien-paivakohtaiset-tiedot avain)]
    [grid/grid {:otsikko  "Päivän toteumat"
                :tunniste :id
                :tyhja    (if (nil? tiedot) [ajax-loader "Haetaan tehtävän päiväkohtaisia tietoja..."]
                              "Tietoja ei löytynyt")}
     [{:otsikko "Suorittaja" :nimi :suorittaja :hae (comp :nimi :suorittaja) :leveys 2}
      {:otsikko "Alkanut" :nimi :alkanut :leveys 1 :fmt pvm/aika}
      {:otsikko "Päättynyt" :nimi :paattynyt :leveys 1 :fmt pvm/aika}
      {:otsikko "Määrä" :tyyppi :numero :nimi :maara :leveys 1 :tasaa :oikea
       :hae     (fn [{:keys [tehtava]}]
                  (->> (fmt/desimaaliluku-opt (:maara tehtava) 1)
                       (fmt/yksikolla (:yksikko tehtava))))}
      {:otsikko "Pituus" :nimi :pituus :leveys 1 :fmt fmt/pituus-opt :tasaa :oikea}
      {:otsikko "Lisätietoja" :nimi :lisatieto :leveys 3}
      {:otsikko     "Tarkastele koko toteumaa"
       :nimi        :tarkastele-toteumaa
       :muokattava? (constantly false)
       :tyyppi      :komponentti
       :leveys      1
       :komponentti (fn [rivi]
                      [:div
                       [:button.nappi-toissijainen.nappi-grid
                        {:on-click #(tiedot/valitse-paivan-toteuma-id! avain (:id rivi))}
                        (ikonit/eye-open) " Toteuma"]])}]
     tiedot]))

(defn taulukko []
  (let [toteumat @tiedot/haetut-toteumat
        urakka-id @nav/valittu-urakka-id
        tunniste (juxt (constantly urakka-id)
                       :pvm
                       :toimenpidekoodi
                       :jarjestelmanlisaama)]
    [:span
     [grid/grid
      {:otsikko                   "Kokonaishintaisten töiden toteumat"
       :tyhja                     (if @tiedot/haetut-toteumat "Toteumia ei löytynyt" [ajax-loader "Haetaan toteumia."])
       :rivi-klikattu             #(do
                                     (nav/vaihda-kartan-koko! :L)
                                     (tiedot/valitse-paivakohtainen-tehtava!
                                      (:pvm %) (:toimenpidekoodi %)))
       :rivi-valinta-peruttu      #(do (reset! tiedot/valittu-paivakohtainen-tehtava nil))
       :mahdollista-rivin-valinta? true
       :max-rivimaara 500
       :max-rivimaaran-ylitys-viesti "Toteumia löytyi yli 500. Tarkenna hakurajausta."
       :tunniste tunniste
       :vetolaatikot-auki tiedot/avatut-toteumat
       :vetolaatikot (into {}
                           (map (juxt
                                 tunniste
                                 (fn [{:keys [pvm toimenpidekoodi jarjestelmanlisaama]}]
                                   [tehtavan-paivakohtaiset-tiedot
                                    urakka-id
                                    pvm toimenpidekoodi jarjestelmanlisaama])))
                           toteumat)}
      [{:nimi :tarkemmat-tiedot :tyyppi :vetolaatikon-tila :leveys 1}
       {:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :pvm :leveys 3}
       {:otsikko "Tehtävä" :tyyppi :string :nimi :nimi :leveys 4}
       {:otsikko "Määrä" :tyyppi :numero :nimi :maara :leveys 2 :tasaa :oikea
        :hae     (fn [rivi] (->> (fmt/desimaaliluku-opt (:maara rivi) 2) (fmt/yksikolla (:yksikko rivi))))}
       {:otsikko "Pituus" :nimi :pituus :leveys 2 :fmt fmt/pituus-opt :tasaa :oikea}
       {:otsikko "Lähde" :nimi :lahde :hae #(if (:jarjestelmanlisaama %) "Urak. järj." "Harja") :tyyppi :string :leveys 3}]
      toteumat]]))

(defn valinnat []
  (let [urakka @nav/valittu-urakka]
    [:span
     (urakka-valinnat/urakan-sopimus urakka)
     (urakka-valinnat/urakan-hoitokausi urakka)
     (urakka-valinnat/aikavali)
     (urakka-valinnat/urakan-toimenpide+kaikki)
     (urakka-valinnat/urakan-kokonaishintainen-tehtava+kaikki)]))

(defn kokonaishintaisten-toteumien-listaus
  "Kokonaishintaisten töiden toteumat"
  []
  [:div
   [valinnat]
   (let [oikeus? (oikeudet/voi-kirjoittaa?
                  oikeudet/urakat-toteumat-kokonaishintaisettyot
                  (:id @nav/valittu-urakka))]
     (yleiset/wrap-if
      (not oikeus?)
      [yleiset/tooltip {} :%
       (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                       oikeudet/urakat-toteumat-kokonaishintaisettyot)]
      [napit/uusi "Lisää toteuma" #(reset! tiedot/valittu-kokonaishintainen-toteuma
                                           (tiedot/uusi-kokonaishintainen-toteuma))
       {:disabled (not oikeus?)}]))
   [taulukko]
   [yleiset/vihje "Näet työn kartalla klikkaamalla riviä."]])

(defn kokonaishintainen-toteuma-lomake []
  (let [muokattu (reaction-writable @tiedot/valittu-kokonaishintainen-toteuma)
        jarjestelman-lisaama-toteuma? (true? (:jarjestelma @muokattu))
        nelostason-tehtavat (map #(nth % 3) @u/urakan-toimenpiteet-ja-tehtavat)
        toimenpideinstanssit u/urakan-toimenpideinstanssit
        ;; Tehtävät pitää kasata tässä erikseen, jotta kun lomakkeessa vaihtaa toimenpideinstanssia,
        ;; myös tehtävät haetaan uudelleen..
        tehtavat (reaction (let [valittu-tpi-id (get-in @muokattu [:tehtava :toimenpideinstanssi :id])
                                 tpi-tiedot (some #(when (= valittu-tpi-id (:tpi_id %)) %) @u/urakan-toimenpideinstanssit)
                                 kaikki-tehtavat @u/urakan-kokonaishintaiset-toimenpiteet-ja-tehtavat-tehtavat
                                 tpin-tehtavat (into [] (keep (fn [[_ _ t3 t4]]
                                                                (when (= (:koodi t3) (:t3_koodi tpi-tiedot))
                                                                  t4))
                                                              kaikki-tehtavat))]
                             (sort-by :nimi tpin-tehtavat)))]
    (fnc []
         [:div
          [napit/takaisin "Takaisin luetteloon" tiedot/poista-toteuman-valinta!]

          [lomake/lomake
           {:otsikko      (if (:id @muokattu)
                            "Muokkaa kokonaishintaista toteumaa"
                            "Luo uusi kokonaishintainen toteuma")
            :muokkaa!     #(do (reset! muokattu %))
            :voi-muokata? (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-kokonaishintaisettyot (:id @nav/valittu-urakka))
            :footer-fn    (fn [tiedot]
                            [napit/palvelinkutsu-nappi
                            "Tallenna toteuma"
                            #(tiedot/tallenna-kokonaishintainen-toteuma! tiedot)
                            {:luokka       "nappi-ensisijainen"
                             :ikoni        (ikonit/tallenna)
                             :kun-onnistuu #(do
                                             (tiedot/toteuman-tallennus-onnistui %)
                                             (reset! tiedot/valittu-kokonaishintainen-toteuma nil))
                             :disabled     (or (not (lomake/voi-tallentaa? tiedot))
                                               jarjestelman-lisaama-toteuma?
                                               (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-kokonaishintaisettyot (:id @nav/valittu-urakka))))}])}
           ;; lisatieto, suorittaja {ytunnus, nimi}, pituus
           ;; reitti!
           [(when jarjestelman-lisaama-toteuma?
              {:otsikko "Lähde" :nimi :luoja :tyyppi :string
               :hae (fn [rivi]
                      (str "Järjestelmä (" (get-in rivi [:suorittaja :nimi]) ")"))
               :muokattava? (constantly false)
               :vihje toteumat/ilmoitus-jarjestelman-luoma-toteuma})
            {:otsikko     "Päivämäärä"
             :nimi        :alkanut
             :pakollinen? true
             :tyyppi      :pvm-aika
             :uusi-rivi?  true
             :aseta (fn [rivi arvo]
                      (-> rivi
                          (assoc :paattynyt arvo)
                          (assoc :alkanut arvo)))
             :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
             :validoi     [[:ei-tyhja "Valitse päivämäärä"]]
             :huomauta     [[:urakan-aikana-ja-hoitokaudella]]}
           (if (:jarjestelma @muokattu)
              {:tyyppi :string
               :otsikko "Pituus"
               :fmt fmt/pituus-opt
               :nimi :pituus
               :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}
              (if-not (= (:reitti @muokattu) :hakee)
                {:tyyppi                            :tierekisteriosoite
                 :nimi                              :tr
                 :pakollinen?         true
                 :sijainti                          (r/wrap (:reitti @muokattu)
                                                            #(swap! muokattu assoc :reitti %))
                 :ala-nayta-virhetta-komponentissa? true
                 :validoi [[:validi-tr "Reittiä ei saada tehtyä" [:reitti]]]
                 }
               {:tyyppi :spinner
                :nimi :spinner
                :viesti "Haetaan reittiä"}))
            {:otsikko "Suorittaja"
             :uusi-rivi? true
             :nimi :suorittajan-nimi
             :hae (comp :nimi :suorittaja)
             :aseta (fn [rivi arvo] (assoc-in rivi [:suorittaja :nimi] arvo))
             :pituus-max 256
             :tyyppi :string
             :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}
            {:otsikko "Suorittajan Y-tunnus"
             :nimi :suorittajan-ytunnus
             :hae (comp :ytunnus :suorittaja)
             :aseta (fn [rivi arvo] (assoc-in rivi [:suorittaja :ytunnus] arvo))
             :pituus-max 9
             :validoi [[:ytunnus]]
             :tyyppi :string
             :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}
            (lomake/ryhma
              {:otsikko "Tehty työ"
               :leveys-col 3}
              {:otsikko       "Toimenpide"
               :nimi          :toimenpide
               :pakollinen?   true
               :muokattava?   (constantly (not jarjestelman-lisaama-toteuma?))
               :tyyppi        :valinta
               :valinnat      @toimenpideinstanssit
               :fmt           #(:tpi_nimi
                                (urakan-toimenpiteet/toimenpideinstanssi-idlla % @toimenpideinstanssit))
               :valinta-arvo  :tpi_id
               :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
               :hae (comp :id :toimenpideinstanssi :tehtava)
               :aseta (fn [rivi arvo]
                        (-> rivi
                            (assoc-in [:tehtava :toimenpideinstanssi :id] arvo)
                            (assoc-in [:tehtava :toimenpidekoodi :id] nil)
                            (assoc-in [:tehtava :yksikko] nil)))
               :leveys-col    3}
              {:otsikko       "Tehtävä"
               :nimi          :tehtava
               :pakollinen?   true
               :muokattava?   (constantly (not jarjestelman-lisaama-toteuma?))
               :tyyppi        :valinta
               :valinnat      @tehtavat
               :valinta-arvo  :id
               :valinta-nayta #(if % (:nimi %) "- Valitse tehtävä -")
               :hae           (comp :id :toimenpidekoodi :tehtava)
               :aseta         (fn [rivi arvo]
                                (-> rivi
                                    (assoc-in [:tehtava :toimenpidekoodi :id] arvo)
                                    (assoc-in [:tehtava :yksikko] (:yksikko
                                                                    (urakan-toimenpiteet/tehtava-idlla
                                                                     arvo nelostason-tehtavat)))))
               :leveys-col    3}
              {:otsikko "Määrä"
               :nimi :maara
               :pakollinen?   true
               :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
               :tyyppi :positiivinen-numero
               :hae (comp :maara :tehtava)
               :aseta (fn [rivi arvo]
                        (assoc-in rivi [:tehtava :maara] arvo))
               :leveys-col 3}
              {:otsikko "Yksikkö"
               :nimi :yksikko
               :muokattava? (constantly false)
               :tyyppi :string
               :hae (comp :yksikko :tehtava)
               :leveys-col 3})
            {:otsikko "Lisätieto"
             :nimi :lisatieto
             :pituus-max 256
             :tyyppi :text
             :uusi-rivi? true
             :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
             :koko [80 :auto]
             :palstoja 2}]
           @muokattu]])))

(def debug-toteuma (atom {}))

(defn kokonaishintaiset-toteumat []
  (komp/luo
    (komp/lippu tiedot/nakymassa? tiedot/karttataso-kokonaishintainen-toteuma)
    (komp/sisaan-ulos #(do
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                          {:toteuma {:toiminto (fn [klikattu-toteuma]
                                                 (log "klikattu toteuma:" (pr-str klikattu-toteuma))
                                                 (go
                                                   (tiedot/valitse-toteuma!
                                                    (<! (tiedot/hae-toteuman-tiedot
                                                         (:id @nav/valittu-urakka)
                                                         (:id klikattu-toteuma))))))
                                       :teksti "Valitse toteuma"}}))
                      #(kartta-tiedot/kasittele-infopaneelin-linkit! nil))
    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @tiedot/valittu-kokonaishintainen-toteuma
         [kokonaishintainen-toteuma-lomake]
         [kokonaishintaisten-toteumien-listaus])])))
