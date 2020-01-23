(ns harja.ui.taulukko.grid
  (:require [clojure.string :as clj-str]))

(defmacro jarjesta-data [jarjestetaan? & body]
  `(binding [harja.ui.taulukko.impl.grid/*jarjesta-data?* ~jarjestetaan?]
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

(defmacro defsolu
  [nimi args & loput]
  (let [pre-post-annettu? (map? (first loput))
        args-pre-post (when pre-post-annettu? (first loput))
        komponentti-fn (if pre-post-annettu?
                      (second loput)
                      (first loput))
        protokollat (if pre-post-annettu?
                      (drop 2 loput)
                      (rest loput))
        protokollat (mapv (fn [[protokollan-nimi toteutus]]
                            `[~protokollan-nimi toteutus])
                          (partition 2 protokollat))]
    `(do
       (defrecord ~nimi ~(vec (cons 'id args))
         harja.ui.taulukko.protokollat.grid-osa/IPiirrettava
         (~'-piirra [~'this]
           ~@(rest (drop-while #(not (vector? %))
                               komponentti-fn)))
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
           []))))