(ns harja.palvelin.raportointi.pdf
  "Raportoinnin elementtien renderöinti PDF:ksi"
  (:require [harja.tyokalut.xsl-fo :as fo]))

(def muodosta-pdf nil)
(defmulti muodosta-pdf
  "Muodostaa PDF:n XSL-FO hiccupin annetulle raporttielementille.
  Dispatch tyypin mukaan (vektorin 1. elementti)."
  (fn [elementti]
    (assert (and (vector? elementti)
                 (> (count elementti) 1)
                 (keyword? (first elementti)))
            "Raporttielementin on oltava vektori, jonka 1. elementti on tyyppi ja muut sen sisältöä.")
    (first elementti)))

(defmethod muodosta-pdf :taulukko [[_ sarakkeet data]]
  [:fo:table
   (for [{:keys [otsikko leveys]} sarakkeet]
     [:fo:table-column {:column-width leveys}])
   [:fo:table-body
    (for [rivi data]
      [:fo:table-row
       (for [i (range (count sarakkeet))
             :let [arvo (nth rivi i)]]
         [:fo:table-cell [:fo:block (str arvo)]])])]])


(defmethod muodosta-pdf :otsikko [[_ teksti]]
  [:fo:block {:font-size "16pt"} teksti])

(defmethod muodosta-pdf :teksti [[_ teksti]]
  [:fo:block {} teksti])

(defmethod muodosta-pdf :pylvaat [[_ otsikko pylvaat]]
  ;;[:pylvaat "Otsikko" [[pylvas1 korkeus1] ... [pylvasN korkeusN]]] -> bar chart svg
  [:fo:instream-foreign-object {:content-width "15cm" :content-height "10cm"}
   [:svg {:xmlns "http://www.w3.org/2000/svg"}
    ;; FIXME
    [:g {:style "fill: red; stroke: #000000"}
     [:rect {:x 0 :y 0 :width 100 :height 100}]]]])




(defmethod muodosta-pdf :yhteenveto [[_ otsikot-ja-arvot]]
  ;;[:yhteenveto [[otsikko1 arvo1] ... [otsikkoN arvoN]]] -> yhteenveto (kuten päällystysilmoituksen alla)
  [:fo:table
   [:fo:table-column {:column-width "25%"}]
   [:fo:table-column {:column-width "75%"}]
   [:fo:table-body
    (for [[otsikko arvo] otsikot-ja-arvot]
      [:fo:table-row
       [:fo:table-cell
        [:fo:block (str otsikko)]]
       [:fo:table-cell
        [:fo:block (str arvo)]]])]])

(defmethod muodosta-pdf :raportti [[_ raportin-tunnistetiedot & sisalto]]
  ;; Muodosta header raportin-tunnistetiedoista!
  (apply fo/dokumentti {}
         (map muodosta-pdf sisalto)))
