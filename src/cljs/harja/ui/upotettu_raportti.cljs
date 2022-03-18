(ns harja.ui.upotettu-raportti
  "Apureita upotettujen raporttien piirtämiseen"
  (:require [reagent.core :refer [atom]]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]]
            [harja.transit :as t]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.modal :as modal])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn raportin-vientimuodot [parametrit]
  [:span
   ^{:key "info-button"}
   [:span {:style {:float "right"}
           :on-click (fn [e]
                       (modal/nayta! {:otsikko "Info"
                                      :leveys "50%"}
                         [:div [:strong "Indeksikertoimenlaskukaava"] [:br]
                          [:p "Indeksikertoimen laskemiseen käytetään yhden desimaalin tarkkuutta Indeksistä ja Perusluvusta.
                          Itse indeksikerroin pyöristetään kolmen desimaalin tarkkuuteen."]
                          [:p "Laskukaava: indeksi / perusluku = indeksikerroin."]]))}
    [ikonit/ikoni-ja-teksti [ikonit/livicon-info-sign] " Info"]]
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
