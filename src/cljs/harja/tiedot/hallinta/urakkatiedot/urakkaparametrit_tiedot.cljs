(ns harja.tiedot.hallinta.urakkatiedot.urakkaparametrit-tiedot
  "Urakkaparametrien ui controlleri."
  (:require [reagent.core :refer [atom] :as reagent]
            [tuck.core :as tuck]
            [harja.tyokalut.tuck :as tuck-apurit]))

(def tila (atom nil))
(def nakymassa? (atom false))
