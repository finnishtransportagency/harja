(ns harja.ui.taulukko.grid
  (:require [clojure.string :as clj-str]))

(defmacro jarjesta-data [jarjestetaan & body]
  `(binding [harja.ui.taulukko.impl.grid/*jarjesta-data* ~jarjestetaan]
     ~@body))

(defmacro triggeroi-seurannat [triggeroidaan? & body]
  `(binding [harja.ui.taulukko.impl.datan-kasittely/*muutetaan-seurattava-arvo?* ~triggeroidaan?]
     ~@body))

(defmacro triggeroi-tapahtumat [triggeroidaan? & body]
  `(binding [harja.ui.taulukko.impl.datan-kasittely/*ajetaan-tapahtuma?* ~triggeroidaan?]
     ~@body))

(defn fn-nimi [record-nimi]
  (-> record-nimi
      str
      (clj-str/replace #"[A-Z]" (fn [W] (str "-" (clj-str/lower-case W))))
      (clj-str/replace #"^-" "")
      symbol))

(defn paivita-syvyydessa
  [osa n f]
  (if (= n 0)
    (f osa)
    (concat (drop-last osa)
            [(paivita-syvyydessa (last osa) (dec n) f)])))

(defmacro defsolu
  [nimi args & loput]
  (let [pre-post-annettu? (map? (first loput))
        args-pre-post (when pre-post-annettu? (first loput))
        render (if pre-post-annettu?
                      (second loput)
                      (first loput))
        protokollat (if pre-post-annettu?
                      (drop 2 loput)
                      (rest loput))
        this-symbol-alustus# (ffirst (drop-while #(not (vector? %)) render))
        create-class-osio (fn [render-fn#]
                            (let [this-symbol-render# (ffirst (drop-while #(not (vector? %)) render-fn#))]
                              `(reagent.core/create-class
                                 {:constructor (fn [~this-symbol-alustus# ~'_]
                                                 (set! (.-domNode ~this-symbol-alustus#) (fn [] (reagent.dom/dom-node ~this-symbol-alustus#)))
                                                 (set! (.-state ~this-symbol-alustus#) (cljs.core/clj->js {:error nil})))
                                  :get-derived-state-from-error (fn [error#]
                                                                  (cljs.core/clj->js {:error error#}))
                                  :component-did-catch (fn [~'_ error-info#]
                                                         (harja.loki/warn (str "SOLU: " (or (::nimi ~this-symbol-alustus#) "Nimetön") "(" (:id ~this-symbol-alustus#) ")" " kaatui virheeseen: "
                                                                               error-info# "\n")))
                                  :display-name (str (or (::nimi ~this-symbol-alustus#) "Nimetön") " (" (:id ~this-symbol-alustus#) ")" " - tyyppi: " (str ~nimi))
                                  :render (fn [~this-symbol-alustus#]
                                            (if-let [error# (.. ~this-symbol-alustus# ~'-state ~'-error)]
                                              [harja.virhekasittely/rendaa-virhe error#]
                                              (let [[~'_ ~this-symbol-render#] (reagent.core/argv ~this-symbol-alustus#)]
                                                ~@(rest (drop-while #(not (vector? %)) render-fn#)))))})))
        protokollat (mapv (fn [[protokollan-nimi toteutus]]
                            `[~protokollan-nimi toteutus])
                          (partition 2 protokollat))
        alustus (rest (drop-while #(not (vector? %)) render))
        sisaltaa-render-fn (last alustus)]
    `(do
       (defrecord ~nimi ~(vec (cons 'id args))
         harja.ui.taulukko.protokollat.grid-osa/IPiirrettava
         (~'-piirra [~this-symbol-alustus#]
           ~@(loop [n-iteration 0
                    sisalto sisaltaa-render-fn
                    render-fn? (= 'fn (first sisalto))]
               (cond
                 render-fn? (concat (butlast alustus)
                                    [(paivita-syvyydessa sisaltaa-render-fn n-iteration create-class-osio)])
                 (nil? sisalto) [(create-class-osio render)]
                 (not (list? sisalto)) (recur (inc n-iteration)
                                              nil
                                              (= 1 0))
                 :else (recur (inc n-iteration)
                              (last sisalto)
                              (= 'fn (and (list? (last sisalto)) (first (last sisalto))))))))
         ~@(if-let [protokolla-toteutus (some (fn [[protokollan-nimi _ :as protokollan-toteutus]]
                                                (when (= 'harja.ui.taulukko.protokollat.grid-osa/IGridOsa ~protokollan-nimi)
                                                  protokollan-toteutus))
                                              protokollat)]
             protokolla-toteutus
             '[harja.ui.taulukko.protokollat.grid-osa/IGridOsa
               (-id [this]
                    (:id this))
               (-id? [this id]
                     (= (:id this) id))
               (-nimi [this]
                      (::nimi this))
               (-aseta-nimi [this nimi]
                            (assoc this ::nimi nimi))])
         harja.ui.taulukko.protokollat.solu/ISolu
         ~@(remove (fn [[protokollan-nimi _]]
                     (= 'harja.ui.taulukko.protokollat.grid-osa/IGridOsa protokollan-nimi))
                   protokollat))
       ~@(if pre-post-annettu?
           `[(defn ~(fn-nimi nimi)
               ~args
              ~args-pre-post
              (~(symbol (str "->" nimi)) (gensym "defsolu") ~@args))]
           `[(defn ~(fn-nimi nimi)
               ~args
               (~(symbol (str "->" nimi)) (gensym "defsolu") ~@args))]))))