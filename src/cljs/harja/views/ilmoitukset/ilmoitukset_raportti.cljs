(ns harja.views.ilmoitukset.ilmoitukset-raportti
  "Tieliikenneilmoituksien raportti"
  (:require [reagent.core :refer [atom] :as r]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.raportit :as raportit]
            [harja.ui.upotettu-raportti :as upotettu-raportti]
            [harja.ui.raportti :refer [muodosta-html]]
            [harja.ui.yleiset :as yleiset])

  (:require-macros [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(def filtterit (atom {}))
(def ilmoitukset (atom {}))

(defonce raportin-parametrit
  (reaction
   (let [ur @nav/valittu-urakka
         valinnat (:valinnat @filtterit)
         alkuaika (:valitetty-urakkaan-alkuaika valinnat)]

     (raportit/urakkaraportin-parametrit
      (:id ur)
      :ilmoitukset-raportti
      {:urakka @nav/valittu-urakka
       :hallintayksikko @nav/valittu-hallintayksikko
       :tiedot @ilmoitukset
       :filtterit @filtterit
       :alkupvm alkuaika
       :urakkatyyppi (:tyyppi ur)}))))

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


(defn ilmoitukset_raportti [haetut-ilmoitukset valitut-filtterit]
  (reset! ilmoitukset haetut-ilmoitukset)
  (reset! filtterit valitut-filtterit)

  (when-let [p @raportin-parametrit]
    [:div
     [upotettu-raportti/raportin-vientimuodot p]
     (suorita-raportti :ilmoitukset-raportti)]))
