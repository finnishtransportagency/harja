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
    (assert (raportti-domain/raporttielementti? elementti))
    (first elementti)))

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
      (str "SUM("(or alkusarake "A") rivi  ":" loppusarake rivi ")")))
  (evaluoi-kaava workbook cell))

(defn- ilman-soft-hyphenia [data]
  (if (string? data)
    (.replace data "\u00AD" "")
    data))

(defmethod muodosta-solu :vain-arvo [arvo solun-tyyli] [arvo solun-tyyli])

(defmethod muodosta-solu :liitteet [[_ liitteet] solun-tyyli]
  [(count liitteet) solun-tyyli])

(defmethod muodosta-solu :arvo-ja-osuus [[_ {:keys [arvo osuus]}] solun-tyyli]
  [arvo solun-tyyli])

(defmethod muodosta-solu :arvo-ja-yksikko [[_ {:keys [arvo yksikko desimaalien-maara]}] solun-tyyli]
  [arvo solun-tyyli (when desimaalien-maara
                      (if (= yksikko "%")
                        nil
                        [:kustomi desimaalien-maara]))])

(defmethod muodosta-solu :erotus-ja-prosentti [[_ {:keys [arvo prosentti desimaalien-maara lihavoi? korosta?
                                                          korosta-hennosti? ala-korosta? varoitus?]}] solun-tyyli]
  (let [etuliite (cond
                   (neg? arvo) "- "
                   (zero? arvo) ""
                   :else "+ ")
        arvo (Math/abs (float arvo))
        prosentti (Math/abs (float prosentti))
        oletustyyli (raportti-domain/solun-oletustyyli-excel lihavoi? korosta? korosta-hennosti?)
        solun-tyyli (if-not (empty? solun-tyyli)
                      solun-tyyli
                      oletustyyli)
        solun-tyyli (if ala-korosta?
                      (dissoc solun-tyyli :background)
                      solun-tyyli)
        solun-tyyli (if varoitus?
                      (merge solun-tyyli
                        {:background :red
                         :font {:color :white}})
                      solun-tyyli)]
    [(str etuliite
       (cond desimaalien-maara (fmt/desimaaliluku-opt arvo desimaalien-maara)
             :else arvo)
       (when prosentti (str " (" etuliite (fmt/prosentti-opt prosentti) ")"))) solun-tyyli]))

;; Säädä yksittäisestä solusta haluttu. Solun tyyli saadaan raporttielementilla esim. näin:
;; [:arvo-ja-yksikko-korostettu {:arvo yht :korosta-hennosti? true :yksikko "%" :desimaalien-maara 2}]
(defmethod muodosta-solu :arvo-ja-yksikko-korostettu [[_ {:keys [arvo yksikko desimaalien-maara lihavoi? korosta?
                                                                 korosta-hennosti? ala-korosta? varoitus?]}] solun-tyyli]
  (let [oletustyyli (raportti-domain/solun-oletustyyli-excel lihavoi? korosta? korosta-hennosti?)
        solun-tyyli (if-not (empty? solun-tyyli)
                      solun-tyyli
                      oletustyyli)
        solun-tyyli (if ala-korosta?
                      (dissoc solun-tyyli :background)
                      solun-tyyli)
        ;; Rivin pääasiallista tyyliä on mahdollista muokata myös varoituksen muodossa, kunhan attribuutti varoitus? on annettu
        ;; Ylikirjoitetaan tässä mahdollisen varoituksen vaikutukset myös yhteenvetoriveille
        solun-tyyli (if varoitus?
                      (merge solun-tyyli
                        {:background :red
                         :font       {:color :white}})
                      solun-tyyli)]
    [arvo solun-tyyli
     (when desimaalien-maara
       (if (= yksikko "%")
         nil
         [:kustomi desimaalien-maara]))]))

(defmethod muodosta-solu :arvo-ja-selite [[_ {:keys [arvo selite]}] solun-tyyli]
  [(str arvo (when selite (str " (" selite ")"))) solun-tyyli])

(defmethod muodosta-solu :varillinen-teksti [[_ {:keys [arvo tyyli fmt]}] solun-tyyli]
  [arvo
   (merge solun-tyyli (when tyyli (tyyli raportti-domain/virhetyylit-excel)))
   fmt])

(defmethod muodosta-solu :infopallura [_ _]
  nil)

(defmethod muodosta-solu :teksti-ja-info [[_ {:keys [arvo]}] solun-tyyli]
  [arvo solun-tyyli])

(defn- taulukko-otsikkorivi [otsikko-rivi sarakkeet sarake-tyyli]
  (dorun
    (map-indexed
      (fn [sarake-nro {:keys [otsikko] :as sarake}]
        (let [cell (.createCell otsikko-rivi sarake-nro)]
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
  [fmt voi-muokata? tyyli]
  ;; voi-muokata? vaikuttaa vain, jos sheet on asetettu protected arvoon,
  ;; joka enabloidaan lipulla :varjele-sheet-muokkauksilta?)
  (.setLocked tyyli (not voi-muokata?))
  (case fmt
    ;; .setDataFormat hakee indeksillä tyylejä.
    ;; Tyylejä voi määritellä itse (https://poi.apache.org/apidocs/org/apache/poi/xssf/usermodel/XSSFDataFormat.html)
    ;; tai voimme käyttää valmiita, sisäänrakennettuja tyylejä.
    ;; http://poi.apache.org/apidocs/org/apache/poi/ss/usermodel/BuiltinFormats.html
    :kokonaisluku (.setDataFormat tyyli 1)
    :raha (.setDataFormat tyyli 8)
    :prosentti (.setDataFormat tyyli 10)
    :numero (.setDataFormat tyyli 2)
    :numero-3desim (.setDataFormat tyyli 3)
    :pvm (.setDataFormat tyyli 14)
    :pvm-aika (.setDataFormat tyyli 22)
    nil))

(defn- tee-raportin-tiedot-rivi  
  [sheet {:keys [nolla raportin-nimi alkupvm urakka loppupvm tyyli
                 custom-ylin-rivi] :as tiedot}]
  (try 
    (let [rivi (.createRow sheet nolla)
          solu (.createCell rivi 0)]
      (excel/set-cell! solu (or
                              custom-ylin-rivi
                              (str raportin-nimi " - " urakka (when (and alkupvm loppupvm)
                                                               (str " - " alkupvm "-" loppupvm)))))
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

(defn- font-leipateksti
  ([] (font-leipateksti 11))
  ([font-koko]
   {:color :black :size font-koko}))

(defn- tasaa-solu [solu tasaa]
  (CellUtil/setAlignment solu
                         (case tasaa
                           :keskita HorizontalAlignment/CENTER
                           :oikea HorizontalAlignment/RIGHT
                           HorizontalAlignment/LEFT)))

(defn- luo-rivi-ennen-tai-jalkeen
  "Luo rivin joko ennen tai jälkeen varsinaisen taulukon."
  [rivi-maaritys riviolio rivi-nro tyyli sheet]
  (reduce (fn [sarake-nro {:keys [teksti tasaa sarakkeita]}]
            (let [solu (.createCell riviolio sarake-nro)]
              (excel/set-cell! solu teksti)
              (excel/set-cell-style! solu tyyli)
              (tasaa-solu solu tasaa)
              (when (> sarakkeita 1)
                (.addMergedRegion sheet (CellRangeAddress. rivi-nro rivi-nro
                                                           sarake-nro
                                                           (+ sarake-nro sarakkeita -1))))
              (+ sarake-nro sarakkeita)))
          0 rivi-maaritys))

(defn- font-otsikko
  ([] (font-otsikko 14))
  ([font-koko]
   {:color :black
    :size font-koko
    :name "Arial"
    :bold true}))

(defn- luo-saraketyyli
  [workbook lista-tyyli? rivi-ennen?]
  (excel/create-cell-style! workbook (if lista-tyyli?
                                       {:border-bottom :thin
                                        :border-top :thin
                                        :border-left :thin
                                        :border-right :thin
                                        :font (font-otsikko (if rivi-ennen? 18 14))}
                                       {:background :grey_25_percent
                                        :font {:color :black}})))

(def puskuririvien-maara-ennen-rivi-jalkeen 5)

(defmethod muodosta-excel :taulukko [[_ {:keys [nimi raportin-tiedot viimeinen-rivi-yhteenveto? lista-tyyli?
                                                sheet-nimi samalle-sheetille? rivi-ennen rivi-jalkeen] :as optiot}
                                      sarakkeet data] workbook]
  (try
    (let [viimeinen-rivi (last data)
          aiempi-sheet (last (excel/sheet-seq workbook))
          [sheet nolla] (if (and (nil? sheet-nimi)
                              (or samalle-sheetille? (nil? nimi))
                              aiempi-sheet)
                          [aiempi-sheet (+ 2 (.getLastRowNum aiempi-sheet))]
                          [(excel/add-sheet! workbook
                                             (WorkbookUtil/createSafeSheetName
                                               (or sheet-nimi nimi))) 0])
          ;; mahdollista haluttujen sheetien sisällä solujen lukitseminen (sheet protection)
          _ (when (:varjele-sheet-muokkauksilta? optiot)
              (.enableLocking sheet))
          sarake-tyyli (luo-saraketyyli workbook lista-tyyli? false)
          rivi-ennen-sarake-tyyli (luo-saraketyyli workbook lista-tyyli? true)
          raportin-tiedot-tyyli (excel/create-cell-style! workbook {:font (font-otsikko)})
          ;; Ei tehdä uutta otsikkoriviä, jos taulukko tulee samalle sheetille.
          tee-raporttitiedot-rivi? (= nolla 0) 

          ;; Luodaan raportin tiedot sisältävä rivi sheetin alkuun tähän indeksiin myöhemmässä vaiheessa. Voisi varmaan käyttää nollaakin suoraan ie. 0 
          raportin-tiedot-rivi nolla
          nolla (+ 2 nolla)
          
          ;; Tehdään vähän väliä raporttirivien ja taulukon otsikolle, kun on useampi taulukko samalla sheetillä
          nolla (if (and samalle-sheetille? tee-raporttitiedot-rivi?)
                  (+ 2 nolla)
                  nolla)
          rivi-ennen-nro nolla
          rivi-ennen-rivi (when rivi-ennen (.createRow sheet nolla))

          rivi-jalkeen-nro (+ puskuririvien-maara-ennen-rivi-jalkeen (count data))
          rivi-jalkeen-rivi (when rivi-jalkeen (.createRow sheet rivi-jalkeen-nro))
          nolla (if rivi-ennen (inc nolla) nolla)
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
                                    rivi-ennen-sarake-tyyli
                                    sheet))

      ;; Jos on useampi taulu samalla sheetillä, laitetaan niiden nimet ennen sarakkeiden otsikkoja. 
      (when samalle-sheetille?
        (tee-taulukon-nimiotsikko sheet nolla nimi raportin-tiedot-tyyli))

      ;; Luodaan otsikot saraketyylillä
      (taulukko-otsikkorivi otsikko-rivi sarakkeet sarake-tyyli)

      (dorun
       (map-indexed
        (fn [rivi-nro rivi]
          (let [rivi-nro (+ nolla 1 rivi-nro)
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
                       arvo-datassa (nth data sarake-nro)
                       ;; ui.yleiset/totuus-ikonin tuki toistaiseksi tämä
                       arvo-datassa (if (= [:span.livicon-check] arvo-datassa)
                                      "X"
                                      arvo-datassa)
                       formatoi-solu? (raportti-domain/formatoi-solu? arvo-datassa)

                       oletustyyli (raportti-domain/solun-oletustyyli-excel lihavoi? korosta? korosta-hennosti?)
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
                                      (partial tyyli-format-mukaan formaatti nil)

                                      formatoi-solu?
                                      (partial tyyli-format-mukaan (:fmt sarake) (:voi-muokata? sarake))

                                      :default
                                      (constantly nil))
                       naytettava-arvo (cond
                                         (and (number? naytettava-arvo) (= :prosentti (:fmt sarake)))
                                         ;; Jos excelissä formatoidaan luku prosentiksi,
                                         ;; excel olettaa, että kyseessä on sadasosia.
                                         ;; Eli kokonaisluku 25 -> 2500%
                                         ;; Muualla Harjassa prosenttilukuformatointi
                                         ;; lisää lähinnä % merkin kokonaisluvun loppuun.
                                         (/ naytettava-arvo 100)

                                         ;; Jos excelissä on raha määrityksenä. Pyöristä kahteen desimaaliin
                                         (and (= :raha (:fmt sarake)) (number? naytettava-arvo))
                                         (BigDecimal.
                                           (as-> (str/replace (fmt/desimaaliluku-opt naytettava-arvo 2 false) "," ".") naytettava-arvo
                                             (str/replace naytettava-arvo "−" "-"))) ;; Mutetaan jostain erikoisesta tilanteesta
                                             ;; tuleva ASCII 8722 merkki normaaliksi 45 miinusmerkiksi. Jos löydät syyn oudolle merkille. Voit korjata.

                                         :default
                                         naytettava-arvo)
                       tyyli (if-let [tyyli (get-in @luodut-tyylit [solun-tyyli (:fmt sarake)])]
                               tyyli
                               (luo-uusi-tyyli solun-tyyli formaatti-fn (:fmt sarake)))]
                   (if (raportti-domain/excel-kaava? arvo-datassa)
                     (aseta-kaava! arvo-datassa workbook cell)
                     (excel/set-cell! cell (ilman-soft-hyphenia naytettava-arvo)))
                   (excel/set-cell-style! cell tyyli)
                   (when (:tasaa sarake)
                     (tasaa-solu cell (:tasaa sarake)))))
              sarakkeet))))
        data))

      ;; Laitetaan automaattiset leveydet
      (dotimes [i (count sarakkeet)]
        (.autoSizeColumn sheet i))
      
      ;; Luodaan tiedot sisältävä rivi sheetin alkuun tässä, koska tämä tietostringi on todennäköisesti tarpeeksi pitkä, että autosizecolumn tekisi ekasta sarakkeesta tosi leveän
      ;; Ja tehdään tämä vain kerran, koska ei haluta montaa tietoriviä, jos useampi taulukko on samalla sheetillä.
      (when tee-raporttitiedot-rivi?
        (tee-raportin-tiedot-rivi sheet (assoc raportin-tiedot :nolla raportin-tiedot-rivi :tyyli raportin-tiedot-tyyli)))

      (when rivi-jalkeen
        (luo-rivi-ennen-tai-jalkeen rivi-jalkeen
                                    rivi-jalkeen-rivi
                                    rivi-jalkeen-nro
                                    (excel/create-cell-style! workbook {:font (font-leipateksti)})
                                    sheet)))
    (catch Throwable t
      (log/error t "Virhe Excel muodostamisessa"))))

(defn- liita-yleiset-tiedot
  [elementti tunnistetiedot]
  (let [e (get elementti 0)]
    (if (= :taulukko e) ;; on optiomappi
      (assoc-in elementti [1 :raportin-tiedot] (:raportin-yleiset-tiedot tunnistetiedot))
      elementti)))

(defmethod muodosta-excel :raportti [[_ raportin-tunnistetiedot & sisalto] workbook]
  (let [sisalto (mapcat #(if (seq? %) % [%]) sisalto)
        tiedoston-nimi (raportit-yleinen/raportti-tiedostonimi raportin-tunnistetiedot)]
    (doseq [elementti (remove nil? sisalto)]
      (muodosta-excel (liita-yleiset-tiedot elementti raportin-tunnistetiedot) workbook))
    tiedoston-nimi))

(defmethod muodosta-excel :default [elementti workbook]
  (log/debug "Excel ei tue elementtiä: " elementti)
  nil)
