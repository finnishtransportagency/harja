(ns harja.views.urakka.toteumat.yksikkohintaiset-tyot
  "Urakan 'Toteumat' välilehden Yksikköhintaist työt osio"
  (:require [reagent.core :refer [atom]]
            [harja.domain.roolit :as roolit]
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

(defonce tehtavien-summat (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                       [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                       nakymassa? @toteumat/yksikkohintaiset-tyot-nakymassa?
                                       valittu-hoitokausi @u/valittu-hoitokausi]
                                      (when (and valittu-urakka-id valittu-sopimus-id valittu-hoitokausi nakymassa?)
                                        (log "Haetaan urakan toteumat: " valittu-urakka-id valittu-sopimus-id valittu-hoitokausi)
                                        (toteumat/hae-urakan-toteumien-tehtavien-summat valittu-urakka-id valittu-sopimus-id valittu-hoitokausi :yksikkohintainen))))


(defn tallenna-toteuma
  "Ottaa lomakkeen ja tehtävät siinä muodossa kuin ne ovat lomake-komponentissa ja muodostaa palvelimelle lähetettävän payloadin."
  [lomakkeen-toteuma lomakkeen-tehtavat]
  (let [lahetettava-toteuma (->
                              (assoc lomakkeen-toteuma
                                :tyyppi :yksikkohintainen
                                :urakka-id (:id @nav/valittu-urakka)
                                :sopimus-id (first @u/valittu-sopimusnumero)
                                :tehtavat (mapv
                                            (fn [rivi]
                                              {:toimenpidekoodi (:tehtava rivi)
                                               :maara           (:maara rivi)
                                               :tehtava-id      (:tehtava-id rivi)
                                               :poistettu       (:poistettu rivi)
                                               })
                                            (grid/filteroi-uudet-poistetut lomakkeen-tehtavat))
                                :hoitokausi-aloituspvm (first @u/valittu-hoitokausi)
                                :hoitokausi-lopetuspvm (second @u/valittu-hoitokausi)))]
    (log "Tallennetaan toteuma: " (pr-str lahetettava-toteuma))
    (toteumat/tallenna-toteuma-ja-yksikkohintaiset-tehtavat lahetettava-toteuma)))

(defn tehtavat-ja-maarat [tehtavat jarjestelman-lisaama-toteuma? tehtavat-virheet]
  (let [tehtavat-tasoineen @u/urakan-toimenpiteet-ja-tehtavat
        nelostason-tehtavat (map #(nth % 3) tehtavat-tasoineen)
        toimenpideinstanssit @u/urakan-toimenpideinstanssit]

    [grid/muokkaus-grid
     {:tyhja        "Ei töitä."
      :voi-muokata? (not jarjestelman-lisaama-toteuma?)
      :muutos       #(reset! tehtavat-virheet (grid/hae-virheet %))}
     [{:otsikko       "Toimenpide" :nimi :toimenpideinstanssi
       :tyyppi        :valinta
       :fmt           #(:tpi_nimi (urakan-toimenpiteet/toimenpideinstanssi-idlla % toimenpideinstanssit))
       :valinta-arvo  :tpi_id
       :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
       :valinnat      toimenpideinstanssit
       :leveys        "30%"
       :validoi       [[:ei-tyhja "Valitse työ"]]
       :aseta         #(assoc %1 :toimenpideinstanssi %2
                                 :tehtava nil)}
      {:otsikko       "Tehtävä" :nimi :tehtava
       :tyyppi        :valinta
       :valinta-arvo  #(:id (nth % 3))
       :valinta-nayta #(if % (:nimi (nth % 3)) "- Valitse tehtävä -")
       :valinnat-fn   #(urakan-toimenpiteet/toimenpideinstanssin-tehtavat
                        (:toimenpideinstanssi %)
                        toimenpideinstanssit tehtavat-tasoineen)
       :leveys        "45%"
       :validoi       [[:ei-tyhja "Valitse tehtävä"]]
       :aseta         (fn [rivi arvo] (assoc rivi
                                        :tehtava arvo
                                        :yksikko (:yksikko (urakan-toimenpiteet/tehtava-idlla arvo nelostason-tehtavat))))}
      {:otsikko "Määrä" :nimi :maara :tyyppi :numero :leveys "25%" :validoi [[:ei-tyhja "Anna määrä"]]}
      {:otsikko "Yks." :nimi :yksikko :tyyppi :string :muokattava? (constantly false) :leveys "15%"}]
     tehtavat]))

(defonce lomakkeessa-muokattava-toteuma (atom nil))

; TODO Jos on aikaa, niin lomakkeen tehtävät voisi toimia paremmin Reagent-wrapin avulla. Nyt tehtävät ja muu lomakedata ovat omissa atomeissaan,
; jotka yhdistetään lopuksi kun lomake tallennetaan. Reagent wrapilla koko lomake saataisiin pidettyä yhdessä atomissa.
(defn yksikkohintaisen-toteuman-muokkaus
  "Uuden toteuman syöttäminen"
  []
  (let [lomake-toteuma (atom (if (empty? @lomakkeessa-muokattava-toteuma)
                               (if @u/urakan-organisaatio
                                 (-> (assoc @lomakkeessa-muokattava-toteuma :suorittajan-nimi (:nimi @u/urakan-organisaatio))
                                     (assoc :suorittajan-ytunnus (:ytunnus @u/urakan-organisaatio)))
                                 @lomakkeessa-muokattava-toteuma)
                               @lomakkeessa-muokattava-toteuma))
        lomake-tehtavat (atom (into {}
                                    (map (fn [[id tehtava]]
                                           [id (assoc tehtava :tehtava
                                                              (:id (:tehtava tehtava)))])
                                         (:tehtavat @lomakkeessa-muokattava-toteuma))))
        tehtavat-virheet (atom nil)
        jarjestelman-lisaama-toteuma? (true? (:jarjestelman-lisaama @lomake-toteuma))
        valmis-tallennettavaksi? (reaction
                                   (and
                                     ; Validoi toteuma
                                     (not jarjestelman-lisaama-toteuma?)
                                     (not (nil? (:alkanut @lomake-toteuma)))
                                     (not (nil? (:paattynyt @lomake-toteuma)))
                                     (not (pvm/ennen? (:paattynyt @lomake-toteuma) (:alkanut @lomake-toteuma)))
                                     ; Validoi tehtävät
                                     (not (empty? (filter #(not (true? (:poistettu %))) (vals @lomake-tehtavat))))
                                     (empty? @tehtavat-virheet)))]

    (log "Lomake-toteuma: " (pr-str @lomake-toteuma))
    (log "Lomake tehtävät: " (pr-str @lomake-tehtavat))
    (komp/luo
      (fn [ur]
        [:div.toteuman-tiedot
         [:button.nappi-toissijainen {:on-click #(reset! lomakkeessa-muokattava-toteuma nil)}
          (ikonit/chevron-left) " Takaisin toteumaluetteloon"]
         (if (:toteuma-id @lomakkeessa-muokattava-toteuma)
           (if jarjestelman-lisaama-toteuma?
             [:h3 "Tarkastele toteumaa"]
             [:h3 "Muokkaa toteumaa"])
           [:h3 "Luo uusi toteuma"])

         [lomake {:luokka       :horizontal
                  :voi-muokata? (and (roolit/rooli-urakassa? roolit/toteumien-kirjaus (:id @nav/valittu-urakka))
                                     (not jarjestelman-lisaama-toteuma?))
                  :muokkaa!     (fn [uusi]
                                  (log "Muokataan toteumaa: " (pr-str uusi))
                                  (reset! lomake-toteuma uusi))
                  :footer       (when (roolit/rooli-urakassa? roolit/toteumien-kirjaus (:id @nav/valittu-urakka))
                                  [harja.ui.napit/palvelinkutsu-nappi
                                   "Tallenna toteuma"
                                   #(tallenna-toteuma @lomake-toteuma @lomake-tehtavat)
                                   {:luokka       "nappi-ensisijainen"
                                    :disabled     (false? @valmis-tallennettavaksi?)
                                    :kun-onnistuu (fn [vastaus]
                                                    (log "Tehtävät tallennettu, vastaus: " (pr-str vastaus))
                                                    (reset! tehtavien-summat (:tehtavien-summat vastaus))
                                                    (reset! lomake-tehtavat nil)
                                                    (reset! lomake-toteuma nil)
                                                    (reset! lomakkeessa-muokattava-toteuma nil))}])}
          [(when jarjestelman-lisaama-toteuma?
             {:otsikko     "Lähde" :nimi :luoja :tyyppi :string
              :hae         (fn [rivi] (str "Järjestelmä (" (:luoja rivi) " / " (:organisaatio rivi) ")"))
              :muokattava? (constantly false)
              :vihje       "Tietojärjestelmästä tulleen toteuman muokkaus ei ole sallittu."})
           {:otsikko "Sopimus" :nimi :sopimus :hae (fn [_] (second @u/valittu-sopimusnumero)) :muokattava? (constantly false)}
           {:otsikko "Aloitus" :nimi :alkanut :tyyppi :pvm :leveys-col 2 :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
            :aseta   (fn [rivi arvo]
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
           {:otsikko "Lopetus" :nimi :paattynyt :tyyppi :pvm :muokattava? (constantly (not jarjestelman-lisaama-toteuma?)) :validoi [[:ei-tyhja "Valitse päivämäärä"]
                                                                                                                                     [:pvm-kentan-jalkeen :alkanut "Lopetuksen pitää olla aloituksen jälkeen"]] :leveys-col 2}
           {:otsikko "Tehtävät" :nimi :tehtavat :leveys "20%" :tyyppi :komponentti :komponentti [tehtavat-ja-maarat lomake-tehtavat jarjestelman-lisaama-toteuma? tehtavat-virheet]}
           {:otsikko "Suorittaja" :nimi :suorittajan-nimi :pituus-max 256 :tyyppi :string :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}
           {:otsikko "Suorittajan Y-tunnus" :nimi :suorittajan-ytunnus :pituus-max 256 :tyyppi :string :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}
           {:otsikko "Lisätieto" :nimi :lisatieto :pituus-max 256 :tyyppi :text :muokattava? (constantly (not jarjestelman-lisaama-toteuma?)) :koko [80 :auto]}]
          @lomake-toteuma]
         (when-not (roolit/rooli-urakassa? roolit/toteumien-kirjaus (:id @nav/valittu-urakka))
           "Käyttäjäroolillasi ei ole oikeutta muokata tätä toteumaa.")]))))

(defn yksiloidyt-tehtavat [rivi tehtavien-summat]
  (let [urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        aikavali [(first @u/valittu-hoitokausi) (second @u/valittu-hoitokausi)]
        toteutuneet-tehtavat (atom nil)]
    (go (reset! toteutuneet-tehtavat
                (<! (toteumat/hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla urakka-id sopimus-id aikavali
                                                                                 :yksikkohintainen (:id rivi)))))

    (fn [toteuma-rivi]
      [:div
       [grid/grid
        {:otsikko     (str "Yksilöidyt tehtävät: " (:nimi toteuma-rivi))
         :tyhja       (if (nil? @toteutuneet-tehtavat) [ajax-loader "Haetaan..."] "Toteumia ei löydy")
         :tallenna    #(go (let [vastaus (<! (toteumat/paivita-yk-hint-toteumien-tehtavat urakka-id sopimus-id aikavali :yksikkohintainen %))]
                             (log "Tehtävät tallennettu: " (pr-str vastaus))
                             (reset! toteutuneet-tehtavat (:tehtavat vastaus))
                             (reset! tehtavien-summat (:tehtavien-summat vastaus))))
         :voi-lisata? false
         :tunniste    :tehtava_id}
        [{:otsikko "Päivämäärä" :nimi :alkanut :muokattava? (constantly false) :tyyppi :pvm :hae (comp pvm/pvm :alkanut) :leveys "20%"}
         {:otsikko "Määrä" :nimi :maara :muokattava? (constantly true) :tyyppi :numero :leveys "20%"}
         {:otsikko "Suorittaja" :nimi :suorittajan_nimi :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
         {:otsikko "Lisätieto" :nimi :lisatieto :muokattava? (constantly false) :tyyppi :string :leveys "20%"}
         {:otsikko     "Tarkastele koko toteumaa" :nimi :tarkastele-toteumaa :muokattava? (constantly false) :tyyppi :komponentti :leveys "20%"
          :komponentti (fn [rivi] [:button.nappi-toissijainen.nappi-grid {:on-click
                                                                          #(go (let [toteuma (<! (toteumat/hae-urakan-toteuma urakka-id (:toteuma_id rivi)))]
                                                                                 (log "toteuma: " (pr-str toteuma)
                                                                                      (let [lomake-tiedot {:toteuma-id           (:id toteuma)
                                                                                                           :tehtavat             (zipmap (iterate inc 1)
                                                                                                                                         (mapv (fn [tehtava]
                                                                                                                                                 (let [tehtava-urakassa (get (first (filter (fn [tehtavat]
                                                                                                                                                                                              (= (:id (get tehtavat 3)) (:tpk-id tehtava)))
                                                                                                                                                                                            @u/urakan-toimenpiteet-ja-tehtavat)) 3)
                                                                                                                                                       emo (get (first (filter (fn [tehtavat]
                                                                                                                                                                                 (= (:id (get tehtavat 3)) (:tpk-id tehtava)))
                                                                                                                                                                               @u/urakan-toimenpiteet-ja-tehtavat)) 2)
                                                                                                                                                       tpi (first (filter (fn [tpi] (= (:t3_koodi tpi) (:koodi emo))) @u/urakan-toimenpideinstanssit))]
                                                                                                                                                   (log "Toteuman 4. tason tehtävän 3. tason emo selvitetty: " (pr-str emo))
                                                                                                                                                   (log "Toteuman 4. tason tehtävän toimenpideinstanssi selvitetty: " (pr-str tpi))
                                                                                                                                                   {
                                                                                                                                                    :tehtava             {:id (:tpk-id tehtava)}
                                                                                                                                                    :maara               (:maara tehtava)
                                                                                                                                                    :tehtava-id          (:tehtava-id tehtava)
                                                                                                                                                    :toimenpideinstanssi (:tpi_id tpi)
                                                                                                                                                    :yksikko             (:yksikko tehtava-urakassa)
                                                                                                                                                    }))
                                                                                                                                               (:tehtavat toteuma)))
                                                                                                           :alkanut              (:alkanut toteuma)
                                                                                                           :paattynyt            (:paattynyt toteuma)
                                                                                                           :lisatieto            (:lisatieto toteuma)
                                                                                                           :suorittajan-nimi     (:suorittajan_nimi toteuma)
                                                                                                           :suorittajan-ytunnus  (:suorittajan_ytunnus toteuma)
                                                                                                           :jarjestelman-lisaama (:jarjestelman_lisaama toteuma)
                                                                                                           :luoja                (:kayttajanimi toteuma)
                                                                                                           :organisaatio         (:organisaatio toteuma)}]
                                                                                        (reset! lomakkeessa-muokattava-toteuma lomake-tiedot)))))}
                                   (ikonit/eye-open) " Toteuma"])}]
        (sort
          (fn [eka toka] (pvm/ennen? (:alkanut eka) (:alkanut toka)))
          (filter (fn [tehtava] (= (:toimenpidekoodi tehtava) (:id toteuma-rivi))) @toteutuneet-tehtavat))]])))

(defn yksikkohintaisten-toteumalistaus
  "Yksikköhintaisten töiden toteumat"
  []
  (let [lisaa-tyoriveille-yksikkohinta (fn [rivit] (map
                                                     (fn [rivi] (assoc rivi :yksikkohinta
                                                                            (or (:yksikkohinta (first (filter
                                                                                                        (fn [tyo] (and (= (:tehtava tyo) (:id rivi))
                                                                                                                       (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                                        @u/urakan-yks-hint-tyot))) 0)))
                                                     rivit))
        lisaa-tyoriveille-suunniteltu-maara (fn [rivit] (map
                                                          (fn [rivi] (assoc rivi :hoitokauden-suunniteltu-maara
                                                                                 (or (:maara (first (filter
                                                                                                      (fn [tyo] (and (= (:tehtava tyo) (:id rivi))
                                                                                                                     (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                                      @u/urakan-yks-hint-tyot))) 0)))
                                                          rivit))
        lisaa-tyoriveille-suunnitellut-kustannukset (fn [rivit]
                                                      (map
                                                        (fn [rivi] (assoc rivi :hoitokauden-suunnitellut-kustannukset
                                                                               (or (:yhteensa (first (filter
                                                                                                       (fn [tyo] (and (= (:tehtava tyo) (:id rivi))
                                                                                                                      (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi))))
                                                                                                       @u/urakan-yks-hint-tyot))) 0)))
                                                        rivit))
        lisaa-tyoriveille-toteutunut-maara (fn [rivit]
                                             (map
                                               (fn [rivi] (assoc rivi :hoitokauden-toteutunut-maara (or (:maara (first (filter
                                                                                                                         (fn [tehtava] (= (:tpk_id tehtava) (:id rivi)))
                                                                                                                         @tehtavien-summat)))
                                                                                                        0)))
                                               rivit))
        lisaa-tyoriveille-toteutuneet-kustannukset (fn [rivit]
                                                     (map
                                                       (fn [rivi] (assoc rivi :hoitokauden-toteutuneet-kustannukset (* (:yksikkohinta rivi) (:hoitokauden-toteutunut-maara rivi))))
                                                       rivit))
        lisaa-tyoriveille-erotus (fn [rivit] (map
                                               (fn [rivi] (assoc rivi :kustannuserotus (- (:hoitokauden-suunnitellut-kustannukset rivi) (:hoitokauden-toteutuneet-kustannukset rivi))))
                                               rivit))
        tyorivit (reaction
                   (let [rivit (map
                                 (fn [tasot] (let [kolmostaso (nth tasot 2)
                                                   nelostaso (nth tasot 3)]
                                               (assoc nelostaso :t3_koodi (:koodi kolmostaso))))
                                 @u/urakan-toimenpiteet-ja-tehtavat)
                         tehtavien-summat @tehtavien-summat]

                     (when tehtavien-summat
                       (log "Rivien pohja tehty: " (pr-str rivit))
                       (log "Tehtävien summat saatu: " (pr-str tehtavien-summat))
                       (log "Rakennetaan lopulliset rivit.")
                       (-> (lisaa-tyoriveille-yksikkohinta rivit)
                           (lisaa-tyoriveille-suunniteltu-maara)
                           (lisaa-tyoriveille-suunnitellut-kustannukset)
                           (lisaa-tyoriveille-toteutunut-maara)
                           (lisaa-tyoriveille-toteutuneet-kustannukset)
                           (lisaa-tyoriveille-erotus)))))
        filteroidyt-tyorivit (reaction
                               (let [valittu-tpi @u/valittu-toimenpideinstanssi]
                                 (filter
                                   (fn [rivi] (and (= (:t3_koodi rivi) (:t3_koodi valittu-tpi))
                                                   (or
                                                     (> (:hoitokauden-toteutunut-maara rivi) 0)
                                                     (> (:hoitokauden-suunniteltu-maara rivi) 0))))
                                   @tyorivit)))
        valittu-tpi @u/valittu-toimenpideinstanssi]

    (komp/luo
      (fn []
        [:div
         [valinnat/urakan-sopimus-ja-hoitokausi-ja-toimenpide @nav/valittu-urakka]

         [:button.nappi-ensisijainen {:on-click #(reset! lomakkeessa-muokattava-toteuma {})
                                      :disabled (not (roolit/rooli-urakassa? roolit/toteumien-kirjaus (:id @nav/valittu-urakka)))}
          (ikonit/plus-sign) " Lisää toteuma"]

         [grid/grid
          {:otsikko      (str "Yksikköhintaisten töiden toteumat: " (:t2_nimi valittu-tpi) " / " (:t3_nimi valittu-tpi) " / " (:tpi_nimi valittu-tpi))
           :tyhja        (if (nil? @tyorivit) [ajax-loader "Haetaan yksikköhintaisten töiden toteumia..."] "Ei yksikköhintaisten töiden toteumia")
           :luokat       ["toteumat-paasisalto"]
           :vetolaatikot (into {} (map (juxt :id (fn [rivi] [yksiloidyt-tehtavat rivi tehtavien-summat])) (filter (fn [rivi] (> (:hoitokauden-toteutunut-maara rivi) 0)) @tyorivit)))
           }
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
          @filteroidyt-tyorivit]]))))

(defn yksikkohintaisten-toteumat []
  (komp/luo
    (komp/lippu toteumat/yksikkohintaiset-tyot-nakymassa?)

    (fn []
      (if @lomakkeessa-muokattava-toteuma
        [yksikkohintaisen-toteuman-muokkaus]
        [yksikkohintaisten-toteumalistaus]))))
