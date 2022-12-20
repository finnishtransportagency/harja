(ns harja.tyokalut.xsl-fo
  "XSL-FO formaatin hiccupin generointiin työkaluja")

(defn dokumentti
  "Tekee juurielementin, joka määrittelee marginit, headerit ja footerit.
Oletuksena muodostuu A4 sivu.

  Optiot mäpissä voi olla seuraavat avaimet:
  :margin         mäppi eri reunojen margineja, oletus {:left \"1cm\" :right \"1cm\" :top \"1cm\" :bottom \"1cm\" :body \"1cm\"}
  :footer         footerin määrittelevä mäppi, jossa avaimet :extent (koko esim. sentteinä) ja :sisalto
  :header         headerin määrittelevä mäppi, jossa avaimet :extent ja :sisalto
  :orientation    sivun orientaatio, joko :portrait (pysty, oletus) tai :landscape

"

  [optiot & sisalto]
  (let [margin (merge {:left "1cm" :right "1cm" :top "1cm" :bottom "1cm"
                       :body "1cm"}
                      (:margin optiot))
        header (merge (:header optiot) {:extent "1cm"})
        footer (merge (:footer optiot) {:extent "1cm"})
        [page-height page-width] (if (= :landscape (:orientation optiot))
                                   ["21cm" "29.7cm"]
                                   ["29.7cm" "21cm"])]

    [:fo:root {:xmlns:fo "http://www.w3.org/1999/XSL/Format"
               :xmlns:svg "http://www.w3.org/2000/svg"}

     ;; Layoutin konffi
     [:fo:layout-master-set
      [:fo:simple-page-master {:master-name "first"
                               :page-height page-height :page-width page-width
                               :margin-top (:top margin) :margin-bottom (:bottom margin) :margin-left (:left margin) :margin-right (:right margin)}
       [:fo:region-body {:region-name "xsl-region-body" :margin-top (:body margin)}]
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

(defn tietoja
  "Tee 2-sarakkeinen Tietoja taulukko, jossa vasen on tietokentän nimi ja oikea on arvo.
  Taulukon layout on automaattinen. Optiot on tällä hetkellä tyhjät, sinne voisi laittaa nimi/arvo tyylit."
  [optiot & nimet-ja-arvot]

  [:fo:table {:table-layout "fixed"}
   [:fo:table-column]
   [:fo:table-column]
   [:fo:table-body
    (for [[nimi arvo] (partition 2 nimet-ja-arvot)]
      [:fo:table-row
       [:fo:table-cell [:fo:block {:font-weight "bold"} (let [nimi (str nimi)]
                                                          (if (.endsWith nimi ":")
                                                            nimi
                                                            (str nimi ":")))]]
       [:fo:table-cell [:fo:block (str arvo)]]])]])

(defn vali
  "Tyhjä rivi"
  []
  [:fo:block "&#x00A0;"])

(defn otsikko
  "Väliotsikko"
  [teksti]
  [:fo:block {:font-size "16pt" :font-weight "bold"} teksti])

(defn checkbox [size checked?]
  [:fo:instream-foreign-object
   [:svg:svg {:width size :height size :viewBox "0 0 20 20" :preserveAspectRatio "xMidYMid meet"}
    [:svg:g {:style "fill: none; stroke: black; stroke-width: 2px;"}
     [:svg:rect {:x 0 :y 0 :width 20 :height 20}]
     (when checked?
       [:svg:line {:x1 4 :y1 10 :x2 10 :y2 17}])
     (when checked?
       [:svg:line {:x1 10 :y1 17 :x2 17 :y2 3}])]]])
