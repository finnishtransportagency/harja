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

(defn- majakat
  [avain acc e]
  (vec
    (keep identity
          (conj acc
                (when (keyword? e)
                  (reset! avain e)
                  [majakka e])
                e))))

(defn tee-majakat
  "Käydään läpi kaikki saadut elementit ja keyword-tunnisteet korvataan majakka-elementeillä. Laitetaan myös navigointi kohdilleen"
  [navigointi nykyinen-avain es]
  (concat
    []
    (keep identity
          (conj
            (into []
                  (reduce
                    (r/partial majakat nykyinen-avain)
                    []
                    es))
            (when-not (keyword? (first es))
              [navigointi @nykyinen-avain])))))

(defn vierita-ylos
  []
  (vierita ::top))

(defn vieritettava-osio
  [{:keys [menukomponentti osionavigointikomponentti] :as _optiot} & osiot]
  (let [avaimet (filter keyword? osiot)
        nykyinen-avain (r/atom nil)                         ; ikävä mutatointi, mut selkeyttää ylipäätään
        alkuvalikko (or menukomponentti
                        (fn [avaimet]
                          [:div "valikkoa ei erikseen määritelty"
                           (for [a avaimet]
                             [:span {:on-click (vierita a)}
                              (name a)])]))
        navigointi (r/partial osionavigointikomponentti avaimet)
        luo-osiot (comp
                    (partition-by keyword?)
                    (mapcat (r/partial tee-majakat navigointi nykyinen-avain))
                    (filter #(not (keyword? %))))
        pohja [:<>
               [majakka ::top]
               [alkuvalikko avaimet]]
        osiot-majakoineen (into [] luo-osiot osiot)]
    (vec (concat pohja osiot-majakoineen))))