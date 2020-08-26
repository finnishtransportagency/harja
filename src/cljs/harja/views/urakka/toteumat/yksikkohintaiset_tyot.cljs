(ns harja.views.urakka.toteumat.yksikkohintaiset-tyot
  "Urakan 'Toteumat' välilehden Yksikköhintaist työt osio"
  (:require [reagent.core :refer [atom]]
            [harja.ui.grid :as grid]
            [harja.ui.grid.gridin-muokkaus :as gridin-muokkaus]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.viesti :as viesti]
            [harja.ui.komponentti :as komp]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.toteumat :as toteumat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.pvm :as pvm]
            [harja.ui.lomake :refer [lomake]]
            [harja.loki :refer [log logt tarkkaile!]]

            [cljs.core.async :refer [<! >! chan]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.domain.skeema :refer [+tyotyypit+]]
            [harja.fmt :as fmt]
            [harja.tiedot.urakka.urakan-toimenpiteet :as urakan-toimenpiteet]
            [harja.tiedot.urakka.toteumat.yksikkohintaiset-tyot :as tiedot]
            [harja.views.kartta :as kartta]
            [harja.tiedot.kartta :as kartta-tiedot]
            [harja.asiakas.kommunikaatio :as k]
            [harja.tiedot.urakka :as u]
            [harja.ui.napit :as napit]
            [cljs-time.core :as t]
            [reagent.core :as r]
            [harja.domain.oikeudet :as oikeudet]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.domain.tierekisteri :as tierekisteri])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]))

(defn- rivi-tehtavaksi [rivi]
  {:toimenpidekoodi (:tehtava rivi)
   :maara (:maara rivi)
   :tehtava-id (:tehtava-id rivi)
   :poistettu (:poistettu rivi)})

(defn- lomakkeen-toteuma-lahetettavaksi [lomakkeen-toteuma lomakkeen-tehtavat]
  (assoc lomakkeen-toteuma
    :tyyppi :yksikkohintainen
    :urakka-id (:id @nav/valittu-urakka)
    :sopimus-id (first @u/valittu-sopimusnumero)
    :tehtavat (mapv rivi-tehtavaksi (gridin-muokkaus/filteroi-uudet-poistetut lomakkeen-tehtavat))
    :toimenpide-id (:tpi_id @u/valittu-toimenpideinstanssi)
    :hoitokausi-aloituspvm (first @u/valittu-hoitokausi)
    :hoitokausi-lopetuspvm (second @u/valittu-hoitokausi)))

(defn nayta-toteuma-lomakkeessa [urakka-id toteuma-id]
  (go (let [toteuma (<! (toteumat/hae-urakan-toteuma urakka-id toteuma-id))]
        (log "toteuma: " (pr-str toteuma))
        (if-not (k/virhe? toteuma)
          (let [lomake-tiedot
                {:toteuma-id (:id toteuma)
                 :tehtavat
                 (zipmap
                   (iterate inc 1)
                   (mapv
                     (fn [tehtava]
                       (let [[_ _ emo tehtava-urakassa]
                             (urakan-toimenpiteet/tehtava-urakassa
                              (:tpk-id tehtava) @u/urakan-toimenpiteet-ja-tehtavat)
                             tpi (some #(when (= (:t3_koodi %) (:koodi emo)) %)
                                       @u/urakan-toimenpideinstanssit)]
                         (log "Tehtava urakassa: " (pr-str tehtava-urakassa))
                         (log "Toteuman 4. tason tehtävän 3. tason emo selvitetty: " (pr-str emo))
                         (log "Toteuman 4. tason tehtävän toimenpideinstanssi selvitetty: " (pr-str tpi))
                         {:tehtava {:id (:tpk-id tehtava)}
                          :maara (:maara tehtava)
                          :tehtava-id (:tehtava-id tehtava)
                          :toimenpideinstanssi (:tpi_id tpi)
                          :yksikko (:yksikko tehtava-urakassa)}))
                     (:tehtavat toteuma)))
                 :alkanut (:alkanut toteuma)
                 :reitti (:reitti toteuma)
                 :tr (:tr toteuma)
                 :paattynyt (:paattynyt toteuma)
                 :lisatieto (:lisatieto toteuma)
                 :suorittajan-nimi (:nimi (:suorittaja toteuma))
                 :suorittajan-ytunnus (:ytunnus (:suorittaja toteuma))
                 :jarjestelman-lisaama (:jarjestelmanlisaama toteuma)
                 :luoja (:kayttajanimi toteuma)
                 :organisaatio (:organisaatio toteuma)}]
            (nav/aseta-valittu-valilehti! :urakat :toteumat)
            (nav/aseta-valittu-valilehti! :toteumat :yksikkohintaiset-tyot)
            (reset! tiedot/valittu-yksikkohintainen-toteuma lomake-tiedot))))))

(defn tallenna-toteuma
  "Ottaa lomakkeen ja tehtävät siinä muodossa kuin ne ovat lomake-komponentissa ja muodostaa palvelimelle lähetettävän payloadin."
  [lomakkeen-toteuma lomakkeen-tehtavat]
  (let [lahetettava-toteuma (lomakkeen-toteuma-lahetettavaksi lomakkeen-toteuma lomakkeen-tehtavat)]
    (log "Tallennetaan toteuma: " (pr-str lahetettava-toteuma))
    (toteumat/tallenna-toteuma-ja-yksikkohintaiset-tehtavat lahetettava-toteuma)))

(defn- nelostason-tehtava [tehtava]
  (nth tehtava 3))

(defn- tehtavan-tiedot [tehtava urakan-hoitokauden-yks-hint-tyot]
  (first (filter
           (fn [tiedot]
             (= (:tehtavan_id tiedot) (:id (nelostason-tehtava tehtava))))
           urakan-hoitokauden-yks-hint-tyot)))

(defn- tyo-hoitokaudella? [tyo]
  (pvm/sama-pvm? (:alkupvm tyo) (first @u/valittu-hoitokausi)))

(defn- valintakasittelija [vain-suunnitellut? t]
  (let [urakan-tpi-tehtavat (urakan-toimenpiteet/toimenpideinstanssin-tehtavat
                             (:toimenpideinstanssi t)
                             @u/urakan-toimenpideinstanssit
                             @u/urakan-toimenpiteet-ja-tehtavat)
        urakan-hoitokauden-yks-hint-tyot (filter tyo-hoitokaudella? @u/urakan-yks-hint-tyot)
        suunnitellut-tehtavat (filter
                                (fn [tehtava]
                                  (> (:yksikkohinta (tehtavan-tiedot tehtava urakan-hoitokauden-yks-hint-tyot)) 0))
                                urakan-tpi-tehtavat)]
    (sort-by #(:nimi (get % 3))
             (if vain-suunnitellut?
               suunnitellut-tehtavat
               urakan-tpi-tehtavat))))

(defn tehtavat-ja-maarat [tehtavat jarjestelman-lisaama-toteuma? tehtavat-virheet]
  (let [nelostason-tehtavat (map nelostason-tehtava @u/urakan-toimenpiteet-ja-tehtavat)
        toimenpideinstanssit @u/urakan-toimenpideinstanssit
        voi-muokata? (not jarjestelman-lisaama-toteuma?)]
    [grid/muokkaus-grid
     {:tyhja "Ei töitä."
      :voi-muokata? voi-muokata?
      :muutos #(reset! tehtavat-virheet (grid/hae-virheet %))}
     [{:otsikko "Toimenpide"
       :nimi :toimenpideinstanssi
       :tyyppi :valinta
       :fmt #(:tpi_nimi (urakan-toimenpiteet/toimenpideinstanssi-idlla % toimenpideinstanssit))
       :valinta-arvo :tpi_id
       :valinta-nayta #(if % (:tpi_nimi %) "- Valitse toimenpide -")
       :valinnat toimenpideinstanssit
       :leveys 30
       :validoi [[:ei-tyhja "Valitse työ"]]
       :aseta #(assoc %1
                :toimenpideinstanssi %2
                :tehtava nil)}
      {:otsikko "Tehtävä"
       :nimi :tehtava
       :tyyppi :valinta
       :valinta-arvo #(:id (nth % 3))
       :valinta-nayta #(if % (:nimi (nth % 3)) "- Valitse tehtävä -")
       :valinnat-fn (partial valintakasittelija voi-muokata?)
       :leveys 45
       :jos-tyhja "Toimenpiteelle ei ole suunniteltu yhtään tehtävää."
       :validoi [[:ei-tyhja "Valitse tehtävä"]]
       :aseta (fn [rivi arvo] (assoc rivi
                                :tehtava arvo
                                :yksikko (:yksikko (urakan-toimenpiteet/tehtava-idlla arvo nelostason-tehtavat))))}
      {:otsikko "Määrä" :nimi
       :maara
       :tyyppi :positiivinen-numero
       :leveys 25
       :validoi [[:ei-tyhja "Anna määrä"]]
       :tasaa :oikea}
      {:otsikko "Yks."
       :nimi :yksikko
       :tyyppi :string
       :muokattava? (constantly false)
       :leveys 15}]
     tehtavat]))

(defn yksikkohintainen-toteumalomake
  "Valmiin kohteen tietoja tarkasteltaessa tiedot annetaan valittu-yksikkohintainen-toteuma atomille.
  Lomakkeen käsittelyn ajaksi tiedot haetaan tästä atomista kahteen eri atomiin käsittelyn ajaksi:
  yksikköhintaiset tehtävät ovat omassa ja muut tiedot omassa atomissa.
  Kun lomake tallennetaan, tiedot yhdistetään näistä atomeista yhdeksi kokonaisuudeksi."
  []
  (let [lomake-toteuma (atom @tiedot/valittu-yksikkohintainen-toteuma)
        lomake-tehtavat (atom (into {}
                                    (map (fn [[id tehtava]]
                                           [id (assoc tehtava :tehtava
                                                              (:id (:tehtava tehtava)))])
                                         (:tehtavat @tiedot/valittu-yksikkohintainen-toteuma))))
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
         [napit/takaisin "Takaisin toteumaluetteloon" #(reset! tiedot/valittu-yksikkohintainen-toteuma nil)]


         [lomake {:otsikko (if (:toteuma-id @tiedot/valittu-yksikkohintainen-toteuma)
                             (if jarjestelman-lisaama-toteuma?
                               "Tarkastele toteumaa"
                               "Muokkaa toteumaa")
                             "Luo uusi toteuma")
                  :voi-muokata? (and (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-yksikkohintaisettyot (:id @nav/valittu-urakka))
                                     (not jarjestelman-lisaama-toteuma?))
                  :muokkaa! (fn [uusi]
                              (log "Muokataan toteumaa: " (pr-str uusi))
                              (reset! lomake-toteuma uusi))
                  :footer (when (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-yksikkohintaisettyot (:id @nav/valittu-urakka))
                            [harja.ui.napit/palvelinkutsu-nappi
                             "Tallenna toteuma"
                             #(tallenna-toteuma @lomake-toteuma @lomake-tehtavat)
                             {:luokka "nappi-ensisijainen"
                              :disabled (or (false? @valmis-tallennettavaksi?)
                                            (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-yksikkohintaisettyot (:id @nav/valittu-urakka))))
                              :kun-onnistuu (fn [vastaus]
                                              (log "Tehtävät tallennettu, vastaus: " (pr-str vastaus))
                                              (reset! tiedot/yks-hint-tehtavien-summat (:tehtavien-summat vastaus))
                                              (reset! lomake-tehtavat nil)
                                              (reset! lomake-toteuma nil)
                                              (reset! tiedot/valittu-yksikkohintainen-toteuma nil))}])}
          [(when jarjestelman-lisaama-toteuma?
             {:otsikko "Lähde" :nimi :luoja :tyyppi :string
              :hae (fn [rivi] (str "Järjestelmä (" (:luoja rivi) " / " (:organisaatio rivi) ")"))
              :muokattava? (constantly false)
              :vihje toteumat/ilmoitus-jarjestelman-luoma-toteuma})
           {:otsikko "Sopimus" :nimi :sopimus :hae (fn [_] (second @u/valittu-sopimusnumero)) :muokattava? (constantly false)}
           {:otsikko "Aloitus" :nimi :alkanut :pakollinen? true :tyyppi :pvm
            :uusi-rivi? true
            :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
            :aseta (fn [rivi arvo]
                     (assoc rivi :alkanut arvo
                                 :paattynyt arvo))
            :validoi [[:ei-tyhja "Valitse päivämäärä"]]
            :huomauta [[:urakan-aikana-ja-hoitokaudella]]}

           {:otsikko "Lopetus" :nimi :paattynyt :pakollinen? true :tyyppi :pvm
            :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
            :validoi [[:ei-tyhja "Valitse päivämäärä"]
                      [:pvm-kentan-jalkeen :alkanut "Lopetuksen pitää olla aloituksen jälkeen"]]}

           {:otsikko "Suorittaja" :nimi :suorittajan-nimi :pituus-max 256 :tyyppi :string
            :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}

           {:otsikko "Suorittajan Y-tunnus" :nimi :suorittajan-ytunnus :pituus-max 256 :tyyppi :string :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))}

           {:otsikko "Tehtävät" :nimi :tehtavat :pakollinen? true
            :uusi-rivi? true :palstoja 2
            :tyyppi :komponentti
            :vihje "Voit syöttää toteumia tehtäville, joille on syötetty hinta yksikköhintaisten töiden suunnitteluosiossa."
            :komponentti (fn [_]
                           [tehtavat-ja-maarat lomake-tehtavat jarjestelman-lisaama-toteuma? tehtavat-virheet])}

           (when-not jarjestelman-lisaama-toteuma?
             {:tyyppi :tierekisteriosoite
              :nimi :tr
              :pakollinen? false
              :sijainti (r/wrap (:reitti @lomake-toteuma)
                                #(swap! lomake-toteuma assoc :reitti %))})

           {:otsikko "Lisätieto" :nimi :lisatieto :pituus-max 256 :tyyppi :text
            :uusi-rivi? true
            :muokattava? (constantly (not jarjestelman-lisaama-toteuma?))
            :koko [80 :auto]
            :palstoja 2}]
          @lomake-toteuma]
         (when-not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-yksikkohintaisettyot (:id @nav/valittu-urakka))
           oikeudet/ilmoitus-ei-oikeutta-muokata-toteumaa)]))))

(defn yksiloidyt-tehtavat [rivi tehtavien-summat]
  (let [urakka-id (:id @nav/valittu-urakka)
        [sopimus-id _] @u/valittu-sopimusnumero
        aikavali [(first @u/valittu-aikavali) (second @u/valittu-aikavali)]
        toteutuneet-tehtavat (atom nil)
        tallenna (reaction
                   (if (or
                         (not (oikeudet/voi-kirjoittaa? oikeudet/urakat-toteumat-yksikkohintaisettyot
                                                        (:id @nav/valittu-urakka)))
                         (nil? @toteutuneet-tehtavat)
                         (every? :jarjestelmanlisaama @toteutuneet-tehtavat))
                     :ei-mahdollinen
                     #(go (let [vastaus (<! (toteumat/paivita-yk-hint-toteumien-tehtavat
                                              urakka-id
                                              sopimus-id
                                              aikavali
                                              :yksikkohintainen
                                              %
                                              (:tpi_id @u/valittu-toimenpideinstanssi)))]
                            (log "Tehtävät tallennettu: " (pr-str vastaus))
                            (reset! toteutuneet-tehtavat (:tehtavat vastaus))
                            (reset! tehtavien-summat (:tehtavien-summat vastaus))))))]
    (go (reset! toteutuneet-tehtavat
                (<! (toteumat/hae-urakan-toteutuneet-tehtavat-toimenpidekoodilla urakka-id sopimus-id aikavali
                                                                                 :yksikkohintainen (:tpk_id rivi)))))

    (fn [toteuma-rivi]
      [:div
       [grid/grid
        {:otsikko (str "Yksilöidyt tehtävät: " (:nimi toteuma-rivi))
         :tyhja (if (nil? @toteutuneet-tehtavat) [ajax-loader "Haetaan..."] "Toteumia ei löydy")
         :tallenna @tallenna
         :voi-lisata? false
         :esta-poistaminen? (fn [rivi] (:jarjestelmanlisaama rivi))
         :esta-poistaminen-tooltip (fn [_] "Järjestelmän lisäämää kohdetta ei voi poistaa.")
         :max-rivimaara 300
         :max-rivimaaran-ylitys-viesti "Liikaa hakutuloksia, rajaa hakua"
         :tunniste :tehtava_id}
        [{:otsikko "Päivämäärä"
          :nimi :alkanut
          :muokattava? (constantly false)
          :tyyppi :pvm
          :hae (comp pvm/pvm :alkanut)
          :leveys 12}
         {:otsikko "Määrä"
          :nimi :maara :validoi [[:ei-tyhja "Anna määrä"]]
          :muokattava? (fn [rivi] (not (:jarjestelmanlisaama rivi)))
          :tyyppi :positiivinen-numero
          :leveys 15
          :tasaa :oikea
          :fmt fmt/desimaaliluku-opt}
         {:otsikko "Suorittaja"
          :nimi :suorittajan_nimi
          :muokattava? (constantly false)
          :tyyppi :string
          :leveys 20}
         {:otsikko "TR-osoite"
          :nimi :tr
          :leveys 20
          :fmt tierekisteri/tierekisteriosoite-tekstina
          :tyyppi :tierekisteriosoite
          :muokattava? (constantly false)}
         {:otsikko "Lisätieto"
          :nimi :lisatieto
          :muokattava? (constantly false)
          :tyyppi :string
          :leveys 20}
         {:otsikko "Tarkastele koko toteumaa"
          :nimi :tarkastele-toteumaa
          :muokattava? (constantly false)
          :tyyppi :komponentti
          :leveys 20
          :komponentti (fn [rivi]
                         [:button.nappi-toissijainen.nappi-grid
                          {:on-click #(nayta-toteuma-lomakkeessa @nav/valittu-urakka-id (:toteuma_id rivi))}
                          (ikonit/eye-open) " Toteuma"])}]
        (when-let [toteutuneet-tehtavat @toteutuneet-tehtavat]
          (sort-by :alkanut t/after? toteutuneet-tehtavat))]])))

(defn- aikavali-joka-vaihtuu-hoitokaudesta
  ([valittu-aikavali-atom] (aikavali-joka-vaihtuu-hoitokaudesta valittu-aikavali-atom nil))
  ([valittu-aikavali-atom {:keys [nayta-otsikko? aikavalin-rajoitus
                                  aloitusaika-pakota-suunta paattymisaika-pakota-suunta
                                  lomake?]}]
   [:span {:class (if lomake?
                    "label-ja-aikavali-lomake"
                    "label-ja-aikavali")}
    (when (and (not lomake?)
               (or (nil? nayta-otsikko?)
                   (true? nayta-otsikko?)))
      [:span.alasvedon-otsikko "Aikaväli"])
    [:div.aikavali-valinnat
     [tee-kentta {:tyyppi :pvm :pakota-suunta aloitusaika-pakota-suunta}
      (r/wrap (first @valittu-aikavali-atom)
              (fn [uusi-arvo]
                (let [uusi-arvo (pvm/paivan-alussa-opt uusi-arvo)]
                  (if-not aikavalin-rajoitus
                    (swap! valittu-aikavali-atom #(pvm/varmista-aikavali-opt [uusi-arvo (second %)] :alku))
                    (swap! valittu-aikavali-atom #(pvm/varmista-aikavali-opt [uusi-arvo (second %)] aikavalin-rajoitus :alku))))
                (log "Uusi aikaväli: " (pr-str @valittu-aikavali-atom))))]
     [:div.pvm-valiviiva-wrap [:span.pvm-valiviiva " \u2014 "]]
     [tee-kentta {:tyyppi :pvm :pakota-suunta paattymisaika-pakota-suunta}
      (r/wrap (second @valittu-aikavali-atom)
              (fn [uusi-arvo]
                (let [uusi-arvo (pvm/paivan-lopussa-opt uusi-arvo)]
                  (if-not aikavalin-rajoitus
                    (swap! valittu-aikavali-atom #(pvm/varmista-aikavali-opt [(first %) uusi-arvo] :loppu))
                    (swap! valittu-aikavali-atom #(pvm/varmista-aikavali-opt [(first %) uusi-arvo] aikavalin-rajoitus :loppu))))
                (log "Uusi aikaväli: " (pr-str @valittu-aikavali-atom))))]]]))

(defn yksikkohintaisten-toteumalistaus
  "Yksikköhintaisten töiden toteumat tehtävittäin"
  []
  (komp/luo
    (fn []
      [:div
       [valinnat/urakan-sopimus @nav/valittu-urakka]
       [valinnat/urakan-hoitokausi @nav/valittu-urakka]
       [valinnat/urakan-toimenpide+kaikki @nav/valittu-urakka]
       [aikavali-joka-vaihtuu-hoitokaudesta u/yksikkohintaiset-aikavali]
       [valinnat/urakan-yksikkohintainen-tehtava+kaikki]

       (let [oikeus? (oikeudet/voi-kirjoittaa?
                       oikeudet/urakat-toteumat-yksikkohintaisettyot
                       (:id @nav/valittu-urakka))]
         (yleiset/wrap-if
           (not oikeus?)
           [yleiset/tooltip {} :%
            (oikeudet/oikeuden-puute-kuvaus :kirjoitus
                                            oikeudet/urakat-toteumat-yksikkohintaisettyot)]
           [napit/uusi "Lisää toteuma" #(reset! tiedot/valittu-yksikkohintainen-toteuma
                                                (tiedot/uusi-yksikkohintainen-toteuma))
            {:disabled (not oikeus?)}]))

       [grid/grid
        {:otsikko (str "Yksikköhintaisten töiden toteumat")
         :tunniste :tpk_id
         :tyhja (if (nil? @tiedot/yks-hint-tyot-tehtavittain)
                  [ajax-loader "Haetaan yksikköhintaisten töiden toteumia..."]
                  "Ei yksikköhintaisten töiden toteumia")
         :luokat ["toteumat-paasisalto"]
         :vetolaatikot (into {} (map (juxt :tpk_id (fn [rivi] [yksiloidyt-tehtavat rivi tiedot/yks-hint-tehtavien-summat]))
                                     @tiedot/yks-hint-tyot-tehtavittain))}
        [{:tyyppi :vetolaatikon-tila :leveys 5}
         {:otsikko "Tehtävä" :nimi :nimi :muokattava? (constantly false) :tyyppi :numero :leveys 25}
         {:otsikko "Yksikkö" :nimi :yksikko :muokattava? (constantly false) :tyyppi :numero :leveys 10}
         {:otsikko "Yksikkö\u00ADhinta" :nimi :yksikkohinta :muokattava? (constantly false) :tyyppi :numero :leveys 10 :tasaa :oikea :fmt fmt/euro-opt}
         {:otsikko "Suunni\u00ADteltu määrä" :nimi :hoitokauden-suunniteltu-maara :muokattava? (constantly false) :tyyppi :numero :leveys 10
          :fmt #(fmt/desimaaliluku-opt % 2) :tasaa :oikea}
         {:otsikko "Toteutu\u00ADnut määrä" :nimi :maara :muokattava? (constantly false) :tyyppi :numero :leveys 10
          :fmt #(fmt/desimaaliluku-opt % 2) :tasaa :oikea}
         {:otsikko "Suunni\u00ADtellut kustan\u00ADnukset" :nimi :hoitokauden-suunnitellut-kustannukset :fmt fmt/euro-opt
          :tasaa :oikea :muokattava? (constantly false) :tyyppi :numero :leveys 10}
         {:otsikko "Toteutu\u00ADneet kustan\u00ADnukset" :nimi :hoitokauden-toteutuneet-kustannukset :fmt fmt/euro-opt
          :tasaa :oikea :muokattava? (constantly false) :tyyppi :numero :leveys 10}
         {:otsikko "Budjettia jäljellä" :nimi :kustannuserotus :muokattava? (constantly false)
          :tyyppi :komponentti :tasaa :oikea
          :komponentti (fn [rivi] (if (>= (:kustannuserotus rivi) 0)
                                    [:span.kustannuserotus.kustannuserotus-positiivinen (fmt/euro-opt (:kustannuserotus rivi))]
                                    [:span.kustannuserotus.kustannuserotus-negatiivinen (fmt/euro-opt (:kustannuserotus rivi))])) :leveys 10}]
        @tiedot/yks-hint-tyot-tehtavittain]])))

(defn yksikkohintaisten-toteumat []
  (komp/luo
    (komp/lippu tiedot/yksikkohintaiset-tyot-nakymassa? tiedot/karttataso-yksikkohintainen-toteuma)
    (komp/sisaan-ulos #(do
                         (kartta-tiedot/kasittele-infopaneelin-linkit!
                          {:toteuma
                           {:toiminto (fn [{id :id :as klikattu-toteuma}]
                                        (nayta-toteuma-lomakkeessa @nav/valittu-urakka-id id))
                            :teksti "Valitse toteuma"}}))
                      #(kartta-tiedot/kasittele-infopaneelin-linkit! nil))
    (fn []
      [:span
       [kartta/kartan-paikka]
       (if @tiedot/valittu-yksikkohintainen-toteuma
         [yksikkohintainen-toteumalomake]
         [yksikkohintaisten-toteumalistaus])])))
