(ns harja.ui.img-with-exif
  (:require [reagent.core :refer [atom]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [cljsjs.exif]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn img-with-exif
  "Luo <img> kuvan, joka käännetään oikeaan orientaatioon
   lukemalla orientaatio kuvan EXIF-metadatasta ja
   asettamalla sitä vastaava CSS-luokka.

   Kuvalla ei saa olla id:tä, sillä tämä komponentti
   luo kuvalle oman id:n.

   Optiot on mappi, joka annetaan <img> elementille."
  [{:keys [on-load] :as optiot}]
  (let [komponentti-id (hash (str optiot (t/now)))
        exif-optiot
        {:id komponentti-id
         :on-load
         (fn []
           (log "Kuva ladattu")
           (when on-load
             (on-load))
           (let [kuva-dom (.getElementById js/document komponentti-id)]
             (log "Kuva domissa: " (pr-str kuva-dom))
             (log "EXIF: " (pr-str js/EXIF))))}]
    (fn [optiot]
      (let [lopulliset-optiot
            (merge
              optiot
              exif-optiot)]
        [:img lopulliset-optiot]))))