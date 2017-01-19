(ns harja.ui.img-with-exif
  (:require [reagent.core :refer [atom]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [cljsjs.exif]
            [harja.loki :refer [log]]

            [cljs.core.async :refer [<!]]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- maarita-kuvan-luokat [optiot exif-orientaatio]
  (if exif-orientaatio
    (assoc optiot :class
                  (str (:class optiot) " " "exif-" exif-orientaatio))
    (assoc optiot :class
                  (str (:class optiot) " " "ladataan-exif-kuvaa"))))

(defn img-with-exif
  "Luo <img> kuvan, joka käännetään oikeaan orientaatioon lukemalla orientaatio kuvan EXIF-metadatasta
   ja asettamalla sitä vastaava CSS-luokka.

   Kuvan latauksen aikana näytetään ajax-loader, jottei latauksessa oleva kuva tule ensin ruudulle
   virheellisellä orientaatiolla.

   Optiot on mappi, joka annetaan <img> elementille. Kuvalla ei saa olla id:tä, sillä tämä komponentti
   luo kuvalle oman id:n."
  [{:keys [on-load] :as optiot}]
  (let [komponentti-id (hash (str optiot (t/now)))
        exif-orientaatio (atom nil)
        exif-optiot
        {:id komponentti-id
         :on-load
         (fn []
           (when on-load
             ;; Kutsu optioissa määriteltyä on-load eventtiä, jos sellainen annettiin
             (on-load))
           (let [kuva-node (.getElementById js/document komponentti-id)
                 kasittele-exif-vastaus
                 (fn [_] (when-let [orientaatio (.getTag js/EXIF (js-this) "Orientation")]
                           (reset! exif-orientaatio orientaatio)))]
             (.getData js/EXIF kuva-node kasittele-exif-vastaus)))}]
    (fn [optiot]
      (let [lopulliset-optiot (merge optiot exif-optiot)
            lopulliset-optiot (maarita-kuvan-luokat lopulliset-optiot @exif-orientaatio)]
        [:span
         (when-not @exif-orientaatio
           [ajax-loader])
         [:img lopulliset-optiot]]))))