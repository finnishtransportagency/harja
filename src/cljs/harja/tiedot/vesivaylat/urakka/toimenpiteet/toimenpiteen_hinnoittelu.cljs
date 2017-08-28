(ns harja.tiedot.vesivaylat.urakka.toimenpiteet.toimenpiteen-hinnoittelu
  (:require [reagent.core :refer [atom]]
            [harja.tiedot.vesivaylat.urakka.toimenpiteet.yksikkohintaiset :as tiedot]
            [harja.loki :refer [log error]]
            [harja.fmt :as fmt])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

;; Toimenpiteen hinnoittelun pudotusvalikkoon protokolla ja eri tyypit.

(defprotocol ValintaRivi
  (valitse [this e! index])
  (formatoi [this])
  (ryhmittely [this]))

(defrecord HintaValinta [nimi] ValintaRivi
  (valitse [this e! index]
    (e! (tiedot/->AsetaTyorivilleTiedot
          {:index index
           :toimenpidekoodi-id nil
           :hinta-nimi (:nimi this)})))

  (formatoi [this] (:nimi this))

  (ryhmittely [_] :hinta))

(defrecord TyoValinta [nimi yksikko toimenpidekoodi-id yksikkohinta]
  ValintaRivi

  (valitse [this e! index]
    (e! (tiedot/->AsetaTyorivilleTiedot
          {:index index
           :toimenpidekoodi-id (:toimenpidekoodi-id this)
           :hinta-nimi nil})))

  (formatoi [this]
    (str (:nimi this) " (" (fmt/euro (:yksikkohinta this))
         " / " (:yksikko this) ")"))

  (ryhmittely [_] :tyo))

(defn suunniteltu-tyo->Record [suunniteltu-tyo]
  (->TyoValinta (:tehtavan_nimi suunniteltu-tyo)
                (:yksikko suunniteltu-tyo)
                (:tehtava suunniteltu-tyo)
                (:yksikkohinta suunniteltu-tyo)))