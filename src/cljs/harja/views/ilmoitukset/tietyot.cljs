(ns harja.views.ilmoitukset.tietyot
  (:require [reagent.core :refer [atom] :as r]
            [harja.atom :refer [paivita-periodisesti] :refer-macros [reaction<!]]
            [harja.tiedot.ilmoitukset.tietyot :as tiedot]
            [harja.ui.bootstrap :as bs]
            [harja.ui.komponentti :as komp]
            [harja.ui.grid :refer [grid]]
            [harja.ui.yleiset :refer [ajax-loader] :as yleiset]
            [harja.ui.kentat :refer [tee-kentta]]
            [harja.ui.napit :refer [palvelinkutsu-nappi] :as napit]
            [harja.ui.valinnat :refer [urakan-hoitokausi-ja-aikavali]]
            [harja.ui.lomake :as lomake]
            [harja.ui.protokollat :as protokollat]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.istunto :as istunto]
            [harja.fmt :as fmt]
            [harja.tiedot.navigaatio :as nav]
            [harja.pvm :as pvm]
            [harja.views.kartta :as kartta]
            [harja.ui.ikonit :as ikonit]
            [harja.domain.tierekisteri :as tr-domain]
            [harja.ui.valinnat :as valinnat]
            [harja.ui.notifikaatiot :as notifikaatiot]
            [tuck.core :refer [tuck send-value! send-async!]]
            [harja.ui.kentat :as kentat]
            [harja.domain.oikeudet :as oikeudet]
            [harja.tiedot.kartta :as kartta-tiedot])
  (:require-macros [cljs.core.async.macros :refer [go]]))



(defn tietyoilmoitukset []
  (log "z 1")
  (fn []
    (log "z 2")
    [:div "terve"]))
