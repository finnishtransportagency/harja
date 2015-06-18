(ns harja.views.urakka.toteumat.muut-tyot
  "Urakan 'Toteumat' välilehden 'Muut työt' osio"
  (:require [reagent.core :refer [atom] :as r]
            [bootstrap :as bs]
            [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :refer [modal] :as modal]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko +korostuksen-kesto+]]
            [harja.ui.napit :as napit]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.indeksit :as i]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.suunnittelu :as s]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.tiedot.istunto :as istunto]
            [harja.views.urakka.valinnat :as valinnat]

            [harja.ui.visualisointi :as vis]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.fmt :as fmt]
            [cljs.core.async :refer [<! >! chan]]
            [clojure.string :as str]
            [cljs-time.core :as t]
            [cljs.core.async :refer [<! timeout]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce valittu-toteuma (atom nil))


(defn tallenna-muu-tyo [muokattu]
  (log "tallenna-muu-tyo" (pr-str muokattu))
  (go (let [_ nil]
        ;(reset! u/muut-tyot-hoitokaudella res)
        )))

(def +valitse-tyyppi+
  "- Valitse tyyppi -")

;; Fixme: tee tarpeettomaksi avainarvoparivektorin avulla
(defn muun-tyon-tyypin-teksti [avainsana]
  "Muun työn tyypin teksti avainsanaa vastaan"
  (case avainsana
    :muutostyo "Muutostyö"
    :akillinen-hoitotyo "Äkillinen hoitotyö"
    :lisatyo "Lisätyö"
    +valitse-tyyppi+))

(def +muun-tyon-tyypit+
  [[:muutostyo "Muutostyö"] [:akillinen-hoitotyo "Äkillinen hoitotyö"] [:lisatyo "Lisätyö"]])


(def korostettavan-rivin-id (atom nil))

;; FIXME: siirrä rivin korostuksen funktio gridiin josta sitä voi käyttää
(defn korosta-rivia
  ([id] (korosta-rivia id +korostuksen-kesto+))
  ([id kesto]
   (reset! korostettavan-rivin-id id)
   (go (<! (timeout kesto))
       (reset! korostettavan-rivin-id nil))))

(def +rivin-luokka+ "korosta")

(defn aseta-rivin-luokka [korostettavan-rivin-id]
  (fn [rivi]
    (if (= korostettavan-rivin-id (:id rivi))
      +rivin-luokka+
      "")))

(defn toteutuneen-muun-tyon-muokkaus
  "Muutos-, lisä- ja äkillisen hoitotyön toteuman muokkaaminen ja lisääminen"
  []
  (let [muokattu (atom (if (:id @valittu-toteuma)
                         (assoc @valittu-toteuma
                           :sopimus @u/valittu-sopimusnumero
                           :toimenpideinstanssi @u/valittu-toimenpideinstanssi)
                         (assoc @valittu-toteuma
                           :sopimus @u/valittu-sopimusnumero
                           :toimenpideinstanssi @u/valittu-toimenpideinstanssi)))
        valmis-tallennettavaksi? (reaction (let [m @muokattu]
                                             (not (and
                                                    (:toimenpideinstanssi m)
                                                    (:tyyppi m)
                                                    (:pvm m)
                                                    (:rahasumma m)))))
        tallennus-kaynnissa (atom false)]

    (komp/luo
      (fn []
        [:div.muun-tyon-tiedot
         [:button.nappi-toissijainen {:on-click #(reset! valittu-toteuma nil)}
          (ikonit/chevron-left) " Takaisin muiden töiden luetteloon"]
         (if (:id @valittu-toteuma)
           [:h3 "Muokkaa toteumaa"]
           [:h3 "Luo uusi toteuma"])

         [lomake {:luokka   :horizontal
                  :muokkaa! (fn [uusi]
                              (log "MUOKATAAN " (pr-str uusi))
                              (reset! muokattu uusi))
                  :footer   [:span
                             [napit/palvelinkutsu-nappi
                              " Tallenna toteuma"
                              #(tallenna-muu-tyo @muokattu)
                              {:luokka "nappi-ensisijainen"
                               :disabled @valmis-tallennettavaksi?
                               :kun-onnistuu #(let [muokatun-id (or (:id @muokattu) %)]
                                               (do
                                                 (korosta-rivia muokatun-id)
                                                 (reset! tallennus-kaynnissa false)
                                                 (reset! valittu-toteuma nil)))
                               :kun-virhe  (reset! tallennus-kaynnissa false)}]
                             (when (:id @muokattu)
                               [:button.nappi-kielteinen
                                {:class (when @tallennus-kaynnissa "disabled")
                                 :on-click
                                        (fn []
                                          (modal/nayta! {:otsikko "Toteuman poistaminen"
                                                         :footer  [:span
                                                                   [:button.nappi-toissijainen {:type     "button"
                                                                                                :on-click #(do (.preventDefault %)
                                                                                                               (modal/piilota!))}
                                                                    "Peruuta"]
                                                                   [:button.nappi-kielteinen {:type     "button"
                                                                                              :on-click #(do (.preventDefault %)
                                                                                                             (modal/piilota!)
                                                                                                             (reset! tallennus-kaynnissa true)
                                                                                                             (go (let [res (tallenna-muu-tyo
                                                                                                                             (assoc @muokattu :poistettu true))]
                                                                                                                   (if res
                                                                                                                     ;; Tallennus ok
                                                                                                                     (do (viesti/nayta! "Toteuma poistettu")
                                                                                                                         (reset! tallennus-kaynnissa false)
                                                                                                                         (reset! valittu-toteuma nil))

                                                                                                                     ;; Epäonnistui jostain syystä
                                                                                                                     (reset! tallennus-kaynnissa false)))))}
                                                                    "Poista toteuma"]]}
                                                        [:div (str "Haluatko varmasti poistaa toteuman "
                                                                   (Math/abs (:rahasumma @muokattu)) "€ päivämäärällä "
                                                                   (pvm/pvm (:pvm @muokattu)) "?")]))}
                                (ikonit/trash) " Poista kustannus"])]}

          [{:otsikko       "Sopimusnumero" :nimi :sopimus
            :tyyppi        :valinta
            :valinta-nayta second
            :valinnat      (:sopimukset @nav/valittu-urakka)
            :fmt           second
            :leveys-col    3}
           {:otsikko       "Toimenpide" :nimi :toimenpideinstanssi
            :tyyppi        :valinta
            :valinta-nayta #(:tpi_nimi %)
            :valinnat      @u/urakan-toimenpideinstanssit
            :fmt           #(:tpi_nimi %)
            :leveys-col    3}
           {:otsikko       "Tyyppi" :nimi :tyyppi
            :tyyppi        :valinta
            :valinta-arvo  first
            :valinta-nayta #(if (nil? %) +valitse-tyyppi+ second)
            :valinnat      +muun-tyon-tyypit+
            :fmt           #(muun-tyon-tyypin-teksti %)
            :validoi       [[:ei-tyhja "Anna kustannustyyppi"]]
            :leveys-col    3}
           {:otsikko "Alkanut pvm" :nimi :alkanut :tyyppi :pvm  :validoi [[:ei-tyhja "Anna toteuman aloituksen päivämäärä"]] :leveys-col 3}
           {:otsikko "Lopetus pvm" :nimi :paattynyt :tyyppi :pvm  :validoi [[:ei-tyhja "Anna toteuman lopetuksen päivämäärä"]] :leveys-col 3}
           ;; FIXME: lisää hinnoittelutyyppi päivän hinta tai muutoshintainen
           {:otsikko "Päivän hinta" :nimi :paivanhinta :tyyppi :numero :validoi [[:ei-tyhja "Anna rahamäärä"]] :leveys-col 3}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :text :pituus-max 1024
            :placeholder "Kirjoita tähän lisätietoa" :koko [80 :auto]}
           ]

          @muokattu]]))))

(defn muut-tyot-toteumalistaus
  "Muiden töiden toteumat"
  []
  (let [urakka @nav/valittu-urakka
        valitut-kustannukset
        (reaction (let [[sopimus-id _] @u/valittu-sopimusnumero
                        toimenpideinstanssi (:tpi_id @u/valittu-toimenpideinstanssi)]
                    (reverse (sort-by :alkanut (filter #(and
                                                     (= sopimus-id (:sopimus %))
                                                     (= (:toimenpideinstanssi %) toimenpideinstanssi))
                                                   @u/muut-tyot-hoitokaudella)))))]

    (komp/luo
      (fn []
        (let [aseta-rivin-luokka (aseta-rivin-luokka @korostettavan-rivin-id)]
          [:div.muut-tyot-toteumat
           [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide urakka]
           [:button.nappi-ensisijainen {:on-click #(reset! valittu-toteuma {})}
            (ikonit/plus-sign) " Lisää toteuma"]

           [grid/grid
            {:otsikko       (str "Muutos-, lisä- ja äkilliset hoitotyöt ")
             :tyhja         (if (nil? @valitut-kustannukset)
                              [ajax-loader "Toteumia haetaan..."]
                              "Ei toteumia saatavilla.")
             :rivi-klikattu #(reset! valittu-toteuma %)
             :rivin-luokka  #(aseta-rivin-luokka %)}
            [{:otsikko "Tyyppi" :nimi :tyyppi :fmt muun-tyon-tyypin-teksti :leveys "20%"}
             {:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :pvm :leveys "10%"}
             {:otsikko "Rahamäärä (€)" :tyyppi :string :nimi :rahasumma :hae #(Math/abs (:rahasumma %))
              :fmt fmt/euro-opt :leveys "10%" :validoi [[:ei-tyhja "Anna hinta."]]}
             {:otsikko "Maksaja" :tyyppi :string :nimi :maksaja
              :hae     #(if (neg? (:rahasumma %)) "Urakoitsija" "Tilaaja") :leveys "10%"}
             {:otsikko "Lisätieto" :nimi :lisatieto :leveys "45%"}
             {:otsikko "Indeksi" :nimi :indeksin_nimi :leveys "10%"}
             ]
            @valitut-kustannukset
            ]])))))



(defn muut-tyot-toteumat []
  (if @valittu-toteuma
    [toteutuneen-muun-tyon-muokkaus]
    [muut-tyot-toteumalistaus]))