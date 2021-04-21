(ns harja.ui.kartta.varit.alpha
  (:require [harja.ui.kartta.varit :refer [rgba]]
            [harja.ui.kartta.varit.puhtaat :as core]))

(def punainen (rgba 255 0 0 0.7))
(def oranssi (rgba 255 128 0 0.7))
(def keltainen (rgba 255 255 0 0.7))
(def magenta (rgba 255 0 255 0.7))
(def vihrea (rgba 0 255 0 0.7))
(def turkoosi (rgba 0 255 128 0.7))
(def syaani (rgba 0 255 255 0.7))
(def sininen (rgba 0 128 255 0.7))
(def tummansininen (rgba 0 0 255 0.7))
(def violetti (rgba 128 0 255 0.7))
(def pinkki (rgba 255 0 128 0.7))
(def lime (rgba 128 255 0 0.7))

(def musta (rgba 0 0 0 0.7))
(def musta-raja (rgba 0 0 0 0.4))
(def valkoinen (rgba 255 255 255 0.7))
(def vaaleanharmaa (rgba 242 242 242 0.7))
(def harmaa (rgba 140 140 140 0.7))
(def tummanharmaa (rgba 77 77 77 0.7))

(def kaikki
  ^{:doc   "Vektori joka sisältää kaikki namespacen värit. Joudutaan valitettavasti rakentamaan
          käsin, koska .cljs puolelta puuttuu tarvittavat työkalut tämän luomiseen."
    :const true}
  [punainen oranssi keltainen magenta vihrea turkoosi syaani sininen
    tummansininen violetti lime pinkki])

#?(:clj (core/varmenna-sisalto 'harja.ui.kartta.varit.alpha))
