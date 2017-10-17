(ns harja.views.vesivaylat.hallinta.alukset
  "Näkymä vesiväyläurakoiden alusten hallitsemiseen."
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.vesivaylat.hallinta.alukset :as tiedot]
            [cljs.core.async :refer [<! >! timeout chan]]
            [harja.ui.yleiset :refer [ajax-loader]]
            [harja.ui.komponentti :as komp]
            [harja.ui.lomake :as lomake]
            [harja.ui.napit :as napit]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.debug :as debug]
            [harja.loki :refer [log]]
            [harja.ui.varmista-kayttajalta :as varmista-kayttajalta]))

(defn alukset* [e! tiedot]
  [:div "TODO"])

(defn alukset []
  [tuck tiedot/tila alukset*])


