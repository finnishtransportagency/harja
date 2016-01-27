(ns harja.tiedot.urakka.paikkaus
  "Tämä nimiavaruus hallinnoi urakan paikkaustietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log tarkkaile!]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u]
            [harja.ui.kartta.esitettavat-asiat :refer [kartalla-esitettavaan-muotoon]])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defonce paikkausilmoitukset-nakymassa? (atom false))

(defn hae-paikkaustoteumat [urakka-id sopimus-id]
  (k/post! :urakan-paikkaustoteumat {:urakka-id  urakka-id
                                     :sopimus-id sopimus-id}))

(defn hae-paikkausilmoitus-paikkauskohteella [urakka-id sopimus-id paikkauskohde-id]
  (k/post! :urakan-paikkausilmoitus-paikkauskohteella {:urakka-id        urakka-id
                                                       :sopimus-id       sopimus-id
                                                       :paikkauskohde-id paikkauskohde-id}))

(defn tallenna-paikkausilmoitus [urakka-id sopimus-id lomakedata]
  (k/post! :tallenna-paikkausilmoitus {:urakka-id        urakka-id
                                       :sopimus-id       sopimus-id
                                       :paikkausilmoitus lomakedata}))

(defonce karttataso-paikkauskohteet (atom false))

(defonce paikkaustoteumat
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               [valittu-sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @paikkausilmoitukset-nakymassa?]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (hae-paikkaustoteumat valittu-urakka-id valittu-sopimus-id))))

(tarkkaile! "Paikkaustoteumat: " paikkaustoteumat)

(defonce paikkausilmoitus-lomakedata (atom nil))            ; Vastaa rakenteeltaan paikkausilmoitus-taulun sisältöä

(defonce paikkauskohteet-kartalla
         (reaction (let [taso @karttataso-paikkauskohteet
                         kohderivit @paallystys/paallystyskohderivit
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

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :valmis "Valmis"
    :aloitettu "Aloitettu"
    "Ei aloitettu"))
