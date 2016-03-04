(ns harja.ui.kartta.ikonit
  "Määrittelee kartan käyttämät ikonit alustariippumattomasti"
  #?(:cljs (:require [harja.ui.dom :refer [ie?]])))

(def ikonikansio #?(:cljs "images/tuplarajat/"
                    :clj "public/images/tuplarajat/"))

#?(:cljs
   (defn karttakuva [perusnimi]
     (str perusnimi (if ie? ".png" ".svg")))

   :clj
   (defn karttakuva [perusnimi]
     (str perusnimi ".png")))

(defn assertoi-ikonin-vari [vari]
  (assert #{"keltainen" "lime" "magenta" "musta" "oranssi" "pinkki"
            "punainen" "sininen" "syaani" "tummansininen" "turkoosi"
            "vihrea" "violetti"} vari))

(defn sijainti-ikoni
  "Oletukena palautetaan <vari-str> värinen sijainti-ikoni, jolla on musta reuna."
  ([vari-str] (sijainti-ikoni "musta" vari-str))
  ([tila-str vari-str]
   (assert (#{"vihrea" "punainen" "oranssi" "musta" "harmaa"} tila-str))
   (assertoi-ikonin-vari vari-str)
   (karttakuva (str ikonikansio"sijainnit/sijainti-"tila-str"-"vari-str))))

(defn nuoli-ikoni [vari-str]
  (assertoi-ikonin-vari vari-str)
  (karttakuva (str ikonikansio"nuolet/nuoli-"vari-str)))

(defn pinni-ikoni [vari-str]
  (assertoi-ikonin-vari vari-str)
  (karttakuva (str ikonikansio"pinnit/pinni-"vari-str)))
