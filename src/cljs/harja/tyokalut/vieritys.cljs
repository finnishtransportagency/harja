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
  [navigointikomponentti komponentin-optiot es]
  (concat
    []
    (keep identity
          (conj
            (into []
                  (reduce
                    (r/partial majakat (:nykyinen komponentin-optiot))
                    []
                    es))
            (when-not (keyword? (first es))
              [navigointikomponentti (assoc komponentin-optiot :nykyinen @(:nykyinen komponentin-optiot))])))))

(defn vierita-ylos
  []
  (vierita ::top))

(defn vieritettava-osio
  [{:keys [menukomponentti osionavigointikomponentti parametrit] :as _optiot} & osiot]
  (let [{menu-optiot :menu
         osionavigointi-optiot :navigointi} parametrit
        avaimet (filter keyword? osiot)
        nykyinen-avain (r/atom nil)                         ; ikävä mutatointi, mut selkeyttää ylipäätään
        alkuvalikko (or menukomponentti
                        (fn [avaimet _]
                          [:div "valikkoa ei erikseen määritelty"
                           (for [a avaimet]
                             [:span {:on-click (vierita a)}
                              (name a)])]))
        luo-osiot (comp
                    (partition-by keyword?)
                    (mapcat (r/partial tee-majakat osionavigointikomponentti (merge {} osionavigointi-optiot {:avaimet avaimet :nykyinen nykyinen-avain})))
                    (filter #(not (keyword? %))))
        pohja [:<>
               [majakka ::top]
               [alkuvalikko (merge {} menu-optiot {:avaimet avaimet})]]
        osiot-majakoineen (into [] luo-osiot osiot)]
    (vec (concat pohja osiot-majakoineen))))