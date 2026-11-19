(ns harja.ui.primitiivit.tabs
  (:require [clojure.string :as str]))

(defn tabin-data-cy
  "Muodostaa välilehden data-cy-ankkurin. Map-otsikon :data-cy voittaa.
  Taso-luokka (esim. tabs-taso1) lisätään ankkurin alkuun."
  [title classes]
  (or (when (map? title) (:data-cy title))
      (let [tabs-taso (re-find #"tabs-taso\d" (str classes))
            teksti (if (map? title) (:teksti title) title)
            cy-title (-> teksti
                         str
                         (str/replace #"ä" "a")
                         (str/replace #"ö" "o"))]
        (if tabs-taso
          (str tabs-taso "-" cy-title)
          cy-title))))
