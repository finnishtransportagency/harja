(ns harja.ui.taulukko.kaytokset
  (:require [harja.loki :as loki]
            [clojure.string :as clj-str]
            [harja.tyokalut.regex :as re]))

(defmulti lisaa-kaytos
          (fn [kaytos _]
            (cond
              (map? kaytos) (first (keys kaytos))
              (keyword? kaytos) kaytos
              :else nil)))

(defmethod lisaa-kaytos :eventin-arvo
  [{{:keys [f]} :eventin-arvo} toiminto]
  (comp toiminto
        (fn [event]
          (when event
            (let [arvo (.. event -target -value)]
              (if f
                (f arvo)
                arvo))))))

(defmethod lisaa-kaytos :positiivinen-numero
  [{{:keys [kokonaisosan-maara desimaalien-maara]} :positiivinen-numero} toiminto]
  (comp toiminto
        (fn [arvo]
          (if (= "" arvo)
            arvo
            (when-let [positiivinen-arvo? (and arvo (re-matches (re-pattern (re/positiivinen-numero-re {:kokonaisosan-maara kokonaisosan-maara
                                                                                                        :desimaalien-maara desimaalien-maara}))
                                                                arvo))]
              (when positiivinen-arvo?
                arvo))))))

(defmethod lisaa-kaytos :numero-pisteella
  [_ toiminto]
  (comp toiminto
        (fn [arvo]
          (when (string? arvo)
            (clj-str/replace arvo #"," ".")))))

(defmethod lisaa-kaytos :str->int
  [_ toiminto]
  (comp toiminto
        (fn [arvo]
          (when (and arvo (re-matches (re-pattern (re/numero-re)) arvo))
            (js/parseInt arvo)))))

(defmethod lisaa-kaytos :str->number
  [_ toiminto]
  (comp toiminto
        (fn [arvo]
          (when (and arvo (re-matches (re-pattern (re/numero-re)) arvo))
            (js/Number arvo)))))

(defmethod lisaa-kaytos :oma
  [{{f :f} :oma} toiminto]
  (comp toiminto
        f))

(defmethod lisaa-kaytos :default
  [kaytos toiminto]
  (loki/warn "KAYTOSTÄ: " kaytos " EI OLE MÄÄRITETTY!")
  toiminto)