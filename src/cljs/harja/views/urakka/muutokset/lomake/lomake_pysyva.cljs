(ns harja.views.urakka.muutokset.lomake.lomake-pysyva
  "Muutokset välilehden lomakkeet - Pysyvä muutos"
  (:require [reagent.core :as r]

            [harja.fmt :as fmt]
            [harja.pvm :as pvm]
            [harja.ui.grid :as grid]
            [harja.ui.napit :as napit]
            [harja.ui.lomake :as lomake]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :as kentat]
            [harja.ui.debug :refer [debug]]
            [harja.views.urakka.muutokset.yhteiset :as yhteiset]
            [harja.tiedot.urakka.muutokset.kirjatut-muutokset-tiedot :as t-kirjatut]
            [harja.ui.yleiset :as yleiset]))

(defn- uusi-tehtava-id [tehtavat-ja-maarat-valittuna-hoitovuonna]
  (dec (apply min
         (conj (map :tehtava tehtavat-ja-maarat-valittuna-hoitovuonna) -1))))

(defn- pysyvan-muutoksen-vetolaatikko
  "Piirtää jatkuvan muutoksen taulukkoon vetolaatikon, jolla hallitaan kustannus- ja tehtävämuutoksia."
  [e! urakan-hoitokaudet {:keys [toimenpideinstanssi kustannusvaikutukset tehtavat_ja_maarat toimenpidekoodi] :as rivi}
   {:keys [hoitovuosi toimenpiteiden-tehtavat] :as muokattava-muutos}]
  (let [g (grid/grid-ohjaus)]
    (fn [e! urakan-hoitokaudet {:keys [toimenpideinstanssi kustannusvaikutukset tehtavat_ja_maarat toimenpidekoodi] :as rivi}
         {:keys [hoitovuosi toimenpiteiden-tehtavat] :as muokattava-muutos}]
      (let [valittu-hoitovuoden-alkuvuosi (pvm/vuosi (first (:hoitovuosi muokattava-muutos)))
            tehtavat-ja-maarat-valittuna-hoitovuonna (filter #(= valittu-hoitovuoden-alkuvuosi
                                                                (:hoitokauden_alkuvuosi %))
                                                       (:tehtavat_ja_maarat rivi))
            muutos-valittuna-hoitovuonna (or
                                           (:summa (first (filter #(= valittu-hoitovuoden-alkuvuosi
                                                                     (:hoitokauden_alkuvuosi %))
                                                            (:kustannusvaikutukset rivi))))
                                           0)
            toimenpiteen-tehtavat (filter #(and
                                             ;; tehtävien käsittelyä ehkä liikaa frontissa. Jos haluat parantaa,
                                             ;; harkitse esim. palveluhakua on-demand, ja mahdollisesti hoiy
                                             (= (:toimenpidekoodi %) (:toimenpidekoodi rivi))
                                             (= (:hoitokauden-alkuvuosi %) valittu-hoitovuoden-alkuvuosi))
                                    (:toimenpiteiden-tehtavat muokattava-muutos))]

        [:span
         [:h3 "Vaikutus tehtävämääriin"]
         [:p (str (:toimenpide rivi) ", "
               (fmt/hoitokauden-jarjestysluku-ja-vuodet (:hoitovuosi muokattava-muutos)
                 urakan-hoitokaudet
                 "Hoitovuosi"))]

         ;; Pakota uudelleenrenderöinti, jotta uudet tiedot valuvat :muutos-callbackiin ja :valinnat-fn:iin
         ^{:key (str valittu-hoitovuoden-alkuvuosi "_"
                  ;; Älä seuraa koko vektorin sisältöä, vaan counttia, jotta ei jatkuvasti renderöidä uudelleen
                  ;; input-kenttien muuttuessa
                  (count tehtavat-ja-maarat-valittuna-hoitovuonna))}
         [grid/grid
          {:luokat ["vaikutus-tehtaviin-grid"]
           :tunniste :tehtava
           :tyhja "Ei tietoja"
           :muokkaa-aina true
           :voi-lisata? true
           :voi-kumota? false
           :voi-muokata? true
           ;; Otetaan gridin oma poisto-toiminto käytöstä, koska tehdään se erikseen kustomoidulla napilla
           :voi-poistaa? (constantly false)
           :ohjaus g
           :uusi-rivi (fn [rivi]
                        ;; Mahdollista useamman uuden rivin luonti kerralla tekemällä lisää :tehtava-id:itä jokaista
                        ;; uutta riviä kohden.
                        ;; Käytättäjän valitua tehtävän, korvataan negatiivinen tehtävä-id oikealla tehtävän id:llä.
                        (let [uusi-id (uusi-tehtava-id tehtavat-ja-maarat-valittuna-hoitovuonna)]
                          (assoc rivi :uusi? true :tehtava uusi-id)))
           :muutos (fn [grid]
                     ;; Jokaisesta muutoksesta taulukkoon tulee eventti tähän, joka käsitellään tuck-eventissä.
                     ;; Muutoksista tehdään kooste-kokoelma tallennusta varten lomakkeen tilaan.
                     (let [rivit (map #(merge (val %)
                                         {:hoitokauden_alkuvuosi valittu-hoitovuoden-alkuvuosi})
                                   (grid/hae-muokkaustila grid))]
                       (e! (t-kirjatut/->PaivitaToimenpiteenTehtavamaarat
                             (:toimenpideinstanssi rivi)
                             valittu-hoitovuoden-alkuvuosi
                             rivit))))}

          ;; Taulukon kentät
          [{:otsikko "Tehtävä"
            :nimi :tehtava
            :tyyppi :valinta
            ;; Varmistetaan, että useammalle riville ei voi valita samaa tehtävää
            :valinnat-fn (fn [rivi]
                           (let [valittu-tehtava (:tehtava rivi)
                                 ;; Valitut tehtävät kaikilla muilla riveillä
                                 valitut-tehtavat (into #{} (map :tehtava tehtavat-ja-maarat-valittuna-hoitovuonna))]
                             (filter (fn [{:keys [tehtava-id]}]
                                       ;; Sallitaan valita tehtävä jos se on jo valittu omalle riville tai
                                       ;; tai on vielä valitsematta muilla riveillä
                                       (or (= tehtava-id valittu-tehtava)
                                         (not (contains? valitut-tehtavat tehtava-id))))
                               toimenpiteen-tehtavat)))
            :leveys 20
            :valinta-arvo :tehtava-id
            :valinta-nayta :tehtava}

           {:otsikko "Yksikkö"
            :nimi :yksikko
            :tyyppi :string
            :leveys 6
            :muokattava? (constantly false)
            :hae (fn [rivi]
                   ;; Haetaan tieto tehtävän määräyksiköstä toimenpiteiden tehtävistä
                   (some #(when (= (:tehtava-id %)
                                  (:tehtava rivi))
                            (:yksikko %))
                     (:toimenpiteiden-tehtavat muokattava-muutos)))}

           {:otsikko "Hoitovuosi"
            :nimi :hoitokauden_alkuvuosi
            :tyyppi :positiivinen-numero
            :leveys 5
            :muokattava? (constantly false)}

           {:otsikko "Suunniteltu määrä"
            :nimi :edellinen_maara
            :tyyppi :positiivinen-numero
            :leveys 10
            :muokattava? (constantly false)}

           {:otsikko "Määrämuutos (+/-)"
            :nimi :maaramuutos
            :tyyppi :numero
            :leveys 20}

           {:otsikko "Muuttunut määrä"
            :nimi :muuttunut-maara
            :tyyppi :numero
            :leveys 20
            :muokattava? (constantly false)
            :hae (fn [rivi] (+ (:suunniteltu-maara rivi) (:maaramuutos rivi)))}

           ;; Kustomoitu poisto-nappi, joka korvaa gridin oman poisto-toiminnon
           {:otsikko ""
            :nimi :toiminnot
            :tyyppi :komponentti
            :leveys 9
            :komponentti (fn [poistettu-rivi]
                           [napit/nappi "Poista rivi"
                            #(e! (t-kirjatut/->MerkitseTehtavanMaaramuutosPoistetuksi
                                   (:toimenpideinstanssi rivi)
                                   (:tehtava poistettu-rivi)
                                   valittu-hoitovuoden-alkuvuosi
                                   true))
                            {:ikoni (ikonit/livicon-trash)
                             :luokka "nappi-toissijainen"}])}]
          tehtavat-ja-maarat-valittuna-hoitovuonna]

         [:h4 "Vaikutus tavoitehintaan"]

         [:label {:for (str "tavoitehintainput-" (:toimenpideinstanssi rivi)) :class "tavoitehinta-label"}
          "Tavoitehinnan muutos euroina (+/-)"]
         [kentat/tee-kentta {:elementin-id (str "tavoitehintainput-" (:toimenpideinstanssi rivi))
                             :tyyppi :numero :fmt fmt/euro-opt
                             :pakollinen? true :input-luokka "tavoitehinnan-muutos-input"
                             :placeholder "Syötä hintavaikutus"}
          (r/wrap muutos-valittuna-hoitovuonna
            (fn [summa]
              (e! (t-kirjatut/->PaivitaToimenpiteenTavoitehinnanMuutos
                    (:toimenpideinstanssi rivi)
                    (pvm/vuosi (first (:hoitovuosi muokattava-muutos)))
                    summa))))]]))))

(defn- grid-pysyvan-muutoksen-vaikutukset*
  [vetolaatikkorivit hoitovuosi toimenpiteiden-tiedot]
  (fn [vetolaatikkorivit hoitovuosi toimenpiteiden-tiedot]
    [grid/grid
     {:tunniste :toimenpideinstanssi
      :luokat ["pysyvan-muutoksen-grid"]
      :muokkaa-aina false
      :vetolaatikot vetolaatikkorivit
      :piilota-toiminnot? true
      :voi-lisata? false
      :voi-kumota? false
      :voi-poistaa? (constantly false)
      :voi-muokata? true}

     [{:tyyppi :vetolaatikon-tila :leveys 2}
      {:otsikko "Toimenpide"
       :nimi :toimenpide
       :tyyppi :string
       :leveys 20
       :muokattava? (constantly false)}

      {:otsikko "Suunniteltu kustannus (€)"
       :muokattava? (constantly false)
       :nimi :budjetoitu_summa
       :vaadi-ei-negatiivinen? true
       :tyyppi :numero
       :fmt fmt/euro-opt
       :tasaa :oikea
       :leveys 8
       :hae (fn [rivi]
              (:budjetoitu_summa (first (filter #(when hoitovuosi
                                                   (= (pvm/vuosi (first hoitovuosi))
                                                     (:hoitokauden_alkuvuosi %)))
                                          (get rivi :budjetoidut_summat)))))}

      {:otsikko "Tavoitehinnan muutos (€)"
       :muokattava? (constantly false)
       :nimi :tavoitehinnan-muutos
       :tyyppi :numero
       :fmt fmt/euro-opt
       :tasaa :oikea
       :leveys 8
       :solun-luokka #(str "tavoitehinnan-muutos-sarake")
       :hae (fn [rivi]
              (:summa (first (filter #(when hoitovuosi
                                        (= (pvm/vuosi (first hoitovuosi))
                                          (:hoitokauden_alkuvuosi %)))
                               (get rivi :kustannusvaikutukset)))))}

      {:otsikko "Muuttunut kustannus (€)"
       :muokattava? (constantly false)
       :nimi :muuttunut-kustannus
       :vaadi-ei-negatiivinen? true
       :tyyppi :numero
       :fmt fmt/euro-opt
       :tasaa :oikea
       :leveys 8
       :hae (fn [rivi]
              (let [budjetoitu (:budjetoitu_summa (first (filter #(when hoitovuosi
                                                                    (= (pvm/vuosi (first hoitovuosi))
                                                                      (:hoitokauden_alkuvuosi %)))
                                                           (get rivi :budjetoidut_summat))))
                    muutos (:summa (first (filter #(when hoitovuosi
                                                     (= (pvm/vuosi (first hoitovuosi))
                                                       (:hoitokauden_alkuvuosi %)))
                                            (get rivi :kustannusvaikutukset))))]
                (when (and budjetoitu (number? budjetoitu) muutos (number? muutos))
                  (+ budjetoitu muutos))))}]
     toimenpiteiden-tiedot]))

(defn taulukko-pysyvan-muutoksen-vaikutukset
  [e! {:keys [urakan-hoitokaudet muokattava-muutos] :as app}]
  (let [vetolaatikkorivit (into {}
                            (map (juxt :toimenpideinstanssi
                                   (fn [rivi]
                                     [pysyvan-muutoksen-vetolaatikko e!
                                      urakan-hoitokaudet
                                      (select-keys rivi [:toimenpideinstanssi
                                                         :toimenpide
                                                         :toimenpidekoodi
                                                         :kustannusvaikutukset
                                                         :tehtavat_ja_maarat])
                                      (select-keys muokattava-muutos [:hoitovuosi :toimenpiteiden-tehtavat :tehtavat_ja_maarat])]

                                     #_[pysyvan-muutoksen-vetolaatikko-old e! app rivi]))
                              (:toimenpiteiden-tiedot muokattava-muutos)))]
    [:div.toimenpiteiden-tiedot
     ;; Näytä debug-info kehittäjille
     [debug muokattava-muutos]

     ;; Header vihje sekä nappi
     [:div.pysyvan-muutoksen-grid-header
      [yleiset/vihje "Valitse toimenpiteet, joita muutos koskee."]

      [napit/nappi "Kopioi tiedot tuleville hoitovuosille"
       #(e! (t-kirjatut/->KopioiHoitovuodenMuutoksetTulevilleHoitovuosille
              (pvm/vuosi (first (:hoitovuosi muokattava-muutos)))
              (:mahdolliset-hoitovuodet-lomakkeella muokattava-muutos)))
       {:ikoni (ikonit/action-copy)
        ;; Disabloi nappi, koska toiminnallisuus ei ole vielä toteutettu
        :disabled false
        :luokka "nappi-toissijainen pysyvan-muutoksen-kopiointinappi"}]]

     [grid-pysyvan-muutoksen-vaikutukset*
      vetolaatikkorivit (:hoitovuosi muokattava-muutos) (:toimenpiteiden-tiedot muokattava-muutos)]]))


(defn lomake-pysyva
  "Pysyvän muutoksen lomakekomponentti"
  [e! {:keys [urakan-hoitokaudet muokattava-muutos valittu-hoitokausi] :as app}]
  [{:tyyppi :komponentti
    :uusi-rivi? true
    :komponentti (fn [_rivi]
                   [:div.perustiedot
                    [yleiset/info-laatikko :neutraali
                     "Pysyvä muutos vaikuttaa kaikkiin tuleviin hoitovuosiin."]])}

   (lomake/ryhma {:otsikko "Perustiedot"}
     {:nimi :nimi
      :otsikko "Nimi"
      :tyyppi :string
      :uusi-rivi? true
      :pakollinen? true
      :validoi [#(when (nil? (seq %)) "Syötä nimi")]
      ::lomake/col-luokka "perustiedot col-sm-6"}

     (yhteiset/+rivi-muutoksen-syy+)
     (yhteiset/+rivi-muutos-voimassa+ urakan-hoitokaudet valittu-hoitokausi))

   (lomake/ryhma {:otsikko "Vaikutus tavoitehintaan ja suunniteltuihin tehtäviin"}

     {:otsikko "Hoitovuosi"
      :nimi :hoitovuosi
      :kaariva-luokka "hoitovuosi-valinta"
      :tarkenne #(str
                   "Oltava lomakkeelle asetetun "
                   "'Voimassa alkaen' -pvm:n jälkeen")
      :tyyppi :valinta
      :valinnat (or (:mahdolliset-hoitovuodet-lomakkeella muokattava-muutos) [])
      :valinta-nayta #(if %
                        (fmt/hoitokauden-jarjestysluku-ja-vuodet % urakan-hoitokaudet "Hoitovuosi")
                        "Valitse")
      :valinta-arvo identity}

     ;; Taulukko jossa vaikutuksia voidaan syöttää
     {:otsikko ""
      :uusi-rivi? true
      :nimi :taulukko-pysyvan-muutoksen-vaikutukset
      :tyyppi :komponentti
      :komponentti (fn [rivi]
                     [taulukko-pysyvan-muutoksen-vaikutukset e! app])})

   (first (yhteiset/liite-kentta e! app))])
