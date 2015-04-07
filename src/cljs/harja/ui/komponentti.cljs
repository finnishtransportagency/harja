(ns harja.ui.komponentti
  "Apureita, joilla voi kasata komponentteja mixin osista."
  (:require [reagent.core :as r :refer [atom]]
            [harja.asiakas.tapahtumat :as t]))

(defn luo
  "Luo uuden komponentin instanssin annetuista toteutuksista.
Toteutus on joko mäppi, joka sisältää lifecycle metodeja tai funktio.
Funktiototeutuksia saa olla maksimissa yksi ja sitä käytetään :reagent-render lifecycle
metodina. Muut toteutusten antamat lifecycle metodit yhdistetään siten, että kaikkien
  metodit kutsutaan."
  
  [& toteutukset]

  (let [render (or (some #(when (fn? %) %) toteutukset)
                   (some :reagent-render toteutukset))
        toteutukset (filter #(not (fn? %)) toteutukset)
        
        get-initial-state (keep :get-initial-state toteutukset)
        component-will-receive-props (keep :component-will-receive-props toteutukset)
        component-will-mount (keep :component-will-mount toteutukset)
        component-did-mount (keep :component-did-mount toteutukset)
        component-will-update (keep :component-will-update toteutukset)
        component-did-update (keep :component-did-update toteutukset)
        component-will-unmount (keep :component-will-unmount toteutukset)]
    
    (r/create-class
     {:reagent-render render
      :get-initial-state (fn [this]
                           (reduce merge (map #(% this) get-initial-state)))
      :component-will-receive-props (fn [this new-argv]
                                      (doseq [f component-will-receive-props]
                                        (apply f this new-argv)))
      :component-will-mount (fn [this]
                              (doseq [f component-will-mount]
                                (f this)))
      :component-did-mount (fn [this]
                             (doseq [f component-did-mount]
                               (f this)))
      :component-will-update (fn [this new-argv]
                               (doseq [f component-will-update]
                                 (apply f this new-argv)))
      :component-did-update (fn [this old-argv]
                              (doseq [f component-did-update]
                                (apply f this old-argv)))
      :component-will-unmount (fn [this]
                                (doseq [f component-will-unmount]
                                  (f this)))})))

                   
(defn kuuntelija
  "Komponentti mixin tapahtuma-aiheiden kuuntelemiseen.
Toteuttaa component-did-mount ja component-will-unmount elinkaarimetodit.
aiheet-ja-kasittelijat on vuorotellen aihe (yksi avainsana tai joukko avainsanoja) ja käsittelyfunktio,
  jolle annetaan kaksi parametria: komponentti ja tapahtuma."
  [& aiheet-ja-kasittelijat]
  (let [kuuntelijat (partition 2 aiheet-ja-kasittelijat)]
    {:component-did-mount (fn [this _]
                            (loop [kahvat []
                                   [[aihe kasittelija] & kuuntelijat] kuuntelijat]
                              (if-not aihe
                                (r/set-state this {::kuuntelijat kahvat})
                                (recur (concat kahvat
                                               (doall (map #(t/kuuntele! % (fn [tapahtuma] (kasittelija this tapahtuma)))
                                                           (if (keyword? aihe)
                                                             [aihe]
                                                             (seq aihe)))))
                                       kuuntelijat))))
      :component-will-unmount (fn [this _]                                
                                (let [kuuntelijat (-> this r/state ::kuuntelijat)]
                                  (doseq [k kuuntelijat]
                                    (k))))}))

(defn sisaan-ulos
  "Mixin, joka käsittelee component-will-mount ja component-will-unmount elinkaaret. Tällä voi kätevästi tehdä jotain komponentin tullessa näkyviin ja perua se komponentin poistuessa."
  [sisaan ulos]
  {:component-will-mount (fn [& _]
                           (sisaan))
   :component-will-unmount (fn [& _]
                             (ulos))})
