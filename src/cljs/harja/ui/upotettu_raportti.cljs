(ns harja.ui.upotettu-raportti
  "Apureita upotettujen raporttien piirtämiseen"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]]
            [harja.transit :as t]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn raportin-vientimuodot 
  "Parametrit hash-map tai useita hash-mappeja. Yhdellä hash-mapilla tehdään PDF/Excel-napit samoilla
  parametreilla, usealla hash-mapilla napeilla on omat parametrit ja lisänä avaimet 
  :kasittelija :pdf/:excel ja :otsikko, joka on napin teksti"
  ([parametrit & loput-parametrit]
   [:span  
    (for [p (concat [parametrit] loput-parametrit)]
      (let [{:keys [kasittelija otsikko]} p]
        ^{:key (str "raportti-" otsikko)}
        [:form {:style {:float "right"} :target "_blank" :method "POST"
                :action ((if (= kasittelija :excel) 
                           k/excel-url 
                           k/pdf-url) :raportointi)}
         [:input {:type "hidden" :name "parametrit"
                  :value (t/clj->transit (dissoc p :otsikko :kasittelija))}]
         [:button.nappi-ensisijainen {:type "submit"}
          (ikonit/print)
          (str " " otsikko)]]))])
  ([parametrit]
   [:span
    ^{:key "raporttixls"}
    [:form {:style {:float "right"} :target "_blank" :method "POST"
            :action (k/excel-url :raportointi)}
     [:input {:type "hidden" :name "parametrit"
              :value (t/clj->transit parametrit)}]
     [:button.nappi-ensisijainen {:type "submit"}
      (ikonit/print)
      " Tallenna Excel"]]
    ^{:key "raporttipdf"}
    [:form {:style {:float "right"} :target "_blank" :method "POST"
            :action (k/pdf-url :raportointi)}
     [:input {:type "hidden" :name "parametrit"
              :value (t/clj->transit parametrit)}]
     [:button.nappi-ensisijainen {:type "submit"}
      (ikonit/print)
      " Tallenna PDF"]]]))
