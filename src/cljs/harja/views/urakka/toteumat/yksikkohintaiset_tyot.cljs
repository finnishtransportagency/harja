(ns harja.views.urakka.toteumat.yksikkohintaiset-tyot
  "Urakan 'Toteumat' välilehden Yksikköhintaist työt osio"
  (:require [harja.ui.grid :as grid]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader kuuntelija linkki sisalla? raksiboksi
                                      livi-pudotusvalikko]]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.views.urakka.toteumat.lampotilat :refer [lampotilat]]
            [harja.pvm :as pvm]

            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt]]

            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce valittu-toteuma (atom nil))

(defn tallenna-toteuma [toteuma tehtavat]
  (let [toteuma (assoc toteuma
                  :urakka-id (:id @nav/valittu-urakka)
                  :sopimus-id (first @u/valittu-sopimusnumero)
                  :tehtavat (mapv (fn [tehtava]
                                    (log "TEHTAVA: " (pr-str tehtava))
                                    {:toimenpidekoodi (:id (:toimenpidekoodi tehtava))
                                     :maara (js/parseFloat (:maara tehtava))})
                                  tehtavat))]
    (toteumat/tallenna-toteuma toteuma)))

(defn yksikkohintaisen-toteuman-muokkaus
  "Uuden toteuman syöttäminen"
  []
  (let [muokattu (atom @valittu-toteuma)
        tehtavat (atom {})
        materiaalit (atom {})
        toimenpiteen-tehtavat (reaction (map #(nth % 3)
                                             (filter (fn [[t1 t2 t3 t4]]
                                                       (= (:koodi t3) (:t3_koodi @u/valittu-toimenpideinstanssi)))
                                                     @u/urakan-toimenpiteet-ja-tehtavat)))
        tallennus-kaynnissa (atom false)]

    (log "@toimenpiteen-tehtavat" (pr-str @toimenpiteen-tehtavat))
    (komp/luo
      (fn [ur]
        [:div.toteuman-tiedot
         [:button.nappi-toissijainen {:on-click #(reset! valittu-toteuma nil)}
          (ikonit/chevron-left) " Takaisin toteumaluetteloon"]
         (if (:id @valittu-toteuma)
           [:h3 "Muokkaa toteumaa"]
           [:h3 "Luo uusi toteuma"])

         [lomake {:luokka :horizontal
                  :muokkaa! (fn [uusi]
                              (log "MUOKATAAN " (pr-str uusi))
                              (reset! muokattu uusi))
                  :footer [:button.nappi-ensisijainen
                           {:class (when @tallennus-kaynnissa "disabled")
                            :on-click
                                   #(do (.preventDefault %)
                                        (reset! tallennus-kaynnissa true)
                                        (go (let [res (<! (tallenna-toteuma @muokattu (vals @tehtavat)))]
                                              (if res
                                                ;; Tallennus ok
                                                (do (viesti/nayta! "Toteuma tallennettu")
                                                    ; FIXME Pitäisikö asettaa tallennus-kaynnissa false? -Jari
                                                    (reset! valittu-toteuma nil))

                                                ;; Epäonnistui jostain syystä
                                                (reset! tallennus-kaynnissa false)))))}
                           "Tallenna toteuma"]
                  }
          [{:otsikko "Sopimus" :nimi :sopimus :hae (fn [_] (second @u/valittu-sopimusnumero)) :muokattava? (constantly false)}

           {:otsikko "Hoitokausi" :nimi :hoitokausi :hae (fn [_]
                                                           (let [[alku loppu] @u/valittu-hoitokausi]
                                                             [:span (pvm/pvm alku) " \u2014 " (pvm/pvm loppu)]))
            :fmt identity
            :muokattava? (constantly false)}

           {:otsikko "Toimenpide" :nimi :toimenpide :hae (fn [_] (:tpi_nimi @u/valittu-toimenpideinstanssi)) :muokattava? (constantly false)}

           {:otsikko       "Tehtävä " :nimi :tehtava :leveys "20%"
            :tyyppi        :valinta :valinta-arvo identity
            :leveys-col 4
            :valinta-nayta #(if (nil? %) "Valitse tehtävä" (:nimi %))
            :valinnat      @toimenpiteen-tehtavat}
           {:otsikko "Toteutunut pvm" :nimi :toteutunut-pvm :tyyppi :pvm :leveys-col 2}
           ;; fixme: alas valitun tehtävän yksikkö toteutuneen määrän jälkeen näkyviin
           {:otsikko "Toteutunut määrä" :nimi :toteutunut-maara :tyyppi :numero :leveys-col 2}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :string :leveys "20%"}
           ]

          @muokattu]]))))

(defn tehtavan-toteumat [rivi]
  (let [urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        aikavali [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]
        toimenpidekoodi (:id rivi)
        tehtavat-toimenpiteittain (reaction<! (toteumat/hae-urakan-tehtavat-toteumittain urakka-id sopimus-id aikavali toimenpidekoodi))]

    (fn [rivi]
      [:div.tehtavat-toteumittain
       [grid/grid
        {:tyhja (if (nil? @tehtavat-toimenpiteittain) [ajax-loader "Haetaan..."] "Toteumia ei löydy")}
        [{:otsikko "Päivämäärä" :nimi :alkanut :muokattava? (constantly false) :tyyppi :komponentti :komponentti (fn [rivi] (pvm/pvm-aika (:alkanut rivi))) :leveys "25%"}
         {:otsikko "Määrä" :nimi :maara :muokattava? (constantly false) :tyyppi :numero :leveys "25%"}
         {:otsikko "Suorittaja" :nimi :suorittajan_nimi :muokattava? (constantly false) :tyyppi :string :leveys "25%"}
         {:otsikko "Lisätieto" :nimi :lisatieto :muokattava? (constantly false) :tyyppi :string :leveys "25%"}]
        (sort
          (fn [eka toka] (pvm/ennen? (:alkanut eka) (:alkanut toka)))
          (filter
            (fn [rivi] (= (:tyyppi rivi) "yksikkohintainen"))
            @tehtavat-toimenpiteittain))]])))

(defn yksikkohintaisten-toteumalistaus
  "Yksikköhintaisten töiden toteumat"
  []
  (let [toteumat (reaction nil) ; Mappi, jossa avaimina :aikavali (vector, mille aikavälille toteumat haettiin) ja :vastaus (backendin vastaus)
        paivita-toteumat (fn []
                           "Päivittää toteumat, jos asetukset ovat muuttuneet ja kannassa on uutta dataa saatavilla."
                           (let [valittu-urakka-id (:id @nav/valittu-urakka)
                                 [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                 valittu-aikavali [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]]
                             (if (or
                                   (nil? @toteumat)
                                   (not (= (:aikavali @toteumat) valittu-aikavali)))
                               (go
                                 (let [uudet-toteumat (<! (toteumat/hae-urakan-toteumat valittu-urakka-id valittu-sopimus-id valittu-aikavali))]
                                   (if (not (= uudet-toteumat @toteumat))
                                     (reset! toteumat {:aikavali valittu-aikavali :vastaus uudet-toteumat})))))))
        hae-nelostason-tehtavat (fn []
                                  "Hakee urakan nelostason tehtävät ja lisää niihin emon koodin."
                                  (map
                                    (fn [tasot] (let [kolmostaso (nth tasot 2)
                                                      nelostaso (nth tasot 3)]
                                                  (assoc nelostaso :t3_koodi (:koodi kolmostaso))))
                                    @u/urakan-toimenpiteet-ja-tehtavat))
        lisaa-tyoriveille-yksikkohinta (fn [rivit valittu-hoitokausi] (map
                                                                        (fn [rivi] (assoc rivi :yksikkohinta
                                                                                               (or (:yksikkohinta (first (filter
                                                                                                                           (fn [tyo] (and (= (:tehtava tyo) (:id rivi))
                                                                                                                                          (pvm/sama-pvm? (:alkupvm tyo) (first valittu-hoitokausi))))
                                                                                                                           @u/urakan-yks-hint-tyot))) 0)))
                                                                        rivit))
        lisaa-tyoriveille-suunniteltu-maara (fn [rivit valittu-hoitokausi] (map
                                                                             (fn [rivi] (assoc rivi :hoitokauden-suunniteltu-maara
                                                                                                    (or (:maara (first (filter
                                                                                                                         (fn [tyo] (and (= (:tehtava tyo) (:id rivi))
                                                                                                                                        (pvm/sama-pvm? (:alkupvm tyo) (first valittu-hoitokausi))))
                                                                                                                         @u/urakan-yks-hint-tyot))) 0)))
                                                                             rivit))
        lisaa-tyoriveille-suunnitellut-kustannukset (fn [rivit valittu-hoitokausi]
                                                      (map
                                                        (fn [rivi] (assoc rivi :hoitokauden-suunnitellut-kustannukset
                                                                               (or (:yhteensa (first (filter
                                                                                                       (fn [tyo] (and (= (:tehtava tyo) (:id rivi))
                                                                                                                      (pvm/sama-pvm? (:alkupvm tyo) (first valittu-hoitokausi))))
                                                                                                       @u/urakan-yks-hint-tyot))) 0)))
                                                        rivit))
        lisaa-tyoriveille-toteutunut-maara (fn [rivit toteumat]
                                             (map
                                               (fn [rivi] (assoc rivi :hoitokauden-toteutunut-maara (reduce + (flatten
                                                                                                                (map (fn [toteuma]
                                                                                                                       (map (fn [tehtava]
                                                                                                                              (if (= (:tpk-id tehtava) (:id rivi))
                                                                                                                                (:maara tehtava)
                                                                                                                                0))
                                                                                                                            (:tehtavat toteuma)))
                                                                                                                     (filter (fn [toteuma] (= (:tyyppi toteuma) "yksikkohintainen")) toteumat))))))
                                               rivit))
        lisaa-tyoriveille-toteutuneet-kustannukset (fn [rivit]
                                                     (map
                                                       (fn [rivi] (assoc rivi :hoitokauden-toteutuneet-kustannukset (* (:yksikkohinta rivi) (:hoitokauden-toteutunut-maara rivi))))
                                                       rivit))
        lisaa-tyoriveille-erotus (fn [rivit] (map
                                               (fn [rivi] (assoc rivi :kustannuserotus (- (:hoitokauden-suunnitellut-kustannukset rivi) (:hoitokauden-toteutuneet-kustannukset rivi))))
                                               rivit))
        tyorivit (reaction
                   (let [rivit (hae-nelostason-tehtavat)
                         valittu-urakka @nav/valittu-urakka
                         valittu-sopimus @u/valittu-sopimusnumero
                         valittu-hoitokausi @u/valittu-hoitokausi
                         valittu-aikavali [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]]

                     (paivita-toteumat)
                     ; Jos toteumia ei ole vielä saatu valitulle hoitokaudelle, palauttaa nil.
                     ; Tämä reaction body ajetaan automaattisesti uudelleen kun toteumat saadaan.
                     (if (and (not (nil? @toteumat))
                              (= (:aikavali @toteumat) valittu-aikavali))
                       (-> (lisaa-tyoriveille-yksikkohinta rivit valittu-hoitokausi)
                           (lisaa-tyoriveille-suunniteltu-maara valittu-hoitokausi)
                           (lisaa-tyoriveille-suunnitellut-kustannukset valittu-hoitokausi)
                           (lisaa-tyoriveille-toteutunut-maara (:vastaus @toteumat))
                           (lisaa-tyoriveille-toteutuneet-kustannukset)
                           (lisaa-tyoriveille-erotus)))))]

    (komp/luo
      (fn []
        [:div.yksikkohintaisten-toteumat
         [:div  "Tämä toiminto on keskeneräinen. Älä raportoi bugeja."]
         [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide @nav/valittu-urakka]

         [grid/grid
          {:otsikko (str "Yksikköhintaisten töiden toteumat: " (:t2_nimi @u/valittu-toimenpideinstanssi) " / " (:t3_nimi @u/valittu-toimenpideinstanssi) " / " (:tpi_nimi @u/valittu-toimenpideinstanssi))
           :tyhja (if (nil? @tyorivit) [ajax-loader "Haetaan yksikköhintaisten töiden toteumia..."] "Ei yksikköhintaisten töiden toteumia")
           :vetolaatikot (into {} (map (juxt :id (fn [rivi] [tehtavan-toteumat rivi])) (filter (fn [rivi] (> (:hoitokauden-toteutunut-maara rivi) 0)) @tyorivit)))}
          [{:tyyppi :vetolaatikon-tila :leveys "5%"}
           {:otsikko "Tehtävä" :nimi :nimi :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Yksikkö" :nimi :yksikko :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Yksikköhinta" :nimi :yksikkohinta :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Suunniteltu määrä" :nimi :hoitokauden-suunniteltu-maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Toteutunut määrä" :nimi :hoitokauden-toteutunut-maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Suunnitellut kustannukset" :nimi :hoitokauden-suunnitellut-kustannukset :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Toteutuneet kustannukset" :nimi :hoitokauden-toteutuneet-kustannukset :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Kustannuserotus" :nimi :kustannuserotus :muokattava? (constantly false) :tyyppi :komponentti :komponentti
                     (fn [rivi] (if (>= (:kustannuserotus rivi) 0)
                                  [:span.kustannuserotus.kustannuserotus-positiivinen (:kustannuserotus rivi)]
                                  [:span.kustannuserotus.kustannuserotus-negatiivinen (:kustannuserotus rivi)])) :leveys "20%"}]
          (filter
            (fn [rivi] (= (:t3_koodi rivi) (:t3_koodi @u/valittu-toimenpideinstanssi)))
            @tyorivit)]
         [:button.nappi-ensisijainen {:on-click #(reset! valittu-toteuma {})}
          (ikonit/plus-sign) " Lisää toteuma"]] ))))

(defn yksikkohintaisten-toteumat []
  (if @valittu-toteuma
    [yksikkohintaisen-toteuman-muokkaus]
    [yksikkohintaisten-toteumalistaus]))