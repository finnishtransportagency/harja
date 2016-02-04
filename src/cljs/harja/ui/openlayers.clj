(ns harja.ui.openlayers)

(defmacro disable-rendering
  "Disable ol3 rendering during body. This is useful for batching operations that would cause rendering."
  [map-sym & body]
  `(let [map# ~map-sym
         view# (.getView map#)]
     (try
       (.setView map# nil)
       ~@body
       (finally
         (.setView map# view#)))))
