(ns harja.ui.taulukko.grid)

(defmacro jarjesta-data [jarjestetaan? & body]
  `(binding [harja.ui.taulukko.grid/*jarjesta-data?* ~jarjestetaan?]
     ~@body))

(defmacro triggeroi-seurannat [triggeroidaan? & body]
  `(binding [harja.ui.taulukko.datan-kasittely/*muutetaan-seurattava-arvo?* ~triggeroidaan?]
     ~@body))