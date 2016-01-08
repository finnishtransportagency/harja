(ns harja.tiedot.urakka.paikkaus
  "Tämä nimiavaruus hallinnoi urakan paikkaustietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.tiedot.urakka.paallystys :as paallystys]
            [harja.tiedot.navigaatio :as nav]
            [harja.tiedot.urakka :as u])
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

(defonce paikkaustoteumat (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                       [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                       nakymassa? @paikkausilmoitukset-nakymassa?]
                                      (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                        (hae-paikkaustoteumat valittu-urakka-id valittu-sopimus-id))))

(defonce paikkausilmoitus-lomakedata (atom nil)) ; Vastaa rakenteeltaan paikkausilmoitus-taulun sisältöä

(defonce paikkauskohteet-kartalla
         (reaction (let [taso @karttataso-paikkauskohteet
                         kohderivit @paallystys/paallystyskohderivit
                         toteumarivit @paikkaustoteumat
                         avoin-paikkausilmoitus (:paikkauskohde-id @paikkausilmoitus-lomakedata)]
                     (when (and taso
                                (or kohderivit toteumarivit))
                       (into []
                             (mapcat #(keep (fn [{sij :sijainti nimi :nimi :as osa}]
                                              (when sij
                                                (let [paikkauskohde-id (:paikkauskohde_id %)]
                                                  {:type             :paikkaus
                                                   :kohde            %
                                                   :paikkauskohde-id paikkauskohde-id
                                                   :tila             (or (:paikkausilmoitus_tila %) (:tila %)) ; Eri keywordissa lähetetystä pyynnöstä riippuen
                                                   :nimi             (str (:nimi %) ": " nimi)
                                                   :osa              osa
                                                   :alue             (assoc sij
                                                                       :stroke {:color (case (or (:paikkausilmoitus_tila %) (:tila %))
                                                                                         :aloitettu "blue"
                                                                                         :valmis "green"
                                                                                         "orange")
                                                                                :width (if (= paikkauskohde-id avoin-paikkausilmoitus) 8 6)})})))
                                            (:kohdeosat %)))
                             (concat (map #(assoc % :paikkauskohde_id (:id %)) ;; yhtenäistä id kohde ja toteumariveille
                                          kohderivit)
                                     toteumarivit))))))

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :valmis "Valmis"
    :aloitettu "Aloitettu"
    "Ei aloitettu"))