(ns harja.ui.raportti
  "Harjan raporttielementtien HTML näyttäminen."
  (:require [harja.ui.grid :as grid]
            [harja.ui.yleiset :as yleiset]
            [harja.ui.visualisointi :as vis]
            [harja.loki :refer [log]]))

(defmulti muodosta-html
  "Muodostaa Reagent komponentin annetulle raporttielementille."
  (fn [elementti]
    (assert (and (vector? elementti)
                 (> (count elementti) 1)
                 (keyword? (first elementti)))
            "Raporttielementin on oltava vektori, jonka 1. elementti on tyyppi ja muut sen sisältöä.")
    (first elementti)))

(defmethod muodosta-html :taulukko [[_ sarakkeet data]]
  (log "GRID DATALLA: " (pr-str sarakkeet) " => " (pr-str data))
  [grid/grid {:otsikko "" :tunniste hash}
   (into []
         (map-indexed (fn [i sarake]
                        {:hae #(nth % i)
                         :leveys (:leveys sarake)
                         :otsikko (:otsikko sarake)
                         :nimi (str "sarake" i)})
                      sarakkeet))
   data])

(defmethod muodosta-html :otsikko [[_ teksti]]
  [:h3 teksti])

(defmethod muodosta-html :teksti [[_ teksti]]
  [:p teksti])

(defmethod muodosta-html :pylvaat [[_ {:keys [otsikko vari]} pylvaat]]
  (let [w (int (* 0.85 @yleiset/leveys))
        h (int (/ w 3))]
    [:span.pylvaat
     [:h3 otsikko]
     [vis/bars {:width w
                :height h}
      pylvaat]]))

(defmethod muodosta-html :yhteenveto [[_ otsikot-ja-arvot]]
  (apply yleiset/taulukkotietonakyma {}
         (mapcat identity otsikot-ja-arvot)))

  
(defmethod muodosta-html :raportti [[_ raportin-tunnistetiedot & sisalto]]
  [:div.raportti
   (map-indexed (fn [i elementti]
                  ^{:key i}
                  [muodosta-html elementti])
                (mapcat (fn [sisalto]
                          (if (list? sisalto)
                            sisalto
                            [sisalto]))
                        sisalto))])

                                   
  
   
  
