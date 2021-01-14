(ns harja.domain.kulut.kustannusten-seuranta
  "Kustannusten seurannan datan prosessoinnin apurit")

;; Raportin pääryhmät jäsennettynä samaan järjestykseen, kuin ui suunnitelmissa on tarkoitettu
(def raportin-paaryhmat
  ["hankintakustannukset", "johto-ja-hallintakorvaus", "hoidonjohdonpalkkio", "erillishankinnat", "varaukset", "bonukset"])

(defn- toimenpide-jarjestys [toimenpide]
  (case (first toimenpide)
    "Talvihoito" 1
    "Liikenneympäristön hoito" 2
    "Sorateiden hoito" 3
    "Päällystepaikkaukset" 4
    "MHU Ylläpito" 5
    "MHU Korvausinvestointi" 6
    "MHU Hoidonjohto" 7))

(defn- summaa-toimenpidetaso [toimenpiteet paaryhmaotsikko]
  (mapv
    (fn [toimenpide]
      (let [toimenpiteen-tehtavat (second toimenpide)
            ;; Toimenpiteet listassa on budjetoidut ja toteutuneet tehtävät
            ;; UI:lla budjetointi lasketaan yhteen toimenpideryhmän perusteella (esim. hankinnat) ja toimenpiteen perusteella (esim. talvihoito)
            ;; Toteutuneet kustannukset näytetään tehtävittäin ryhmiteltynä.
            ;; Lisätyöt erotellaan omaksi pääryhmäkseen, koska tietokantahaku ei tee siitä omaa pääryhmää automaattisesti.
            ;; Poistetaan siis budjetointiin liittyvät tehtävät :toteutunut = budjetoitu tai hth ja lasketaan lisätyöt yhteen.
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
         :toimenpide (first toimenpide)
         :jarjestys jarjestys
         :toimenpide-toteutunut-summa (reduce (fn [summa tehtava]
                                                (+ summa (:toteutunut_summa tehtava)))
                                              0 toteutuneet-tehtavat) ;; vain toteutuneet tehtävät ilman lisätöitä
         :toimenpide-budjetoitu-summa (reduce (fn [summa tehtava]
                                                (+ summa (:budjetoitu_summa tehtava)))
                                              0 toimenpiteen-tehtavat)
         :lisatyot-summa (reduce (fn [summa tehtava]
                                   (if (= "lisatyo" (:maksutyyppi tehtava))
                                     (+ summa (:toteutunut_summa tehtava))
                                     summa))
                                 0 toimenpiteen-tehtavat)
         :lisatyot (filter (fn [tehtava]
                             (when (= "lisatyo" (:maksutyyppi tehtava))
                               true))
                           toimenpiteen-tehtavat)
         :tehtavat (sort-by :jarjestys toteutuneet-tehtavat)}))
    toimenpiteet))

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
                                       toimistotehtavat)]
    (vec [
          {:paaryhma paaryhmaotsikko
           :toimenpide "Palkat"
           :jarjestys (some #(:jarjestys %) palkkatehtavat)
           :toimenpide-toteutunut-summa (apply + (map (fn [rivi]
                                                        (:toteutunut_summa rivi))
                                                      palkkatehtavat))
           :toimenpide-budjetoitu-summa (apply + (map (fn [rivi]
                                                        (:budjetoitu_summa rivi))
                                                      palkkatehtavat))
           :tehtavat toteutuneet-palkat}
          {:paaryhma paaryhmaotsikko
           :toimenpide "Toimistokulut"
           :jarjestys (some #(:jarjestys %) toimistotehtavat)
           :toimenpide-toteutunut-summa (apply + (map (fn [rivi]
                                                        (:toteutunut_summa rivi))
                                                      toimistotehtavat))
           :toimenpide-budjetoitu-summa (apply + (map (fn [rivi]
                                                        (:budjetoitu_summa rivi))
                                                      toimistotehtavat))
           :lisatyot-summa (reduce (fn [summa tehtava]
                                     (if (= "lisatyo" (:maksutyyppi tehtava))
                                       (+ summa (:toteutunut_summa tehtava))
                                       summa))
                                   0 toimistotehtavat)
           :lisatyot (filter (fn [tehtava]
                               (when (= "lisatyo" (:maksutyyppi tehtava))
                                 true))
                             toimistotehtavat)
           :tehtavat toteutuneet-toimistotehtavat}])))

(defn- summaa-tehtavat [taulukko-rivit tehtavat indeksi]
  (-> taulukko-rivit
      (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-budjetoitu"))
             (apply + (map (fn [rivi]
                             (:budjetoitu_summa rivi))
                           tehtavat)))
      (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-toteutunut"))
             (apply + (map (fn [rivi]
                             (:toteutunut_summa rivi))
                           tehtavat)))))

(defn- summaa-paaryhman-toimenpiteet [taulukko-rivit indeksi toimenpiteet]
  (-> taulukko-rivit
      (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-budjetoitu"))
             (apply + (map (fn [rivi]
                             (:toimenpide-budjetoitu-summa rivi))
                           toimenpiteet)))
      (assoc (keyword (str (nth raportin-paaryhmat indeksi) "-toteutunut"))
             (apply + (map (fn [rivi]
                             (:toimenpide-toteutunut-summa rivi))
                           toimenpiteet)))
      (assoc :lisatyot-summa (reduce (fn [summa rivi]
                                       (+ (or summa 0) (or (:lisatyot-summa rivi) 0)))
                                     (:lisatyot-summa taulukko-rivit)
                                     toimenpiteet))
      (assoc :lisatyot (reduce (fn [kaikki toimenpide]
                                 (concat kaikki
                                         (:lisatyot toimenpide)))
                               (:lisatyot taulukko-rivit)
                               toimenpiteet))))

(defn jarjesta-tehtavat
  "Tietokannasta saadaan kaikki kustannukset alimman tasoluokan mukaan eli tehtävittäin.
  Nämä tehtävät pitää järjestellä jokainen omaan pääryhmäänsä (Hankintakustannukset, Hoidonjohdonpalkkio, Erillishankinnat,
  Johto-ja Hallintakorvaukset, Lisätyöt, Yhteensä. Sekä näiden pääryhmien alla toimiviin toimenpiteisiin
  ja toimenpiteiden alla rahavarauksiin ja hankintoihin.

  Tämä kaikki kootaan tässä funktiossa."
  [data]
  (let [paaryhmat (group-by :paaryhma data)
        hankintakustannukset (get paaryhmat (nth raportin-paaryhmat 0)) ;; hankinta
        jjhallinta-kustannukset (get paaryhmat (nth raportin-paaryhmat 1)) ;; johto-ja hallinta..
        hoidonjohdonpalkkiot (get paaryhmat (nth raportin-paaryhmat 2))
        erillishankinnat (get paaryhmat (nth raportin-paaryhmat 3))
        varaukset (get paaryhmat (nth raportin-paaryhmat 4))
        bonukset (get paaryhmat (nth raportin-paaryhmat 5))

        ;; Ryhmittele hankintakustannusten alla olevat tiedot toimenpiteen perusteella
        hankintakustannusten-toimenpiteet (sort-by toimenpide-jarjestys (group-by :toimenpide hankintakustannukset))
        hankintakustannusten-toimenpiteet (summaa-toimenpidetaso hankintakustannusten-toimenpiteet (nth raportin-paaryhmat 0))
        jjhallinnan-toimenpiteet (summaa-hoito-ja-hallinta-tehtavat jjhallinta-kustannukset (nth raportin-paaryhmat 1))
        jjhallinnan-toimenpiteet (sort-by :jarjestys jjhallinnan-toimenpiteet)
        varaukset (sort-by toimenpide-jarjestys (group-by :toimenpide varaukset))
        varaus-toimenpiteet (summaa-toimenpidetaso varaukset (nth raportin-paaryhmat 4))
        bonukset (group-by :toimenpide bonukset)
        bonus-toimenpiteet (summaa-toimenpidetaso bonukset (nth raportin-paaryhmat 5))

        taulukon-rivit (-> {}
                           ;; Aseta pääryhmän avaimelle toimenpiteet
                           (assoc (keyword (nth raportin-paaryhmat 0)) hankintakustannusten-toimenpiteet)
                           ;; Aseta pääryhmän avaimaille budjetoitu summa ja toteutunut summa
                           (summaa-paaryhman-toimenpiteet 0 hankintakustannusten-toimenpiteet)
                           (assoc (keyword (nth raportin-paaryhmat 1)) jjhallinnan-toimenpiteet)
                           (summaa-paaryhman-toimenpiteet 1 jjhallinnan-toimenpiteet)
                           (summaa-tehtavat hoidonjohdonpalkkiot 2)
                           (summaa-tehtavat erillishankinnat 3)
                           (assoc (keyword (nth raportin-paaryhmat 4)) varaus-toimenpiteet)
                           (summaa-paaryhman-toimenpiteet 4 varaus-toimenpiteet)
                           (assoc (keyword (nth raportin-paaryhmat 5)) bonus-toimenpiteet)
                           (summaa-paaryhman-toimenpiteet 5 bonus-toimenpiteet))

        yhteensa {:toimenpide "Yhteensä"
                  :yht-toteutunut-summa (apply + (map (fn [pr]
                                                        (get taulukon-rivit (keyword (str pr "-toteutunut"))))
                                                      raportin-paaryhmat))
                  :yht-budjetoitu-summa (apply + (map (fn [pr]
                                                        (get taulukon-rivit (keyword (str pr "-budjetoitu"))))
                                                      raportin-paaryhmat))}]
    {:taulukon-rivit taulukon-rivit
     :yhteensa yhteensa}))
