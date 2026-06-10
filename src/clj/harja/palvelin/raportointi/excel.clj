(ns harja.palvelin.raportointi.excel
  "Harja raporttielementtien vienti Excel muotoon.

  Harjan raportit ovat Clojuren tietorakenteita, joissa käytetään
  tiettyä rakennetta ja tiettyjä avainsanoja. Nämä raportit annetaan
  eteenpäin moottoreille, jotka luovat tietorakenteen pohjalta raportin.
  Tärkeä yksityiskohta on, että raporttien olisi tarkoitus sisältää ns.
  raakaa dataa, ja antaa raportin formatoida data oikeaan muotoon sarakkeen :fmt
  tiedon perusteella.

  Excel-moottori koostuu lähinnä muodosta-excel multimetodista. Tärkein
  näistä on :taulukko tyypin käsittelijä.

  Koska moottori käyttää Apache POI kirjastoa, joudutaan koodissa käyttämään
  ikäviä oliomaisuuksia. Tämä ilmenee erityisesti solujen tyylittelyssä.

  EXCEL TYYLIT

  POI sisältää sisäänrakennenttuja tyylejä, joita solulle voi asettaa.
  Jos tarvitaan uusia custom tyylejä luoda Exceliä varten:
  http://poi.apache.org/apidocs/org/apache/poi/ss/usermodel/BuiltinFormats.html
  Yllä olevasta linkistä voi katsoa mallia, missä muodossa format-str voi antaa."

  (:require [taoensso.timbre :as log]
            [dk.ative.docjure.spreadsheet :as excel]
            [clojure.string :as str]
            [harja.fmt :as fmt]
            [harja.domain.raportointi :as raportti-domain]
            [harja.palvelin.raportointi.raportit.yleinen :as raportit-yleinen])
  (:import (org.apache.poi.ss.util CellReference WorkbookUtil CellRangeAddress CellUtil)
           (org.apache.poi.ss.usermodel HorizontalAlignment)))

(defmulti muodosta-excel
  "Muodostaa Excel data annetulle raporttielementille.
  Dispatch tyypin mukaan (vektorin 1. elementti)."
  (fn [elementti workbook]
    (when-not (raportti-domain/raporttielementti? elementti)
      (log/warn "Elementti ei ole raporttielementti, ohitetaan: " (pr-str elementti)))
    (when (raportti-domain/raporttielementti? elementti)
      (first elementti))))

(defmulti muodosta-solu
  "Raporttisolujen tyylittely täytyy Apache POI kirjaston takia tehdä niin,
  että metodit palauttavat solun datan, tyyliobjektin, jota ne ovat
  mahdollisesti täydentäneet, sekä optionaalisen formaatin.
  Moottorissa on olemassa oletustyyli soluille, jonka solut ottavat vastaan, ja muokkaavat.
  Solu voi esimerkiksi sisältää virheen, jolloin Tyyliobjektiin asetetaan tieto,
  että fontin pitää olla punainen."
  (fn [elementti tyyli]
    (if (raportti-domain/raporttielementti? elementti)
      (first elementti)
      :vain-arvo)))


(defn solu [rivi-nro sarake-nro]
  (.formatAsString (CellReference. rivi-nro sarake-nro)))

(defmulti aseta-kaava!
  (fn [[_ {:keys [kaava]}] _ _]
    kaava))

(defn- evaluoi-kaava
  "Luo kaavaevaluaattorin ja evaluoi kaavan. Parametrina sisään workbook ja solu."
  [workbook cell]
  (-> workbook
    (.getCreationHelper)
    (.createFormulaEvaluator)
    (.evaluateFormulaCell cell)))

(defn parsi-sarakekirjain
  "Parsii Excel-solun sarakekirjaimen, esim. 'A16' --> A, AC15 --> AC"
  [osoite]
  (re-find (re-pattern #"^[a-zA-Z]+") (.toString osoite)))

(defn parsi-rivinumero
  "Parsii Excel-solun rivinumeron, esim. 'A16' --> 16"
  [osoite]
  (let [rivinumero-stringina (re-find (re-pattern "\\d+") (.toString osoite))]
    (when rivinumero-stringina (Integer/parseInt rivinumero-stringina))))

(defmethod aseta-kaava! :summaa-yllaolevat [[_ {:keys [alkurivi loppurivi]}] workbook cell]
  (let [osoite (-> cell .getAddress)
        sarake (first (.toString osoite))]
    (.setCellFormula
      cell
      (str "SUM(" sarake (or alkurivi 1) ":" sarake (or loppurivi (.getRow osoite)) ")"))
    (evaluoi-kaava workbook cell)))

(defmethod aseta-kaava! :summaa-vieressaolevat [[_ {:keys [alkusarake loppusarake]}] workbook cell]
  (let [osoite (-> cell .getAddress)
        rivi (parsi-rivinumero osoite)
        loppusarake (or loppusarake (parsi-sarakekirjain osoite))]
    (.setCellFormula
      cell
      (str "SUM(" (or alkusarake "A") rivi ":" loppusarake rivi ")")))
  (evaluoi-kaava workbook cell))

(defn- ilman-soft-hyphenia [data]
  (if (string? data)
    (.replace data "\u00AD" "")
    data))

(defmethod muodosta-solu :vain-arvo [arvo solun-tyyli] [arvo solun-tyyli])

;; Excelissä tekee täsmälleen saman kuin ylempi :vain-arvo, mutta pdf:ssä ja html raportissa ui on eri näköinen ja me joudutaan
;; käyttämään samoja elementtejä, niin se täällä excel puolella vaikuttaa toistolta, mutta ei ole kokonaisuudessaan sitä.
(defmethod muodosta-solu :arvo [[_ {:keys [arvo lihavoi? korosta?
                                           korosta-hennosti? ala-korosta? korosta-harmaa?
                                           varoitus? huomio?]}] solun-tyyli]
  (let [oletustyyli (raportti-domain/solun-oletustyyli-excel lihavoi? korosta? korosta-hennosti? korosta-harmaa? varoitus? huomio?)
        solun-tyyli (if-not (empty? solun-tyyli)
                      solun-tyyli
                      oletustyyli)]
    [arvo solun-tyyli]))

(defmethod muodosta-solu :boolean [[_ {:keys [arvo]}] solun-tyyli]
  [(if arvo "Kyllä" "Ei") solun-tyyli])

(defmethod muodosta-solu :liitteet [[_ liitteet] solun-tyyli]
  [(count liitteet) solun-tyyli])

(defmethod muodosta-solu :arvo-ja-osuus [[_ {:keys [arvo osuus]}] solun-tyyli]
  [arvo solun-tyyli])

(defmethod muodosta-solu :arvo-yksikko-ja-osuus [[_ {:keys [arvo osuus yksikko]}] solun-tyyli]
  [(str arvo " " yksikko " (" osuus " %)") solun-tyyli])

(defmethod muodosta-solu :arvo-ja-yksikko [[_ {:keys [arvo yksikko desimaalien-maara]}] solun-tyyli]
  [arvo solun-tyyli (when desimaalien-maara
                      (if (= yksikko "%")
                        nil
                        [:kustomi desimaalien-maara]))])

(defmethod muodosta-solu :erotus-ja-prosentti [[_ {:keys [arvo prosentti desimaalien-maara lihavoi? korosta?
                                                          korosta-hennosti? ala-korosta? korosta-harmaa?
                                                          varoitus? huomio?]}] solun-tyyli]
  (let [etuliite (cond
                   (neg? arvo) "- "
                   (zero? arvo) ""
                   :else "+ ")
        arvo (Math/abs (float arvo))
        prosentti (Math/abs (float prosentti))
        oletustyyli (raportti-domain/solun-oletustyyli-excel lihavoi? korosta? korosta-hennosti? korosta-harmaa? varoitus? huomio?)
        solun-tyyli (if-not (empty? solun-tyyli)
                      solun-tyyli
                      oletustyyli)
        solun-tyyli (if ala-korosta?
                      (dissoc solun-tyyli :background)
                      solun-tyyli)
        solun-tyyli (if varoitus?
                      (merge solun-tyyli
                        {:background :red
                         :font {:color :white :name "Open Sans" :size 12}})
                      solun-tyyli)]
    [(str etuliite
       (cond desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara)
         :else arvo)
       (when prosentti (str " (" etuliite (fmt/prosentti-opt prosentti) ")"))) solun-tyyli]))

;; Säädä yksittäisestä solusta haluttu. Solun tyyli saadaan raporttielementilla esim. näin:
;; [:arvo-ja-yksikko-korostettu {:arvo yht :korosta-hennosti? true :yksikko "%" :desimaalien-maara 2}]
(defmethod muodosta-solu :arvo-ja-yksikko-korostettu [[_ {:keys [arvo yksikko desimaalien-maara lihavoi? korosta?
                                                                 korosta-hennosti? korosta-harmaa? ala-korosta?
                                                                 varoitus? huomio?]}] solun-tyyli]
  (let [oletustyyli (raportti-domain/solun-oletustyyli-excel lihavoi? korosta? korosta-hennosti? korosta-harmaa? varoitus? huomio?)
        solun-tyyli (if-not (empty? solun-tyyli)
                      solun-tyyli
                      oletustyyli)
        solun-tyyli (if ala-korosta?
                      (dissoc solun-tyyli :background)
                      solun-tyyli)
        ;; Rivin pääasiallista tyyliä on mahdollista muokata myös varoituksen muodossa, kunhan attribuutti varoitus? on annettu
        ;; Ylikirjoitetaan tässä mahdollisen varoituksen vaikutukset myös yhteenvetoriveille
        solun-tyyli (cond
                      varoitus?
                      (merge solun-tyyli
                        {:background :red
                         :font {:color :white :name "Open Sans" :size 12}})
                      huomio?
                      (merge solun-tyyli
                        {:background :orange
                         :font {:color :black :name "Open Sans" :size 12}})
                      :default solun-tyyli)]
    [arvo solun-tyyli
     (when desimaalien-maara
       (if (= yksikko "%")
         nil
         [:kustomi desimaalien-maara]))]))

(defmethod muodosta-solu :arvo-ja-selite [[_ {:keys [arvo selite]}] solun-tyyli]
  [(str arvo (when selite (str " (" selite ")"))) solun-tyyli])

(defmethod muodosta-solu :varillinen-teksti [[_ {:keys [arvo tyyli fmt lihavoi? korosta-hennosti?]}] solun-tyyli]
  (let [solun-tyyli (cond
                      lihavoi?
                      (merge solun-tyyli {:font {:bold true :name "Open Sans" :size 12}})

                      (nil? solun-tyyli)
                      {:font {:name "Open Sans" :size 12}}

                      :else solun-tyyli)

        solun-tyyli (if korosta-hennosti?
                      (merge solun-tyyli {:background :light_cornflower_blue})
                      solun-tyyli)]
    [arvo
     (merge solun-tyyli (when tyyli (tyyli raportti-domain/virhetyylit-excel)))
     fmt]))

(defmethod muodosta-solu :infopallura [_ _]
  nil)

(defmethod muodosta-solu :teksti-ja-info [[_ {:keys [arvo]}] solun-tyyli]
  [arvo solun-tyyli])

(defmethod muodosta-excel :teksti [[_ teksti] workbook]
  (when-let [sheet (last (excel/sheet-seq workbook))]
    (let [rivi-numero (let [viimeinen (.getLastRowNum sheet)]
                        (if (and (zero? viimeinen) (nil? (.getRow sheet 0)))
                          0
                          (inc viimeinen)))
          tyyli (excel/create-cell-style! workbook {:font {:color :black :name "Open Sans" :size 12}})
          rivi (.createRow sheet rivi-numero)
          cell (.createCell rivi 0)]
      (excel/set-cell! cell teksti)
      (excel/set-cell-style! cell tyyli))))

(defmethod muodosta-solu :osittain-boldattu-teksti
  ;; Joihinkin teksteihin halutaan osittain boldattu teksti. Se ei ole mahdollista Excelissä, joten tehdään
  ;; vain tämä elementti, joka toimii kuten teksti -elementit toimii, mutta ei aiheuta erroreita
  [[_ {:keys [boldattu-teksti teksti] :as tiedot}] solun-tyyli]
  [(str boldattu-teksti teksti) solun-tyyli])

(defmethod muodosta-excel :tyhja-rivi [_ workbook]
  (when-let [sheet (last (excel/sheet-seq workbook))]
    (let [rivi-numero (inc (.getLastRowNum sheet))]
      (.createRow sheet rivi-numero))))

(defn- font-otsikko
  ([] (font-otsikko 14))
  ([font-koko]
   {:color :black
    :size font-koko
    :name "Open Sans"
    :bold true}))

(defn- luo-saraketyyli
  [workbook lista-tyyli? taustavari lihavoitu?]
  (excel/create-cell-style! workbook (if lista-tyyli?
                                       {:border-bottom :thin
                                        :border-top :thin
                                        :border-left :thin
                                        :border-right :thin
                                        :font (font-otsikko 14)}
                                       {:background (or taustavari :grey_25_percent)
                                        :font (merge
                                                {:color :black :name "Open Sans" :size 12}
                                                (when lihavoitu? {:bold true}))})))

(defn taulukko-otsikkorivi [otsikko-rivi sarakkeet workbook lista-tyyli?]
  (dorun
    (map-indexed
      (fn [sarake-nro {:keys [otsikko taustavari lihavoitu?] :as sarake}]
        (let [cell (.createCell otsikko-rivi sarake-nro)
              sarake-tyyli (luo-saraketyyli workbook lista-tyyli? taustavari lihavoitu?)]
          (excel/set-cell! cell (ilman-soft-hyphenia otsikko))
          (excel/set-cell-style! cell sarake-tyyli)))
      sarakkeet)))

(defn luo-data-formaatti
  "Luo custom Excel tyyli. Format-str on esim '$#,##0_;[Red]($#,##0)'"
  [workbook format-str]
  (let [creation-helper (.getCreationHelper workbook)
        data-format (.createDataFormat creation-helper)]
    (.getFormat data-format format-str)))

(defn tyyli-kustom-format-mukaan [desimaalien-maara workbook tyyli]
  (let [pattern (apply str "0." (repeat desimaalien-maara 0))
        data-format (luo-data-formaatti workbook pattern)]
    (.setDataFormat tyyli data-format)))

(defn tyyli-format-mukaan
  "Antaa Excel-soluille erityyppisiä formattereita, kuten raha, kokonaisluku tai pvm."
  [workbook fmt voi-muokata? tyyli]
  ;; voi-muokata? vaikuttaa vain, jos sheet on asetettu protected arvoon,
  ;; joka enabloidaan lipulla :varjele-sheet-muokkauksilta?)
  (.setLocked tyyli (not voi-muokata?))

  ;; Lisätty tyyliformaatti euroille
  (let [raha-formaatti (luo-data-formaatti workbook "€#,##0.00;[Red]-€#,##0.00")]
    (case fmt
      ;; .setDataFormat hakee indeksillä tyylejä.
      ;; Tyylejä voi määritellä itse (https://poi.apache.org/apidocs/org/apache/poi/xssf/usermodel/XSSFDataFormat.html)
      ;; tai voimme käyttää valmiita, sisäänrakennettuja tyylejä.
      ;; http://poi.apache.org/apidocs/org/apache/poi/ss/usermodel/BuiltinFormats.html
      :kokonaisluku (.setDataFormat tyyli 1)
      :raha (.setDataFormat tyyli raha-formaatti)
      :prosentti (.setDataFormat tyyli 10)
      :numero (.setDataFormat tyyli 2)
      :numero-3desim (.setDataFormat tyyli 3)
      :pvm (.setDataFormat tyyli 14)
      :pvm-aika (.setDataFormat tyyli 22)
      nil)))

(defn- tee-raportin-tiedot-rivi
  [sheet {:keys [nolla raportin-nimi alkupvm urakka loppupvm tyyli
                 custom-ylin-rivi] :as tiedot}]
  (try
    (let [rivi (.createRow sheet nolla)
          solu (.createCell rivi 0)
          ;; Jos loppupvm on täysin sama, sitä ei tarvitse mainita
          loppupvm (if (= loppupvm alkupvm) nil loppupvm)]
      (excel/set-cell! solu (or
                              custom-ylin-rivi
                              (str raportin-nimi
                                (when urakka (str ", " urakka))
                                (when (and alkupvm (not loppupvm)) (str ", " alkupvm))
                                (when (and alkupvm loppupvm) (str ", " alkupvm " - " loppupvm)))))
      (excel/set-cell-style! solu tyyli)
      ;; Tehdään otsikkorivin 20 ensimmäistä solua mergetyksi.
      ;; Täten se ei häiritse automaattista solujen koon luontia, ja otsikon pitäisi kuitenkin näkyä klippaamatta.
      (.addMergedRegion sheet (CellRangeAddress. nolla nolla 0 20))
      sheet)
    (catch Throwable t
      (log/error t "Virhe Excel muodostamisessa"))))

(defn- tee-taulukon-nimiotsikko [sheet nolla nimi raportin-tiedot-tyyli]
  (let [rivi (.createRow sheet (dec nolla))
        solu (.createCell rivi 0)]
    (excel/set-cell! solu nimi)
    (excel/set-cell-style! solu raportin-tiedot-tyyli)))

(defn- tee-sheet-otsikkoteksti [sheet rivinumero otsikkoteksti tyyli]
  (let [rivi (.createRow sheet rivinumero)
        solu (.createCell rivi 0)]
    (excel/set-cell! solu otsikkoteksti)
    (excel/set-cell-style! solu tyyli)))

(defn- tee-tekstirivi [sheet rivinumero teksti tyyli]
  (let [rivi (.createRow sheet rivinumero)
        solu (.createCell rivi 0)]
    (excel/set-cell! solu teksti)
    (excel/set-cell-style! solu tyyli)))

(defn- font-leipateksti
  ([] (font-leipateksti 11))
  ([font-koko]
   {:color :black :size font-koko :name "Open Sans"}))

(defn tasaa-solu [solu tasaa]
  (CellUtil/setAlignment solu
    (case tasaa
      :keskita HorizontalAlignment/CENTER
      :oikea HorizontalAlignment/RIGHT
      HorizontalAlignment/LEFT)))

(defn- luo-rivi-ennen-tyyli
  [workbook lista-tyyli? taustavari tummenna-teksti? lihavoitu?]
  (excel/create-cell-style! workbook (if lista-tyyli?
                                       {:border-bottom :thin
                                        :border-top :thin
                                        :border-left :thin
                                        :border-right :thin
                                        :font (font-otsikko 18)}
                                       {:background (or taustavari (if tummenna-teksti?
                                                                     :light_cornflower_blue
                                                                     :grey_25_percent))
                                        :font (merge {:color :black :name "Open Sans" :size 12}
                                                (when lihavoitu? {:bold true}))})))

(defn luo-rivi-jalkeen-tyyli [workbook]
  (excel/create-cell-style! workbook {:font (font-leipateksti)}))

(defn- luo-rivi-ennen-tai-jalkeen
  "Luo rivin joko ennen tai jälkeen varsinaisen taulukon."
  [rivi-maaritys riviolio rivi-nro sheet workbook lista-tyyli? rivi-ennen?]
  (reduce (fn [sarake-nro {:keys [teksti tasaa sarakkeita taustavari tummenna-teksti? lihavoitu?]}]
            (let [sarakeryhman-tyyli (cond

                                       rivi-ennen?
                                       (luo-rivi-ennen-tyyli workbook lista-tyyli? taustavari tummenna-teksti? lihavoitu?)

                                       (not rivi-ennen?)
                                       (luo-rivi-jalkeen-tyyli workbook))
                  solu (.createCell riviolio sarake-nro)]
              (excel/set-cell! solu teksti)
              (excel/set-cell-style! solu sarakeryhman-tyyli)
              (tasaa-solu solu tasaa)
              (when (> sarakkeita 1)
                (.addMergedRegion sheet (CellRangeAddress. rivi-nro rivi-nro
                                          sarake-nro
                                          (+ sarake-nro sarakkeita -1))))
              (+ sarake-nro sarakkeita)))
    0 rivi-maaritys))

(def puskuririvien-maara-ennen-rivi-jalkeen 5)

(defn taulukon-valiotsikko [otsikko workbook]
  ;; Tekee väliotsikon exceliin mikäli tämä puuttuu, annetaan raportin taulukon parametreissa 
  (let [aiempi-sheet (last (excel/sheet-seq workbook))
        [sheet rivi-numero] [aiempi-sheet (inc (.getLastRowNum aiempi-sheet))]
        tyyli-tiedot {:border-bottom :thin :background :grey_25_percent :font {:bold true :color :black :size 12 :name "Open Sans"}}
        tyyli (excel/create-cell-style! workbook tyyli-tiedot)

        rivi (.createRow sheet rivi-numero)
        rivin-solu (.createCell rivi 0)
        harmaa-sivu (.createCell rivi 1)]
    (raportti-domain/tee-solu rivin-solu otsikko tyyli)
    (raportti-domain/tee-solu harmaa-sivu nil tyyli)
    rivi-numero))

(defn- excel-alkuteksti->elementti
  "Taulukon alkutekstit. Excelissä otsikointi on kinkkistä, kun ne tulevat omille sheeteilleen ja
  näin ovat erilaisia, kuin pdf tai html versioissa. Uusi sheet tulee aina taulukon mukana.
  Joten näillä alkuteksteillä rakennetaan samanlainen otsikointi taulukoille, kuin html ja pdf versioissa."
  [x]
  (cond
    (string? x) [:teksti x]
    (and (vector? x) (keyword? (first x))) x
    :else [:teksti (str x)]))

(defn- excel-alkuteksti-tyyli [workbook tyyppi]
  (case tyyppi
    :otsikko-title         (excel/create-cell-style! workbook {:font (font-otsikko 18)})
    :otsikko-heading       (excel/create-cell-style! workbook {:font (font-otsikko 14)})
    :otsikko-heading-small (excel/create-cell-style! workbook {:font (font-otsikko 12)})
    :teksti                (excel/create-cell-style! workbook {:font (font-leipateksti 12)})
    (excel/create-cell-style! workbook {:font (font-leipateksti 12)})))

(defmethod muodosta-excel :taulukko [[_ {:keys [nimi otsikko excel-alkutekstit raportin-tiedot
                                                viimeinen-rivi-yhteenveto? lista-tyyli?
                                                sheet-nimi samalle-sheetille?
                                                rivi-ennen rivi-jalkeen] :as optiot}
                                      sarakkeet data] workbook]
  (try
    (let [viimeinen-rivi (last data)
          aiempi-sheet (last (excel/sheet-seq workbook))
          [sheet nolla] (if (and
                              (or samalle-sheetille? (nil? sheet-nimi))
                              (or samalle-sheetille? (nil? nimi))
                              aiempi-sheet)
                          [aiempi-sheet (+ 2 (.getLastRowNum aiempi-sheet))]
                          [(excel/add-sheet! workbook
                             (WorkbookUtil/createSafeSheetName
                               (or sheet-nimi nimi))) 0])
          ;; mahdollista haluttujen sheetien sisällä solujen lukitseminen (sheet protection)
          _ (when (:varjele-sheet-muokkauksilta? optiot)
              (.enableLocking sheet))
          raportin-tiedot-tyyli (excel/create-cell-style! workbook {:font (font-otsikko)})
          ;; Ei tehdä uutta otsikkoriviä, jos taulukko tulee samalle sheetille.
          tee-raporttitiedot-rivi? (= nolla 0)

          ;; Luodaan raportin tiedot sisältävä rivi sheetin alkuun tähän indeksiin myöhemmässä vaiheessa. Voisi varmaan käyttää nollaakin suoraan ie. 0 
          raportin-tiedot-rivi nolla
          nolla (+ 2 nolla)

          ;; Tehdään vähän väliä raporttirivien ja taulukon otsikolle, kun on useampi taulukko samalla sheetillä
          ;; Nolla on nimetty vähän heikosti, mutta tarkoittaa nollariviä itse taulukon tiedoille. Eli rivin järjestynumero, josta taulukko alkaa
          nolla (if (and samalle-sheetille? tee-raporttitiedot-rivi?)
                  (+ 2 nolla)
                  nolla)
          rivi-ennen-nro nolla
          rivi-ennen-rivi (when rivi-ennen (.createRow sheet nolla))

          rivi-jalkeen-nro (+ puskuririvien-maara-ennen-rivi-jalkeen (count data))
          rivi-jalkeen-rivi (when rivi-jalkeen (.createRow sheet rivi-jalkeen-nro))
          nolla (if rivi-ennen (inc nolla) nolla)
          nolla (if excel-alkutekstit (+ nolla (count excel-alkutekstit))
                  nolla)
          otsikko-rivi (.createRow sheet nolla)
          luodut-tyylit (atom {})
          luo-uusi-tyyli (fn [solun-tyyli formaatti-fn sarake-fmt]
                           (let [uusi-tyyli (doto (excel/create-cell-style! workbook solun-tyyli)
                                              formaatti-fn)]
                             (swap! luodut-tyylit assoc-in [solun-tyyli sarake-fmt] uusi-tyyli)
                             uusi-tyyli))]
      ;; Luodaan mahdollinen rivi-ennen
      (when rivi-ennen
        (luo-rivi-ennen-tai-jalkeen rivi-ennen
          rivi-ennen-rivi
          rivi-ennen-nro
          sheet
          workbook
          lista-tyyli?
          true))

      ;; Luodaan sheetille apuotsikot
      (when excel-alkutekstit
        (dorun
          (map-indexed
            (fn [rivi-nro rivi]
              (let [[tyyppi teksti] (excel-alkuteksti->elementti rivi)
                    tyyli           (excel-alkuteksti-tyyli workbook tyyppi)]
                (tee-tekstirivi sheet (+ 2 rivi-nro) teksti tyyli)))
            excel-alkutekstit)))

      ;; Jos on useampi taulu samalla sheetillä, laitetaan niiden nimet ennen sarakkeiden otsikkoja.
      (when samalle-sheetille?
        ;; Jos taulukon nimeä ei ole, käytä taulukon otsikkoa
        (let [rivi-otsikko (if (nil? nimi) otsikko nimi)]
          (tee-taulukon-nimiotsikko sheet nolla rivi-otsikko raportin-tiedot-tyyli)))

      ;;Luodaan sheet:tille otsikko - Käytä taulukolle annettua otsikkoa, jos se on annettu
      (when otsikko
        (tee-sheet-otsikkoteksti sheet 1 otsikko raportin-tiedot-tyyli))

      (taulukko-otsikkorivi otsikko-rivi sarakkeet workbook lista-tyyli?)

      (dorun
        (map-indexed
          (fn [rivi-nro rivi]
            ;; Lisää väliotsikot mikäli nämä puuttuvat
            (let [lisatty-otsikko (when (:otsikko rivi)
                                    (taulukon-valiotsikko (:otsikko rivi) workbook))
                  rivi-nro (+ nolla 1 rivi-nro)
                  rivi-nro (if (= rivi-nro lisatty-otsikko) (inc rivi-nro) rivi-nro)
                  [data optiot] (if (map? rivi)
                                  [(:rivi rivi) rivi]
                                  [rivi {}])
                  row (.createRow sheet rivi-nro)]
              (dorun
                (map-indexed
                  (fn [sarake-nro sarake]
                    (let [cell (.createCell row sarake-nro)
                          lihavoi? (or (:lihavoi? optiot)
                                     (and viimeinen-rivi-yhteenveto?
                                       (= rivi viimeinen-rivi)))
                          korosta? (:korosta? optiot)
                          korosta-hennosti? (:korosta-hennosti? optiot)
                          varoitus? (:varoitus? optiot)
                          huomio? (:huomio? optiot)
                          korosta-harmaa? (:korosta-harmaa? optiot)
                          arvo-datassa (nth data sarake-nro nil)
                          ;; ui.yleiset/totuus-ikonin tuki toistaiseksi tämä
                          arvo-datassa (if (= [:span.livicon-check] arvo-datassa)
                                         "X"
                                         arvo-datassa)
                          sarake-fmt (:fmt sarake)
                          solu-fmt (and (vector? arvo-datassa)
                                     (:fmt (second arvo-datassa)))
                          formatoi-solu? (raportti-domain/formatoi-solu? arvo-datassa)

                          oletustyyli (raportti-domain/solun-oletustyyli-excel lihavoi? korosta? korosta-hennosti? korosta-harmaa? varoitus? huomio?)
                          [naytettava-arvo solun-tyyli formaatti]
                          (if (and (raportti-domain/raporttielementti? arvo-datassa)
                                (not (raportti-domain/excel-kaava? arvo-datassa)))
                            (muodosta-solu arvo-datassa oletustyyli)
                            [arvo-datassa oletustyyli])
                          kustomi-formaatti? (and (vector? formaatti) (= (first formaatti) :kustomi))
                          ;; Jos solun muodostus on antanut formaatin, käytä sitä.
                          ;; Jos sarakkeelle on annettu formaatti, käytä sitä.
                          ;; Muuten käytetään oletusformaattia arvon mukaan.
                          formaatti-fn (cond
                                         kustomi-formaatti?
                                         (partial tyyli-kustom-format-mukaan (second formaatti) workbook)

                                         formaatti
                                         (partial tyyli-format-mukaan workbook formaatti nil)

                                         formatoi-solu?
                                         (partial tyyli-format-mukaan workbook (or solu-fmt sarake-fmt) (:voi-muokata? sarake))

                                         :default
                                         (constantly nil))
                          naytettava-arvo (cond
                                            (and (number? naytettava-arvo) (= :prosentti sarake-fmt))
                                            ;; Jos excelissä formatoidaan luku prosentiksi,
                                            ;; excel olettaa, että kyseessä on sadasosia.
                                            ;; Eli kokonaisluku 25 -> 2500%
                                            ;; Muualla Harjassa prosenttilukuformatointi
                                            ;; lisää lähinnä % merkin kokonaisluvun loppuun.
                                            (/ naytettava-arvo 100)

                                            ;; Jos excelissä on raha määrityksenä. Pyöristä kahteen desimaaliin
                                            (and (= :raha sarake-fmt) (number? naytettava-arvo))
                                            (BigDecimal.
                                              (as-> (str/replace (fmt/desimaaliluku-opt naytettava-arvo 2 false) "," ".") naytettava-arvo
                                                (str/replace naytettava-arvo "−" "-"))) ;; Mutetaan jostain erikoisesta tilanteesta
                                            ;; tuleva ASCII 8722 merkki normaaliksi 45 miinusmerkiksi. Jos löydät syyn oudolle merkille. Voit korjata.
                                            :else
                                            naytettava-arvo)
                          tyyli (if-let [tyyli (get-in @luodut-tyylit [solun-tyyli (or solu-fmt sarake-fmt)])]
                                  tyyli
                                  (luo-uusi-tyyli solun-tyyli formaatti-fn (or solu-fmt sarake-fmt)))]
                      (if (raportti-domain/excel-kaava? arvo-datassa)
                        (aseta-kaava! arvo-datassa workbook cell)
                        (excel/set-cell! cell (ilman-soft-hyphenia naytettava-arvo)))
                      (excel/set-cell-style! cell tyyli)
                      (when (:tasaa sarake)
                        (tasaa-solu cell (:tasaa sarake)))))
                  sarakkeet))))
          data))

      ;; Luodaan tiedot sisältävä rivi sheetin alkuun tässä, koska tämä tietostringi on todennäköisesti tarpeeksi pitkä, että autosizecolumn tekisi ekasta sarakkeesta tosi leveän
      ;; Ja tehdään tämä vain kerran, koska ei haluta montaa tietoriviä, jos useampi taulukko on samalla sheetillä.
      (when tee-raporttitiedot-rivi?
        (tee-raportin-tiedot-rivi sheet (assoc raportin-tiedot :nolla raportin-tiedot-rivi :tyyli raportin-tiedot-tyyli)))

      (when rivi-jalkeen
        (luo-rivi-ennen-tai-jalkeen rivi-jalkeen
          rivi-jalkeen-rivi
          rivi-jalkeen-nro
          sheet
          workbook
          false
          false)))
    (catch Throwable t
      (log/error t "Virhe Excel muodostamisessa"))))

(defn- liita-yleiset-tiedot
  [elementti tunnistetiedot]
  (let [e (get elementti 0)]
    (if (= :taulukko e) ;; on optiomappi
      (assoc-in elementti [1 :raportin-tiedot] (:raportin-yleiset-tiedot tunnistetiedot))
      elementti)))

(defmethod muodosta-excel :jakaja [_ _] nil)

(defn- luo-otsikko-rivi-sheetille
  "Luo otsikkorivin viimeiselle sheetille. Jos sheettejä ei ole, ohitetaan.
  Font-koko määrittää otsikon koon ja lihavointi on oletuksena päällä."
  [workbook teksti font-koko]
  (when-let [sheet (last (excel/sheet-seq workbook))]
    (let [rivi-numero (let [viimeinen (.getLastRowNum sheet)]
                        ;; Jos sheet on tyhjä (lastRowNum 0 ja ei rivejä), aloitetaan riviltä 0
                        ;; Muuten jätetään yksi tyhjä rivi ennen otsikkoa
                        (if (and (zero? viimeinen) (nil? (.getRow sheet 0)))
                          0
                          (+ 2 viimeinen)))
        tyyli (excel/create-cell-style! workbook {:font (font-otsikko font-koko)})
        rivi (.createRow sheet rivi-numero)
        solu (.createCell rivi 0)]
    (excel/set-cell! solu teksti)
    (excel/set-cell-style! solu tyyli))))

(defmethod muodosta-excel :otsikko [[_ _] _] nil)

(defmethod muodosta-excel :otsikko-title [[_ teksti] workbook]
  (luo-otsikko-rivi-sheetille workbook teksti 18))

(defmethod muodosta-excel :otsikko-heading [[_ teksti] workbook]
  (luo-otsikko-rivi-sheetille workbook teksti 14))

(defmethod muodosta-excel :otsikko-heading-small [[_ teksti] workbook]
  (luo-otsikko-rivi-sheetille workbook teksti 12))

(defn- lisaa-ajettu-teksti-ensimmaiselle-sheetille!
  "Lisää 'Ajettu'-tekstin ensimmäisen sheetin viimeiselle riville.
  Kutsutaan kaikkien elementtien kirjoituksen jälkeen."
  [workbook nyt]
  (when-let [sheet (first (excel/sheet-seq workbook))]
    (let [viimeinen-rivi (inc (.getLastRowNum sheet))
          rivi (.createRow sheet viimeinen-rivi)
          cell (.createCell rivi 0)]
      (excel/set-cell! cell (str "Ajettu " nyt)))))

(defmethod muodosta-excel :raportti [[_ raportin-tunnistetiedot & sisalto] workbook]
  (let [sisalto (mapcat #(if (seq? %) % [%]) sisalto)
        tiedoston-nimi (raportit-yleinen/raportti-tiedostonimi raportin-tunnistetiedot)
        ;; Tulostuspäivä
        nyt (.format (java.text.SimpleDateFormat. "dd.MM.yyyy HH:mm") (java.util.Date.))]

    (doseq [elementti (remove nil? sisalto)]
      (if (raportti-domain/raporttielementti? elementti)
        (muodosta-excel (liita-yleiset-tiedot elementti raportin-tunnistetiedot) workbook)
        (log/warn "Ohitetaan virheellinen excel-elementti (ei raporttielementti): " (pr-str elementti))))

    (lisaa-ajettu-teksti-ensimmaiselle-sheetille! workbook nyt)
    ;; Käydään lopuksi koko excel läpi ja pakotetaan solujen koot automaattisesti 20% suuremmaksi, kuin 5.2.5 versio poi kirjastosta laskee
    (doseq [sheet (excel/sheet-seq workbook)]
      (let [sarake-maara (reduce (fn [maksimi i]
                                   (if i
                                     (max maksimi (.getLastCellNum i))
                                     maksimi))
                           0
                           (excel/row-seq sheet))]
        (dotimes [i sarake-maara]
          (.autoSizeColumn sheet i)
          (.setColumnWidth sheet i (min
                                     ;; ColumnWidth on merkin pituus jaettuna 256:lla.
                                     ;; Rajoitetaan sarakkeen leveys 100:n merkkiin.
                                     (* 100 256)
                                     (* 1.25 (.getColumnWidth sheet i)))))))
    tiedoston-nimi))

(defmethod muodosta-excel :pohjan-taytto [[_ {:keys [nimi ensimmainen-rivi sheet-nro] :as optiot} data] workbook]
  (let [sheet (nth (excel/sheet-seq workbook) sheet-nro)]
    (dorun
      (map-indexed
        (fn [rivi-nro rivi]
          (let [rivi-nro (+ rivi-nro ensimmainen-rivi)
                ;; Ei haluta ylikirjoittaa pohjassa olevaa riviä
                row (or
                      (nth (excel/row-seq sheet) rivi-nro nil)
                      (.createRow sheet rivi-nro))]
            (dorun
              (map-indexed
                (fn [sarake-nro sarake]
                  (let [cell (or
                               (nth (excel/cell-seq row) sarake-nro nil)
                               (.createCell row sarake-nro))]
                    (when sarake
                      (excel/set-cell! cell sarake))))
                rivi)))) data))
    nimi))

(defmethod muodosta-excel :default [elementti _]
  (log/debug "Excel ei tue elementtiä: " elementti)
  nil)
