(ns harja.ui.debug)

(defn debug [& args]
  ;; Tuotannossa debug inspektori ei tee mitään
  )

(defn sticky-debug [& _args])

(defonce kehitys? true)

(defn df-shell [& args])

(defn df-shell-kaikki [& args])