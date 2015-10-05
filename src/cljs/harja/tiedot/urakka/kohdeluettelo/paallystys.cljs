(ns harja.tiedot.urakka.kohdeluettelo.paallystys
  "Tämä nimiavaruus hallinnoi urakan päällystystietoja."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.asiakas.tapahtumat :as t]
            [cljs.core.async :refer [<! >! chan]]
            [harja.loki :refer [log logt]]
            [harja.pvm :as pvm]
            [harja.ui.protokollat :refer [Haku hae]]
            [harja.tiedot.navigaatio :as nav]
            [harja.loki :refer [log tarkkaile!]]
            [harja.tiedot.urakka :as u]
            [harja.tiedot.urakka.kohdeluettelo.paikkaus :as paikkaus])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]
                   [reagent.ratom :refer [reaction]]))

(defonce paallystysilmoitukset-nakymassa? (atom false))
(defonce paallystys-tai-paikkauskohteet-nakymassa (atom false))

(defn hae-paallystyskohteet [urakka-id sopimus-id]
  (k/post! :urakan-paallystyskohteet {:urakka-id  urakka-id
                                      :sopimus-id sopimus-id}))

(defn hae-paallystyskohdeosat [urakka-id sopimus-id paallystyskohde-id]
  (k/post! :urakan-paallystyskohdeosat {:urakka-id          urakka-id
                                        :sopimus-id         sopimus-id
                                        :paallystyskohde-id paallystyskohde-id}))

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

(defn tallenna-paallystyskohteet [urakka-id sopimus-id kohteet]
  (k/post! :tallenna-paallystyskohteet {:urakka-id  urakka-id
                                        :sopimus-id sopimus-id
                                        :kohteet    kohteet}))

(defn tallenna-paallystyskohdeosat [urakka-id sopimus-id paallystyskohde-id osat]
  (k/post! :tallenna-paallystyskohdeosat {:urakka-id          urakka-id
                                          :sopimus-id         sopimus-id
                                          :paallystyskohde-id paallystyskohde-id
                                          :osat               osat}))

(defonce paallystyskohderivit (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                           [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                           nakymassa? @paallystys-tai-paikkauskohteet-nakymassa]
                                          (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                            (log "PÄÄ Haetaan päällystyskohteet.")
                                            (hae-paallystyskohteet valittu-urakka-id valittu-sopimus-id))))
(tarkkaile! "Päällystyskohderivit: " paallystyskohderivit)

(defn paivita-kohde! [id funktio & argumentit]
  (swap! paallystyskohderivit
         (fn [kohderivit]
           (into []
                 (map (fn [kohderivi]
                        (if (= id (:id kohderivi))
                          (apply funktio kohderivi argumentit)
                          kohderivi)))
                 kohderivit))))

(defonce karttataso-paallystyskohteet (atom false))
(defonce karttataso-paikkauskohteet (atom false))

(defonce paallystystoteumat (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                         [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                         nakymassa? @paallystysilmoitukset-nakymassa?]
                                        (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                          (log "PÄÄ Haetaan päällystystoteumat.")
                                          (hae-paallystystoteumat valittu-urakka-id valittu-sopimus-id))))
(tarkkaile! "Päällystysilmoitukset: " paallystystoteumat)
(defonce paikkaustoteumat (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                                       [valittu-sopimus-id _] @u/valittu-sopimusnumero
                                       nakymassa? @paikkaus/paikkausilmoitukset-nakymassa]
                                      (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                                        (log "PAI Haetaan paikkausilmoitukset")
                                        (paikkaus/hae-paikkaustoteumat valittu-urakka-id valittu-sopimus-id))))
(tarkkaile! "Paikkaustoteumat: " paikkaustoteumat)

(defonce paallystysilmoitus-lomakedata (atom nil))          ; Vastaa rakenteeltaan päällystysilmoitus-taulun sisältöä
(defonce paikkausilmoitus-lomakedata (atom nil))            ; Vastaa rakenteeltaan paikkausilmoitus-taulun sisältöä

(defonce paallystyskohteet-kartalla
         (reaction (let [taso @karttataso-paallystyskohteet
                         kohderivit @paallystyskohderivit
                         toteumarivit @paallystystoteumat
                         avoin-paallystysilmoitus (:paallystyskohde-id @paallystysilmoitus-lomakedata)]
                     (when (and taso
                                (or kohderivit toteumarivit))
                       (log "Asetetaan päällystyskohteet kartalle")
                       (log "Kohderivit: " (pr-str kohderivit))
                       (log "Toteumarivit: " (pr-str toteumarivit))
                       (into []
                             (mapcat #(keep (fn [{sij :sijainti nimi :nimi :as osa}]
                                              (when sij
                                                (let [paallystyskohde-id (:paallystyskohde_id %)]
                                                  {:type               :paallystyskohde
                                                   :kohde              %
                                                   :paallystyskohde-id paallystyskohde-id
                                                   :tila               (or (:paallystysilmoitus_tila %) (:tila %)) ; Eri keywordissa lähetetystä pyynnöstä riippuen
                                                   :nimi               (str (:nimi %) ": " nimi)
                                                   :osa                osa
                                                   :alue               (assoc sij
                                                                         :stroke {:color (case (or (:paallystysilmoitus_tila %) (:tila %))
                                                                                           :aloitettu "blue"
                                                                                           :valmis "green"
                                                                                           "orange")
                                                                                  :width (if (= paallystyskohde-id avoin-paallystysilmoitus) 8 6)})})))
                                            (:kohdeosat %)))
                             (concat (map #(assoc % :paallystyskohde_id (:id %)) ;; yhtenäistä id kohde ja toteumariveille
                                          kohderivit)
                                     toteumarivit))))))

(defonce paikkauskohteet-kartalla
         (reaction (let [taso @karttataso-paikkauskohteet
                         kohderivit @paallystyskohderivit
                         toteumarivit @paikkaustoteumat
                         avoin-paikkausilmoitus (:paikkauskohde-id @paikkausilmoitus-lomakedata)]
                     (when (and taso
                                (or kohderivit toteumarivit))
                       (log "Asetetaan paikkauskohteet kartalle")
                       (log "Kohderivit: " (pr-str kohderivit))
                       (log "Toteumarivit: " (pr-str toteumarivit))
                       (into []
                             (mapcat #(keep (fn [{sij :sijainti nimi :nimi :as osa}]
                                              (when sij
                                                (let [paikkauskohde-id (:paikkauskohde_id %)]
                                                  {:type             :paikkauskohde
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