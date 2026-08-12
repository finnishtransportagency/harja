(ns harja.views.urakka.muutokset.lomake.lomake-pysyva
  "Muutokset välilehden lomakkeet - Pysyvä muutos"
  (:require
    [reagent.core :as r]
    [harja.fmt :as fmt]
    [harja.pvm :as pvm]
    [harja.ui.grid :as grid]
    [harja.ui.napit :as napit]
    [harja.ui.lomake :as lomake]
    [harja.ui.ikonit :as ikonit]
    [harja.ui.kentat :as kentat]
    [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]

    [harja.domain.muutos-domain :as muutos-domain]
    [harja.views.urakka.muutokset.yhteiset :as yhteiset]
    [harja.tiedot.urakka.muutokset.kirjatut-muutokset-tiedot :as t-kirjatut]
    [harja.ui.yleiset :as yleiset]))

(defn- uusi-tehtava-id [tehtavat-ja-maarat-valittuna-hoitovuonna]
  (dec (apply min
         (conj (map :tehtava tehtavat-ja-maarat-valittuna-hoitovuonna) -1))))

(defn- hae-elementti-hoitokauden-alkuvuodella
  [hoitovuosi sekvenssi]
  (let [alkuvuosi (some-> hoitovuosi (first) (pvm/vuosi))]
    (some #(when (= alkuvuosi (:hoitokauden_alkuvuosi %)) %) sekvenssi)))

(defn- hae-tehtavan-suunniteltu-maara
  [muokattava-muutos rivi]
  ;; Suunniteltu määrä haetaan riville suoraan tietokantakyselyllä olemassa olevalle pysyvälle muutokselle
  ;; = Nopeampi
  (or (:suunniteltu_maara rivi)
    ;; Kun tehdään uutta pysyvää muutosta, etsitään suunniteltu määrä toimenpiteiden tehtävien joukosta
    (some #(when (= (:tehtava-id %)
                   (:tehtava rivi))
             (:suunniteltu-maara %))
      (:toimenpiteiden-tehtavat muokattava-muutos))))

(defn- pysyvan-muutoksen-vetolaatikko
  "Piirtää jatkuvan muutoksen taulukkoon vetolaatikon, jolla hallitaan kustannus- ja tehtävämuutoksia."
  [e! urakan-hoitokaudet rivi muokattava-muutos voi-muokata?]
  (let [g (grid/grid-ohjaus)]
    (fn [e! urakan-hoitokaudet rivi muokattava-muutos voi-muokata?]
      (let [valittu-hoitovuoden-alkuvuosi (some-> (:hoitovuosi muokattava-muutos) (first) (pvm/vuosi))
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
                  (count tehtavat-ja-maarat-valittuna-hoitovuonna) "_"
                  voi-muokata?)}
         [grid/grid
          {:luokat ["vaikutus-tehtaviin-grid"]
           :tunniste :tehtava
           :tyhja "Ei tietoja"
           :muokkaa-aina true
           :voi-lisata? voi-muokata?
           :voi-kumota? false
           :voi-muokata? voi-muokata?
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
            :elementin-id #(str (:tehtava-id %) (gensym))
            :nimi :tehtava
            :tyyppi :valinta
            :muokattava? (constantly voi-muokata?)
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
            :leveys 25
            :valinta-arvo :tehtava-id
            :valinta-nayta :tehtava}

           {:otsikko "Yksikkö"
            :nimi :yksikko
            :tyyppi :string
            :leveys 6
            :tasaa :oikea
            :muokattava? (constantly false)
            :hae (fn [rivi]
                   ;; Haetaan tieto tehtävän määräyksiköstä toimenpiteiden tehtävistä
                   (some #(when (= (:tehtava-id %)
                                  (:tehtava rivi))
                            (:yksikko %))
                     (:toimenpiteiden-tehtavat muokattava-muutos)))}

           {:otsikko "Suunniteltu määrä"
            :nimi :suunniteltu_maara
            :tyyppi :positiivinen-numero
            :leveys 10
            :tasaa :oikea
            :muokattava? (constantly false)
            :hae (fn [rivi]
                   (hae-tehtavan-suunniteltu-maara muokattava-muutos rivi))}

           {:otsikko "Määrämuutos (+/-)"
            :nimi :maaramuutos
            :tyyppi :numero
            :tasaa :oikea
            :leveys 10
            :muokattava? (constantly voi-muokata?)}

           {:otsikko "Muuttunut määrä"
            :nimi :muuttunut-maara
            :tyyppi :numero
            :tasaa :oikea
            :leveys 10
            :muokattava? (constantly false)
            :hae (fn [rivi]
                   (fmt/desimaaliluku
                     (+ (or (hae-tehtavan-suunniteltu-maara muokattava-muutos rivi) 0) (:maaramuutos rivi))
                     2))}

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
                             :luokka "nappi-toissijainen"
                             :disabled (not voi-muokata?)}])}]
          tehtavat-ja-maarat-valittuna-hoitovuonna]

         [:h4 "Vaikutus tavoitehintaan"]

         [:label {:for (str "tavoitehintainput-" (:toimenpideinstanssi rivi)) :class "tavoitehinta-label"}
          "Tavoitehinnan muutos euroina (+/-)"]
         [kentat/tee-kentta {:elementin-id (str "tavoitehintainput-" (:toimenpideinstanssi rivi))
                             :tyyppi :numero :fmt fmt/euro-opt
                             :disabled? (not voi-muokata?)
                             :pakollinen? true
                             :input-luokka "tavoitehinnan-muutos-input"
                             :placeholder "Syötä hintavaikutus"}
          (r/wrap muutos-valittuna-hoitovuonna
            (fn [summa]
              (e! (t-kirjatut/->PaivitaToimenpiteenTavoitehinnanMuutos
                    (:toimenpideinstanssi rivi)
                    (some-> (:hoitovuosi muokattava-muutos) (first) (pvm/vuosi))
                    summa))))]

         ;; Jos halutaan tallentaa tavoitehinnan muutos ilman tehtävämuutoksia, vaaditaan tähän jokin syy
         (let [kv-valittuna-hoitovuonna (filter #(= valittu-hoitovuoden-alkuvuosi (:hoitokauden_alkuvuosi %))
                                          (:kustannusvaikutukset rivi))

               kv-valittuna-hoitovuonna (when (some? kv-valittuna-hoitovuonna)
                                          (first kv-valittuna-hoitovuonna))

               tehtavamaaramuutos-kirjattu? (-> kv-valittuna-hoitovuonna :tehtavamaaramuutos-kirjattu?)
               ei-tehtavamuutoksia-syy (-> kv-valittuna-hoitovuonna :syy)]

           [:div.tehtava-vaikutus-valinta.padding-top-16
            [kentat/tee-kentta {:tyyppi :checkbox
                                :teksti "Pysyvä muutos ei vaikuta tehtävä- ja määräluettelon määriin"
                                :valitse! #(e! (t-kirjatut/->PaivitaTehtavavaikutus rivi valittu-hoitovuoden-alkuvuosi))}
             (if (some? tehtavamaaramuutos-kirjattu?)
               (not tehtavamaaramuutos-kirjattu?)
               false)]

            (when
              (false? tehtavamaaramuutos-kirjattu?)
              [:div.padding-top-16
               [kentat/tee-otsikollinen-kentta {:otsikko "Kerro miksi tavoitehinta muuttuu mutta tehtävämäärät eivät muutu"
                                                :kentta-params {:tyyppi :text :validoi [#(when (nil? (seq %)) "Syötä muutoksen syy")]}
                                                :arvo-atom (r/wrap ei-tehtavamuutoksia-syy
                                                             #(e! (t-kirjatut/->PaivitaTehtavavaikutusSyy
                                                                    (:toimenpideinstanssi rivi)
                                                                    %
                                                                    valittu-hoitovuoden-alkuvuosi)))
                                                :luokka ""}]])])]))))

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
      :voi-muokata? true
      :rivi-jalkeen-fn (fn [rivit]
                         (let [tavoitehinnan-muutokset (map (fn [rivi]
                                                              (or (some->
                                                                    (hae-elementti-hoitokauden-alkuvuodella
                                                                      hoitovuosi
                                                                      (get rivi :kustannusvaikutukset))
                                                                    :summa) 0))
                                                         rivit)
                               tavoitehinnan-muutokset-yhteensa (apply + tavoitehinnan-muutokset)]
                           [{:teksti "" :luokka "yhteensa" :leveys 5 :sarakkeita 1}
                            {:teksti "Yhteensä" :luokka "yhteensa" :leveys 15 :sarakkeita 1}
                            {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                            {:teksti (fmt/euro-opt false true tavoitehinnan-muutokset-yhteensa) :luokka "yhteensa" :leveys 8 :tasaa :oikea}
                            {:teksti "" :luokka "yhteensa" :leveys 8 :tasaa :oikea}]))}

     [{:tyyppi :vetolaatikon-tila :leveys 3}
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
              (:budjetoitu_summa (hae-elementti-hoitokauden-alkuvuodella hoitovuosi (get rivi :budjetoidut_summat))))}

      {:otsikko "Tavoitehinnan muutos (€)"
       :muokattava? (constantly false)
       :nimi :tavoitehinnan-muutos
       :tyyppi :numero
       :fmt (partial fmt/euro-opt false true)
       :tasaa :oikea
       :leveys 8
       :solun-luokka #(str "tavoitehinnan-muutos-sarake")
       :hae (fn [rivi]
              (:summa (hae-elementti-hoitokauden-alkuvuodella hoitovuosi (get rivi :kustannusvaikutukset))))}

      {:otsikko "Muuttunut kustannus (€)"
       :muokattava? (constantly false)
       :nimi :muuttunut-kustannus
       :vaadi-ei-negatiivinen? true
       :tyyppi :numero
       :fmt (partial fmt/euro-opt false false)
       :tasaa :oikea
       :leveys 8
       :hae (fn [rivi]
              (let [budjetoitu (:budjetoitu_summa (hae-elementti-hoitokauden-alkuvuodella hoitovuosi (get rivi :budjetoidut_summat)))
                    muutos (:summa (hae-elementti-hoitokauden-alkuvuodella hoitovuosi (get rivi :kustannusvaikutukset)))]
                (when (and budjetoitu (number? budjetoitu) muutos (number? muutos))
                  (+ budjetoitu muutos))))}]
     toimenpiteiden-tiedot]))

(defn- kopioi-tuleville-hoitovuosille! [e! muokattava-muutos]
  (e! (t-kirjatut/->KopioiHoitovuodenMuutoksetTulevilleHoitovuosille
        (some-> (:hoitovuosi muokattava-muutos) (first) (pvm/vuosi))
        (:voimassa_alkaen muokattava-muutos)
        (:mahdolliset-hoitovuodet-lomakkeella muokattava-muutos))))

(defn taulukko-pysyvan-muutoksen-vaikutukset
  [e! {:keys [urakan-hoitokaudet muokattava-muutos budjettitavoitteet] :as _app}]
  (let [hoitovuosi (:hoitovuosi muokattava-muutos)
        voimassa-alkaen (:voimassa_alkaen muokattava-muutos)
        {:keys [tavoitehinta-indeksikorjattu-per-hoitovuosi]} budjettitavoitteet
        hoitovuosi-lukittu? (muutos-domain/pysyva-muutos-hoitovuosi-lukittu? tavoitehinta-indeksikorjattu-per-hoitovuosi voimassa-alkaen hoitovuosi)
        voi-muokata? (and
                       ;; Voi muokata, jos "voimassa alkaen" osuu johonkin hoitovuoteen tai hoitovuosi on sen jälkeen
                       (muutos-domain/voimassa-alkaen-hoitovuodella-tai-jalkeen? voimassa-alkaen hoitovuosi)
                       ;; Voi muokata, jos hoitovuosi ei ole vielä lukittu
                       (not hoitovuosi-lukittu?))

        vetolaatikkorivit (into {}
                            (map (juxt :toimenpideinstanssi
                                   (fn [rivi]
                                     [pysyvan-muutoksen-vetolaatikko e!
                                      urakan-hoitokaudet
                                      (select-keys rivi [:toimenpideinstanssi
                                                         :toimenpide
                                                         :toimenpidekoodi
                                                         :kustannusvaikutukset
                                                         :tehtavat_ja_maarat
                                                         :syy
                                                         :tehtavamaaramuutos-kirjattu?])
                                      (select-keys muokattava-muutos [:hoitovuosi
                                                                      :toimenpiteiden-tehtavat
                                                                      :tehtavat_ja_maarat])
                                      voi-muokata?]))
                              (:toimenpiteiden-tiedot muokattava-muutos)))]
    [:div.toimenpiteiden-tiedot
     ;; Näytä debug-info kehittäjille
     #_[debug muokattava-muutos]

     ;; Header vihje sekä nappi
     [:div.pysyvan-muutoksen-grid-header
      (if voi-muokata?
        [yleiset/vihje "Valitse toimenpiteet, joita muutos koskee."]
        [yleiset/vihje (str "Hoitovuoden tietoja ei voi muokata. "
                         (when (not (muutos-domain/voimassa-alkaen-hoitovuodella-tai-jalkeen? voimassa-alkaen hoitovuosi))
                           "Voimassa alkaen-päivämäärä ei ole valitulla hoitovuodella."))])

      [napit/nappi "Kopioi tiedot tuleville hoitovuosille"
       (fn []
         (if (t-kirjatut/pysyvia-muutoksia-tulevilla-hoitovuosilla? hoitovuosi muokattava-muutos)
           (varmista-kayttajalta/varmista-kayttajalta
             {:otsikko "Tulevilla hoitovuosilla on jo tietoja"
              :sisalto [:div "Tulevilla hoitovuosilla on jo tietoja. Ylikirjoitetaanko tiedot valitun hoitovuoden tiedoilla?"
                        [:div "Ylikirjoitetut tiedot menetetään, kun tallennat lomakkeen."]]
              :hyvaksy "Ylikirjoita"
              :toiminto-fn #(kopioi-tuleville-hoitovuosille! e! muokattava-muutos)})
           (kopioi-tuleville-hoitovuosille! e! muokattava-muutos)))
       {:ikoni (ikonit/action-copy)
        :luokka "nappi-toissijainen pysyvan-muutoksen-kopiointinappi"}]]

     [grid-pysyvan-muutoksen-vaikutukset*
      vetolaatikkorivit
      (:hoitovuosi muokattava-muutos)
      (:toimenpiteiden-tiedot muokattava-muutos)]]))


(defn lomake-pysyva
  "Pysyvän muutoksen lomakekomponentti"
  [e! {:keys [urakan-hoitokaudet muokattava-muutos budjettitavoitteet
              haku-kaynnissa? muutoksen-tiedot-haku-kaynnissa? valittu-hoitokausi] :as app}]

  (let [muokataan? (:id muokattava-muutos)
        voimassa-alkaen (:voimassa_alkaen muokattava-muutos)
        hoitovuosi (:hoitovuosi muokattava-muutos)
        {:keys [tavoitehinta-indeksikorjattu-per-hoitovuosi]} budjettitavoitteet
        ;; Voimassa-alkaen lukitus tarkastatetaan vain muokkaustilanteessa
        voimassa-alkaen-lukittu? (if muokataan?
                                   (muutos-domain/pysyva-muutos-voimassa-alkaen-lukittu? tavoitehinta-indeksikorjattu-per-hoitovuosi)
                                   false)]
    [(lomake/ryhma {:otsikko "Perustiedot"}
       (yhteiset/+rivi-muutoksen-syy+)

       (when
         voimassa-alkaen-lukittu?
         {:uusi-rivi? true
          :tyyppi :komponentti
          :komponentti (fn [_]
                         [yleiset/info-laatikko :vahva-ilmoitus
                          "Hoitovuoden alun tavoitehintoja on vahvistettu"
                          "Peru vahvistukset muokataksesi voimassa alkaen päivämäärää."
                          nil
                          {:ikoni-fn #(ikonit/harja-icon-status-alert)}])})

       (yhteiset/+rivi-muutos-voimassa+ urakan-hoitokaudet valittu-hoitokausi
         {:pakota-valittuun-hoitokauteen? false

          :voi-muokata? (not voimassa-alkaen-lukittu?)})

       ;; -- Info-laatikot --
       (when (muutos-domain/muutos-voimassa-kesken-hoitokauden? voimassa-alkaen hoitovuosi)
         {:tyyppi :komponentti
          :uusi-rivi? true
          :komponentti (fn [_rivi]
                         [:div.perustiedot
                          [yleiset/info-laatikko :neutraali
                           [:<>
                            [:p [:b "Muutoksen voimassaolo alkaa kesken hoitovuoden"]]
                            [:div "Kun tallennat tiedot, ne käsitellään seuraavasti:"]
                            [:ul
                             [:li
                              "Ensimmäisen hoitovuoden tavoitehinnan muutos lisätään hoitovuoden lopun "
                              "tavoitehintaan ilman indeksikorjausta."]
                             [:li
                              "Seuraavien hoitovuosien osalta tavoitehinnan muutokset siirtyvät automaattisesti "
                              "Hoitovuoden alun tavoitehinta -välilehdelle indeksikorjattavaksi."]]]]])})


       ;; -- Liitekenttä --
       (first (yhteiset/liite-kentta e! app)))

     ;; --

     ;; Jakaja
     {:tyyppi :komponentti
      :uusi-rivi? true
      :komponentti (fn [_rivi] [:hr])}

     ;; --

     ;; -- Vaikutukset tavoitehintaan ja suunniteltuihin tehtäviin --
     (lomake/ryhma {:otsikko "Vaikutus tavoitehintaan ja suunniteltuihin tehtäviin"}
       {:otsikko "Hoitovuosi"
        :nimi :hoitovuosi
        :kaariva-luokka "hoitovuosi-valinta"
        :tyyppi :valinta
        :valinnat (or (:mahdolliset-hoitovuodet-lomakkeella muokattava-muutos) [])
        :valinta-nayta #(if %
                          (fmt/hoitokauden-jarjestysluku-ja-vuodet % urakan-hoitokaudet "Hoitovuosi")
                          "Valitse")
        :valinta-arvo identity}

       (when
         ;; Jos voimassa-alkaen on validi, ja hoitovuosi on lukittu, näytetään info-laatikko lukituksesta
         (and
           (muutos-domain/voimassa-alkaen-hoitovuodella-tai-jalkeen? voimassa-alkaen hoitovuosi)
           (muutos-domain/pysyva-muutos-hoitovuosi-lukittu? tavoitehinta-indeksikorjattu-per-hoitovuosi voimassa-alkaen hoitovuosi))

         ;; TODO: Välikatselmuksen päätökset lukituksen tarkastus toteutetaan myöhemmin, HARJA-1767
         ;;       Välikatselmuksesta johtuvan lukituksen yhteydessä näytetään erilainen info-laatikko, ks. figma
         {:uusi-rivi? true
          :tyyppi :komponentti
          :komponentti (fn [_]
                         (let [hoitovuosi-str (fmt/hoitokauden-jarjestysluku-ja-alku-ja-loppupvm
                                                (some-> (:hoitovuosi muokattava-muutos) (first) (pvm/vuosi))
                                                (map #(some-> % (first) (pvm/vuosi)) urakan-hoitokaudet) "hoitovuoden"
                                                false)]
                           [yleiset/info-laatikko :vahva-ilmoitus
                            "Hoitovuoden alun tavoitehinta on vahvistettu"
                            [:<>
                             [:div hoitovuosi-str " osalta pysyvän muutoksen tavoitehintavaikutukset sisältyät jo vahvistettuun hoitovuoden alun tavoitehintaan." [:br]
                              "Jos haluat tehdä muutoksia, vahvistus on peruttava ja tehtävä uudelleen."]
                             [:br]
                             [:div [napit/yleinen-toissijainen "Peru vahvistus"
                                    #(e! (t-kirjatut/->PeruutaTavoiteJaKattohinta
                                           (some-> (:hoitovuosi muokattava-muutos) (first) (pvm/vuosi))))]]]
                            nil
                            {:ikoni-fn #(ikonit/harja-icon-status-alert)}]))})

       (if muutoksen-tiedot-haku-kaynnissa?
         {:tyyppi :komponentti
          :uusi-rivi? true
          :komponentti (fn [_rivi]
                         [yleiset/ajax-loader "Haetaan muutoksen tietoja..."])}

         (if hoitovuosi
           ;; Taulukko jossa vaikutuksia voidaan syöttää
           {:otsikko ""
            :uusi-rivi? true
            :nimi :taulukko-pysyvan-muutoksen-vaikutukset
            :kaariva-luokka "pysyva-muutos-vaikutukset-lomake-wrapper"
            :tyyppi :komponentti
            :komponentti (fn [rivi]
                           [taulukko-pysyvan-muutoksen-vaikutukset e! app])}
           {:tyyppi :komponentti
            :uusi-rivi? true
            :komponentti (fn [_rivi]
                           [:div.perustiedot
                            [yleiset/info-laatikko :neutraali
                             "Valitse hoitovuosi, jotta voit tehdä pysyvän muutoksen."]])})))]))
