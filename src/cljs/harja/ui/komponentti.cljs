(ns harja.ui.komponentti
  "Apureita, joilla voi kasata komponentteja mixin osista."
  (:require [reagent.core :as r :refer [atom]]
            [harja.asiakas.tapahtumat :as t]
            [harja.tiedot.muokkauslukko :as lukko]
            [harja.tiedot.istunto :as istunto]
            [harja.ui.dom :as dom]
            [harja.loki :refer [log error]]
            [goog.events :as events]
            [harja.virhekasittely :as virhekasittely]
            [goog.events.EventType :as EventType]
            [harja.tiedot.kartta :as kartta-tiedot]))

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
     {:reagent-render (fn [& args] (try
                                     (apply render args)
                                     (catch :default e
                                       (error "VIRHE RENDERÖITÄESSÄ KOMPONENTTIA!")
                                       (error e) ;; Logita erikseen, jotta helpompi tarkistaa
                                       [virhekasittely/rendaa-virhe e])))
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

(defn dom-kuuntelija
  "Mixin DOM tapahtumien kuuntelemiseen annetussa elementissä.
  Toteuttaa component-did-mount ja component-will-unmount elinkaarimetodit.
  aiheet-ja-kasittelijat on vuorotellen aihe (goog.events.EventType enumeraation arvo) ja käsittelyfunktio,
  jolle annetaan kaksi parametria: komponentti ja tapahtuma."
  [dom-node & aiheet-ja-kasittelijat]
  (let [kuuntelijat (partition 2 aiheet-ja-kasittelijat)]
    {:component-did-mount (fn [this _]
                            (loop [kahvat []
                                   [[aihe kasittelija] & kuuntelijat] kuuntelijat]
                              (if-not aihe
                                (r/set-state this {::dom-kuuntelijat kahvat})
                                (let [kuuntelija-fn (fn [tapahtuma] (kasittelija this tapahtuma))]
                                  (events/listen dom-node aihe kuuntelija-fn)
                                  (recur (conj kahvat [aihe kuuntelija-fn]) kuuntelijat)))))

     :component-will-unmount (fn [this _]
                               (let [kuuntelijat (-> this r/state ::dom-kuuntelijat)]
                                 (doseq [[aihe kuuntelija-fn] kuuntelijat]
                                   (events/unlisten dom-node aihe kuuntelija-fn))))}))

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

(defn piirretty
  "Mixin, joka kutsutaan kun komponentti on oikeasti DOMissa asti (component-did-mount)"
  [piirretty]
  {:component-did-mount (fn [comp]
                          (piirretty comp))})

(defn lippu-arvo
  "Mixin joka asettaa ensimmäisen arvon kun komponentti näytetään ja toisen arvon kun komponentti poistuu."
  [arvo-sisaan arvo-ulos & lippu-atomit]
  (sisaan-ulos #(doseq [lippu-atom lippu-atomit] (reset! lippu-atom arvo-sisaan))
               #(doseq [lippu-atom lippu-atomit] (reset! lippu-atom arvo-ulos))))

(defn lippu
  "Mixin, joka asettaa annetun atomin tilan joko true tai false sen mukaan onko komponentti näkyvissä."
  [& lippu-atomit]
  (sisaan-ulos #(doseq [lippu-atom lippu-atomit] (reset! lippu-atom true))
               #(doseq [lippu-atom lippu-atomit] (reset! lippu-atom false))))

(defn avain-lippu
  "Mixin, joka lisää annettuun atomiin avaimen (conj) ja poistaa sen (disj) kun komponentti poistuu.
  Atomin arvon tulee olla setti."
  [atomi avain]
  (sisaan-ulos #(swap! atomi conj avain)
               #(swap! atomi disj avain)))

(defn lukko
  "Mixin, joka hoitaa muokkauslukkoon liittyvät toimenpiteet."
  [lukko-id]
  (sisaan-ulos #(lukko/paivita-lukko lukko-id)
               #(lukko/vapauta-lukko lukko-id)))

(defn pysayta-ajastin
  "Mixin, joka pysäyttää aikakatkaisun ajastimen komponentin ollessa aktiivinen."
  []
  (sisaan-ulos #(istunto/pysayta-ajastin!)
               #(istunto/kaynnista-ajastin!)))

(defn kun-muuttuu
  "Mixin, joka seuraa annetun parametrien muuttumista. Tekee :component-will-receive-props
  elinkaaren kuuntelijan ja laukaisee callbackin aina kun parametrit muuttuvat.
  Callbackille annetaan samat parametrit kuin render funktiolle."
  [callback]
  {:component-will-receive-props
   (fn [& args]
     (apply callback (drop 2 args)))})

(defn kun-muuttui
  "Mixin, jota kutsutaan kun komponentti päivittyi. Tekee :component-did-update
  elinkaaren kuuntelijan ja laukaisee callbackin aina kun parametrit muuttuvat.
  Callbackille annetaan samat parametrit kuin render funktiolle."
  [callback]
  {:component-did-update
   (fn [& args]
     (apply callback (drop 2 args)))})

(defn vanhat-ja-uudet-parametrit
  "Mixin, jonka avulla voi verrata komponentin vanhoja ja uusia parametreja. Tekee
  :component-will-receive-props elinkaaren kuuntelijan. Callbackille annetaan parametrina
  kaksi vektoria, joista ensimmäinen sisältää vanhat parametrit, ja toinen uuden. Parametrit
  ovat samat, kuin render-funktiolle annettavat."
  [callback]
  {:component-will-receive-props
   ;; Reagentin dokumentaation mukaan tämän pitäisi olla [this argv].
   ;; Todellisuudessa [this jotain-häröä param1 param2 ..]
   (fn [this _ & uudet]
     ;; Reactissa ensimmäinen parametri on props, ja loput on children
     (let [vanhat (into [(r/props this)] (r/children this))]
       (callback vanhat (vec uudet))))})

(defn klikattu-ulkopuolelle
  "Mixin, joka kutsuu annettua funktiota kun klikataan komponentin ulkopuolelle. Esim. hovereiden sulkemiseen."
  [ulkopuolella-fn]
  (kuuntelija :body-klikkaus
              (fn [this tapahtuma]
                (when-not (dom/sisalla? this (:tapahtuma tapahtuma))
                  (ulkopuolella-fn)))))

(defn ulos
  "Mixin, joka kutsuu annettua funktiota komponentin poistuessa."
  [funktio]
  {:component-will-unmount
   (fn [_]
     (funktio))})

(defn watcher
  "Komponentti mixin atomin add-watch/remove-watch tekemiseen kun component-did-mount ja component-will-unmount
  elinkaaritapahtumien yhteydessä.
  atomit-ja-kasittelijat on vuorotellen atomi ja käsittelyfunktio,
  jolle annetaan kolme parametria: komponentti, vanha arvo ja uusi arvo."
  [& atomit-ja-kasittelijat]
  (let [kasittelijat (partition 2 atomit-ja-kasittelijat)
        key (gensym "komponenttiwatch")]
    {:component-did-mount (fn [this _]
                            (loop [kahvat []
                                   [[atomi kasittelija] & kasittelijat] kasittelijat]
                              (if-not atomi
                                (r/set-state this {::atomien-watcherit kahvat})
                                (do (add-watch atomi key (fn [_ _ vanha uusi]
                                                           (kasittelija this vanha uusi)))
                                    (recur (conj kahvat atomi)
                                           kasittelijat)))))
     :component-will-unmount (fn [this _]
                               (let [atomit (-> this r/state ::atomien-watcherit)]
                                 (doseq [a atomit]
                                   (remove-watch a key))))}))

(defn karttakontrollit
  "Näyttää karttakontrollit karttanäkymässä kun tämä komponentti on näkyvissä."
  [nimi kontrollit]
  (sisaan-ulos #(kartta-tiedot/nayta-kartan-kontrollit! nimi kontrollit)
               #(kartta-tiedot/poista-kartan-kontrollit! nimi)))
