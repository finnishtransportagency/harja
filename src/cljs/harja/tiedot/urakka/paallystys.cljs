(ns harja.tiedot.urakka.paallystys
  "Päällystyksen tiedot"
  (:require
    [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                              livi-pudotusvalikko]]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce paallystyskohteet-nakymassa? (atom false))
(defonce paallystysilmoitukset-nakymassa? (atom false))

(defn hae-paallystystoteumat [urakka-id sopimus-id]
  (k/post! :urakan-paallystystoteumat {:urakka-id  urakka-id
                                       :sopimus-id sopimus-id}))

(defn hae-paallystysilmoitus-paallystyskohteella [urakka-id sopimus-id paallystyskohde-id]
  (k/post! :urakan-paallystysilmoitus-paallystyskohteella {:urakka-id          urakka-id
                                                           :sopimus-id         sopimus-id
                                                           :paallystyskohde-id paallystyskohde-id}))

(defn tallenna-paallystysilmoitus [urakka-id sopimus-id lomakedata]
  (k/post! :tallenna-paallystysilmoitus {:urakka-id          urakka-id
                                         :sopimus-id         sopimus-id
                                         :paallystysilmoitus lomakedata}))

(defonce paallystystoteumat
         (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                      [valittu-sopimus-id _] @u/valittu-sopimusnumero
                      nakymassa? @paallystysilmoitukset-nakymassa?]
                     {:nil-kun-haku-kaynnissa? true}
                     (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                       (hae-paallystystoteumat valittu-urakka-id valittu-sopimus-id))))

(defonce paallystysilmoitus-lomakedata (atom nil)) ; Vastaa rakenteeltaan päällystysilmoitus-taulun sisältöä

(def paallystysilmoituslomake-lukittu? (reaction (let [_ @lukko/nykyinen-lukko]
                                               (lukko/nykyinen-nakyma-lukittu?))))

(defn nayta-paatos [tila]
  (case tila
    :hyvaksytty [:span.ilmoitus-hyvaksytty (paallystys-ja-paikkaus/kuvaile-paatostyyppi tila)]
    :hylatty [:span.ilmoitus-hylatty (paallystys-ja-paikkaus/kuvaile-paatostyyppi tila)]
    ""))