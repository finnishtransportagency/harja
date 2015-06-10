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
                  :tyyppi :yksikkohintainen
                  :urakka-id (:id @nav/valittu-urakka)
                  :sopimus-id (first @u/valittu-sopimusnumero)
                  :tehtavat lomakkeen-tehtavat))]
    (log "TOT Tallennetaan toteuma: " (pr-str schema-toteuma))
    (toteumat/tallenna-toteuma-ja-yksikkohintaiset-tehtavat schema-toteuma)))

(defn tehtavat-ja-maarat [tehtavat]
  (let [toimenpiteen-tehtavat (reaction (map #(nth % 3) @u/urakan-toimenpiteet-ja-tehtavat))]

    [grid/muokkaus-grid
     {:tyhja "Ei töitä."}
     [{:otsikko "Tehtävät" :nimi :tehtava :tyyppi :valinta
       :valinta-arvo :id
       :valinnat @toimenpiteen-tehtavat
       :valinta-nayta #(if % (:nimi %) "- valitse tehtävä -")
       :validoi [[:ei-tyhja "Valitse tehtävä."]]
       :leveys "50%"}

      {:otsikko "Määrä" :nimi :maara :tyyppi :string :leveys "40%"}
      {:otsikko "Yks." :muokattava? (constantly false) :nimi :yksikko :hae :yksikko :leveys "5%"}] ; FIXME Yksikön hakeminen ei toimi
     tehtavat]))


(defn ryhmittele-tehtavat [rivit]
  (let [otsikko (fn [rivi] (:toimenpidekoodi rivi)) ; FIXME Toimenpiteen mukaan
        otsikon-mukaan (group-by otsikko rivit)]
    (doall (mapcat (fn [[otsikko rivit]]
                     (concat [(grid/otsikko otsikko)] rivit))
                   (seq otsikon-mukaan)))))

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
                                   (and ;(not (empty? (:aloituspvm @lomake-toteuma))) FIXME Lomake ei toimi jos tämä on tässä
                                        (not (empty? (:suorittajan-nimi @lomake-toteuma)))
                                        (not (empty? (:suorittajan-ytunnus @lomake-toteuma)))
                                        (pvm/ennen? (:aloituspvm @lomake-toteuma) (:lopetuspvm @lomake-toteuma))
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
                                                                        {:toimenpidekoodi (:tehtava rivi)
                                                                         :maara (js/parseInt (:maara rivi))
                                                                         :tehtava-id (:tehtava-id rivi)
                                                                         :poistettu (:poistettu rivi)})
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

           {:otsikko "Aloitus" :nimi :aloituspvm :tyyppi :pvm :aseta (fn [rivi arvo]
                                                                              (assoc
                                                                                (if
                                                                                  (or
                                                                                    (not (:lopetuspvm rivi))
                                                                                    (pvm/jalkeen? arvo (:lopetuspvm rivi)))
                                                                                  (assoc rivi :lopetuspvm arvo)
                                                                                  rivi)
                                                                                :aloituspvm
                                                                                arvo)) :validoi [[:ei-tyhja "Valitse päivämäärä"]] :leveys-col 2}
           {:otsikko "Lopetus" :nimi :lopetuspvm :tyyppi :pvm :validoi [[:ei-tyhja "Valitse päivämäärä"]] :leveys-col 2} ; FIXME Kun aloitus annettu, aseta tälle arvoksi sama. Miten?
           {:otsikko "Suorittaja" :nimi :suorittajan-nimi :tyyppi :string  :validoi [[:ei-tyhja "Kirjoita suorittaja"]]}
           {:otsikko "Suorittajan Y-tunnus" :nimi :suorittajan-ytunnus :tyyppi :string  :validoi [[:ei-tyhja "Kirjoita suorittajan y-tunnus"]]}
           {:otsikko "Lisätieto" :nimi :lisatieto :tyyppi :text :koko [80 :auto]}
           {:otsikko "Tehtävät" :nimi :tehtavat :leveys "20%" :tyyppi :komponentti :komponentti [tehtavat-ja-maarat lomake-tehtavat]}] ; FIXME Ryhmittele toteuman mukaan
          @lomake-toteuma]]))))

(defn yksiloidyt-tehtavat [rivi]
  (let [urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        aikavali [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]
        toteutuneet-tehtavat (reaction<! (toteumat/hae-urakan-toteutuneet-tehtavat urakka-id sopimus-id aikavali :yksikkohintainen))]
    ; FIXME Hakee kaikki tehtävät jokaiselle haitarille, rajoita haku samoihin tehtäviin. Tämän jälkeen kun klikataan Toteuma-nappia, tee uusi kysely ja hae saman toteuman tehtävät lomaketta varten
    (fn [toteuma-rivi]
      [:div.tehtavat-toteumittain
       [grid/grid
        {:otsikko     (str "Yksilöidyt tehtävät: " (:nimi toteuma-rivi))
         :tyhja       (if (nil? @toteutuneet-tehtavat) [ajax-loader "Haetaan..."] "Toteumia ei löydy")
         :tallenna    #(go (let [vastaus (<! (toteumat/paivita-yk-hint-toteumien-tehtavat urakka-id sopimus-id aikavali :yksikkohintainen %))]
                             (reset! toteutuneet-tehtavat vastaus))) ; FIXME Yhteenveto-rivi ei päivity, kyselyn pitää palauttaa uusi summa kannasta?
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
                                                                                                                                                      :tehtava-id (:tehtava_id tehtava)
                                                                                                                                                      })
                                                                                                                                       (filter (fn [tehtava] ; Hae tehtävät, jotka kuuluvat samaan toteumaan
                                                                                                                                                 (= (:toteuma_id tehtava) (:toteuma_id rivi)))
                                                                                                                                               @toteutuneet-tehtavat)))
                                                                                                        :aloituspvm       (:alkanut rivi)
                                                                                                        :lopetuspvm       (:paattynyt rivi)
                                                                                                        :lisatieto        (:lisatieto rivi)
                                                                                                        :suorittajan-nimi (:suorittajan_nimi rivi)
                                                                                                        :suorittajan-ytunnus (:suorittajan_ytunnus rivi)})}
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
                         valittu-hoitokausi @u/valittu-hoitokausi
                         valittu-aikavali [(first valittu-hoitokausi) (second valittu-hoitokausi)]
                         toteumat @toteumat]

                     ; FIXME Tee mieluummin SQL-kysely joka palauttaa tämän ja summat suoraan kannasta
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
            (fn [rivi] (and (= (:t3_koodi rivi) (:t3_koodi @u/valittu-toimenpideinstanssi))
                            (or
                              (> (:hoitokauden-toteutunut-maara rivi) 0)
                              (> (:hoitokauden-suunniteltu-maara rivi) 0))))
            @tyorivit)]]))))

(defn yksikkohintaisten-toteumat []
  (if @lomakkeessa-muokattava-toteuma
    [yksikkohintaisen-toteuman-muokkaus]
    [yksikkohintaisten-toteumalistaus]))