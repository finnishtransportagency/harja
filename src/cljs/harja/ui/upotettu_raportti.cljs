(ns harja.ui.upotettu-raportti
  "Apureita upotettujen raporttien piirtämiseen"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]]
            [harja.transit :as t]
            [harja.ui.ikonit :as ikonit])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn raportin-vientimuodot [parametrit]
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
     " Tallenna PDF"]]])
