(ns harja.domain.kulut.kustannusten-seuranta
  "Kustannusten seurannan datan prosessoinnin apurit")

;; Raportin pääryhmät jäsennettynä samaan järjestykseen, kuin ui suunnitelmissa on tarkoitettu
(def raportin-paaryhmat
  ["hankintakustannukset", "johto-ja-hallintakorvaus", "hoidonjohdonpalkkio", "erillishankinnat", "rahavaraukset",
   "bonukset", "siirto", "tavoitehinnanoikaisu", "tavoitepalkkio", "tavoitehinnan-ylitys", "kattohinnan-ylitys",
   "sanktiot", "ulkopuoliset-rahavaraukset", "lisatyo", "muukulu-tavoitehintainen", "muukulu-eitavoitehintainen"])

(def yhteenvedosta-jatettavat-paaryhmat
  (set (map #(nth raportin-paaryhmat %) [5 8 9 10 11 12 13 15])))

(defn- toimenpide-jarjestys [toimenpide]
  (case (first toimenpide)
    "Talvihoito" 1
    "Liikenneympäristön hoito" 2
    "Sorateiden hoito" 3
    "Päällystepaikkaukset" 4
    "MHU Ylläpito" 5
    "MHU Korvausinvestointi" 6
    "MHU Hoidonjohto" 7
    ;; TODO: Onkohan tämä hyvä. Tämä siis määrää, että missä järjestyksessä toisen portaan asiat on listattu
    ;; ja rahavarauksien järjestys tulisi niiden id:n perusteella. Mutta haku ei palauta toimenpide_id:tä tai rahavaraus_id:tä

    8))

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
         #_#_:lisatyot-summa (reduce (fn [summa tehtava]
                                   (if (= "lisatyo" (:maksutyyppi tehtava))
                                     (+ summa (or (:toteutunut_summa tehtava) 0))
                                     summa))
                           0 toimenpiteen-tehtavat)
         #_#_:lisatyot (filter (fn [tehtava]
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
                                         #_ (not= "lisatyo" (:maksutyyppi tehtava)))
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
         #_ :lisatyot-summa (reduce (fn [summa tehtava]
                                   (if (= "lisatyo" (:maksutyyppi tehtava))
                                     (+ summa (or (:toteutunut_summa tehtava) 0))
                                     summa))
                           0 tehtavat)
         #_ :lisatyot (filter (fn [tehtava]
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

(defn- summaa-hoitokauden-paattamisen-kulut [tehtavat paaryhmaotsikko]
  (let [toteutuneet-tehtavat
        (filter
          (fn [tehtava]
            (when (and
                    (not= "hjh" (:toteutunut tehtava))
                    (not= "budjetointi" (:toteutunut tehtava))
                    #_ (not= "lisatyo" (:maksutyyppi tehtava)))
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
  "Summataan hankintakustannukset, johto ja hallintokorvaukset sekä rahavaraukset"
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
          #_(assoc :lisatyot-summa (reduce (fn [summa rivi]
                                           (+ (or summa 0) (or (:lisatyot-summa rivi) 0)))
                                   (:lisatyot-summa taulukko-rivit)
                                   toimenpiteet))
          #_(assoc :lisatyot (reduce (fn [kaikki toimenpide]
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
        hankintakustannukset (get (select-keys paaryhmat ["hankintakustannukset"]) "hankintakustannukset") ;; hankinta
        jjhallinta-kustannukset (get (select-keys paaryhmat ["johto-ja-hallintakorvaus"]) "johto-ja-hallintakorvaus") ;; johto-ja-hallintakorvaus
        hoidonjohdonpalkkiot (get (select-keys paaryhmat ["hoidonjohdonpalkkio"]) "hoidonjohdonpalkkio") ;; hoidonjohdonpalkkio
        erillishankinnat (get (select-keys paaryhmat ["erillishankinnat"]) "erillishankinnat") ;; erillishankinnat
        rahavaraukset (get (select-keys paaryhmat ["rahavaraukset"]) "rahavaraukset") ;; rahavaraukset
        bonukset (get (select-keys paaryhmat ["bonukset"]) "bonukset") ;; bonukset
        siirrot (get (select-keys paaryhmat ["siirto"]) "siirto")  ;; siirto
        tavoitehinnanoikaisut (get (select-keys paaryhmat ["tavoitehinnanoikaisu"]) "tavoitehinnanoikaisu") ;; tavoitehinnanoikaisu
        tavoitepalkkiot (get (select-keys paaryhmat ["tavoitepalkkio"]) "tavoitepalkkio") ;; tavoitepalkkio
        tavoitehinnan-ylitykset (get (select-keys paaryhmat ["tavoitehinnan-ylitys"]) "tavoitehinnan-ylitys") ;; tavoitehinnan-ylitys
        kattohinnan-ylitykset (get (select-keys paaryhmat ["kattohinnan-ylitys"]) "kattohinnan-ylitys") ;; kattohinnan-ylitys
        sanktiot (get (select-keys paaryhmat ["sanktiot"]) "sanktiot") ;; sanktiot
        ulkopuoliset-rahavaraukset (get (select-keys paaryhmat ["ulkopuoliset-rahavaraukset"]) "ulkopuoliset-rahavaraukset") ;; ulkopuoliset-rahavaraukset
        lisatyot (get (select-keys paaryhmat ["lisatyo"]) "lisatyo") ;; lisatyo
        muutkulut-tavoitehintainen (get (select-keys paaryhmat ["muukulu-tavoitehintainen"]) "muukulu-tavoitehintainen") ;; muukulu-tavoitehintainen
        muutkulut-eitavoitehintainen (get (select-keys paaryhmat ["muukulu-eitavoitehintainen"]) "muukulu-eitavoitehintainen") ;; muukulu-eitavoitehintainen

        ;; Ryhmittele hankintakustannusten alla olevat tiedot toimenpiteen perusteella
        hankintakustannusten-toimenpiteet (sort-by toimenpide-jarjestys (group-by :toimenpide hankintakustannukset))
        hankintakustannusten-toimenpiteet (summaa-toimenpidetaso hankintakustannusten-toimenpiteet (nth raportin-paaryhmat 0))
        jjhallinnan-tehtavat (summaa-paaryhman-tehtavat jjhallinta-kustannukset (nth raportin-paaryhmat 1))
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
        lisatyo-tehtavat (summaa-paaryhman-tehtavat lisatyot (nth raportin-paaryhmat 13))
        muukulu-tavoitehintainen-tehtavat (summaa-paaryhman-tehtavat muutkulut-tavoitehintainen (nth raportin-paaryhmat 14))
        muukulu-eitavoitehintainen-tehtavat (summaa-paaryhman-tehtavat muutkulut-eitavoitehintainen (nth raportin-paaryhmat 15))

        taulukon-rivit (-> {}
                         ;; Aseta pääryhmän avaimelle toimenpiteet
                         (assoc (keyword (nth raportin-paaryhmat 0)) hankintakustannusten-toimenpiteet)
                         ;; Aseta pääryhmän avaimaille budjetoitu summa ja toteutunut summa
                         (summaa-paaryhman-toimenpiteet 0 hankintakustannusten-toimenpiteet)

                         (assoc (keyword (nth raportin-paaryhmat 1))  jjhallinnan-tehtavat)
                         (summaa-tehtavat jjhallinnan-tehtavat 1)

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
                         (summaa-tehtavat ulkopuoliset-rahavaraukset-tehtavat 12)

                         (assoc (keyword (nth raportin-paaryhmat 13)) lisatyo-tehtavat)
                         (summaa-tehtavat lisatyo-tehtavat 13)

                         (assoc (keyword (nth raportin-paaryhmat 14)) muukulu-tavoitehintainen-tehtavat)
                         (summaa-tehtavat muukulu-tavoitehintainen-tehtavat 14)

                         (assoc (keyword (nth raportin-paaryhmat 15)) muukulu-eitavoitehintainen-tehtavat)
                         (summaa-tehtavat muukulu-eitavoitehintainen-tehtavat 15))
        taulukon-rivit (into (sorted-map) taulukon-rivit)

        ;; Yhteensä tavoitehintaiset
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
