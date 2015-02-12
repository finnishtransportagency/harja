(ns harja.ui.yleiset
  "Helpottavia makroja yleisiin UI-juttuihin."
  )

(defmacro deftk
  "Määrittelee tilaa ylläpitävän komponentin, jossa sisäinen tila on atomeita, joiden sisältä
  on johdettu parametreistä. Kun komponentin parametrit muuttuvat, tila lasketaan uudestaan (esim. haut serveriltä)."
  [nimi parametrit tila body & optiot]
  (let [nimet (map first (partition 2 tila))]
    `(defn ~nimi [~@parametrit]
       (let [paivita# (fn [this#]
                        (.log js/console "päivitetään")
                        (let [{:keys [~@nimet]} (reagent/state this#)]
                          (cljs.core.async.macros/go
                            ~@(map (fn [[nimi form]]
                                     `(reset! ~nimi ~form))
                                   (partition 2 tila)))))]
         (reagent/create-class
          {:display-name ~(str nimi)
          
           :get-initial-state
           (fn [this#]
             (.log js/console "get-initial-state!")
             (let [state# (hash-map ~@(mapcat (fn [nimi]
                                                `(~(keyword nimi) (atom nil)))
                                              nimet))]
               (.log js/console " => " (pr-str state#))
               state#))
           
           :component-did-mount
           (fn [this#]
             (paivita# this#))
           
           :reagent-render
           (fn [~@parametrit]
             (let [{:keys [~@nimet]} (reagent/state (reagent/current-component))]
               ~body))})))))
  
