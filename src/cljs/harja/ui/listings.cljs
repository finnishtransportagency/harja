(ns harja.ui.listings
  (:require [reagent.core :as reagent :refer [atom]]))

(defn suodatettu-lista
  "Luettelo, jossa on hakukenttä filtteröinnille.
  opts voi sisältää
  :term      hakutermin atomi
  :selection valitun listaitemin atomi
  :format    funktio jolla itemi muutetaan stringiksi, oletus str
  :haku      funktio jolla haetaan itemistä, kenttä jota vasten hakusuodatus (oletus :name)
  :on-select funktio, jolla valinta tehdään (oletuksena reset! valinta-atomille)
  :aputeksti 

  lista sisältää luettelon josta hakea."
  [opts lista]
  (let [term (or (:term opts) (atom ""))
        valittu (or (:selection opts) (atom nil))
        fmt (or (:format opts) str)

        ;; Itemin hakukenttä, oletuksena :name
        haku (or (:haku opts) :name)

        ;; Jos valinnan tekemiseen on määritelty funktio, käytä sitä. Muuten reset! valinta atomille.
        on-select (or (:on-select opts) #(reset! valittu %))

        ;; Indeksi korostettuun elementtiin näppäimistöliikkumista varten (nil jos ei korostettua)
        korostus-idx (atom nil)

        ]
    (fn [opts lista]
      
      (let [itemit (fn [term] (filter #(not= (.indexOf (.toLowerCase (haku %)) (.toLowerCase term)) -1) lista))] 
        [:div.haku-container
             
             [:input.haku-input.form-control
              {:type "text"
               :value @term
               :placeholder (:aputeksti opts)
      
               ;; käsitellään ylos/alas/enter näppäimet, joilla listasta voi valita näppäimistöllä
               :on-key-down #(let [kc (.-keyCode %)]
                              (when (or (= kc 38)
                                        (= kc 40)
                                        (= kc 13))
                                (.preventDefault %)
                                (swap! korostus-idx
                                       (fn [k]
                                         (case kc
                                           38 ;; nuoli ylös
                                           (if (or (nil? k)
                                                   (= 0 k))
                                             (dec (count (itemit @term)))
                                             (dec k))
                                     
                                           40 ;; nuoli alas
                                           (if (or (nil? k)
                                                   (= (dec (count (itemit @term))) k))
                                             0
                                             (inc k))
      
                                           13 ;; enter
                                           (when k
                                             (on-select (nth (itemit @term) k))
                                             nil))))))
               :on-change #(do
                             (reset! korostus-idx nil)
                             (reset! term (.-value (.-target %)))
                             (.log js/console (-> % .-target .-value)))}]
             [:div.haku-lista-container
              [:ul.haku-lista
               (let [selected @valittu
                     term @term
                     korostus @korostus-idx]
                 (map-indexed
                  (fn [i item]
                    ^{:key (:id item)}
                    [:li.haku-lista-item.klikattava
                     {:on-click #(on-select item)
                      :class (str (when (= item selected) "selected ")
                                  (when (= i korostus) "korostettu "))}  
                     [:div.haku-lista-item-nimi 
                      (fmt item)]])
                  (itemit term)))]]]))))
