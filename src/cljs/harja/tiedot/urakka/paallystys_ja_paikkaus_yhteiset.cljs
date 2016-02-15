(ns harja.tiedot.urakka.paallystys-ja-paikkaus-yhteiset
  "Päällystyksen ja paikkauksen yhteisiä fronttijuttuja"
  (:require
    [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                              livi-pudotusvalikko]]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [harja.domain.paallystys.paallystys-ja-paikkaus-yhteiset :as yhteiset-cljc]
    [cljs.core.async :refer [<!]])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))


(def lomake-lukittu-muokkaukselta? (reaction (let [_ @lukko/nykyinen-lukko]
                                               (lukko/nykyinen-nakyma-lukittu?))))

(defn lomake-lukittu-huomautus
  [nykyinen-lukko]
  [:div.lomake-lukittu-huomautus
   (harja.ui.ikonit/info-sign) (str " Lomakkeen muokkaaminen on estetty, sillä toinen käyttäjä"
                                    (when (and (:etunimi nykyinen-lukko)
                                               (:sukunimi nykyinen-lukko))
                                      (str " (" (:etunimi nykyinen-lukko) " " (:sukunimi nykyinen-lukko) ")"))
                                    " muokkaa parhaillaan lomaketta. Yritä hetken kuluttua uudelleen.")])

(defn nayta-paatos [tila]
  (case tila
    :hyvaksytty [:span.ilmoitus-hyvaksytty (yhteiset-cljc/kuvaile-paatostyyppi tila)]
    :hylatty [:span.ilmoitus-hylatty (yhteiset-cljc/kuvaile-paatostyyppi tila)]
    ""))