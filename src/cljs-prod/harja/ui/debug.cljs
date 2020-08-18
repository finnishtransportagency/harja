(ns harja.ui.debug)

(goog-define ^boolean ESIMERKIT false)

(defn debug [& args]
  ;; Tuotannossa debug inspektori ei tee mitään
  )
(defonce kehitys? true)