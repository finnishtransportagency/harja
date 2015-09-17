(ns harja.tyokalut.xsl-fo
  "XSL-FO formaatin hiccupin generointiin työkaluja")

(defn dokumentti
  "Tekee juurielementin, joka määrittelee marginit, headerit ja footerit.
Oletuksena muodostuu A4 sivu.

  Optiot mäpissä voi olla seuraavat avaimet:
  :margin         mäppi eri reunojen margineja, oletus {:left \"2.5cm\" :right \"2.5cm\" :top \"1cm\" :bottom \"2cm\"}
  :footer         footerin määrittelevä mäppi, jossa avaimet :extent (koko esim. sentteinä) ja :sisalto
  :header         headerin määrittelevä mäppi, jossa avaimet :extent ja :sisalto

"
  
  [optiot & sisalto]
  (let [margin (merge (:margin optiot)
                      {:left "2.5cm" :right "2.5cm" :top "1cm" :bottom "2cm"})
        header (merge (:header optiot) {:extent "1cm"})
        footer (merge (:footer optiot) {:extent "1.5cm"})]
    
    [:fo:root {:xmlns:fo "http://www.w3.org/1999/XSL/Format"}
     
     ;; Layoutin konffi
     [:fo:layout-master-set
      [:fo:simple-page-master {:master-name "first"
                               :page-height "29.7cm" :page-width "21cm"
                               :margin-top (:top margin) :margin-bottom (:bottom margin) :margin-left (:left margin) :margin-right (:right margin)}
       [:fo:region-body {:region-name "xsl-region-body" :margin-top "1cm"}]
       [:fo:region-before {:region-name "xsl-region-before" :extent (:extent header)}]
       [:fo:region-after {:region-name "xsl-region-after" :extent (:extent footer)}]]]
     
     
     
     ;; Itse sisältö
     [:fo:page-sequence {:master-reference "first"}
      (when-let [sisalto (:sisalto header)]
        [:fo:static-content {:flow-name "xsl-region-before"}
         sisalto])
      (when-let [sisalto (:sisalto footer)]
        [:fo:static-content {:flow-name "xsl-region-after"}
         sisalto])
      [:fo:flow {:flow-name "xsl-region-body"}
       sisalto]]]))
  
