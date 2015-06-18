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
            [harja.tiedot.urakka.muut-tyot :as muut-tyot]
            [harja.tiedot.istunto :as istunto]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.views.urakka.valinnat :as valinnat]

            [harja.ui.visualisointi :as vis]
            [harja.ui.lomake :as lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]
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
  (log "front: tallenna-muu-tyo" (pr-str muokattu))
  (go (let [ur (:id @nav/valittu-urakka)
            [sop _] @u/valittu-sopimusnumero
            toteuma (assoc muokattu
                      :urakka-id ur
                      :sopimus-id sop)
            res (<! (muut-tyot/tallenna-muiden-toiden-toteuma toteuma))]
        (log "tallennettu, palvelu vastasi: " (pr-str res))
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
  [:muutostyo :akillinen-hoitotyo :lisatyo])


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
                                                    (get-in m [:tehtava :toimenpidekoodi])
                                                    (:tyyppi m)
                                                    (:alkanut m)
                                                    (or (get-in m [:tehtava :paivanhinta])
                                                        (:maara m))))))
        tallennus-kaynnissa (atom false)
        tehtavat-tasoineen @u/urakan-toimenpiteet-ja-tehtavat
        toimenpideinstanssit @u/urakan-toimenpideinstanssit
        jarjestelman-lisaama-toteuma? false                 ;FIXME: tähän haettava arvo
        ]

    (komp/luo
      (fn []
        [:div.muun-tyon-tiedot
         [:button.nappi-toissijainen {:on-click #(reset! valittu-toteuma nil)}
          (ikonit/chevron-left) " Takaisin muiden töiden luetteloon"]
         (if (get-in @valittu-toteuma [:tehtava :id])
           [:h3 "Muokkaa toteumaa"]
           [:h3 "Luo uusi toteuma"])

         [lomake {:luokka   :horizontal
                  :voi-muokata? true ;fixme: ei voi jos koneen lisäämä toteuma. Katso ysk.hint. töiden tot. esimerkkitoteutus
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
            :valinta-nayta #(if (nil? %) +valitse-tyyppi+ (muun-tyon-tyypin-teksti %))
            :valinnat      +muun-tyon-tyypit+
            :validoi       [[:ei-tyhja "Anna kustannustyyppi"]]
            :leveys-col    3}
           {:otsikko       "Tehtävä" :nimi :tehtava
            :hae #(get-in % [:tehtava :toimenpidekoodi])
            :valinta-arvo  #(:id (nth % 3))
            :valinta-nayta #(if % (:nimi (nth % 3)) "- Valitse tehtävä -")
            :tyyppi        :valinta
            :valinnat-fn   #(urakan-toimenpiteet/toimenpideinstanssin-tehtavat
                             (get-in @muokattu [:toimenpideinstanssi :tpi_id])
                             toimenpideinstanssit tehtavat-tasoineen)
            :fmt           #(muun-tyon-tyypin-teksti %)
            :validoi       [[:ei-tyhja "Anna kustannustyyppi"]]
            :aseta        (fn [rivi arvo] (assoc-in rivi
                                            [:tehtava :toimenpidekoodi] arvo))
            :leveys-col    3}
           (when (get-in @muokattu [:tehtava :toimenpidekoodi])
             (lomake/ryhma
              "Tehtävän tarkemmat tiedot"
              ;; FIXME: lisää hinnoittelutyyppi päivän hinta tai muutoshintainen
              {:otsikko "Määrä" :nimi :maara :tyyppi :numero :varoita [[:ei-tyhja "Haluatko jättää määrän antamatta?"]] :leveys-col 3}
              {:otsikko       "Hinnoittelu" :nimi :hinnoittelu
               :tyyppi        :valinta
               :valinta-arvo  first
               :valinta-nayta second
               :valinnat      [[:yksikkohinta "Yksikköhinta"] [:paivanhinta "Päivän hinta"]]
               :leveys-col    3}
              ;; FIXME: jos on valittu :yksikkohinta-hinnoittelu, täytetään näkyviin suunnitelupuolelta
              ;; yksikköhinta. Jos sitä ei ole vielä syötetty, implisiittisesti syötetään se tässä näkymässä
              ;; ja tallennetaan muutoshintainen_tyo-tauluun, mistä se on jatkossa käytössä toteumille.
              (when (= (:hinnoittelu @muokattu) :paivanhinta)
                {:otsikko "Päivän hinta" :nimi :paivanhinta :tyyppi :numero :validoi [[:ei-tyhja "Anna rahamäärä"]] :leveys-col 3})
              {:otsikko "Aloitus" :nimi :alkanut :tyyppi :pvm :leveys-col 2 :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
               :aseta (fn [rivi arvo]
                        (assoc
                          (if
                            (or
                              (not (:paattynyt rivi))
                              (pvm/jalkeen? arvo (:paattynyt rivi)))
                            (assoc rivi :paattynyt arvo)
                            rivi)
                          :alkanut
                          arvo))
               :validoi [[:ei-tyhja "Valitse päivämäärä"]]
               :varoita [[:urakan-aikana]]}
              {:otsikko "Lopetus" :nimi :paattynyt :tyyppi :pvm
               :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
               :validoi [[:ei-tyhja "Valitse päivämäärä"]
                         [:pvm-kentan-jalkeen :alkanut "Lopetuksen pitää olla aloituksen jälkeen"]]
               :leveys-col 2}
              {:otsikko "Suorittaja" :nimi :suorittajan-nimi :tyyppi :string :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}
              {:otsikko "Suorittajan Y-tunnus" :nimi :suorittajan-ytunnus :tyyppi :string :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}
              {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :text :pituus-max 1024
               :placeholder "Kirjoita tähän lisätietoa" :koko [80 :auto]}
              ))

           ]

          @muokattu]]))))

(defn muut-tyot-toteumalistaus
  "Muiden töiden toteumat"
  []
  (let [urakka @nav/valittu-urakka

        valitut-muut-tyot (reaction (let [toimenpideinstanssi @u/valittu-toimenpideinstanssi]
                    (reverse (sort-by :alkanut (filter #(= (get-in % [:tehtava :emo])
                                                           (:id toimenpideinstanssi))
                                                       @u/muut-tyot-hoitokaudella)))))
        tyorivit
        (reaction
          (map (fn [muu-tyo]
                 (let [muutoshintainen-tyo
                       (first (filter (fn [muutoshinta]
                                        (= (:tehtava muutoshinta)
                                           (get-in muu-tyo [:tehtava :toimenpidekoodi]))) @u/muutoshintaiset-tyot))]
                   (assoc muu-tyo
                     :hinnoittelu (if (get-in muu-tyo [:tehtava :paivanhinta]) :paivanhinta :yksikkohinta)
                     :yksikko (:yksikko muutoshintainen-tyo)
                     :yksikkohinta (:yksikkohinta muutoshintainen-tyo))))
               @valitut-muut-tyot))]
(tarkkaile! "työrivit" tyorivit)
    (komp/luo
      (fn []
        (let [aseta-rivin-luokka (aseta-rivin-luokka @korostettavan-rivin-id)]
          [:div.muut-tyot-toteumat
           [:div "Ominaisuus kehityksen alla - ethän raportoi bugeja, kiitos."]
           [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide urakka]
           [:button.nappi-ensisijainen {:on-click #(reset! valittu-toteuma {})}
            (ikonit/plus-sign) " Lisää toteuma"]

           [grid/grid
            {:otsikko       (str "Toteutuneet muutos-, lisä- ja äkilliset hoitotyöt ")
             :tyhja         (if (nil? @valitut-muut-tyot)
                              [ajax-loader "Toteumia haetaan..."]
                              "Ei toteumia saatavilla.")
             :rivi-klikattu #(reset! valittu-toteuma %)
             ;:rivin-luokka  #(aseta-rivin-luokka %)
             :tunniste      #(get-in % [:tehtava :id])}
            [{:otsikko "Pvm" :tyyppi :pvm :fmt pvm/pvm :nimi :alkanut :leveys "10%"}
             {:otsikko "Tyyppi" :nimi :tyyppi :fmt muun-tyon-tyypin-teksti :leveys "15%"}
             {:otsikko "Tehtävä" :tyyppi :string :nimi :tehtavan_nimi
              :hae     #(get-in % [:tehtava :nimi]) :leveys "25%"}
             {:otsikko "Määrä" :tyyppi :string :nimi :maara
              :leveys "10%"}
             {:otsikko "Yksikkö"
              :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "10%"}
             {:otsikko "Yksikköhinta" :nimi :yksikkohinta :tasaa :oikea :tyyppi :numero
              :hae #(if (get-in % [:tehtava :paivanhinta])
                     nil
                     (:yksikkohinta %))
              :muokattava? (constantly false) :fmt fmt/euro-opt :leveys "10%"}
             {:otsikko "Hinnoittelu" :tyyppi :string :nimi :hinnoittelu
              :hae     #(if (get-in % [:tehtava :paivanhinta]) "Päivän hinta" "Yksikköhinta") :leveys "10%"}
             {:otsikko "Kustannus (€)" :tyyppi :string :nimi :kustannus :tasaa :oikea
              :hae     #(if (get-in % [:tehtava :paivanhinta])
                         (get-in % [:tehtava :paivanhinta])
                         (if (and (:maara %) (:yksikkohinta %))
                           (* (:maara %) (:yksikkohinta %))
                           "Ei voi laskea"))
              :fmt     fmt/euro-opt :leveys "10%"}

             ]
            @tyorivit
             ]])
        ))))



(defn muut-tyot-toteumat []
  (if @valittu-toteuma
    [toteutuneen-muun-tyon-muokkaus]
    [muut-tyot-toteumalistaus]))