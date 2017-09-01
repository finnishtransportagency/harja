(ns harja.views.tilannekuva.tilannekuva-jaettu
  (:require [harja.ui.modal :as modal]
            [harja.views.ilmoituskuittaukset :as ik])
  (:require-macros [harja.tyokalut.ui :refer [for*]]))

(defn nayta-kuittausten-tiedot
  [kuittaukset]
  (modal/nayta! {:otsikko "Kuittaukset"}
                (for* [kuittaus kuittaukset
                       :when (not= :valitys (:kuittaustyyppi kuittaus))]
                  (let [kuittaus (update kuittaus :kanava #(keyword %))]
                    (ik/kuittauksen-tiedot kuittaus)))))
