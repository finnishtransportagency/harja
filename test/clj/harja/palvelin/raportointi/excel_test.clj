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
        [400.5]]]
      workbook)
    (let [sheet (.getSheetAt workbook 0)
          integer-cell (.getCell (.getRow sheet 3) 0)
          desimaali-cell (.getCell (.getRow sheet 4) 0)]
      (is (= "400" (.formatCellValue formatter integer-cell)))
      (is (= "400,5" (.formatCellValue formatter desimaali-cell)))
      (is (= "#,##0.##" (.getDataFormatString (.getCellStyle integer-cell)))))))

