(ns harja.domain.kulut.kustannusten-seuranta
  "Kustannusten seurannan datan prosessoinnin apurit")

;; Raportin pääryhmät jäsennettynä samaan järjestykseen, kuin ui suunnitelmissa on tarkoitettu
(def raportin-paaryhmat
  ["hankintakustannukset", "johto-ja-hallintakorvaus", "hoidonjohdonpalkkio", "erillishankinnat", "rahavaraukset",
   "bonukset", "siirto", "tavoitehinnanoikaisu", "tavoitepalkkio", "tavoitehinnan-ylitys", "kattohinnan-ylitys", "sanktiot", "ulkopuoliset-rahavaraukset"])

(def yhteenvedosta-jatettavat-paaryhmat
  (set (map #(nth raportin-paaryhmat %) [5 8 9 10 11 12])))

(defn- toimenpide-jarjestys [toimenpide]
  (case (first toimenpide)
    "Talvihoito" 1
    "Liikenneympäristön hoito" 2
    "Sorateiden hoito" 3
    "Päällystepaikkaukset" 4
    "MHU Ylläpito" 5
    "MHU Korvausinvestointi" 6
    "MHU Hoidonjohto" 7))

(defn yhdista-totetuneet-ja-budjetoidut [toteutuneet budjetoidut]
  (map
    (fn [[grp-avain arvot]]
      {:toimenpide (:toimenpide (first arvot))
       :toteutunut_summa (reduce + (map :toteutunut_summa arvot))
       :budjetoitu_summa (reduce + (map :budjetoitu_summa arvot))
       :budjetoitu_summa_indeksikorjattu (reduce + (map :budjetoitu_summa_indeksikorjattu arvot))
       :toteutunut (:toteutunut (first arvot))
       :tehtava_nimi (:tehtava_nimi (first arvot))
       :toimenpideryhma "rahavaraus" #_ (:toimenpideryhma (first arvot))

       :maksutyyppi (:maksutyyppi (first arvot))
       :jarjestys (:jarjestys (first arvot))
       :paaryhma (:paaryhma (first arvot))})
    (group-by :tehtava_nimi (concat toteutuneet budjetoidut))))

(defn- summaa-toimenpidetaso
  "Käytetään seuraaville pääryhmille: hankintakustannukset ja rahavaraukset."
  [toimenpiteet paaryhmaotsikko]
  (mapv
    (fn [toimenpide]
      (let [toimenpiteen-tehtavat (second toimenpide)
            ;; Toimenpiteet listassa on budjetoidut ja toteutuneet tehtävät
            ;; UI:lla budjetointi lasketaan yhteen toimenpideryhmän perusteella (esim. hankinnat) ja toimenpiteen perusteella (esim. talvihoito)
            ;; Toteutuneet kustannukset näytetään tehtävittäin ryhmiteltynä.
            ;; Lisätyöt erotellaan omaksi pääryhmäkseen, koska tietokantahaku ei tee siitä omaa pääryhmää automaattisesti.
            ;; Poistetaan siis budjetointiin liittyvät tehtävät :toteutunut = budjetoitu tai hth ja lasketaan lisätyöt yhteen.
            indeksoitavat-tehtavat (filter
                                     (fn [tehtava]
                                       (when (and
                                               (not= "hjh" (:toteutunut tehtava))
                                               (not= "lisatyo" (:maksutyyppi tehtava)))
                                         tehtava))
                                     toimenpiteen-tehtavat)
            toteutuneet-tehtavat (filter
                                   (fn [tehtava]
                                     (when (and
                                             (not= "hjh" (:toteutunut tehtava))
                                             (not= "budjetointi" (:toteutunut tehtava))
                                             (not= "lisatyo" (:maksutyyppi tehtava)))
                                       tehtava))
                                   toimenpiteen-tehtavat)
            budjetoidut-tehtavat (filter
                                   (fn [tehtava]
                                     (when (and
                                             (not= "hjh" (:toteutunut tehtava))
                                             (not= "toteutunut" (:toteutunut tehtava))
                                             (not= "lisatyo" (:maksutyyppi tehtava)))
                                       tehtava))
                                   toimenpiteen-tehtavat)
            yhdistetyt-tehtavat (if (= paaryhmaotsikko "rahavaraukset")
                                  (yhdista-totetuneet-ja-budjetoidut toteutuneet-tehtavat budjetoidut-tehtavat)
                                  toteutuneet-tehtavat)
            jarjestys (some #(:jarjestys %) toimenpiteen-tehtavat)]
        {:paaryhma paaryhmaotsikko
         :toimenpide (first toimenpide)
         :jarjestys jarjestys
         :toimenpide-toteutunut-summa (reduce (fn [summa tehtava]
                                                (+ summa (or (:toteutunut_summa tehtava) 0))) ;; vain toteutuneet tehtävät ilman lisätöitä
                                        0 toteutuneet-tehtavat)
         :toimenpide-budjetoitu-summa (reduce (fn [summa tehtava]
                                                (+ summa (or (:budjetoitu_summa tehtava) 0)))
                                        0 toimenpiteen-tehtavat)
         :toimenpide-budjetoitu-summa-indeksikorjattu (reduce (fn [summa tehtava]
                                                                (+ summa (or (:budjetoitu_summa_indeksikorjattu tehtava) 0)))
                                                        0 toimenpiteen-tehtavat)
         :lisatyot-summa (reduce (fn [summa tehtava]
                                   (if (= "lisatyo" (:maksutyyppi tehtava))
                                     (+ summa (or (:toteutunut_summa tehtava) 0))
                                     summa))
                           0 toimenpiteen-tehtavat)
         :lisatyot (filter (fn [tehtava]
                             (when (= "lisatyo" (:maksutyyppi tehtava))
                               true))
                     toimenpiteen-tehtavat)
         :tehtavat (sort-by :jarjestys yhdistetyt-tehtavat)
         ;; Asetetaan vahvistus-status nulliksi, jos yhtään toteumaa tai budjettia ei ole annettu.
         ;; Päätellään myöhemmin, että näytetäänkö nämä vahvistettuina tai vahvistamattomina
         (keyword (str paaryhmaotsikko "-indeksikorjaus-vahvistettu"))
         (when indeksoitavat-tehtavat
           (every? #(not (nil? (:indeksikorjaus_vahvistettu %))) indeksoitavat-tehtavat))}))
    toimenpiteet))

(defn- summaa-paaryhman-tehtavat
  "Käytetään pääryhmille: Hoidonjohdonpalkkiot, bonus, erillishankinta, siirrot ja tavoitehinnan oikaisut"
  [tehtavat paaryhmaotsikko]
  (let [toteutuneet-tehtavat (filter
                               (fn [tehtava]
                                 (when (and
                                         (not= "hjh" (:toteutunut tehtava))
                                         (not= "budjetointi" (:toteutunut tehtava))
                                         (not= "lisatyo" (:maksutyyppi tehtava)))
                                   tehtava))
                               tehtavat)
        tehtava-map
        {:paaryhma paaryhmaotsikko
         :toimenpide (:toimenpide (first tehtavat))
         :jarjestys (:jarjestys (first tehtavat))
         (keyword (str paaryhmaotsikko "-toteutunut")) (reduce (fn [summa tehtava]
                                                                 (+ summa (or (:toteutunut_summa tehtava) 0)))
                                                         0 toteutuneet-tehtavat) ;; vain toteutuneet tehtävät ilman lisätöitä
         (keyword (str paaryhmaotsikko "-budjetoitu")) (reduce (fn [summa tehtava]
                                                                 (+ summa (or (:budjetoitu_summa tehtava) 0)))
                                                         0 tehtavat)
         (keyword (str paaryhmaotsikko "-budjetoitu-indeksikorjattu")) (reduce (fn [summa tehtava]
                                                                                 (+ summa (or (:budjetoitu_summa_indeksikorjattu tehtava) 0)))
                                                                         0 tehtavat)
         :lisatyot-summa (reduce (fn [summa tehtava]
                                   (if (= "lisatyo" (:maksutyyppi tehtava))
                                     (+ summa (or (:toteutunut_summa tehtava) 0))
                                     summa))
                           0 tehtavat)
         :lisatyot (filter (fn [tehtava]
                             (when (= "lisatyo" (:maksutyyppi tehtava))
                               true))
                     tehtavat)
         :tehtavat (sort-by :jarjestys toteutuneet-tehtavat)
         ;; Asetetaan vahvistus-status nulliksi, jos yhtään toteumaa tai budjettia ei ole annettu.
         ;; Päätellään myöhemmin, että näytetäänkö nämä vahvistettuina tai vahvistamattomina
         (keyword (str paaryhmaotsikko "-indeksikorjaus-vahvistettu"))
         (when tehtavat
           (every? #(not (nil? (:indeksikorjaus_vahvistettu %))) tehtavat))}]
    tehtava-map))

(defn- summaa-hoito-ja-hallinta-tehtavat [tehtavat paaryhmaotsikko]
  (let [;; Toimenpiteet mäpissä on budjetoidut ja toteutuneet toimenpiteet
        ;; UI:lla budjetointi lasketaan yhteen  ja toteutuneet kustannukset näytetään
        ;; rivikohtaisesti.
        ;; Poistetaan siis budjetointiin liittyvät tehtävät :toteutunut = budjetoitu tai hth
        ;; Jaotellaan tehtävät joko palkkoihin tai toimistokuluihin
        palkkatehtavat (filter (fn [tehtava]
                                 (when (= "palkat" (:toimenpideryhma tehtava))
                                   tehtava))
                               tehtavat)
        toimistotehtavat (filter (fn [tehtava]
                                   (when (and
                                           (= "toimistokulut" (:toimenpideryhma tehtava))
                                           (not= "lisatyo" (:maksutyyppi tehtava)))
                                     tehtava))
                                 tehtavat)
        toteutuneet-palkat (filter
                             (fn [tehtava]
                               (when (and
                                       (not= "hjh" (:toteutunut tehtava))
                                       (not= "budjetointi" (:toteutunut tehtava))
                                       (not= "lisatyo" (:maksutyyppi tehtava)))
                                 tehtava))
                             palkkatehtavat)
        toteutuneet-toimistotehtavat (filter
                                       (fn [tehtava]
                                         (when (and
                                                 (not= "hjh" (:toteutunut tehtava))
                                                 (not= "budjetointi" (:toteutunut tehtava))
                                                 (not= "lisatyo" (:maksutyyppi tehtava)))
                                           tehtava))
                                       toimistotehtavat)
        palkat-vahvistettu (when (and palkkatehtavat (not (empty? palkkatehtavat)))
                             (every? #(not (nil? (:indeksikorjaus_vahvistettu %))) palkkatehtavat))
        toimistokulut-vahvistettu (when (and toimistotehtavat (not (empty? toimistotehtavat)))
                                    (every? #(not (nil? (:indeksikorjaus_vahvistettu %))) toimistotehtavat))]
    (vec [
          {:paaryhma paaryhmaotsikko
           :toimenpide "Palkat"
           :jarjestys (some #(:jarjestys %) palkkatehtavat)
           :toimenpide-toteutunut-summa (apply + (map (fn [rivi]
                                                        (or (:toteutunut_summa rivi) 0))
                                                   palkkatehtavat))
           :toimenpide-budjetoitu-summa (apply + (map (fn [rivi]
                                                        (or (:budjetoitu_summa rivi) 0))
                                                   palkkatehtavat))
           :toimenpide-budjetoitu-summa-indeksikorjattu (apply + (map (fn [rivi]
                                                                        (or (:budjetoitu_summa_indeksikorjattu rivi) 0))
                                                                   palkkatehtavat))
           :tehtavat toteutuneet-palkat
           ;; Asetetaan vahvistus-status nulliksi, jos yhtään toteumaa tai budjettia ei ole annettu.
           ;; Päätellään myöhemmin, että näytetäänkö nämä vahvistettuina tai vahvistamattomina
           :johto-ja-hallintakorvaus-indeksikorjaus-vahvistettu palkat-vahvistettu}
          {:paaryhma paaryhmaotsikko
           :toimenpide "Toimistokulut"
           :jarjestys (some #(:jarjestys %) toimistotehtavat)
           :toimenpide-toteutunut-summa (apply + (map (fn [rivi]
                                                        (or (:toteutunut_summa rivi) 0))
                                                   toimistotehtavat))
           :toimenpide-budjetoitu-summa (apply + (map (fn [rivi]
                                                        (or (:budjetoitu_summa rivi) 0))
                                                   toimistotehtavat))
           :toimenpide-budjetoitu-summa-indeksikorjattu (apply + (map (fn [rivi]
                                                                        (or (:budjetoitu_summa_indeksikorjattu rivi) 0))
                                                                   toimistotehtavat))
           :lisatyot-summa (reduce (fn [summa tehtava]
                                     (if (= "lisatyo" (:maksutyyppi tehtava))
                                       (+ summa (or (:toteutunut_summa tehtava) 0))
                                       summa))
                             0 tehtavat)
           :lisatyot (filter (fn [tehtava]
                               (when (= "lisatyo" (:maksutyyppi tehtava))
                                 true))
                       tehtavat)
           :tehtavat toteutuneet-toimistotehtavat
           ;; Asetetaan vahvistus-status nulliksi, jos yhtään toteumaa tai budjettia ei ole annettu.
           ;; Päätellään myöhemmin, että näytetäänkö nämä vahvistettuina tai vahvistamattomina
           :johto-ja-hallintakorvaus-indeksikorjaus-vahvistettu toimistokulut-vahvistettu}])))

(defn- summaa-hoitokauden-paattamisen-kulut [tehtavat paaryhmaotsikko]
  (let [toteutuneet-tehtavat
        (filter
          (fn [tehtava]
            (when (and
                    (not= "hjh" (:toteutunut tehtava))
                    (not= "budjetointi" (:toteutunut tehtava))
                    (not= "lisatyo" (:maksutyyppi tehtava)))
              tehtava))
          tehtavat)]
    {:paaryhma paaryhmaotsikko
     :toimenpide (:toimenpide (first tehtavat))
     :jarjestys (some #(:jarjestys %) tehtavat)
     :toimenpide-toteutunut-summa (apply + (map (fn [rivi]
                                                  (:toteutunut_summa rivi))
                                             toteutuneet-tehtavat))
     :toimenpide-budjetoitu-summa (apply + (map (fn [rivi]
                                                  (:budjetoitu_summa rivi))
                                             tehtavat))
     :toimenpide-budjetoitu-summa-indeksikorjattu nil ;; Hoitokauden päättämisen kuluja ei indeksikorjata
     :tehtavat toteutuneet-tehtavat}))

(defn- summaa-tehtavat
  "Summaa tehtäviä pääryhmille: hoidonjohtopalkkiot, erillishankinnat, bonus, siirto ja tavoitehinta"
  [taulukko-rivit paaryhma indeksi]
  (let [bud-key (keyword (str (nth raportin-paaryhmat indeksi) "-budjetoitu"))
        bud-idx-key (keyword (str (nth raportin-paaryhmat indeksi) "-budjetoitu-indeksikorjattu"))
        tot-key (keyword (str (nth raportin-paaryhmat indeksi) "-toteutunut"))
        vahvistettu-key (keyword (str (nth raportin-paaryhmat indeksi) "-indeksikorjaus-vahvistettu"))
        rivit (-> taulukko-rivit
                (assoc bud-key (bud-key paaryhma))
                (assoc bud-idx-key (bud-idx-key paaryhma))
                (assoc tot-key (tot-key paaryhma))
                (assoc vahvistettu-key (vahvistettu-key paaryhma)))]
    rivit))

(defn- summaa-paaryhman-toimenpiteet
  "Summataan hankintakustannukset, johto ja hallintakorvaukset sekä rahavaraukset"
  [taulukko-rivit indeksi toimenpiteet]
  (let [indeksikorjaus-vahvistettu-avain (keyword (str (nth raportin-paaryhmat indeksi) "-indeksikorjaus-vahvistettu"))
        ;; Jos yksikin arvo on false, niin osio on vahvistamatta
        indeksikorjaus-vahvistettu-arvo (every? (fn [rivi]
                                                  (if
                                                    (or (true? (get rivi indeksikorjaus-vahvistettu-avain))
                                                      (nil? (get rivi indeksikorjaus-vahvistettu-avain)))
                                                    true
                                                    false))
                                          toimenpiteet)
        taulukko
        (-> taulukko-rivit
          (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-budjetoitu"))
                 (apply + (map (fn [rivi]
                                 (or (:toimenpide-budjetoitu-summa rivi) 0))
                            toimenpiteet)))
          (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-budjetoitu-indeksikorjattu"))
                 (apply + (map (fn [rivi]
                                 (or (:toimenpide-budjetoitu-summa-indeksikorjattu rivi) 0))
                            toimenpiteet)))
          (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-toteutunut"))
                 (apply + (map (fn [rivi]
                                 (or (:toimenpide-toteutunut-summa rivi) 0))
                            toimenpiteet)))
          (assoc indeksikorjaus-vahvistettu-avain indeksikorjaus-vahvistettu-arvo)
          (assoc :lisatyot-summa (reduce (fn [summa rivi]
                                           (+ (or summa 0) (or (:lisatyot-summa rivi) 0)))
                                   (:lisatyot-summa taulukko-rivit)
                                   toimenpiteet))
          (assoc :lisatyot (reduce (fn [kaikki toimenpide]
                                     (concat kaikki
                                       (:lisatyot toimenpide)))
                             (:lisatyot taulukko-rivit)
                             toimenpiteet)))]

    taulukko))

(defn jarjesta-tehtavat
  "Tietokannasta saadaan kaikki kustannukset alimman tasoluokan mukaan eli tehtävittäin.
  Nämä tehtävät pitää järjestellä jokainen omaan pääryhmäänsä (Hankintakustannukset, Hoidonjohdonpalkkio, Erillishankinnat,
  Johto-ja Hallintakorvaukset, Lisätyöt, Bonukset, Tavoitehinnan oikaisut, Yhteensä. Sekä näiden pääryhmien alla toimiviin toimenpiteisiin
  ja toimenpiteiden alla mahdollisesti rahavarauksiin ja hankintoihin.

  Tämä kaikki kootaan tässä funktiossa."
  [data]
  (let [paaryhmat (group-by :paaryhma data)
        hankintakustannukset (get paaryhmat (nth raportin-paaryhmat 0)) ;; hankinta
        jjhallinta-kustannukset (get paaryhmat (nth raportin-paaryhmat 1)) ;; johto-ja hallinta..
        hoidonjohdonpalkkiot (get paaryhmat (nth raportin-paaryhmat 2))
        erillishankinnat (get paaryhmat (nth raportin-paaryhmat 3))
        rahavaraukset (get paaryhmat (nth raportin-paaryhmat 4))
        bonukset (get paaryhmat (nth raportin-paaryhmat 5))
        siirrot (get paaryhmat (nth raportin-paaryhmat 6))
        tavoitehinnanoikaisut (get paaryhmat (nth raportin-paaryhmat 7))
        tavoitepalkkiot (get paaryhmat (nth raportin-paaryhmat 8))
        tavoitehinnan-ylitykset (get paaryhmat (nth raportin-paaryhmat 9))
        kattohinnan-ylitykset (get paaryhmat (nth raportin-paaryhmat 10))
        sanktiot (get paaryhmat (nth raportin-paaryhmat 11))
        ulkopuoliset-rahavaraukset (get paaryhmat (nth raportin-paaryhmat 12))

        ;; Ryhmittele hankintakustannusten alla olevat tiedot toimenpiteen perusteella
        hankintakustannusten-toimenpiteet (sort-by toimenpide-jarjestys (group-by :toimenpide hankintakustannukset))
        hankintakustannusten-toimenpiteet (summaa-toimenpidetaso hankintakustannusten-toimenpiteet (nth raportin-paaryhmat 0))
        jjhallinnan-toimenpiteet (summaa-hoito-ja-hallinta-tehtavat jjhallinta-kustannukset (nth raportin-paaryhmat 1))
        jjhallinnan-toimenpiteet (sort-by :jarjestys jjhallinnan-toimenpiteet)
        rahavaraukset (sort-by toimenpide-jarjestys (group-by :toimenpide rahavaraukset))
        rahavaraus-toimenpiteet (summaa-toimenpidetaso rahavaraukset (nth raportin-paaryhmat 4))
        hoidonjohdonpalkkiot (summaa-paaryhman-tehtavat hoidonjohdonpalkkiot (nth raportin-paaryhmat 2))
        bonus-tehtavat (summaa-paaryhman-tehtavat bonukset (nth raportin-paaryhmat 5))
        erillishankinta-tehtavat (summaa-paaryhman-tehtavat erillishankinnat (nth raportin-paaryhmat 3))
        siirrot (summaa-paaryhman-tehtavat siirrot (nth raportin-paaryhmat 6))
        tavoitehinnanoikaisut (summaa-paaryhman-tehtavat tavoitehinnanoikaisut (nth raportin-paaryhmat 7))
        tavoitepalkkiot (summaa-hoitokauden-paattamisen-kulut tavoitepalkkiot (nth raportin-paaryhmat 8))
        tavoitehinnan-ylitykset (summaa-hoitokauden-paattamisen-kulut tavoitehinnan-ylitykset (nth raportin-paaryhmat 9))
        kattohinnan-ylitykset (summaa-hoitokauden-paattamisen-kulut kattohinnan-ylitykset (nth raportin-paaryhmat 10))
        sanktio-tehtavat (summaa-paaryhman-tehtavat sanktiot (nth raportin-paaryhmat 11))
        ulkopuoliset-rahavaraukset-tehtavat (summaa-paaryhman-tehtavat ulkopuoliset-rahavaraukset (nth raportin-paaryhmat 12))

        taulukon-rivit (-> {}
                           ;; Aseta pääryhmän avaimelle toimenpiteet
                           (assoc (keyword (nth raportin-paaryhmat 0)) hankintakustannusten-toimenpiteet)
                           ;; Aseta pääryhmän avaimaille budjetoitu summa ja toteutunut summa
                           (summaa-paaryhman-toimenpiteet 0 hankintakustannusten-toimenpiteet)

                           (assoc (keyword (nth raportin-paaryhmat 1)) jjhallinnan-toimenpiteet)
                           (summaa-paaryhman-toimenpiteet 1 jjhallinnan-toimenpiteet)

                           (assoc (keyword (nth raportin-paaryhmat 2)) hoidonjohdonpalkkiot)
                           (summaa-tehtavat hoidonjohdonpalkkiot 2)

                           (assoc (keyword (nth raportin-paaryhmat 3)) erillishankinta-tehtavat)
                           (summaa-tehtavat erillishankinta-tehtavat 3)

                           (assoc (keyword (nth raportin-paaryhmat 4)) rahavaraus-toimenpiteet)
                           (summaa-paaryhman-toimenpiteet 4 rahavaraus-toimenpiteet)

                           (assoc (keyword (nth raportin-paaryhmat 5)) bonus-tehtavat)
                           (summaa-tehtavat bonus-tehtavat 5)

                           (assoc (keyword (nth raportin-paaryhmat 6)) siirrot)
                           (summaa-tehtavat siirrot 6)

                           (assoc (keyword (nth raportin-paaryhmat 7)) tavoitehinnanoikaisut)
                           (summaa-tehtavat tavoitehinnanoikaisut 7)

                           (assoc (keyword (nth raportin-paaryhmat 8)) tavoitepalkkiot)
                           (summaa-paaryhman-toimenpiteet 8 tavoitepalkkiot)

                           (assoc (keyword (nth raportin-paaryhmat 9)) tavoitehinnan-ylitykset)
                           (summaa-paaryhman-toimenpiteet 9 tavoitehinnan-ylitykset)

                           (assoc (keyword (nth raportin-paaryhmat 10)) kattohinnan-ylitykset)
                           (summaa-paaryhman-toimenpiteet 10 kattohinnan-ylitykset)

                           (assoc (keyword (nth raportin-paaryhmat 11)) sanktio-tehtavat)
                           (summaa-tehtavat sanktio-tehtavat 11)

                           (assoc (keyword (nth raportin-paaryhmat 12)) ulkopuoliset-rahavaraukset-tehtavat)
                           (summaa-tehtavat ulkopuoliset-rahavaraukset-tehtavat 12))

        yhteensa {:toimenpide "Yhteensä"
                  :yht-toteutunut-summa (apply + (map (fn [pr]
                                                        (get taulukon-rivit (keyword (str pr "-toteutunut"))))
                                                   (remove #(yhteenvedosta-jatettavat-paaryhmat %) raportin-paaryhmat)))
                  :yht-budjetoitu-summa (apply + (map (fn [pr]
                                                        (get taulukon-rivit (keyword (str pr "-budjetoitu"))))
                                                   (remove #(yhteenvedosta-jatettavat-paaryhmat %) raportin-paaryhmat)))
                  :yht-budjetoitu-summa-indeksikorjattu (apply + (map (fn [pr]
                                                                        (get taulukon-rivit (keyword (str pr "-budjetoitu-indeksikorjattu"))))
                                                                   (remove #(yhteenvedosta-jatettavat-paaryhmat %) raportin-paaryhmat)))}]
    {:taulukon-rivit taulukon-rivit
     :yhteensa yhteensa}))
