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

