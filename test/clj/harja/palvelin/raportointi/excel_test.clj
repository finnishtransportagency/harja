(ns harja.palvelin.raportointi.excel-test
  (:require
    [clojure.test :refer :all]
    [harja.palvelin.raportointi.excel :as excel])
  (:import
    (org.apache.poi.ss.usermodel DataFormatter)
    (org.apache.poi.xssf.usermodel XSSFFont XSSFWorkbook)
    (java.util Locale)))

(defn- rgb [vari]
  (mapv #(bit-and 0xff %) (.getRGB vari)))

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

