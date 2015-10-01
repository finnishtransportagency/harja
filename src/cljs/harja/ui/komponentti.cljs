(ns harja.ui.komponentti
  "Apureita, joilla voi kasata komponentteja mixin osista."
  (:require [reagent.core :as r :refer [atom]]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.muokkauslukko :as lukko]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.yleiset :as yleiset]))

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
aiheet-ja-kasittelijat on vuorotellen aihe (yksi avainsana tai vektori avainsanoja) ja käsittelyfunktio,
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
  "Mixin, joka käsittelee component-will-mount ja component-will-unmount elinkaaret. Tällä voi kätevästi tehdä jotain
  komponentin tullessa näkyviin ja perua se komponentin poistuessa."
  [sisaan ulos]
  {:component-will-mount (fn [& _]
                           (sisaan))
   :component-will-unmount (fn [& _]
                             (ulos))})

(defn sisaan
  "Mixin, joka käsittelee component-will-mount ja component-will-unmount elinkaaret. Tällä voi kätevästi tehdä jotain
  komponentin tullessa näkyviin ja perua se komponentin poistuessa."
  [sisaan]
  {:component-will-mount (fn [& _]
                           (sisaan))})

(defn lippu
  "Mixin, joka asettaa annetun atomin tilan joko true tai false sen mukaan onko komponentti näkyvissä."
  [& lippu-atomit]
  (sisaan-ulos #(doseq [lippu-atom lippu-atomit] (reset! lippu-atom true))
               #(doseq [lippu-atom lippu-atomit] (reset! lippu-atom false))))

(defn lukko
  "Mixin, joka hoitaa muokkauslukkoon liittyvät toimenpiteet."
  [lukko-id]
  (sisaan-ulos #(lukko/paivita-lukko lukko-id)
               #(lukko/vapauta-lukko lukko-id)))

(defn pysayta-ajastin
  "Mixin, joka pysäyttää aikakatkaisun ajastimen komponentin ollessa aktiivinen."
  []
  (sisaan-ulos #(istunto/pysayta-ajastin)
               #(istunto/kaynnista-ajastin)))

(defn kun-muuttuu
  "Mixin, joka seuraa annetun parametrin muuttumista. Tekee :component-will-receive-props elinkaaren
  kuuntelijan ja laukaisee callbackin aina kun järjestysnumerolla (nollasta alkaen) ilmaistu parametri muuttuu.
Callbackille annetaan samat parametrit kuin render funktiolle."
  [alkuarvo jarjestys callback]
  (let [arvo (cljs.core/atom alkuarvo)]
    {:component-will-receive-props
     (fn [& args]
       (let [uusi-arvo (nth args (+ jarjestys 2))]
         (when (not= @arvo uusi-arvo)
           (reset! arvo uusi-arvo)
           (callback (drop 2 args)))))}))

(defn klikattu-ulkopuolelle
  "Mixin, joka kutsuu annettua funktiota kun klikataan komponentin ulkopuolelle. Esim. hovereiden sulkemiseen."
  [ulkopuolella-fn]
  (kuuntelija :body-klikkaus
              (fn [this tapahtuma]
                (when-not (yleiset/sisalla? this (:tapahtuma tapahtuma))
                  (ulkopuolella-fn)))))

(defn ulos
  "Mixin, joka kutsuu annettua funktiota komponentin poistuessa."
  [funktio]
  {:component-will-unmount
   (fn [_]
     (funktio))})
  
