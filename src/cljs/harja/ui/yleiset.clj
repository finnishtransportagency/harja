(ns harja.ui.yleiset
  "Helpottavia makroja yleisiin UI-juttuihin."
  )

(defmacro deftk
  "Määrittelee tilaa ylläpitävän komponentin, jossa sisäinen tila on atomeita, joiden sisältä
  on johdettu parametreistä. Kun komponentin parametrit muuttuvat, tila lasketaan uudestaan (esim. haut serveriltä)."
  [nimi parametrit tila body & optiot]
  (let [nimet (map first (partition 2 tila))]
    `(defn ~nimi [~@parametrit]
       (let [paivita# (fn [this# ~@parametrit]
                        ;;(.log js/console "paivita, this= " this# ", parametrit: " ~@parametrit ", state: " (pr-str (reagent/state this#)))
                        (let [{:keys [~@nimet]} (reagent/state this#)]
                          (cljs.core.async.macros/go
                            ~@(map (fn [[nimi form]]
                                     `(reset! ~nimi (let [res# ~form]
                                                      (.log js/console "uusi " ~(str nimi) "= " res#)
                                                      res#)))
                                   (partition 2 tila)))))]
         ;;(.log js/console "ALKUTILA parametrit: " (pr-str ~@parametrit))
         (reagent/create-class
          {:display-name ~(str nimi)
          
           :get-initial-state
           (fn [this#]
             (let [state# (hash-map ~@(mapcat (fn [nimi]
                                                `(~(keyword nimi) (reagent.core/atom nil)))
                                              nimet))]
               state#))
           
           :component-did-mount
           (fn [this#]
             ;;(.log js/console "MOUNT " ~@parametrit)
             (paivita# this# ~@parametrit))

           :component-will-receive-props
           (fn [this#  new-argv#]
             ;;(.log js/console "PROPSIT TULI: this=" this# ", new-argv="   new-argv#)
             (apply paivita# this# (rest new-argv#)))
             
           :reagent-render
           (fn [~@parametrit]
             (let [{:keys [~@nimet]} (reagent/state (reagent/current-component))]
               ~body))})))))
  
