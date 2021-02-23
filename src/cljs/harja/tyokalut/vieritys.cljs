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

(defn tee-majakat
  "Käydään läpi kaikki saadut elementit ja keyword-tunnisteet korvataan majakka-elementeillä. ::navigointi-keywordillä sitten seuraavassa vaiheessa korvataan navigointikomponentilla."
  [es]
  (concat
    []
    (keep identity
          (conj
            (into []
                  (reduce
                    (fn [acc e]
                      (keep identity
                            (conj acc
                                  (when (keyword? e)
                                    [majakka e])
                                  e)))
                    []
                    es))
            (when-not (keyword? (first es))
              ::navigointi)))))

(defn vierita-ylos
  []
  (vierita ::top))

(defn tee-navigointi
  "Transduceri joka täydentää navigointielementit kohdilleen. Tilallinen, koska pitää tietää, mihin osioon navigointikomponentti liittyy. Sitten navigointikomponentin sisällä voidaan luoda oikea vieritys."
  [navigointi]
  (fn [rf]
    (let [aiemmat (volatile! [])]
      (fn
        ([]
         (rf))
        ([acc]
         (rf acc))
        ([acc e]
         (let [at @aiemmat
               avain (when (= e ::navigointi)
                       (loop [vika (last at)
                              loput (butlast at)]
                         (if (or
                               (nil? vika)
                               (keyword? vika))
                           vika
                           (recur (last loput) (butlast loput)))))
               elementti [navigointi avain]]
           (vswap! aiemmat conj (if (= e ::navigointi)
                                  elementti
                                  e))
           (if (= e ::navigointi)
             (rf acc elementti)
             (rf acc e))))))))

(defn vieritettava-osio
  [{:keys [menukomponentti osionavigointikomponentti] :as _optiot} & osiot]
  (let [avaimet (filter keyword? osiot)
        alkuvalikko (or menukomponentti
                        (fn [avaimet]
                          [:div "valikkoa ei erikseen määritelty"
                           (for [a avaimet]
                             [:span {:on-click (vierita a)}
                              (name a)])]))
        navigointi (r/partial osionavigointikomponentti avaimet)
        luo-osiot (comp
                    (partition-by keyword?)
                    (mapcat tee-majakat)
                    (tee-navigointi navigointi)
                    (filter #(not (keyword? %))))
        pohja [:<>
               [majakka ::top]
               [alkuvalikko avaimet]]
        osiot-majakoineen (into [] luo-osiot osiot)]
    (vec (concat pohja osiot-majakoineen))))