(ns harja.tyokalut.vieritys
  (:require [reagent.core :as r]
            [clojure.string :as str]))

(defonce vierityskohteet (r/atom {}))


(defn- majakan-tunniste [kw]
  (if-not (str/ends-with? (str kw) "-majakka")
    (keyword (str (when (namespace kw)
                    (str (namespace kw) "/"))
               (name kw) "-majakka"))
    kw))

(defn majakka
  "Vieritystargetti"
  [_]
  (r/create-class
    {:component-will-unmount
     #(reset! vierityskohteet {})

     :reagent-render
     (fn [nimi]
       (let [tunniste (majakan-tunniste nimi)]
         [:span {:id tunniste
                 :ref (fn [e]
                        (swap! vierityskohteet assoc tunniste e))}]))}))

(defn vierita
  [kohde-kw]
  (fn [_]
    (let [kohde (majakan-tunniste kohde-kw)
          elementti (kohde @vierityskohteet)
          parametrit (js-obj "behavior" "smooth")]
      (.scrollIntoView elementti parametrit))))

(defn- majakat
  [avain acc e]
  (vec
    (keep identity
      (conj acc
        (when (keyword? e)
          ;; Jos e on keyword, tehdään siitä majakan tunniste ja lisätään perään -majakka loppuosa, jotta HTML ID:t
          ;; pysyvät uniikkeina näkymässä.
          (let [e (majakan-tunniste e)]
            (reset! avain e)
            [majakka e]))
        e))))

(defn tee-majakat
  "Käydään läpi kaikki saadut elementit ja keyword-tunnisteet korvataan majakka-elementeillä. Laitetaan myös navigointi kohdilleen"
  [navigointikomponentti komponentin-optiot els]
  (concat
    []
    (keep identity
          (conj
            (into []
                  (reduce
                    (r/partial majakat (:nykyinen komponentin-optiot))
                    []
                    els))
            (when-not (keyword? (first els))
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
                    (mapcat (r/partial
                              tee-majakat
                              osionavigointikomponentti
                              (merge {} osionavigointi-optiot
                                ;; Muuta annetut osioiden kw:t majakan tunnisteiksi, joita käytetään sisäisesti vierityslogiikassa.
                                {:avaimet (map majakan-tunniste avaimet)
                                 :nykyinen nykyinen-avain})))
                    (filter #(not (keyword? %))))
        pohja [:<>
               [majakka ::top]
               ;; Alkuvalikolle annetaan avaimet sellaisena kuin ne vieritettava-osio komponentille on annettu.
               ;;  Niitä käytetään alkuvalikossa sellaisenaan, joten niissä ei saa olla "-majakka"-päätettä.
               [alkuvalikko (merge {} menu-optiot {:avaimet avaimet})]]
        osiot-majakoineen (into [] luo-osiot osiot)]
    (vec (concat pohja osiot-majakoineen))))