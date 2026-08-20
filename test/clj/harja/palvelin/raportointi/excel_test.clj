(ns harja.palvelin.raportointi.excel-test
  (:require
    [clojure.test :refer :all]
    [harja.palvelin.raportointi.excel :as excel])
  (:import
    (org.apache.poi.ss.usermodel DataFormatter)
    (org.apache.poi.xssf.usermodel XSSFWorkbook)
    (java.util Locale)))

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

