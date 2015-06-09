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
            [harja.fmt :as fmt])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce lomakkeessa-muokattava-toteuma (atom nil))

(defn tallenna-toteuma [lomakkeen-toteuma lomakkeen-tehtavat]
  (let [schema-toteuma (->
                  (assoc lomakkeen-toteuma
                  :alkanut (:toteutunut-pvm lomakkeen-toteuma)
                  :paattynyt (:toteutunut-pvm lomakkeen-toteuma)
                  :tyyppi :yksikkohintainen
                  :urakka-id (:id @nav/valittu-urakka)
                  :sopimus-id (first @u/valittu-sopimusnumero)
                  :tehtavat lomakkeen-tehtavat
                  :materiaalit [])
                  (dissoc :toteutunut-pvm))]
    (log "TOT Tallennetaan toteuma: " (pr-str schema-toteuma))
    (toteumat/tallenna-toteuma schema-toteuma)))

(defn tehtavat-ja-maarat [tehtavat]
  (let [toimenpiteen-tehtavat (reaction (map #(nth % 3)
                                             (filter (fn [[t1 t2 t3 t4]]
                                                       (= (:koodi t3) (:t3_koodi @u/valittu-toimenpideinstanssi)))
                                                     @u/urakan-toimenpiteet-ja-tehtavat)))]
    [grid/muokkaus-grid
     {:tyhja "Ei töitä."}
     [{:otsikko "Tehtävät" :nimi :tehtava :tyyppi :valinta
       :valinnat @toimenpiteen-tehtavat
       :valinta-nayta #(if % (:nimi %) "- valitse tehtävä -")
       :validoi [[:ei-tyhja "Valitse tehtävä."]]
       :leveys "50%"}

      {:otsikko "Määrä" :nimi :maara :tyyppi :string :leveys "40%"}
      {:otsikko "Yks." :muokattava? (constantly false) :nimi :yksikko :hae (comp :yksikko :tehtava) :leveys "5%"}]
     tehtavat]))

(defn yksikkohintaisen-toteuman-muokkaus
  "Uuden toteuman syöttäminen"
  []
  (let [lomake-toteuma (atom @lomakkeessa-muokattava-toteuma)
        lomake-tehtavat (atom (:tehtavat @lomakkeessa-muokattava-toteuma))
        valmis-tallennettavaksi? (reaction
                                   (and ;(not (empty? (:toteutunut-pvm @lomake-toteuma))) FIXME pvm:ää ei voi valita jos tämä on tässä
                                        (not (empty? (:suorittajan-nimi @lomake-toteuma)))
                                        (not (empty? (vals @lomake-tehtavat)))))]

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
                                                                        {:toimenpidekoodi (:id (:tehtava rivi))
                                                                         :maara (js/parseInt (:maara rivi))})
                                                                      (vals @lomake-tehtavat)))
                             {:luokka :nappi-ensisijainen :disabled (false? @valmis-tallennettavaksi?)
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

           {:otsikko "Toimenpide" :nimi :toimenpide :hae (fn [_] (:tpi_nimi @u/valittu-toimenpideinstanssi)) :muokattava? (constantly false)}
           {:otsikko "Toteutunut pvm" :nimi :toteutunut-pvm :tyyppi :pvm  :validoi [[:ei-tyhja "Valitse päivämäärä"]] :leveys-col 2}
           {:otsikko "Suorittaja" :nimi :suorittajan-nimi :tyyppi :string  :validoi [[:ei-tyhja "Kirjoita suorittaja"]]}
           {:otsikko "Tehtävät" :nimi :tehtavat :leveys "20%" :tyyppi :komponentti :komponentti [tehtavat-ja-maarat lomake-tehtavat]}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :text :koko [80 :auto]}]
          @lomake-toteuma]]))))

(defn yksiloidyt-tehtavat [rivi]
  (let [urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        aikavali [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]
        toteutuneet-tehtavat (reaction<! (toteumat/hae-urakan-toteutuneet-tehtavat urakka-id sopimus-id aikavali :yksikkohintainen))]

    (fn [rivi]
      [:div.tehtavat-toteumittain
       [grid/grid
        {:otsikko     (str "Yksilöidyt tehtävät: " (:nimi rivi))
         :tyhja       (if (nil? @toteutuneet-tehtavat) [ajax-loader "Haetaan..."] "Toteumia ei löydy")
         :tallenna    #(toteumat/paivita-yk-hint-toteumien-tehtavat urakka-id %)
         :voi-lisata? false
         :tunniste    :tehtava_id}
        [{:otsikko "Päivämäärä" :nimi :alkanut :muokattava? (constantly false) :tyyppi :pvm :hae (comp pvm/pvm :alkanut) :leveys "20%"}
         {:otsikko "Määrä" :nimi :maara :muokattava? (constantly true) :tyyppi :numero :leveys "20%"}
         {:otsikko "Suorittaja" :nimi :suorittajan_nimi :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
         {:otsikko "Lisätieto" :nimi :lisatieto :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
         {:otsikko "Tarkastele koko toteumaa" :nimi :tarkastele-toteumaa :muokattava? (constantly false) :tyyppi :komponentti :leveys "20%"
          :komponentti (fn [rivi] [:button.nappi-toissijainen {:on-click
                                                               #(reset! lomakkeessa-muokattava-toteuma {:toteuma-id       (:toteuma_id rivi)
                                                                                                        :tehtavat         (zipmap (iterate inc 1)
                                                                                                                                  (map (fn [tehtava] {
                                                                                                                                                      :tehtava {:id (:toimenpidekoodi tehtava)}
                                                                                                                                                      :maara (:maara tehtava)
                                                                                                                                                      :yksikko (:yksikko tehtava)
                                                                                                                                                      :nimi (:toimenpide tehtava)
                                                                                                                                                      })
                                                                                                                                       (filter (fn [tehtava] ; Hae tehtävät, jotka kuuluvat samaan toteumaan
                                                                                                                                                 (= (:toteuma_id tehtava) (:toteuma_id rivi)))
                                                                                                                                               @toteutuneet-tehtavat)))
                                                                                                        :toteutunut-pvm   (:alkanut rivi)
                                                                                                        :lisatieto        (:lisatieto rivi)
                                                                                                        :suorittajan-nimi (:suorittajan_nimi rivi)})}
                                   (ikonit/eye-open) " Toteuma"])}]
        (sort
          (fn [eka toka] (pvm/ennen? (:alkanut eka) (:alkanut toka)))
          (filter (fn [tehtava] (= (:toimenpidekoodi tehtava) (:id rivi))) @toteutuneet-tehtavat))]])))

(defn yksikkohintaisten-toteumalistaus
  "Yksikköhintaisten töiden toteumat"
  []
  (let [valittu-aikavali (reaction @u/valittu-hoitokausi)
        toteumat (reaction<! (let [valittu-urakka-id (:id @nav/valittu-urakka)
                                    [valittu-sopimus-id _] @u/valittu-sopimusnumero]
                                    (toteumat/hae-urakan-toteumat valittu-urakka-id valittu-sopimus-id @valittu-aikavali)))
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
                         valittu-hoitokausi @u/valittu-hoitokausi
                         valittu-aikavali [(first valittu-hoitokausi) (second valittu-hoitokausi)]
                         toteumat @toteumat]

                     (when toteumat
                       (-> (lisaa-tyoriveille-yksikkohinta rivit valittu-hoitokausi)
                           (lisaa-tyoriveille-suunniteltu-maara valittu-hoitokausi)
                           (lisaa-tyoriveille-suunnitellut-kustannukset valittu-hoitokausi)
                           (lisaa-tyoriveille-toteutunut-maara toteumat)
                           (lisaa-tyoriveille-toteutuneet-kustannukset)
                           (lisaa-tyoriveille-erotus)))))]

    ; TODO UI:n mahdollisuus valita tarkempi aikaväli (kuukaisväli ja päivämääräväli)

    (komp/luo
      (fn []
        [:div#yksikkohintaisten-toteumat
         [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide @nav/valittu-urakka]

         [:button.nappi-ensisijainen {:on-click #(reset! lomakkeessa-muokattava-toteuma {})}
          (ikonit/plus-sign) " Lisää toteuma"]

         [grid/grid
          {:otsikko (str "Yksikköhintaisten töiden toteumat: " (:t2_nimi @u/valittu-toimenpideinstanssi) " / " (:t3_nimi @u/valittu-toimenpideinstanssi) " / " (:tpi_nimi @u/valittu-toimenpideinstanssi))
           :tyhja (if (nil? @tyorivit) [ajax-loader "Haetaan yksikköhintaisten töiden toteumia..."] "Ei yksikköhintaisten töiden toteumia")
           :vetolaatikot (into {} (map (juxt :id (fn [rivi] [yksiloidyt-tehtavat rivi])) (filter (fn [rivi] (> (:hoitokauden-toteutunut-maara rivi) 0)) @tyorivit)))}
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
            (fn [rivi] (= (:t3_koodi rivi) (:t3_koodi @u/valittu-toimenpideinstanssi)))
            @tyorivit)]]))))

(defn yksikkohintaisten-toteumat []
  (if @lomakkeessa-muokattava-toteuma
    [yksikkohintaisen-toteuman-muokkaus]
    [yksikkohintaisten-toteumalistaus]))