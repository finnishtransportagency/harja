(ns harja.palvelin.komponentit.pdf-vienti-test
  (:require [harja.palvelin.komponentit.pdf-vienti :as pdf-vienti]
            [harja.testi :refer :all]
            [harja.tyokalut.xsl-fo :as fo]
            [clojure.test :refer [deftest is]]))



(deftest virheellinen-fop-heittaa-poikkeuksen
  (let [ff (#'pdf-vienti/luo-fop-factory)
        out (java.io.ByteArrayOutputStream.)]
    (is (thrown? javax.xml.transform.TransformerException
                 (.write out
                         (#'pdf-vienti/hiccup->pdf
                          ff
                          (fo/dokumentti
                           [:fo:ASDASD {:font-family "Helvetica" :font-size "14pt"} "Jotain tekstiä tänne"])))))))

(deftest testipdf-generoituu-oikein
  (let [ff (#'pdf-vienti/luo-fop-factory)
        out (java.io.ByteArrayOutputStream.)]
    (.write out
            (#'pdf-vienti/hiccup->pdf
             ff
             (fo/dokumentti
              {:header {:extent "1cm"
                        :sisalto [:fo:block "Harja - järjestelmän tuloste"]}
               :footer {:extent "1cm"
                        :sisalto [:fo:block "FOOTERISSAHAN ME"]
                        }}

              [:fo:block {:font-family "Helvetica" :font-size "14pt"} "Jotain tekstiä tänne"]
              [:fo:block {:space-after.optimum "10pt" :font-family "Helvetica" :font-size "10pt"}
               [:fo:table
                [:fo:table-column {:column-width "10cm"}]
                [:fo:table-column {:column-width "10cm"}]
                [:fo:table-body
                 (for [[eka toka] [["1" "jotain"] ["2" "ihan"] ["3" "muuta"]]]
                   [:fo:table-row
                    [:fo:table-cell
                     [:fo:block eka]]
                    [:fo:table-cell
                     [:fo:block toka]]])]]])))
    (is (> (count (.toByteArray out)) 500) "PDF luotu")))

  
