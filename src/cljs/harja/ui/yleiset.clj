(ns harja.ui.yleiset
  "Helpottavia makroja yleisiin UI-juttuihin."
  )

(def +tyypit+ #{:atom :reaction :run!})

(defn- tilat
  "Partitioi tilan kahden alkion osiin, jos toinen alkio on :reaction, otetaan kolmaskin."
  [tila]
  (loop [tilat []
         [n arvo & loput] tila]
    (if (nil? n)
      tilat
      (if (+tyypit+ arvo)
        (recur (conj tilat [n arvo (first loput)])
               (rest loput))
        (recur (conj tilat [n :atom arvo])
               loput)))))

(defmacro deftk
  "Määrittelee tilaa ylläpitävän komponentin, jossa sisäinen tila on atomeita, joiden sisältä
  on johdettu parametreistä. Kun komponentin parametrit muuttuvat, tila lasketaan uudestaan (esim. haut serveriltä)."
  [nimi parametrit tila body & optiot]
  (let [tilat (tilat tila)
        nimet (map first tilat)]
    `(defn ~nimi [~@parametrit]
       (let [paivita# (fn [this# ~@parametrit]
                        ;;(.log js/console "paivita, this= " this# ", parametrit: " ~@parametrit ", state: " (pr-str (reagent/state this#)))
                        (let [{:keys [~@nimet]} (reagent.core/state this#)]
                          (cljs.core.async.macros/go
                            ~@(map (fn [[nimi tyyppi form]]
                                     (when (= tyyppi :atom)
                                       `(reset! ~nimi (let [res# ~form]
                                                        (.log js/console "uusi " ~(str nimi) "= " res#)
                                                        res#))))
                                   tilat))))]
         ;;(.log js/console "ALKUTILA parametrit: " (pr-str ~@parametrit))
         (reagent.core/create-class
          {:display-name ~(str nimi)
          
           :get-initial-state
           (fn [this#]
             (let [~@(mapcat (fn [[nimi tyyppi form]]
                               (cond
                                (= tyyppi :atom)
                                `(~nimi (reagent.core/atom nil))
                                
                                (= tyyppi :reaction)
                                `(~nimi (reagent.ratom/reaction ~form))))
                             tilat)]
               (hash-map ~@(mapcat (fn [[nimi & _]]
                                     `(~(keyword nimi) ~nimi)) tilat))))
           
           
           :component-did-mount
           (fn [this#]
             ;;(.log js/console "MOUNT " ~@parametrit)
             (paivita# this# ~@parametrit))

           :component-will-receive-props
           (fn [this#  new-argv#]
             (.log js/console "PROPSIT TULI: this=" this# ", new-argv="   new-argv#)
             (apply paivita# this# (rest new-argv#)))
             
           :reagent-render
           (fn [~@parametrit]
             (let [{:keys [~@nimet]} (reagent.core/state (reagent.core/current-component))]
               ~body))})))))
  
