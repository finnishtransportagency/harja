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

(defn tabin-id
  "Muodostaa data-cy-ankkurista vakaan HTML-id:n."
  [title classes]
  (str "harja-tab-" (str/replace (tabin-data-cy title classes) #"[^A-Za-z0-9_-]" "-")))

(defn seuraava-tabin-indeksi
  "Palauttaa nuoli-, Home- tai End-näppäimen kohdeindeksin."
  [indeksi maara nappain]
  (when (pos? maara)
    (case nappain
      "ArrowRight" (mod (inc indeksi) maara)
      "ArrowLeft" (mod (dec indeksi) maara)
      "Home" 0
      "End" (dec maara)
      nil)))
