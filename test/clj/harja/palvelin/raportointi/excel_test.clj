(ns harja.palvelin.raportointi.excel-test
  (:require
    [clojure.test :refer :all]
    [clojure.string :as str]
    [harja.palvelin.raportointi.excel :as excel])
  (:import
    (org.apache.poi.ss.usermodel DataFormatter)
    (org.apache.poi.xssf.usermodel XSSFFont XSSFWorkbook)
    (java.util Locale)))

(defn- rgb [vari]
  (mapv #(bit-and 0xff %) (.getRGB vari)))

(defn- sheet-tekstit
  [sheet]
  (mapcat (fn [rivi]
            (keep (fn [solu]
                    (when (= org.apache.poi.ss.usermodel.CellType/STRING
                             (.getCellType solu))
                      (.getStringCellValue solu)))
              (iterator-seq (.cellIterator rivi))))
    (iterator-seq (.rowIterator sheet))))

(deftest numero-opt-kayttaa-oikeaa-excel-formatointia
  (let [workbook (XSSFWorkbook.)
        formatter (DataFormatter. (Locale/forLanguageTag "fi-FI"))]
    (excel/muodosta-excel
      [:taulukko {:nimi "Testi"
                  :sheet-nimi "Testi"}
       [{:otsikko "Määrä" :fmt :numero-opt}]
       [[400.0]
        [400.5]
        [400.22344534534534]]]
      workbook)
    (let [sheet (.getSheetAt workbook 0)
          integer-arvo (.getCell (.getRow sheet 3) 0)
          desimaali-arvo (.getCell (.getRow sheet 4) 0)
          pitkä-desimaali-arvo (.getCell (.getRow sheet 5) 0)]
      (is (= "400" (.formatCellValue formatter integer-arvo)))
      (is (= "400,5" (.formatCellValue formatter desimaali-arvo)))
      (is (= "400,22" (.formatCellValue formatter pitkä-desimaali-arvo))))))

(deftest raportin-tekstielementit-sailyvat-excel-viennissa
  (let [workbook (XSSFWorkbook.)]
    (excel/muodosta-excel
      [:raportti {:nimi "Testi"}
       [:otsikko "Ryhmä"
        [:taulukko {:nimi "Taulukko"
                    :sheet-nimi "Taulukko"}
         [{:otsikko "Arvo"}]
         [[1]]]
        [:teksti "Säilyvä teksti"]]]
      workbook)
    (let [tekstit (mapcat (fn [rivi]
                            (keep (fn [solu]
                                    (when (= org.apache.poi.ss.usermodel.CellType/STRING
                                             (.getCellType solu))
                                      (.getStringCellValue solu)))
                                  (iterator-seq (.cellIterator rivi))))
                          (iterator-seq (.rowIterator (.getSheetAt workbook 0))))]
      (is (some #(= "Säilyvä teksti" %) tekstit)))))

(deftest raportin-yhteenveto-ja-rivityylit-noudattavat-speksia
  (let [workbook (XSSFWorkbook.)]
    (excel/muodosta-excel
      [:taulukko {:nimi "Tyylit"
                  :sheet-nimi "Tyylit"}
       [{:otsikko "Rivi"}
        {:otsikko "Summa"}]
       [{:lihavoi? true
         :korosta-hennosti? true
         :rivi ["Yhteensä" 100]}
        {:himmennetty? true
         :rivi ["Nollasumma" 0]}
        {:negatiivinen? true
         :rivi ["Negatiivinen" -100]}]]
      workbook)
    (let [sheet (.getSheetAt workbook 0)
          yhteenveto (.getCell (.getRow sheet 3) 0)
         nollasumma (.getCell (.getRow sheet 4) 0)
             negatiivinen (.getCell (.getRow sheet 5) 0)]
              (is (= [154 199 252]
                (rgb (.getFillForegroundColorColor (.getCellStyle yhteenveto)))))
      (is (.getBold (.getFont (.getCellStyle yhteenveto))))
      (is (= org.apache.poi.ss.usermodel.FillPatternType/NO_FILL
             (.getFillPattern (.getCellStyle nollasumma))))
              (is (= [248 215 209]
                (rgb (.getFillForegroundColorColor (.getCellStyle negatiivinen)))))
              (is (= [180 10 20]
                (rgb (.getXSSFColor
                  (cast XSSFFont (.getFont (.getCellStyle negatiivinen))))))))))

    (deftest varillisen-tekstin-yhteenvetovari-on-yhtenainen
      (let [workbook (XSSFWorkbook.)]
        (excel/muodosta-excel
          [:taulukko {:nimi "Tyylit"
                      :sheet-nimi "Tyylit"}
           [{:otsikko "Arvo"}]
           [[[:varillinen-teksti {:arvo "Yhteensä"
                                  :korosta-hennosti? true}]]]]
          workbook)
        (let [solu (.getCell (.getRow (.getSheetAt workbook 0) 3) 0)]
          (is (= [154 199 252]
                 (rgb (.getFillForegroundColorColor (.getCellStyle solu))))))))

(deftest taulukot-kayttavat-omaa-valilehtisopimustaan
  (let [workbook (XSSFWorkbook.)]
        (excel/muodosta-excel
          [:raportti {:nimi "Testi"}
           [:taulukko {:otsikko "Yhteenveto"
                       :sheet-nimi "Yhteenveto"}
            [{:otsikko "Rivi"}]
            [["Yhteenveto"]]]
           [:taulukko {:otsikko "Sanktiot"
                       :sheet-nimi "Urakka"}
            [{:otsikko "Rivi"}]
            [["Sanktiot"]]]
           [:taulukko {:otsikko "Bonukset"
                       :sheet-nimi "Urakka"
                       :samalle-sheetille? true}
            [{:otsikko "Rivi"}]
            [["Bonukset"]]]]
          workbook)
        (is (= 2 (.getNumberOfSheets workbook)))
        (is (= "Yhteenveto" (.getSheetName (.getSheetAt workbook 0))))
        (is (= "Urakka" (.getSheetName (.getSheetAt workbook 1))))
        (let [tekstit (mapcat (fn [rivi]
                                (keep (fn [solu]
                                        (when (= org.apache.poi.ss.usermodel.CellType/STRING
                                                 (.getCellType solu))
                                          (.getStringCellValue solu)))
                                      (iterator-seq (.cellIterator rivi))))
                              (iterator-seq (.rowIterator (.getSheetAt workbook 1))))]
          (is (some #(= "Sanktiot" %) tekstit))
          (is (some #(= "Bonukset" %) tekstit)))))

(deftest valilehtien-nimet-puhdistetaan-katkaistaan-ja-yksiloidaan
  (let [workbook (XSSFWorkbook.)
        sheet-nimi (str "Sama urakka " (apply str (repeat 30 "x")) "/[?]")]
    (excel/muodosta-excel
      [:raportti {:nimi "Testi"}
       [:taulukko {:otsikko "Ensimmäinen"
                   :sheet-nimi sheet-nimi}
        [{:otsikko "Rivi"}]
        [["Ensimmäinen"]]]
       [:taulukko {:otsikko "Toinen"
                   :sheet-nimi sheet-nimi}
        [{:otsikko "Rivi"}]
        [["Toinen"]]]]
      workbook)
    (let [sheetit (mapv #(.getSheetAt workbook %) (range (.getNumberOfSheets workbook)))
          nimet (mapv #(.getSheetName %) sheetit)
          kielletyt-merkit ["\\" "/" "?" "*" "[" "]" ":"]]
      (is (= 2 (count sheetit)))
      (is (every? #(<= (count %) 31) nimet))
      (is (every? #(not-any? (fn [merkki] (str/includes? % merkki))
                       kielletyt-merkit)
            nimet))
      (is (= 2 (count (set (map str/lower-case nimet)))))
      (is (some #(= "Ensimmäinen" %) (sheet-tekstit (first sheetit))))
      (is (some #(= "Toinen" %) (sheet-tekstit (second sheetit)))))))

(deftest taulukon-omat-metatiedot-sailyvat-aggregaatissa
  (let [workbook (XSSFWorkbook.)]
    (excel/muodosta-excel
      [:raportti {:nimi "Koko maa"
                  :raportin-yleiset-tiedot {:raportin-nimi "Koko maa"}}
       [:taulukko {:sheet-nimi "Testiurakka"
                   :raportin-tiedot {:raportin-nimi "Testiurakka"
                                     :alkupvm "01.10.2025"
                                     :loppupvm "30.09.2030"}}
        [{:otsikko "Rivi"}]
        [["Arvo"]]]]
      workbook)
    (is (= "Testiurakka, 01.10.2025 - 30.09.2030"
           (-> workbook
             (.getSheetAt 0)
             (.getRow 0)
             (.getCell 0)
             (.getStringCellValue))))))
