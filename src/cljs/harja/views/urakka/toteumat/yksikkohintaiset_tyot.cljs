(ns harja.views.urakka.toteumat.yksikkohintaiset-tyot
  "Urakan 'Toteumat' välilehden Yksikköhintaist työt osio"
  (:require [reagent.core :refer [atom]]
            [harja.ui.grid :as grid]
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
            [harja.loki :refer [log logt tarkkaile!]]

            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce lomakkeessa-muokattava-toteuma (atom nil))

(defn tallenna-toteuma [lomakkeen-toteuma lomakkeen-tehtavat]
  (let [schema-toteuma (->
                         (assoc lomakkeen-toteuma
                           :tyyppi :yksikkohintainen
                           :urakka-id (:id @nav/valittu-urakka)
                           :sopimus-id (first @u/valittu-sopimusnumero)
                           :tehtavat lomakkeen-tehtavat))]
    (log "TOT Tallennetaan toteuma: " (pr-str schema-toteuma))
    (toteumat/tallenna-toteuma-ja-yksikkohintaiset-tehtavat schema-toteuma)))

(defn tehtavat-ja-maarat [tehtavat]
  (let [tehtavat-tasoineen @u/urakan-toimenpiteet-ja-tehtavat
        nelostason-tehtavat (map #(nth % 3) tehtavat-tasoineen)
        toimenpideinstanssit @u/urakan-toimenpideinstanssit]

    (tarkkaile! "TOT Tehtävät-atomi:" tehtavat)
    (log "TOT Toimenpideinstanssit " (pr-str toimenpideinstanssit))

    [grid/muokkaus-grid
     {:tyhja "Ei töitä."}
     [{:otsikko       "Toimenpide" :nimi :toimenpideinstanssi
       :tyyppi        :valinta
       :fmt           #(:tpi_nimi (urakan-toimenpiteet/toimenpideinstanssi-idlla % toimenpideinstanssit))
       :valinta-arvo  :tpi_id
       :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
       :valinnat      toimenpideinstanssit
       :leveys "25%"
       :aseta         #(assoc %1 :toimenpideinstanssi %2
                                 :tehtava nil)}
      {:otsikko       "Tehtävä" :nimi :tehtava
       :valinta-arvo  #(:id (nth % 3))
       :valinta-nayta #(if % (:nimi (nth % 3)) "- Valitse tehtävä -")
       :tyyppi        :valinta
       :valinnat-fn   #(urakan-toimenpiteet/toimenpideinstanssin-tehtavat
                        (:toimenpideinstanssi %)
                        toimenpideinstanssit tehtavat-tasoineen)
       :leveys        "45%"
       :aseta         (fn [rivi arvo] (assoc rivi
                                        :tehtava arvo
                                        :yksikko (:yksikko (urakan-toimenpiteet/tehtava-idlla arvo nelostason-tehtavat))))}
      {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys "40%"}
      {:otsikko "Yks." :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "5%"}]
     tehtavat]))

(defn yksikkohintaisen-toteuman-muokkaus
  "Uuden toteuman syöttäminen"
  []
  (let [lomake-toteuma (atom @lomakkeessa-muokattava-toteuma)
        lomake-tehtavat (atom (into {}
                                    (map (fn [[id tehtava]]
                                           [id (assoc tehtava :tehtava
                                                              (:id (:tehtava tehtava)))])
                                         (:tehtavat @lomakkeessa-muokattava-toteuma))))
        valmis-tallennettavaksi? (reaction
                                   (and
                                     ; Validoi toteuma
                                     (not (nil? (:aloituspvm @lomake-toteuma)))
                                     (not (nil? (:lopetuspvm @lomake-toteuma)))
                                     (not (pvm/ennen? (:lopetuspvm @lomake-toteuma) (:aloituspvm @lomake-toteuma)))
                                     (pvm/valissa? (:aloituspvm @lomake-toteuma) (first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi))
                                     (not (empty? (:suorittajan-nimi @lomake-toteuma)))
                                     (not (empty? (:suorittajan-ytunnus @lomake-toteuma)))
                                     ; Validoi tehtävät
                                     (not (empty? (filter #(not (true? (:poistettu %))) (vals @lomake-tehtavat))))
                                     (nil? (some #(nil? (:tehtava %)) (filter #(not (true? (:poistettu %))) (vals @lomake-tehtavat))))
                                     (nil? (some #(not (integer? (:maara %))) (filter #(not (true? (:poistettu %))) (vals @lomake-tehtavat))))))]

    (log "TOT Lomake-toteuma: " (pr-str @lomake-toteuma))
    (log "TOT Lomake tehtävät: " (pr-str @lomake-tehtavat))
    (komp/luo
      (fn [ur]
        [:div.toteuman-tiedot
         [:button.nappi-toissijainen {:on-click #(reset! lomakkeessa-muokattava-toteuma nil)}
          (ikonit/chevron-left) " Takaisin toteumaluetteloon"]
         (if (:toteuma-id @lomakkeessa-muokattava-toteuma)
           [:h3 "Muokkaa toteumaa"]
           [:h3 "Luo uusi toteuma"])

         [lomake {:luokka :horizontal
                  :muokkaa! (fn [uusi]
                              (log "TOT Muokataan toteumaa: " (pr-str uusi))
                              (reset! lomake-toteuma uusi))
                  :footer   [harja.ui.napit/palvelinkutsu-nappi
                             "Tallenna toteuma"
                             #(tallenna-toteuma @lomake-toteuma (mapv
                                                                  (fn [rivi]
                                                                    {:toimenpidekoodi (:tehtava rivi)
                                                                     :maara (js/parseInt (:maara rivi))
                                                                     :tehtava-id (:tehtava-id rivi)
                                                                     :poistettu (:poistettu rivi)})
                                                                  (vals @lomake-tehtavat)))
                             {:luokka "nappi-ensisijainen"
                              :disabled (false? @valmis-tallennettavaksi?)
                              :kun-onnistuu #(do
                                              (reset! lomake-tehtavat nil)
                                              (reset! lomake-toteuma nil)
                                              (reset! lomakkeessa-muokattava-toteuma nil))}]
                  }
          [{:otsikko "Sopimus" :nimi :sopimus :hae (fn [_] (second @u/valittu-sopimusnumero)) :muokattava? (constantly false)}
           {:otsikko "Hoitokausi" :nimi :hoitokausi :hae (fn [_]
                                                           (let [[alku loppu] @u/valittu-hoitokausi]
                                                             [:span (pvm/pvm alku) " \u2014 " (pvm/pvm loppu)]))
            :fmt identity
            :muokattava? (constantly false)}
           {:otsikko "Aloitus" :nimi :aloituspvm :tyyppi :pvm :leveys-col 2 :aseta (fn [rivi arvo]
                                                                       (assoc
                                                                         (if
                                                                           (or
                                                                             (not (:lopetuspvm rivi))
                                                                             (pvm/jalkeen? arvo (:lopetuspvm rivi)))
                                                                           (assoc rivi :lopetuspvm arvo)
                                                                           rivi)
                                                                         :aloituspvm
                                                                         arvo)) :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                                                                         [:hoitokaudella "Toteuman pitää olla hoitokaudella"]]}
           {:otsikko "Lopetus" :nimi :lopetuspvm :tyyppi :pvm :validoi [[:ei-tyhja "Valitse päivämäärä"]] :leveys-col 2}
           {:otsikko "Suorittaja" :nimi :suorittajan-nimi :tyyppi :string  :validoi [[:ei-tyhja "Kirjoita suorittaja"]]}
           {:otsikko "Suorittajan Y-tunnus" :nimi :suorittajan-ytunnus :tyyppi :string  :validoi [[:ei-tyhja "Kirjoita suorittajan y-tunnus"]]}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :text :koko [80 :auto]}
           {:otsikko "Tehtävät" :nimi :tehtavat :leveys "20%" :tyyppi :komponentti :komponentti [tehtavat-ja-maarat lomake-tehtavat]}]
          @lomake-toteuma]]))))

(defn yksiloidyt-tehtavat [rivi toteumat]
  (let [urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        toteumat toteumat
        aikavali [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]
        toteutuneet-tehtavat (reaction<! (toteumat/hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla urakka-id sopimus-id aikavali :yksikkohintainen (:id rivi)))]
    (fn [toteuma-rivi]
      [:div
       [grid/grid
        {:otsikko     (str "Yksilöidyt tehtävät: " (:nimi toteuma-rivi))
         :tyhja       (if (nil? @toteutuneet-tehtavat) [ajax-loader "Haetaan..."] "Toteumia ei löydy")
         :tallenna    #(go (let [vastaus (<! (toteumat/paivita-yk-hint-toteumien-tehtavat urakka-id sopimus-id aikavali :yksikkohintainen %))]
                             (log "TOT Tehtävät tallennettu: " (pr-str vastaus))
                             (reset! toteutuneet-tehtavat (:tehtavat vastaus))
                             (reset! toteumat (:toteumat vastaus))))
         :voi-lisata? false
         :tunniste    :tehtava_id
         :luokat ["toteumat-haitari"]}
        [{:otsikko "Päivämäärä" :nimi :alkanut :muokattava? (constantly false) :tyyppi :pvm :hae (comp pvm/pvm :alkanut) :leveys "20%"}
         {:otsikko "Määrä" :nimi :maara :muokattava? (constantly true) :tyyppi :numero :leveys "20%"}
         {:otsikko "Suorittaja" :nimi :suorittajan_nimi :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
         {:otsikko "Lisätieto" :nimi :lisatieto :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
         {:otsikko "Tarkastele koko toteumaa" :nimi :tarkastele-toteumaa :muokattava? (constantly false) :tyyppi :komponentti :leveys "20%"
          :komponentti (fn [rivi] [:button.nappi-toissijainen {:on-click
                                                               #(go (let [toteuma (<! (toteumat/hae-urakan-toteuma urakka-id (:toteuma_id rivi)))]
                                                                      (log "TOT toteuma: " (pr-str toteuma)
                                                                           (let [lomake-tiedot {:toteuma-id       (:id toteuma)
                                                                                                :tehtavat         (zipmap (iterate inc 1)
                                                                                                                          (mapv (fn [tehtava]
                                                                                                                                  (let [tehtava-urakassa (get (first (filter (fn [tehtavat]
                                                                                                                                                                  (= (:id (get tehtavat 3)) (:tpk-id tehtava)))
                                                                                                                                                                @u/urakan-toimenpiteet-ja-tehtavat)) 3)
                                                                                                                                        emo (get (first (filter (fn [tehtavat]
                                                                                                                                                                  (= (:id (get tehtavat 3)) (:tpk-id tehtava)))
                                                                                                                                                                @u/urakan-toimenpiteet-ja-tehtavat)) 2)
                                                                                                                                        tpi (first (filter (fn [tpi] (= (:t3_koodi tpi) (:koodi emo))) @u/urakan-toimenpideinstanssit))]
                                                                                                                                    (log "TOT Toteuman 4. tason tehtävän 3. tason emo selvitetty: " (pr-str emo))
                                                                                                                                    (log "TOT Toteuman 4. tason tehtävän toimenpideinstanssi selvitetty: " (pr-str tpi))
                                                                                                                                    {
                                                                                                                                     :tehtava {:id (:tpk-id tehtava)}
                                                                                                                                     :maara (:maara tehtava)
                                                                                                                                     :tehtava-id (:tehtava-id tehtava)
                                                                                                                                     :toimenpideinstanssi (:tpi_id tpi)
                                                                                                                                     :yksikko (:yksikko tehtava-urakassa)
                                                                                                                                     }))
                                                                                                                                (:tehtavat toteuma)))
                                                                                                :aloituspvm       (:alkanut toteuma)
                                                                                                :lopetuspvm       (:paattynyt toteuma)
                                                                                                :lisatieto        (:lisatieto toteuma)
                                                                                                :suorittajan-nimi (:suorittajan_nimi toteuma)
                                                                                                :suorittajan-ytunnus (:suorittajan_ytunnus toteuma)}]
                                                                             (log "Toteuma-data lomakkeelle: " (pr-str lomakkeessa-muokattava-toteuma))
                                                                             (reset! lomakkeessa-muokattava-toteuma lomake-tiedot)))))}
                                   (ikonit/eye-open) " Toteuma"])}]
        (sort
          (fn [eka toka] (pvm/ennen? (:alkanut eka) (:alkanut toka)))
          (filter (fn [tehtava] (= (:toimenpidekoodi tehtava) (:id toteuma-rivi))) @toteutuneet-tehtavat))]])))

(defn yksikkohintaisten-toteumalistaus
  "Yksikköhintaisten töiden toteumat"
  []
  (let [valittu-aikavali (reaction @u/valittu-hoitokausi)
        toteumat (reaction<! (let [valittu-urakka-id (:id @nav/valittu-urakka)
                                   [valittu-sopimus-id _] @u/valittu-sopimusnumero]
                               (toteumat/hae-urakan-toteumat valittu-urakka-id valittu-sopimus-id @valittu-aikavali :yksikkohintainen)))
        muodosta-nelostason-tehtavat (fn []
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
                                                                                                                     toteumat)))))
                                               rivit))
        lisaa-tyoriveille-toteutuneet-kustannukset (fn [rivit]
                                                     (map
                                                       (fn [rivi] (assoc rivi :hoitokauden-toteutuneet-kustannukset (* (:yksikkohinta rivi) (:hoitokauden-toteutunut-maara rivi))))
                                                       rivit))
        lisaa-tyoriveille-erotus (fn [rivit] (map
                                               (fn [rivi] (assoc rivi :kustannuserotus (- (:hoitokauden-suunnitellut-kustannukset rivi) (:hoitokauden-toteutuneet-kustannukset rivi))))
                                               rivit))
        tyorivit (reaction
                   (let [rivit (muodosta-nelostason-tehtavat)
                         valittu-urakka @nav/valittu-urakka
                         valittu-sopimus @u/valittu-sopimusnumero
                         valittu-hoitokausi [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]
                         valittu-aikavali @u/valittu-aikavali
                         toteumat @toteumat]

                     ; TODO Jos/kun halutaan valita tarkempi aikaväli, tee SQL-kysely joka palauttaa suunnitelmat tietyltä aikaväliltä. Frontti laskee jokaiselle tehtävälle suunnitellun summan.
                     ; TODO Nyt back palauttaa kaikki toteumat, joista frontti laske toteuman summan jokaiselle tehtävälle. Tee mieluummin kysely, joka palauttaa summat tehtävittäin valmiiksi kannasta.
                     (when toteumat
                       (-> (lisaa-tyoriveille-yksikkohinta rivit valittu-hoitokausi)
                           (lisaa-tyoriveille-suunniteltu-maara valittu-hoitokausi)
                           (lisaa-tyoriveille-suunnitellut-kustannukset valittu-hoitokausi)
                           (lisaa-tyoriveille-toteutunut-maara toteumat)
                           (lisaa-tyoriveille-toteutuneet-kustannukset)
                           (lisaa-tyoriveille-erotus)))))]

    (komp/luo
      (fn []
        [:div
         [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide @nav/valittu-urakka]

         [:button.nappi-ensisijainen {:on-click #(reset! lomakkeessa-muokattava-toteuma {})}
          (ikonit/plus-sign) " Lisää toteuma"]

         [grid/grid
          {:otsikko (str "Yksikköhintaisten töiden toteumat: " (:t2_nimi @u/valittu-toimenpideinstanssi) " / " (:t3_nimi @u/valittu-toimenpideinstanssi) " / " (:tpi_nimi @u/valittu-toimenpideinstanssi))
           :tyhja (if (nil? @tyorivit) [ajax-loader "Haetaan yksikköhintaisten töiden toteumia..."] "Ei yksikköhintaisten töiden toteumia")
           :luokat ["toteumat-paasisalto"]
           :vetolaatikot (into {} (map (juxt :id (fn [rivi] [yksiloidyt-tehtavat rivi toteumat])) (filter (fn [rivi] (> (:hoitokauden-toteutunut-maara rivi) 0)) @tyorivit)))}
          [{:tyyppi :vetolaatikon-tila :leveys "5%"}
           {:otsikko "Tehtävä" :nimi :nimi :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Yksikkö" :nimi :yksikko :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Yksikköhinta" :nimi :yksikkohinta :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Suunniteltu määrä" :nimi :hoitokauden-suunniteltu-maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Toteutunut määrä" :nimi :hoitokauden-toteutunut-maara :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Suunnitellut kustannukset" :nimi :hoitokauden-suunnitellut-kustannukset :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Toteutuneet kustannukset" :nimi :hoitokauden-toteutuneet-kustannukset :fmt fmt/euro-opt :muokattava? (constantly false) :tyyppi :numero :leveys "20%"}
           {:otsikko "Budjettia jäljellä" :nimi :kustannuserotus :muokattava? (constantly false) :tyyppi :komponentti :komponentti
                     (fn [rivi] (if (>= (:kustannuserotus rivi) 0)
                                  [:span.kustannuserotus.kustannuserotus-positiivinen (fmt/euro-opt (:kustannuserotus rivi))]
                                  [:span.kustannuserotus.kustannuserotus-negatiivinen (fmt/euro-opt (:kustannuserotus rivi))])) :leveys "20%"}]
          (filter
            (fn [rivi] (and (= (:t3_koodi rivi) (:t3_koodi @u/valittu-toimenpideinstanssi))
                            (or
                              (> (:hoitokauden-toteutunut-maara rivi) 0)
                              (> (:hoitokauden-suunniteltu-maara rivi) 0))))
            @tyorivit)]]))))

(defn yksikkohintaisten-toteumat []
  (if @lomakkeessa-muokattava-toteuma
    [yksikkohintaisen-toteuman-muokkaus]
    [yksikkohintaisten-toteumalistaus]))