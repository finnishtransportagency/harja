(ns harja.palvelin.raportointi.vastaanottotarkastus-mhu-laskutusyht-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [harja.testi :refer [+kayttaja-jvh+ hae-kajaanin-maanteiden-hoitourakan-2025-2030-id jarjestelma
                                testi-http-palvelin testitietokanta urakkatieto-fixture]]
            [com.stuartsierra.component :as component]
            [harja.palvelin.komponentit.tietokanta :as tietokanta]
            [harja.kyselyt.urakat :as urakat-q]
            [harja.kyselyt.konversio :as konversio]
            [harja.palvelin.asetukset :as asetukset]
            [harja.pvm :as pvm]
            [harja.palvelin.palvelut.lupaus.lupaus-palvelu :as lupaus-palvelu]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-yhteiset :as laskutusyhteenveto-yhteiset]
            [harja.palvelin.raportointi.raportit.laskutusyhteenveto-taulukko-tyomaa :as laskutusyhteenveto-taulukko-tyomaa]
            [harja.palvelin.raportointi.excel :as excel]
            [harja.palvelin.raportointi.pdf :as pdf]
            [harja.palvelin.raportointi.raportit.vastaanottotarkastus-mhu :as vastaanottotarkastus-mhu]
            [harja.palvelin.raportointi.vastaanottotarkastus-mhu-test-apurit :as testiapurit])
  (:import (org.apache.poi.xssf.usermodel XSSFWorkbook)))

(defn jarjestelma-fixture [testit]
  (alter-var-root #'jarjestelma
    (fn [_]
      (component/start
        (component/system-map
          :db (tietokanta/luo-tietokanta testitietokanta)
          :http-palvelin (testi-http-palvelin)
          :hae-urakan-lupaustiedot (component/using
                                     (lupaus-palvelu/->Lupaus {:kehitysmoodi true})
                                     [:http-palvelin :db])))))
  (testit)
  (alter-var-root #'jarjestelma component/stop))

(use-fixtures :each
  urakkatieto-fixture
  jarjestelma-fixture)

(defn- testi-laskutus-urakka [urakka-id]
  (first (urakat-q/hae-urakan-tiedot
           (:db jarjestelma)
           {:id urakka-id})))

(defn- testi-laskutus-hoitokaudet [urakka]
  (->> (urakat-q/hae-urakan-hoitokaudet (:db jarjestelma) (:id urakka))
    (sort-by :alkupvm)
    vec))

(defn- testi-laskutus-vuosidata [hoitokaudet]
  (mapv (fn [indeksi {:keys [alkupvm loppupvm]}]
          (let [kerroin (inc indeksi)
                rahavarausnimet (if (even? indeksi)
                                  ["Äkilliset hoitotyöt" "Vahinkojen korjaukset"]
                                  ["Vahinkojen korjaukset" "Äkilliset hoitotyöt" "Uusi rahavaraus"])
                rahavarausarvot (if (even? indeksi)
                                  [(* 10M kerroin) (* 20M kerroin)]
                                  [(* 20M kerroin) (* 10M kerroin) (* 30M kerroin)])]
            {:otsikko (str "Laskutus " (pvm/vuosi alkupvm) "-" (pvm/vuosi loppupvm) " (€)")
             :hoitovuosi (pvm/vuosi alkupvm)
             :data {:talvihoito_hoitokausi_yht (* 1M kerroin)
                    :lyh_hoitokausi_yht (* 2M kerroin)
                    :sora_hoitokausi_yht (* 3M kerroin)
                    :paallyste_hoitokausi_yht (* 4M kerroin)
                    :yllapito_hoitokausi_yht (* 5M kerroin)
                    :korvausinv_hoitokausi_yht (* 6M kerroin)
                    :hankinnat_hoitokausi_yht (* 21M kerroin)
                    :johtojahallinto_hoitokausi_yht (* 7M kerroin)
                    :erillishankinnat_hoitokausi_yht (* 8M kerroin)
                    :hjpalkkio_hoitokausi_yht (* 9M kerroin)
                    :hoidonjohto_hoitokausi_yht (* 24M kerroin)
                    :muutos_erillis_hoitokausi_yht (* 10M kerroin)
                    :jjh_muutos_hoitokausi_yht (* 11M kerroin)
                    :muutostyo_hoitokausi_yht (* 21M kerroin)
                    :rahavaraus_nimet rahavarausnimet
                    :hoitokausi_yht_array rahavarausarvot
                    :kaikki_rahavaraukset_hoitokausi_yht (reduce + 0M rahavarausarvot)
                    :muut_kulut_hoitokausi (* 12M kerroin)
                    :arvonvahennykset_hoitokausi_yht (* 13M kerroin)
                    :muut_kulut_hoitokausi_yht (* 25M kerroin)
                    :lisatyot_hoitokausi_yht (* 14M kerroin)
                    :bonukset_hoitokausi_yht (* 15M kerroin)
                    :sanktiot_hoitokausi_yht (* -2M kerroin)
                    :muut_kulut_ei_tavoite_hoitokausi (* 16M kerroin)
                    :paatos_kattoh_ylitys_hoitokausi_yht (* 17M kerroin)
                    :paatos_tavoiteh_ylitys_hoitokausi_yht (* 18M kerroin)
                    :paatos_tavoitepalkkio_hoitokausi_yht (* 19M kerroin)
                    :paatos_hoidonjohtopalkkion_muutos_hoitokausi_yht (* 20M kerroin)
                    :muut_kulut_ei_tavoite_hoitokausi_yht (* 43M kerroin)
                    :yhteensa_kaikki_hoitokausi_yht (* 100M kerroin)}}))
    (range)
    hoitokaudet))

(defn- laskutusvuositaulukko [sisalto nimi]
  (some (fn [osa]
          (when (and (vector? osa)
                  (= :taulukko (first osa))
                  (= nimi (get-in osa [2 0 :otsikko])))
            osa))
    (tree-seq coll? seq sisalto)))

(defn- raporttirivin-arvot [taulukko nimi]
  (some (fn [rivi]
          (let [arvot (if (map? rivi) (:rivi rivi) rivi)]
            (when (= nimi (first arvot))
              arvot)))
    (nth taulukko 3)))

(defn- laskutus-sisalto [vuosidata urakan-alkuvuosi]
  (with-redefs [;; Oikastaan vähän. PGArrayn matkiminen on vaikeaa muuten
                konversio/pgarray->vector identity]
    (into [[:otsikko "Laskutusyhteenveto"]]
      (laskutusyhteenveto-taulukko-tyomaa/taulukot-tyomaakokous-vuosittain
        vuosidata urakan-alkuvuosi))))

(defn- excel-sivun-tekstit [sivu]
  (mapcat (fn [rivi]
            (keep (fn [solu]
                    (when (= org.apache.poi.ss.usermodel.CellType/STRING
                            (.getCellType solu))
                      (.getStringCellValue solu)))
              (iterator-seq (.cellIterator rivi))))
    (iterator-seq (.rowIterator sivu))))

(deftest laskutusyhteenveto-muodostaa-sarakkeet-ja-kustannusryhmat
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        urakka (testi-laskutus-urakka urakka-id)
        vuosidata (testi-laskutus-vuosidata (testi-laskutus-hoitokaudet urakka))
        sisalto (laskutus-sisalto vuosidata (pvm/vuosi (:alkupvm urakka)))
        taulukot (filter #(and (vector? %) (= :taulukko (first %)))
                   (tree-seq coll? seq sisalto))
        vuosien-otsikot (mapv :otsikko vuosidata)
        tavoitehinnan-ulkopuolisten-vuosien-otsikot
        (mapv (fn [{:keys [hoitovuosi]}]
                (str hoitovuosi "-" (inc hoitovuosi) " (€)"))
          vuosidata)
        yhteenveto-vuosiarvot (mapv #(* 100M %) (range 1 (inc (count vuosidata))))
        laskutus-yhteenveto (last taulukot)
        sarakeotsikot (fn [nimi]
                        (mapv :otsikko (nth (laskutusvuositaulukko sisalto nimi) 2)))]
    (is (= ["Hankinnat" "Hoidonjohto" "Muutokset" "Rahavaraukset"
            "Muut tavoitehintaan vaikuttavat kulut" "Kustannus" "Yhteenveto"]
          (mapv #(get-in % [2 0 :otsikko]) taulukot)))
    (doseq [nimi ["Hankinnat" "Hoidonjohto" "Muutokset" "Rahavaraukset"
                  "Muut tavoitehintaan vaikuttavat kulut" "Kustannus"]]
      (is (= (into [nimi]
               (concat (if (= nimi "Kustannus")
                         tavoitehinnan-ulkopuolisten-vuosien-otsikot
                         vuosien-otsikot)
                 ["Yhteensä"]))
            (sarakeotsikot nimi)))
      (is (every? #(= :raha (:fmt %))
            (rest (nth (laskutusvuositaulukko sisalto nimi) 2))))
      (is (true? (get-in (laskutusvuositaulukko sisalto nimi) [1 :viimeinen-rivi-yhteenveto?]))))
    (is (= (into ["Yhteenveto"]
             (concat tavoitehinnan-ulkopuolisten-vuosien-otsikot ["Yhteensä (€)"]))
          (mapv :otsikko (nth laskutus-yhteenveto 2))))
    (is (every? #(= :raha (:fmt %))
          (rest (nth laskutus-yhteenveto 2))))
    (is (= (into ["Laskutus yhteensä"]
             (conj yhteenveto-vuosiarvot (reduce + 0M yhteenveto-vuosiarvot)))
          (:rivi (first (nth laskutus-yhteenveto 3)))))
    (is (= 1 (count (nth laskutus-yhteenveto 3))))
    (is (= [:otsikko "Tavoitehinnan ulkopuoliset kustannukset"]
          (nth sisalto 6)))))

(deftest laskutusyhteenveto-kohdistaa-vuosiarvot-ja-laskee-summat
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        urakka (testi-laskutus-urakka urakka-id)
        vuosidata (testi-laskutus-vuosidata (testi-laskutus-hoitokaudet urakka))
        sisalto (laskutus-sisalto vuosidata (pvm/vuosi (:alkupvm urakka)))
        hankinnat (laskutusvuositaulukko sisalto "Hankinnat")
        hoidonjohto (laskutusvuositaulukko sisalto "Hoidonjohto")
        muutokset (laskutusvuositaulukko sisalto "Muutokset")
        tavoitehintaiset (laskutusvuositaulukko sisalto "Muut tavoitehintaan vaikuttavat kulut")
        ulkopuoliset (laskutusvuositaulukko sisalto "Kustannus")
        vuosikertoimet (mapv #(* 1M %) (range 1 6))]
    (is (= (into ["Talvihoito"] (concat vuosikertoimet [(reduce + 0M vuosikertoimet)]))
          (raporttirivin-arvot hankinnat "Talvihoito")))
    (is (= (into ["Yhteensä"] (concat (map #(* 21M %) vuosikertoimet)
                               [(reduce + 0M (map #(* 21M %) vuosikertoimet))]))
          (raporttirivin-arvot hankinnat "Yhteensä")))
    (is (= (into ["Johto- ja hallintokorvaukset"] (concat (map #(* 7M %) vuosikertoimet)
                                                   [(reduce + 0M (map #(* 7M %) vuosikertoimet))]))
          (raporttirivin-arvot hoidonjohto "Johto- ja hallintokorvaukset")))
    (is (= (into ["Muutostyöt (erillisrahoitetut)"] (concat (map #(* 10M %) vuosikertoimet)
                                                      [(reduce + 0M (map #(* 10M %) vuosikertoimet))]))
          (raporttirivin-arvot muutokset "Muutostyöt (erillisrahoitetut)")))
    (is (= (into ["Muut tavoitehintaan vaikuttavat kulut"] (concat (map #(* 12M %) vuosikertoimet)
                                                             [(reduce + 0M (map #(* 12M %) vuosikertoimet))]))
          (raporttirivin-arvot tavoitehintaiset "Muut tavoitehintaan vaikuttavat kulut")))
    (is (= (into ["Arvonvähennykset"] (concat (map #(* 13M %) vuosikertoimet)
                                         [(reduce + 0M (map #(* 13M %) vuosikertoimet))]))
          (raporttirivin-arvot tavoitehintaiset "Arvonvähennykset")))
    (is (= (into ["Sanktiot"] (concat (map #(* -2M %) vuosikertoimet)
                                [(reduce + 0M (map #(* -2M %) vuosikertoimet))]))
          (raporttirivin-arvot ulkopuoliset "Sanktiot")))
    (is (= (into ["Yhteensä"] (concat (map #(* 43M %) vuosikertoimet)
                               [(reduce + 0M (map #(* 43M %) vuosikertoimet))]))
          (raporttirivin-arvot ulkopuoliset "Yhteensä")))))

(deftest laskutusyhteenveto-kohdistaa-rahavaraukset-nimen-mukaan
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        urakka (testi-laskutus-urakka urakka-id)
        vuosidata (testi-laskutus-vuosidata (testi-laskutus-hoitokaudet urakka))
        taulukko (laskutusvuositaulukko
                   (laskutus-sisalto vuosidata (pvm/vuosi (:alkupvm urakka)))
                   "Rahavaraukset")]
    (is (= ["Äkilliset hoitotyöt" 10M 20M 30M 40M 50M 150M]
          (raporttirivin-arvot taulukko "Äkilliset hoitotyöt")))
    (is (= ["Vahinkojen korjaukset" 20M 40M 60M 80M 100M 300M]
          (raporttirivin-arvot taulukko "Vahinkojen korjaukset")))
    (is (= ["Uusi rahavaraus" 0M 60M 0M 120M 0M 180M]
          (raporttirivin-arvot taulukko "Uusi rahavaraus")))
    (is (= ["Yhteensä" 30M 120M 90M 240M 150M 630M]
          (raporttirivin-arvot taulukko "Yhteensä")))))

(deftest laskutusyhteenveto-nayttaa-ehtojen-mukaiset-rivit
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        urakka (testi-laskutus-urakka urakka-id)
        vuosidata (testi-laskutus-vuosidata (testi-laskutus-hoitokaudet urakka))
        urakan-alkuvuosi (pvm/vuosi (:alkupvm urakka))
        ilman-muutoksia (with-redefs [;; :mhu-muutokset käyttöön
                                      asetukset/ominaisuus-kaytossa? (constantly false)]
                          (laskutusyhteenveto-taulukko-tyomaa/taulukot-tyomaakokous-vuosittain
                            vuosidata urakan-alkuvuosi))
        muutoksilla (laskutus-sisalto vuosidata urakan-alkuvuosi)
        ilman-muutoksia (laskutusvuositaulukko ilman-muutoksia "Muutokset")
        ulkopuoliset (laskutusvuositaulukko muutoksilla "Kustannus")]
    (is (nil? ilman-muutoksia))
    (doseq [nimi ["Hoitovuoden päätös / Urakoitsija maksaa kattohinnan ylityksestä"
                  "Hoitovuoden päätös / Urakoitsija maksaa tavoitehinnan ylityksestä"
                  "Tavoitepalkkio"
                  "Hoitovuoden päätös / Hoidonjohtopalkkion muutos"]]
      (is (some? (raporttirivin-arvot ulkopuoliset nimi))))))

(deftest laskutusyhteenveto-hakee-tiedot-hoitokausittain
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        kutsut (atom [])
        nyt #inst "2026-03-15T00:00:00.000-00:00"
        hoitokaudet (testi-laskutus-hoitokaudet (testi-laskutus-urakka urakka-id))]
    (with-redefs [pvm/nyt (constantly nyt)
                  laskutusyhteenveto-yhteiset/hae-tyomaa-laskutusyhteenvedon-tiedot
                  (fn [_ _ parametrit]
                    (swap! kutsut conj parametrit)
                    [{}])]
      (let [sisalto (vastaanottotarkastus-mhu/muodosta-laskutusyhteenveto-taulukko
                      (:db jarjestelma) +kayttaja-jvh+
                      {:id urakka-id :alkupvm #inst "2025-10-01T00:00:00.000-00:00"}
                      hoitokaudet)
            hankinnat (laskutusvuositaulukko sisalto "Hankinnat")]
        (is (= (count hoitokaudet) (count @kutsut)))
        (is (= (mapv (fn [{:keys [alkupvm loppupvm]}]
                       {:urakka-id urakka-id
                        :alkupvm alkupvm
                        :loppupvm loppupvm
                        :haun-loppupvm loppupvm})
                 hoitokaudet)
              @kutsut))
        (is (= (mapv #(str "Laskutus " (pvm/vuosi (:alkupvm %)) "-" (pvm/vuosi (:loppupvm %)) " (€)")
                  hoitokaudet)
              (mapv :otsikko (rest (butlast (nth hankinnat 2))))))))))

(deftest laskutusyhteenveto-ei-hae-tietoja-ilman-hoitokausia
  (let [kutsut (atom 0)]
    (with-redefs [laskutusyhteenveto-yhteiset/hae-tyomaa-laskutusyhteenvedon-tiedot
                  (fn [& _] (swap! kutsut inc))]
      (is (= [[:otsikko "Laskutusyhteenveto"] [:teksti "Ei hoitovuosia."]]
            (vastaanottotarkastus-mhu/muodosta-laskutusyhteenveto-taulukko
              (:db jarjestelma) +kayttaja-jvh+
              {:id 987 :alkupvm #inst "2025-10-01T00:00:00.000-00:00"} [])))
      (is (zero? @kutsut)))))

(deftest laskutusyhteenveto-tulostuu-exceliin-ja-pdfaan
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        urakka (testi-laskutus-urakka urakka-id)
        vuosidata (testi-laskutus-vuosidata (testi-laskutus-hoitokaudet urakka))
        sisalto (laskutus-sisalto vuosidata (pvm/vuosi (:alkupvm urakka)))
        hankinnat (laskutusvuositaulukko sisalto "Hankinnat")
        vuosien-otsikot (mapv :otsikko vuosidata)]
    (testing "Excel-vienti sijoittaa vuositaulukot samalle välilehdelle"
      (with-open [workbook (XSSFWorkbook.)]
        (excel/muodosta-excel (into [:raportti {:nimi "Testi"}] sisalto) workbook)
        (is (= 1 (.getNumberOfSheets workbook)))
        (let [sivu (.getSheetAt workbook 0)
              tekstit (set (excel-sivun-tekstit sivu))]
          (is (= "Laskutusyhteenveto" (.getSheetName sivu)))
          (is (every? tekstit (concat vuosien-otsikot ["Hankinnat" "Yhteensä"]))))))
    (testing "PDF-taulukossa on vuosien lisäksi yhteensä-sarake ja otsikot"
      (let [pdf-taulukko (pdf/muodosta-pdf hankinnat)
            pdf-sisalto (tree-seq coll? seq pdf-taulukko)
            pdf-taulukon-otsake (some #(when (and (vector? %) (= :fo:table-header (first %))) %)
                                  pdf-sisalto)
            pdf-sarakkeiden-otsikot (set (keep #(when (and (vector? %) (= :fo:block (first %)))
                                                  (second %))
                                          (tree-seq coll? seq pdf-taulukon-otsake)))
            pdf-sarakkeet (filter #(and (vector? %) (= :fo:table-column (first %)))
                            pdf-sisalto)]
        (is (= 7 (count pdf-sarakkeet)))
        (is (every? pdf-sarakkeiden-otsikot
              (map #(str "<![CDATA[" % "]]>") vuosien-otsikot)))
        (is (contains? pdf-sarakkeiden-otsikot "<![CDATA[Yhteensä]]>"))))))

(deftest laskutusyhteenveto-nayttaa-arvonvahennykset-vain-kayttoon-kuuluvilla-vuosilla
  (let [vuosidata (mapv (fn [vuosi data]
                          {:hoitovuosi vuosi
                           :otsikko (str "Laskutus " vuosi "-" (inc vuosi) " (€)")
                           :data data})
                  [2024 2025 2026]
                  [{:muut_kulut_hoitokausi 10M
                    :arvonvahennykset_hoitokausi_yht 0M
                    :muut_kulut_hoitokausi_yht 10M}
                   {:muut_kulut_hoitokausi 20M
                    :arvonvahennykset_hoitokausi_yht 0M
                    :muut_kulut_hoitokausi_yht 20M
                    :paatos_tavoitepalkkio_hoitokausi_yht 0M}
                   {:muut_kulut_hoitokausi 30M
                    :arvonvahennykset_hoitokausi_yht 30M
                    :muut_kulut_hoitokausi_yht 60M
                    :paatos_tavoitepalkkio_hoitokausi_yht 7M}])
        sisalto (laskutus-sisalto vuosidata 2019)
        tavoitehintaiset (laskutusvuositaulukko sisalto "Muut tavoitehintaan vaikuttavat kulut")
        ulkopuoliset (laskutusvuositaulukko sisalto "Kustannus")
        tavoitehinnan-rivit (nth tavoitehintaiset 3)
        ulkopuoliset-rivit (nth ulkopuoliset 3)
        riveista-nimet (fn [rivit]
                         (mapv #(first (if (map? %) (:rivi %) %)) rivit))]
    (is (= ["Muut tavoitehintaan vaikuttavat kulut" "Arvonvähennykset" "Yhteensä"]
          (riveista-nimet tavoitehinnan-rivit)))
    (is (= ["Arvonvähennykset" 0M 0M 30M 30M]
          (raporttirivin-arvot tavoitehintaiset "Arvonvähennykset")))
    (is (= ["Tavoitepalkkio" 0M 0M 7M 7M]
          (raporttirivin-arvot ulkopuoliset "Tavoitepalkkio")))
    (is (= 1 (count (filter #{"Arvonvähennykset"} (riveista-nimet tavoitehinnan-rivit)))))
    (is (= 1 (count (filter #{"Tavoitepalkkio"} (riveista-nimet ulkopuoliset-rivit)))))))

(deftest laskutusyhteenveto-rajaa-keskeneraisen-hoitovuoden-toteumaan
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        alkupvm #inst "2025-10-01T00:00:00.000-00:00"
        loppupvm #inst "2026-09-30T00:00:00.000-00:00"
        haetut-parametrit (atom [])]
    (with-redefs [laskutusyhteenveto-yhteiset/hae-tyomaa-laskutusyhteenvedon-tiedot
                  (fn [_ _ parametrit]
                    (swap! haetut-parametrit conj parametrit)
                    [{}])]
      (vastaanottotarkastus-mhu/muodosta-laskutusyhteenveto-taulukko
        (:db jarjestelma) +kayttaja-jvh+ {:id urakka-id :alkupvm alkupvm}
        [{:alkupvm alkupvm :loppupvm loppupvm}])
      (is (= [{:urakka-id urakka-id :alkupvm alkupvm :loppupvm loppupvm :haun-loppupvm loppupvm}]
            @haetut-parametrit)))))

(deftest laskutusyhteenveto-liitetaan-raporttiin-oikeaan-kohtaan
  (let [urakka-id (hae-kajaanin-maanteiden-hoitourakan-2025-2030-id)
        hoitokaudet (testi-laskutus-hoitokaudet (testi-laskutus-urakka urakka-id))
        odotetut-lupausbonukset-ja-sanktiot
        (mapv (fn [indeksi]
                (case indeksi
                  0 75M
                  1 300M
                  150M))
          (range (count hoitokaudet)))
        laskutus-osio [[:otsikko "Laskutusyhteenveto"]
                       [:taulukko {:otsikko "Laskutuksen testitaulukko"} [] []]]
        raportti (with-redefs [vastaanottotarkastus-mhu/muodosta-laskutusyhteenveto-taulukko
                               (fn [& _] laskutus-osio)]
                   (testiapurit/muodosta-testiraportti true))
        lupaus-taulukko (some (fn [osa]
                                (when (and (vector? osa)
                                        (= :taulukko (first osa))
                                        (= "Lupaukset" (get-in osa [1 :otsikko])))
                                  osa))
                          (tree-seq coll? seq raportti))
        lupausvuosien-rivit (when lupaus-taulukko
                              (butlast (nth lupaus-taulukko 3)))
        laskutus-otsikon-indeksi (.indexOf raportti [:otsikko "Laskutusyhteenveto"])
        laskutustaulukon-indeksi (.indexOf raportti (second laskutus-osio))
        tavoitehintataulukon-indeksi (first
                                      (keep-indexed (fn [indeksi osa]
                                                      (when (and (vector? osa)
                                                              (= :taulukko (first osa))
                                                              (= "Urakan tavoitehintaan kuuluvat kustannukset"
                                                                (get-in osa [1 :otsikko])))
                                                        indeksi))
                                        raportti))]
    (is (not= -1 laskutus-otsikon-indeksi))
    (is (not= -1 laskutustaulukon-indeksi))
    (is (< laskutus-otsikon-indeksi laskutustaulukon-indeksi tavoitehintataulukon-indeksi))
    (is (some? lupaus-taulukko))
    (is (= odotetut-lupausbonukset-ja-sanktiot
          (mapv #(nth % 3) lupausvuosien-rivit)))))
