(ns harja.tyokalut.vieritys
  (:require [reagent.core :as r]))

(defonce vierityskohteet (r/atom {}))

(defn majakka
  "Vieritystargetti"
  [_]
  (r/create-class
    {:component-will-unmount
     #(reset! vierityskohteet {})

     :reagent-render
     (fn [nimi]
       [:span {:id  nimi
               :ref (fn [e]
                      (swap! vierityskohteet assoc nimi e))}])}))

(defn vierita
  [kohde]
  (fn [_]
    (let [elementti (kohde @vierityskohteet)
          parametrit (js-obj "behavior" "smooth")]
      (.scrollIntoView elementti parametrit))))

(defn- tee-majakat
  [es]
  (concat [:<>]
        (mapv (fn [e]
                #_(println e)
               (if (keyword? e)
                 [[:span {:on-click (vierita ::top)} "alkuun"]
                  [majakka e]]
                 [e]))
             es)))

(defn vieritettava-osio
  [{:keys [menukomponentti] :as _optiot} & osiot]
  (let [avaimet (filter keyword? osiot)
        alkuvalikko (or menukomponentti
                        (fn [avaimet]
                          [:div "valikko"
                           (for [a avaimet]
                             [:span {:on-click (vierita a)}
                              (name a)])]))
        luo-osiot (comp
                    (partition-by keyword?)
                    (map tee-majakat))
        pohja [:<>
               [majakka ::top]
               [alkuvalikko avaimet]]]
    (println (into pohja luo-osiot osiot))
    (into pohja luo-osiot osiot)))