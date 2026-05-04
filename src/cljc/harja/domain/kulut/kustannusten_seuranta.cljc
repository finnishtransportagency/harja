(ns harja.domain.kulut.kustannusten-seuranta
  "Kustannusten seurannan datan prosessoinnin apurit"
  (:require [harja.pvm :as pvm]))

;; Raportin pääryhmät jäsennettynä samaan järjestykseen, kuin ui suunnitelmissa on tarkoitettu
(def raportin-paaryhmat
  ["hankintakustannukset", "johto-ja-hallintokorvaus", "hoidonjohdonpalkkio", "muutokset", "erillishankinnat", "rahavaraukset",
   "bonukset", "siirto", "tavoitehinnanoikaisu", "tavoitepalkkio", "tavoitehinnan-ylitys", "kattohinnan-ylitys",
   "sanktiot", "ulkopuoliset-rahavaraukset", "lisatyo", "muukulu-tavoitehintainen", "muukulu-eitavoitehintainen"])

(def tavoitehintaan-kuulumattomat-paaryhmat
  (set (map #(nth raportin-paaryhmat %) [6 9 10 11 12 13 14 16])))

(defn- toimenpide-jarjestys [toimenpide]
  (case (first toimenpide)
    "Talvihoito" 1
    "Liikenneympäristön hoito" 2
    "Sorateiden hoito" 3
    "Päällystepaikkaukset" 4
    "MHU Ylläpito" 5
    "MHU Korvausinvestointi" 6
    "MHU Hoidonjohto" 7
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
       :toimenpideryhma (:toimenpideryhma (first arvot))
       :maksutyyppi (:maksutyyppi (first arvot))
       :jarjestys (:jarjestys (first arvot))
       :paaryhma (:paaryhma (first arvot))})
    (group-by :tehtava_nimi (concat toteutuneet budjetoidut))))

(defn- summaa-toimenpidetaso
  "Käytetään seuraaville pääryhmille: hankintakustannukset, muutokset ja rahavaraukset."
  [toimenpiteet paaryhmaotsikko & [urakan-sopimustyyppi]]
  (sort-by :jarjestys
    (mapv
      (fn [toimenpide]
        (let [toimenpiteen-tehtavat (second toimenpide)
              toimenpide-nimi (first toimenpide)
              toimenpide-nimi  (cond
                                 (= toimenpide-nimi "erillisrahoitettu-muutos")
                                 "Muutostyöt (erillisrahoitetut)"

                                 (= toimenpide-nimi "jjh-muutos")
                                 (if (= :mhu+ urakan-sopimustyyppi)
                                   "Kumppanuusmaksun muutos"
                                   "Johto- ja hallintokorvauksen muutokset")

                                 (= toimenpide-nimi "pysyva")
                                 "Pysyvät muutokset"
                                 
                                 :else
                                 toimenpide-nimi)
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
              jarjestys (some #(:jarjestys %) toimenpiteen-tehtavat)]
          {:paaryhma paaryhmaotsikko
           :toimenpide toimenpide-nimi
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
           :tehtavat (sort-by :jarjestys toteutuneet-tehtavat)
           ;; Asetetaan vahvistus-status nulliksi, jos yhtään toteumaa tai budjettia ei ole annettu.
           ;; Päätellään myöhemmin, että näytetäänkö nämä vahvistettuina tai vahvistamattomina
           (keyword (str paaryhmaotsikko "-indeksikorjaus-vahvistettu"))
           (when indeksoitavat-tehtavat
             (every? #(not (nil? (:indeksikorjaus_vahvistettu %))) indeksoitavat-tehtavat))}))
      toimenpiteet)))

(defn- summaa-paaryhman-tehtavat
  "Käytetään pääryhmille: Hoidonjohdonpalkkiot, bonus, erillishankinta, siirrot ja tavoitehinnan oikaisut"
  [tehtavat paaryhmaotsikko]
  (let [toteutuneet-tehtavat (filter
                               (fn [tehtava]
                                 (when (and
                                         (not= "hjh" (:toteutunut tehtava))
                                         (not= "budjetointi" (:toteutunut tehtava)))
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
          (assoc indeksikorjaus-vahvistettu-avain indeksikorjaus-vahvistettu-arvo))]

    taulukko))

(defn jarjesta-tehtavat
  "Tietokannasta saadaan kaikki kustannukset alimman tasoluokan mukaan eli tehtävittäin.
  Nämä tehtävät pitää järjestellä jokainen omaan pääryhmäänsä (Hankintakustannukset, Hoidonjohdonpalkkio, Erillishankinnat,
  Johto-ja Hallintakorvaukset, Lisätyöt, Bonukset, Tavoitehinnan oikaisut, Yhteensä. Sekä näiden pääryhmien alla toimiviin toimenpiteisiin
  ja toimenpiteiden alla mahdollisesti rahavarauksiin ja hankintoihin.

  Tämä kaikki kootaan tässä funktiossa."
  ([data] (jarjesta-tehtavat data nil))
  ([data urakan-sopimustyyppi]
  (let [paaryhmat (group-by :paaryhma data)
        hankintakustannukset (get (select-keys paaryhmat ["hankintakustannukset"]) "hankintakustannukset") ;; hankinta
        jjhallinta-kustannukset (get (select-keys paaryhmat ["johto-ja-hallintokorvaus"]) "johto-ja-hallintokorvaus") ;; johto-ja-hallintokorvaus
        hoidonjohdonpalkkiot (get (select-keys paaryhmat ["hoidonjohdonpalkkio"]) "hoidonjohdonpalkkio") ;; hoidonjohdonpalkkio
        muutokset (get (select-keys paaryhmat ["muutokset"]) "muutokset") ;; muutokset 
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

        rahavaraukset (sort-by toimenpide-jarjestys (group-by :toimenpide rahavaraukset))
        muutokset (sort-by toimenpide-jarjestys (group-by :kulu_tyyppi muutokset))
        hankintakustannusten-toimenpiteet (sort-by toimenpide-jarjestys (group-by :toimenpide hankintakustannukset))

        ;; Ryhmittele hankintakustannusten alla olevat tiedot toimenpiteen perusteella
        hankintakustannusten-toimenpiteet (summaa-toimenpidetaso hankintakustannusten-toimenpiteet (nth raportin-paaryhmat 0))
        jjhallinnan-tehtavat (summaa-paaryhman-tehtavat jjhallinta-kustannukset (nth raportin-paaryhmat 1))
        hoidonjohdonpalkkiot (summaa-paaryhman-tehtavat hoidonjohdonpalkkiot (nth raportin-paaryhmat 2))
        muutokset (summaa-toimenpidetaso muutokset (nth raportin-paaryhmat 3) urakan-sopimustyyppi)
        erillishankinta-tehtavat (summaa-paaryhman-tehtavat erillishankinnat (nth raportin-paaryhmat 4))
        rahavaraus-toimenpiteet (summaa-toimenpidetaso rahavaraukset (nth raportin-paaryhmat 5))
        bonus-tehtavat (summaa-paaryhman-tehtavat bonukset (nth raportin-paaryhmat 6))
        siirrot (summaa-paaryhman-tehtavat siirrot (nth raportin-paaryhmat 7))
        tavoitehinnanoikaisut (summaa-paaryhman-tehtavat tavoitehinnanoikaisut (nth raportin-paaryhmat 8))
        tavoitepalkkiot (summaa-hoitokauden-paattamisen-kulut tavoitepalkkiot (nth raportin-paaryhmat 9))
        tavoitehinnan-ylitykset (summaa-hoitokauden-paattamisen-kulut tavoitehinnan-ylitykset (nth raportin-paaryhmat 10))
        kattohinnan-ylitykset (summaa-hoitokauden-paattamisen-kulut kattohinnan-ylitykset (nth raportin-paaryhmat 11))
        sanktio-tehtavat (summaa-paaryhman-tehtavat sanktiot (nth raportin-paaryhmat 12))
        ulkopuoliset-rahavaraukset-tehtavat (summaa-paaryhman-tehtavat ulkopuoliset-rahavaraukset (nth raportin-paaryhmat 13))
        lisatyo-tehtavat (summaa-paaryhman-tehtavat lisatyot (nth raportin-paaryhmat 14))
        muukulu-tavoitehintainen-tehtavat (summaa-paaryhman-tehtavat muutkulut-tavoitehintainen (nth raportin-paaryhmat 15))
        muukulu-eitavoitehintainen-tehtavat (summaa-paaryhman-tehtavat muutkulut-eitavoitehintainen (nth raportin-paaryhmat 16))

        taulukon-rivit (-> {}
                         ;; Aseta pääryhmän avaimelle toimenpiteet
                         (assoc (keyword (nth raportin-paaryhmat 0)) hankintakustannusten-toimenpiteet)
                         ;; Aseta pääryhmän avaimaille budjetoitu summa ja toteutunut summa
                         (summaa-paaryhman-toimenpiteet 0 hankintakustannusten-toimenpiteet)

                         (assoc (keyword (nth raportin-paaryhmat 1))  jjhallinnan-tehtavat)
                         (summaa-tehtavat jjhallinnan-tehtavat 1)

                         (assoc (keyword (nth raportin-paaryhmat 2)) hoidonjohdonpalkkiot)
                         (summaa-tehtavat hoidonjohdonpalkkiot 2)

                         (assoc (keyword (nth raportin-paaryhmat 3)) muutokset)
                         (summaa-paaryhman-toimenpiteet 3 muutokset)

                         (assoc (keyword (nth raportin-paaryhmat 4)) erillishankinta-tehtavat)
                         (summaa-tehtavat erillishankinta-tehtavat 4)

                         (assoc (keyword (nth raportin-paaryhmat 5)) rahavaraus-toimenpiteet)
                         (summaa-paaryhman-toimenpiteet 5 rahavaraus-toimenpiteet)

                         (assoc (keyword (nth raportin-paaryhmat 6)) bonus-tehtavat)
                         (summaa-tehtavat bonus-tehtavat 6)

                         (assoc (keyword (nth raportin-paaryhmat 7)) siirrot)
                         (summaa-tehtavat siirrot 7)

                         (assoc (keyword (nth raportin-paaryhmat 8)) tavoitehinnanoikaisut)
                         (summaa-tehtavat tavoitehinnanoikaisut 8)

                         (assoc (keyword (nth raportin-paaryhmat 9)) tavoitepalkkiot)
                         (summaa-paaryhman-toimenpiteet 9 tavoitepalkkiot)

                         (assoc (keyword (nth raportin-paaryhmat 10)) tavoitehinnan-ylitykset)
                         (summaa-paaryhman-toimenpiteet 10 tavoitehinnan-ylitykset)

                         (assoc (keyword (nth raportin-paaryhmat 11)) kattohinnan-ylitykset)
                         (summaa-paaryhman-toimenpiteet 11 kattohinnan-ylitykset)

                         (assoc (keyword (nth raportin-paaryhmat 12)) sanktio-tehtavat)
                         (summaa-tehtavat sanktio-tehtavat 12)

                         (assoc (keyword (nth raportin-paaryhmat 13)) ulkopuoliset-rahavaraukset-tehtavat)
                         (summaa-tehtavat ulkopuoliset-rahavaraukset-tehtavat 13)

                         (assoc (keyword (nth raportin-paaryhmat 14)) lisatyo-tehtavat)
                         (summaa-tehtavat lisatyo-tehtavat 14)

                         (assoc (keyword (nth raportin-paaryhmat 15)) muukulu-tavoitehintainen-tehtavat)
                         (summaa-tehtavat muukulu-tavoitehintainen-tehtavat 15)

                         (assoc (keyword (nth raportin-paaryhmat 16)) muukulu-eitavoitehintainen-tehtavat)
                         (summaa-tehtavat muukulu-eitavoitehintainen-tehtavat 16))
        taulukon-rivit (into (sorted-map) taulukon-rivit)

        toteutunut #(get taulukon-rivit (keyword (str % "-toteutunut")))
        budjetoitu #(get taulukon-rivit (keyword (str % "-budjetoitu")))
        budjetoitu-indeksikorjattu #(get taulukon-rivit (keyword (str % "-budjetoitu-indeksikorjattu")))
        rivit (remove #(tavoitehintaan-kuulumattomat-paaryhmat %) raportin-paaryhmat)

        ;; Yhteensä tavoitehintaiset
        yhteensa {:toimenpide "Yhteensä"
                  :yht-toteutunut-summa (apply + (map toteutunut rivit))
                  :yht-budjetoitu-summa (apply + (map budjetoitu rivit))
                  :yht-budjetoitu-summa-indeksikorjattu (apply + (map budjetoitu-indeksikorjattu rivit))}]
    {:taulukon-rivit taulukon-rivit
     :yhteensa yhteensa})))

(defn valikatselmuksen-takarajapvm [vuosi]
  ;; hox: kk on tässä 0-indeksinen, eli ko. pvm on 15.11.
  (pvm/luo-pvm vuosi 10 15))

(defn valikatselmuksen-paatostyypin-nimi [tyyppi]
  (case tyyppi
    "tavoitehinnan-ylitys" "Tavoitehinnan ylitys"
    "kattohinnan-ylitys" "Kattohinnan ylitys"
    "tavoitehinnan-alitus" "Tavoitehinnan alitus"
    "tavoitehinnan-muutokset" "Tavoitehinnan muutokset"
    "lupausbonus" "Lupausbonus"
    "lupaussanktio" "Lupaussanktio"
    "bonus" "Lupausbonus"
    "sanktio" "Lupaussanktio"
    "taytetty" "Lupaus täytetty"

    "tuntematon tyyppi"))
