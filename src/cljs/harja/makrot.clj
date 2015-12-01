(ns harja.makrot)

(defmacro defc [name args & body]
  (assert (symbol? name) "function name must be a symbol")
  (assert (vector? args) "argument list must be a vector")
  `(defn ~name ~args
     (try
       ~@body
       (catch :default e#
         [harja.virhekasittely/rendaa-virhe e#]))))

(defmacro fnc [args & body]
  (assert (vector? args) "argument list must be a vector")
  `(fn ~args
     (try
       ~@body
       (catch :default e#
         [harja.virhekasittely/rendaa-virhe e#]))))

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

(defmacro nappaa-virhe [& body]
  `(try
     ~@body
     (catch :default e#
       (harja.virhekasittely/arsyttava-virhe "go-blokki kaatui: " e#))))
