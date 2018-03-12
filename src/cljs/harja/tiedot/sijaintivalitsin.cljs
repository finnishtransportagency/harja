(ns harja.tiedot.sijaintivalitsin
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log logt tarkkaile!]]
            [harja.ui.kartta.ikonit :as kartta-ikonit]
            [harja.ui.kartta.esitettavat-asiat :refer [maarittele-feature]]
            [harja.ui.kartta.varit.puhtaat :as puhtaat]
            [harja.ui.kartta.asioiden-ulkoasu :as asioiden-ulkoasu])
  (:require-macros
    [reagent.ratom :refer [reaction run!]]
    [cljs.core.async.macros :refer [go]]))

(def karttataso-sijainti (atom false))
(def valittu-sijainti (atom nil))

(def sijainti-kartalla
  (reaction
    (when @karttataso-sijainti
      (let [feature (maarittele-feature @valittu-sijainti true (kartta-ikonit/pinni-ikoni "musta"))]
        ;; Jos feature on nil, ei haluta näyttää mitään ja [{:alue nil}] aiheuttaa erroria luo-geometria funktiossa
        (when feature
          [{:alue feature}])))))
