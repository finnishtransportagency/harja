(ns harja.views.kartta.infopaneeli
  "Muodostaa kartalle overlayn, joka sisältää klikatussa koordinaatissa
  olevien asioiden tiedot."
  (:require [harja.ui.komponentti :as komp]
            [reagent.core :refer [atom] :as reagent]
            [cljs.core.async :as async]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.napit :as napit])
  (:require-macros
   [cljs.core.async.macros :as async-macros]))

;; kun asiat-pisteessä :haetaan = true, täämän pitisi resetoitua
(defonce valittu-asia (atom nil))

(defn esita-otsikko [asia]
  (log "esita-otsikko" (pr-str asia))
  [:div {:on-click #(reset! valittu-asia asia)} (:otsikko asia)
   ])

(defn esita-yksityiskohdat [asia ainoa-asia?]
  (let []
    [:div
     [:div (:otsikko asia)]
     [:div (-> asia :tiedot count)]]))

(defn infopaneeli [asiat-pisteessa]
  (when-let [sisalto @asiat-pisteessa]
    (let [vain-yksi-asia? (-> sisalto :asiat count (= 1))
          useampi-asia? (not vain-yksi-asia?)
          esita-yksityiskohdat? (or @valittu-asia vain-yksi-asia?)
          ainoa-asia (when vain-yksi-asia? (-> sisalto :asiat first))]
      ;; tyhjennetään valinta kun valinnassa on asia joka ei esiinny @asiat-pisteessa
      ;; (esim latauksen aikana)
      (when-not (some #(= @valittu-asia %) (:asiat sisalto))
        (reset! valittu-asia nil))
      [:div#kartan-infopaneeli
       [:div
        (when (and @valittu-asia useampi-asia?)
           [napit/takaisin "" #(reset! valittu-asia nil)])
        (when (:haetaan? sisalto)
          [ajax-loader])
        [:button.close {:on-click #(reset! asiat-pisteessa nil)
                        :type "button"}
         [:span "×"]
         ]]
       (when-not (-> sisalto :asiat empty?)
         (if esita-yksityiskohdat?
           [esita-yksityiskohdat (or @valittu-asia ainoa-asia) vain-yksi-asia?]
           [:div (doall (for [[idx asia] (map-indexed #(do [%1 %2]) (-> sisalto :asiat))]
                          ^{:key idx}
                          [esita-otsikko asia]))]))])))
