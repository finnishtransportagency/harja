(ns harja.tiedot.urakka.paikkaus
  "Paikkauksen tiedot"
  (:require
    [reagent.core :refer [atom] :as r]
    [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.urakka.yllapitokohteet :as yllapitokohteet]
    [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]]
    [harja.tiedot.urakka :as u]
    [harja.tiedot.navigaatio :as nav]
    [harja.tiedot.urakka :as urakka])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce paikkauskohteet-nakymassa? (atom false))
(defonce paikkausilmoitukset-nakymassa? (atom false))

(defn hae-paikkaustoteumat [urakka-id sopimus-id]
  (k/post! :urakan-paikkaustoteumat {:urakka-id  urakka-id
                                     :sopimus-id sopimus-id}))

(defn hae-paikkausilmoitus-paikkauskohteella [urakka-id sopimus-id paikkauskohde-id]
  (k/post! :urakan-paikkausilmoitus-paikkauskohteella {:urakka-id        urakka-id
                                                       :sopimus-id       sopimus-id
                                                       :paikkauskohde-id paikkauskohde-id}))

(defn tallenna-paikkausilmoitus! [urakka-id sopimus-id lomakedata]
  (urakka/lukitse-valitun-urakan-yha-sidonta!)
  (k/post! :tallenna-paikkausilmoitus {:urakka-id        urakka-id
                                       :sopimus-id       sopimus-id
                                       :paikkausilmoitus lomakedata}))

(def paikkausilmoituslomake-lukittu? (reaction (let [_ @lukko/nykyinen-lukko]
                                               (lukko/nykyinen-nakyma-lukittu?))))

(defonce karttataso-paikkauskohteet (atom false))

(defonce paikkaustoteumat
         (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                      [valittu-sopimus-id _] @u/valittu-sopimusnumero
                      nakymassa? @paikkausilmoitukset-nakymassa?]
                     {:nil-kun-haku-kaynnissa? true}
                     (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                       (hae-paikkaustoteumat valittu-urakka-id valittu-sopimus-id))))

(defonce paikkausilmoitus-lomakedata (atom nil)) ; Vastaa rakenteeltaan paikkausilmoitus-taulun sisältöä

(def paikkauskohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               [valittu-sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @paikkauskohteet-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (yllapitokohteet/hae-yllapitokohteet valittu-urakka-id valittu-sopimus-id))))

(defonce paikkauskohteet-kartalla
         (reaction (let [taso @karttataso-paikkauskohteet
                         kohderivit @paikkauskohteet
                         toteumarivit @paikkaustoteumat
                         avoin-paikkausilmoitus (:paikkauskohde-id @paikkausilmoitus-lomakedata)]
                     (when (and taso
                                (or kohderivit toteumarivit))
                       (kartalla-esitettavaan-muotoon
                         (concat (map #(assoc % :paikkauskohde_id (:id %)) ;; yhtenäistä id kohde ja toteumariveille
                                      kohderivit)
                                 toteumarivit)
                         @paikkausilmoitus-lomakedata
                         [:paikkauskohde_id]
                         (comp
                           (mapcat (fn [kohde]
                                     (keep (fn [kohdeosa]
                                             (assoc (merge kohdeosa
                                                           (dissoc kohde :kohdeosat))
                                               :tila (or (:paikkausilmoitus_tila kohde) (:tila kohde))
                                               :avoin? (= (:paikkauskohde_id kohde) avoin-paikkausilmoitus)
                                               :osa kohdeosa ;; Redundanttia, tarvitaanko tosiaan?
                                               :nimi (str (:nimi kohde) ": " (:nimi kohdeosa))))
                                           (:kohdeosat kohde))))
                           (keep #(and (:sijainti %) %))
                           (map #(assoc % :tyyppi-kartalla :paikkaus))))))))
