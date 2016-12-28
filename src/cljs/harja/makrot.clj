(ns harja.makrot
  (:require [harja.loki]))

(defmacro defc [name args & body]
  (assert (symbol? name) "function name must be a symbol")
  (assert (vector? args) "argument list must be a vector")
  `(defn ~name ~args
     (try
       ~@body
       (catch :default e#
         (harja.loki/error e#)
         [harja.virhekasittely/rendaa-virhe e#]))))

(defmacro fnc [args & body]
  (assert (vector? args) "argument list must be a vector")
  `(fn ~args
     (try
       ~@body
       (catch :default e#
         (harja.loki/error e#)
         [harja.virhekasittely/rendaa-virhe e#]))))

(defmacro kasittele-virhe
  [& body]
  `(try
     ~@body
     (catch :default e#
       (harja.loki/error e#)
       [harja.virhekasittely/rendaa-virhe e#])))

(defmacro with-loop-from-channel
  "Makro joka lukee loopissa viestejä annetusta kanavasta kunnes kanava menee kiinni.
   Viestin käsittelypoikkeukset napataan kiinni, logitetaan ja viestien lukeminen
   jatkuu normaalisti"
  [chan binding & body]
  (assert (symbol? binding) "binding must be a symbol")
  `(let [c# ~chan]
     (cljs.core.async.macros/go-loop [~binding (cljs.core.async/<! c#)]
       (when (not (nil? ~binding))
         (try
           ~@body
           (catch :default e#
             (harja.loki/log "VIRHE GO-blokissa: " e#)))
         (recur (cljs.core.async/<! c#))))))

(defmacro with-items-from-channel
  "Makro joka lukee loopissa viestejä annetusta kanavasta. Binding on vektori, jossa
  ensimmäinen on arvon nimi (tai destrukturointi) ja toinen on kanava. Looppaa niin
  kauan kuin kanavassa on tavaraa."
  [[name chan :as binding] & body]
  (assert (and (vector? binding)
               (= 2 (count binding)))
          "Binding must be vector of [name chan]")
  `(let [c# ~chan]
     (cljs.core.async.macros/go-loop []
       (let [value# (cljs.core.async/<! c#)]
         (when (some? value#)
           (let [~name value#]
             (try
               ~@body
               (catch :default e#
                 (harja.loki/log "VIRHE with-items-from-channel blokissa: " e#))))
           (recur))))))

(defmacro nappaa-virhe [& body]
  `(try
     ~@body
     (catch :default e#
       (harja.loki/error e#)
       (harja.virhekasittely/arsyttava-virhe "go-blokki kaatui: " e#))))

(defmacro nappaa-virhe-hiljaa [& body]
  `(try
     ~@body
     (catch :default e#
       (harja.loki/error e#))))

;; Helpompi Google Closure luokkien extend,
;; http://www.50ply.com/blog/2012/07/08/extending-closure-from-clojurescript/

(defn- to-property [sym]
  (symbol (str "-" sym)))

(def ^:dynamic *current-method* nil)

(defmacro goog-extend [type base-type ctor & methods]
  `(do
     (defn ~type ~@ctor)

     (goog/inherits ~type ~base-type)

     ~@(mapv
        (fn [method]
          (binding [*current-method* (name (first method))]
            `(set! (.. ~type -prototype ~(to-property (first method)))
                   (fn ~@(rest method)))))
        methods)))

;; Yläluokan metodin kutsuminen (super arg1 ... argN), this automaattisesti

#_(defmacro super [& args]
  (if *current-method*
    ;; Metodikutsu
    `(goog/base (cljs.core/js* "this") ~*current-method* ~@args)

    ;; Ei metodissa, konstruktorikutsu
    `(goog/base (cljs.core/js* "this") ~@args)))


;;; Async threading makrot, kommentoitu... ehkä fmap on parempi
