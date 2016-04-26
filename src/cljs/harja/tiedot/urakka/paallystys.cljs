(ns harja.tiedot.urakka.paallystys
  "Päällystyksen tiedot"
  (:require
    [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                              livi-pudotusvalikko]]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [harja.domain.paallystys-ja-paikkaus :as paallystys-ja-paikkaus]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka :as u])

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

(defonce karttataso-paallystyskohteet (atom false))

(def paallystyskohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               [valittu-sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @paallystyskohteet-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (yllapitokohteet/hae-yllapitokohteet valittu-urakka-id valittu-sopimus-id))))

(defonce paallystyskohteet-kartalla
         (reaction (let [taso @karttataso-paallystyskohteet
                         kohderivit @paallystyskohteet
                         toteumarivit @paallystystoteumat
                         avoin-paallystysilmoitus (:paallystyskohde-id @paallystysilmoitus-lomakedata)]
                     (when (and taso
                                (or kohderivit toteumarivit))
                       (kartalla-esitettavaan-muotoon
                         (concat (map #(assoc % :paallystyskohde_id (:id %)) ;; yhtenäistä id kohde ja toteumariveille
                                      kohderivit)
                                 toteumarivit)
                         @paallystysilmoitus-lomakedata
                         [:paallystyskohde_id]
                         (comp
                           (mapcat (fn [kohde]
                                     (keep (fn [kohdeosa]
                                             (assoc (merge kohdeosa
                                                           (dissoc kohde :kohdeosat))
                                               :tila (or (:paallystysilmoitus_tila kohde) (:tila kohde))
                                               :avoin? (= (:paallystyskohde_id kohde) avoin-paallystysilmoitus)
                                               :osa kohdeosa ;; Redundanttia, tarvitaanko tosiaan?
                                               :nimi (str (:nimi kohde) ": " (:nimi kohdeosa))))
                                           (:kohdeosat kohde))))
                           (keep #(and (:sijainti %) %))
                           (map #(assoc % :tyyppi-kartalla :paallystys))))))))