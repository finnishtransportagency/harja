(ns harja.ui.grid
  "Gridin ja muokkausgridin koottu rajapinta."
  (:require [reagent.core :refer [atom] :as r]
            [harja.loki :refer [log tarkkaile! logt] :refer-macros [mittaa-aika]]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.kentat :refer [tee-kentta nayta-arvo vain-luku-atomina]]
            [harja.ui.grid.perus :as perus]
            [harja.ui.grid.muokkaus :as muokkaus]
            [harja.ui.grid.protokollat :as protokollat]
            [cljs.core.async :refer [<! put! chan]]
            [schema.core :as s :include-macros true]
            [harja.ui.ikonit :as ikonit]
            [cljs-time.core :as t])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]
                   [harja.makrot :refer [fnc]]))

(def gridia-muokataan? protokollat/gridia-muokataan?)
(def grid-ohjaus protokollat/grid-ohjaus)
(def otsikko protokollat/otsikko)

(def grid perus/grid)
(def muokkaus-grid muokkaus/muokkaus-grid)