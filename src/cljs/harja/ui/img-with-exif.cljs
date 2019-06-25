(ns harja.ui.img-with-exif
  (:require [reagent.core :refer [atom]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.loki :refer [log]]
            [harja.tiedot.exif :as exif]
            [cljs.core.async :refer [<!]]
            [cljs-time.core :as t]
            [harja.ui.komponentti :as komp]
            [reagent.core :as r])
  (:require-macros [cljs.core.async.macros :refer [go]]))

(defn- maarita-kuvan-luokat [optiot exif-orientaatio exif-luettu?]
  (if-not exif-luettu?
    (assoc optiot :class
                  (str (:class optiot) " " "ladataan-exif-kuvaa"))
    (if exif-orientaatio
     (assoc optiot :class
                   (str (:class optiot) " " "exif-" exif-orientaatio))
     optiot)))

(defn- kasittele-exif-tietojen-hakuvastaus! [exif-orientaatio-atom exif-luettu? exif-fn]
  (when-let [orientaatio (exif-fn "Orientation")]
    (reset! exif-orientaatio-atom orientaatio))
  (reset! exif-luettu? true))

(defn img-with-exif
  "Luo <img> kuvan, joka käännetään oikeaan orientaatioon lukemalla orientaatio kuvan EXIF-metadatasta
   ja asettamalla sitä vastaava CSS-luokka.

   Kuvan latauksen aikana näytetään ajax-loader, jottei latauksessa oleva kuva tule ensin ruudulle
   virheellisellä orientaatiolla.

   Optiot on mappi, joka annetaan <img> elementille."
  [{:keys [on-load] :as optiot}]
  ;; PENDING Joku kaunis päivä tätä komponenttia ei enää tarvita, vaan CSS:n
  ;; image-orientation toimii kaikkialla <3
  ;; Ks. http://caniuse.com/#feat=css-image-orientation
  (let [exif-orientaatio (atom nil)
        exif-luettu? (atom false)
        img-node (atom nil)
        exif-optiot {:on-load
                     (fn []
                       (when on-load
                         (on-load)) ;; Kutsu optioissa määriteltyä on-load eventtiä, jos sellainen annettiin
                       (exif/lue-kuvan-exif-tiedot
                         @img-node
                         (partial kasittele-exif-tietojen-hakuvastaus!
                                  exif-orientaatio
                                  exif-luettu?)))}]
    (komp/luo
      (komp/piirretty
        (fn [this]
          (reset! img-node (.-lastChild (r/dom-node this)))))
      (fn [optiot]
        (let [lopulliset-optiot (merge optiot exif-optiot)
              lopulliset-optiot (maarita-kuvan-luokat lopulliset-optiot @exif-orientaatio @exif-luettu?)]
          [:span
           (when-not @exif-luettu?
             [ajax-loader])
           [:img lopulliset-optiot]])))))
