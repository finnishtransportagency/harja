(ns harja.tiedot.urakka.toteumat.muut-materiaalit
  "Tämä nimiavaruus hallinnoi urakan toteumien muut materiaalit näkymän tietoja."
  (:require [harja.asiakas.kommunikaatio :as k]
            [reagent.core :refer [atom]]))

(def muut-materiaalit-toteuma-nakymassa? (atom true))

(def valitun-materiaalitoteuman-tiedot (atom nil))

(defn tallenna-toteuma-ja-toteumamateriaalit! [toteuma toteumamateriaalit hoitokausi sopimus-id]
  (k/post! :tallenna-toteuma-ja-toteumamateriaalit {:toteuma toteuma
                                                    :toteumamateriaalit toteumamateriaalit
                                                    :hoitokausi hoitokausi
                                                    :sopimus sopimus-id}))
