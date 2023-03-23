(ns harja.views.ilmoitukset.ilmoitukset-raportti
  "Tieliikenneilmoituksien raportti"
  (:require [harja.tiedot.ilmoitukset.tieliikenneilmoitukset :as tiedot]
            [harja.tiedot.ilmoitukset.tietyoilmoitukset :as tietyoilmoitukset-tiedot]

            [reagent.core :refer [atom] :as r]
            [cljs.core.async :refer [<! >! chan]]

            [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
            [harja.ui.komponentti :as komp]
            [harja.ui.ikonit :as ikonit]
            [harja.ui.yleiset :refer [vihje]]
            [harja.tiedot.raportit :as raportit]
            [harja.views.raportit :as raportit-ui]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.ui.valinnat :as ui-valinnat]
            [harja.views.urakka.valinnat :as valinnat]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.ui.yleiset :as yleiset])
  

  (:require-macros [harja.atom :refer [reaction<! reaction-writable]]
                   [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction run!]]
                   [harja.atom :refer [reaction<!]]))

(defonce raportin-parametrit
  (reaction
   (raportit/urakkaraportin-parametrit
    nil
    :ilmoitukset-raportti
    {:test "test"})))


(defonce raportin-tiedot
  (reaction<! [p @raportin-parametrit]
              {:nil-kun-haku-kaynnissa? true}
              (when p
                (raportit/suorita-raportti p))))


(defn suorita-raportti [raportin-avain]
  (if-let [tiedot @raportin-tiedot]
    [muodosta-html
     (-> tiedot
         (assoc-in [1 :tunniste] raportin-avain))]
    [yleiset/ajax-loader "Raporttia suoritetaan..."]))


(defn ilmoitukset_raportti []
  (when-let [p @raportin-parametrit]
    [:div
     [upotettu-raportti/raportin-vientimuodot p]
     (suorita-raportti :ilmoitukset-raportti)]))


