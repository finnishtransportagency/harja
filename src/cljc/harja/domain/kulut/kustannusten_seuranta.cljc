(ns harja.domain.kulut.kustannusten-seuranta
  "Kustannusten seurannan datan prosessoinnin apurit"
  (:require [harja.pvm :as pvm]))

;; Raportin pääryhmät jäsennettynä samaan järjestykseen, kuin ui suunnitelmissa on tarkoitettu
(def raportin-paaryhmat
  ["hankintakustannukset", "johto-ja-hallintokorvaus", "hoidonjohdonpalkkio", "muutokset", "erillishankinnat", "rahavaraukset",
   "bonukset", "siirto", "tavoitehinnanoikaisu", "tavoitepalkkio", "tavoitehinnan-ylitys", "kattohinnan-ylitys",
   "sanktiot", "arvonvahennykset", "ulkopuoliset-rahavaraukset", "lisatyo", "muukulu-tavoitehintainen", "muukulu-eitavoitehintainen"])

(def tavoitehintaan-kuulumattomat-paaryhmat
  #{ "bonukset", "sanktiot", "tavoitepalkkio", "tavoitehinnan-ylitys", "kattohinnan-ylitys",
    "ulkopuoliset-rahavaraukset", "lisatyo", "muukulu-eitavoitehintainen"})

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
  [taulukko-rivit paaryhma paaryhma-nimi]
  (let [bud-key (keyword (str paaryhma-nimi "-budjetoitu"))
        bud-idx-key (keyword (str paaryhma-nimi "-budjetoitu-indeksikorjattu"))
        tot-key (keyword (str paaryhma-nimi "-toteutunut"))
        vahvistettu-key (keyword (str paaryhma-nimi "-indeksikorjaus-vahvistettu"))
        rivit (-> taulukko-rivit
                (assoc bud-key (bud-key paaryhma))
                (assoc bud-idx-key (bud-idx-key paaryhma))
                (assoc tot-key (tot-key paaryhma))
                (assoc vahvistettu-key (vahvistettu-key paaryhma)))]
    rivit))

(defn- summaa-paaryhman-toimenpiteet
  "Summataan hankintakustannukset, johto ja hallintokorvaukset sekä rahavaraukset"
  [taulukko-rivit paaryhma-nimi toimenpiteet]
  (let [indeksikorjaus-vahvistettu-avain (keyword (str paaryhma-nimi "-indeksikorjaus-vahvistettu"))
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
          (assoc (keyword (str paaryhma-nimi "-budjetoitu"))
                 (apply + (map (fn [rivi]
                                 (or (:toimenpide-budjetoitu-summa rivi) 0))
                            toimenpiteet)))
          (assoc (keyword (str paaryhma-nimi "-budjetoitu-indeksikorjattu"))
                 (apply + (map (fn [rivi]
                                 (or (:toimenpide-budjetoitu-summa-indeksikorjattu rivi) 0))
                            toimenpiteet)))
          (assoc (keyword (str paaryhma-nimi "-toteutunut"))
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
        ;; Apufunktio pääryhmän tietojen hakemiseen
        hae-paaryhma #(get (select-keys paaryhmat [%]) %)

        ;; Hakee ja prosessoi pääryhmät niiden nimen perusteella
        hankintakustannukset (hae-paaryhma "hankintakustannukset")
        jjhallinta-kustannukset (hae-paaryhma "johto-ja-hallintokorvaus")
        hoidonjohdonpalkkiot (hae-paaryhma "hoidonjohdonpalkkio")
        muutokset (hae-paaryhma "muutokset")
        erillishankinnat (hae-paaryhma "erillishankinnat")
        rahavaraukset (hae-paaryhma "rahavaraukset")
        bonukset (hae-paaryhma "bonukset")
        siirrot (hae-paaryhma "siirto")
        tavoitehinnanoikaisut (hae-paaryhma "tavoitehinnanoikaisu")
        tavoitepalkkiot (hae-paaryhma "tavoitepalkkio")
        tavoitehinnan-ylitykset (hae-paaryhma "tavoitehinnan-ylitys")
        kattohinnan-ylitykset (hae-paaryhma "kattohinnan-ylitys")
        sanktiot (hae-paaryhma "sanktiot")
        arvonvahennykset (hae-paaryhma "arvonvahennykset")
        ulkopuoliset-rahavaraukset (hae-paaryhma "ulkopuoliset-rahavaraukset")
        lisatyot (hae-paaryhma "lisatyo")
        muutkulut-tavoitehintainen (hae-paaryhma "muukulu-tavoitehintainen")
        muutkulut-eitavoitehintainen (hae-paaryhma "muukulu-eitavoitehintainen")

        rahavaraukset (sort-by toimenpide-jarjestys (group-by :toimenpide rahavaraukset))
        muutokset (sort-by toimenpide-jarjestys (group-by :kulu_tyyppi muutokset))
        hankintakustannusten-toimenpiteet (sort-by toimenpide-jarjestys (group-by :toimenpide hankintakustannukset))
        ;; Kolmiportaisessa mallissa arvonvähennyksillä on tehtäväryhmäkohtainen järjestys
        arvonvahennykset-kolmiportainen? (and (sequential? arvonvahennykset)
                                              (some (comp some? :jarjestys) arvonvahennykset))
        ;; MHU24 ja aiemmat urakat käyttävät kaksiportaista arvonvähennysmallia
        arvonvahennykset (if arvonvahennykset-kolmiportainen?
                           (sort-by toimenpide-jarjestys (group-by :toimenpide arvonvahennykset))
                           arvonvahennykset)

        ;; Summaa pääryhmät niiden nimen perusteella
        paaryhmat-summattuna
        {"hankintakustannukset" {:toimenpiteet (summaa-toimenpidetaso hankintakustannusten-toimenpiteet "hankintakustannukset")}
         "johto-ja-hallintokorvaus" {:tehtavat (summaa-paaryhman-tehtavat jjhallinta-kustannukset "johto-ja-hallintokorvaus")}
         "hoidonjohdonpalkkio" {:tehtavat (summaa-paaryhman-tehtavat hoidonjohdonpalkkiot "hoidonjohdonpalkkio")}
         "muutokset" {:toimenpiteet (summaa-toimenpidetaso muutokset "muutokset" urakan-sopimustyyppi)}
         "erillishankinnat" {:tehtavat (summaa-paaryhman-tehtavat erillishankinnat "erillishankinnat")}
         "rahavaraukset" {:toimenpiteet (summaa-toimenpidetaso rahavaraukset "rahavaraukset")}
         "bonukset" {:tehtavat (summaa-paaryhman-tehtavat bonukset "bonukset")}
         "siirto" {:tehtavat (summaa-paaryhman-tehtavat siirrot "siirto")}
         "tavoitehinnanoikaisu" {:tehtavat (summaa-paaryhman-tehtavat tavoitehinnanoikaisut "tavoitehinnanoikaisu")}
         "tavoitepalkkio" {:toimenpiteet (summaa-hoitokauden-paattamisen-kulut tavoitepalkkiot "tavoitepalkkio")}
         "tavoitehinnan-ylitys" {:toimenpiteet (summaa-hoitokauden-paattamisen-kulut tavoitehinnan-ylitykset "tavoitehinnan-ylitys")}
         "kattohinnan-ylitys" {:toimenpiteet (summaa-hoitokauden-paattamisen-kulut kattohinnan-ylitykset "kattohinnan-ylitys")}
         "sanktiot" {:tehtavat (summaa-paaryhman-tehtavat sanktiot "sanktiot")}
         "arvonvahennykset" (if arvonvahennykset-kolmiportainen?
                              {:toimenpiteet (summaa-toimenpidetaso arvonvahennykset "arvonvahennykset")}
                              {:tehtavat (summaa-paaryhman-tehtavat arvonvahennykset "arvonvahennykset")})
         "ulkopuoliset-rahavaraukset" {:tehtavat (summaa-paaryhman-tehtavat ulkopuoliset-rahavaraukset "ulkopuoliset-rahavaraukset")}
         "lisatyo" {:tehtavat (summaa-paaryhman-tehtavat lisatyot "lisatyo")}
         "muukulu-tavoitehintainen" {:tehtavat (summaa-paaryhman-tehtavat muutkulut-tavoitehintainen "muukulu-tavoitehintainen")}
         "muukulu-eitavoitehintainen" {:tehtavat (summaa-paaryhman-tehtavat muutkulut-eitavoitehintainen "muukulu-eitavoitehintainen")}}

        ;; Rakennetaan taulukon rivit iteroimalla pääryhmien lista
        ;; Tämä poistaa kaikki indeksinumerot ja tekee koodista automaattisesti skaalautuvan
        taulukon-rivit (reduce
                         (fn [taulukko paaryhma-nimi]
                           (let [paaryhma-tieto (get paaryhmat-summattuna paaryhma-nimi)]
                             (cond
                               (:toimenpiteet paaryhma-tieto)
                               (-> taulukko
                                 (assoc (keyword paaryhma-nimi) (:toimenpiteet paaryhma-tieto))
                                 (summaa-paaryhman-toimenpiteet paaryhma-nimi (:toimenpiteet paaryhma-tieto)))

                               (:tehtavat paaryhma-tieto)
                               (-> taulukko
                                 (assoc (keyword paaryhma-nimi) (:tehtavat paaryhma-tieto))
                                 (summaa-tehtavat (:tehtavat paaryhma-tieto) paaryhma-nimi))

                               :else taulukko)))
                         {}
                         raportin-paaryhmat)
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
