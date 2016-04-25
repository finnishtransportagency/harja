(ns harja.tiedot.urakka.yllapito
  "Päällystyksen ja paikkauksen tiedot"
  (:require
    [harja.ui.yleiset :refer [ajax-loader linkki raksiboksi
                              livi-pudotusvalikko]]
    [harja.tiedot.muokkauslukko :as lukko]
    [harja.loki :refer [log tarkkaile!]]
    [harja.domain.paallystys.paallystys-ja-paikkaus-yhteiset :as yhteiset-cljc]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defonce paallystys-tai-paikkauskohteet-nakymassa (atom false))
(defonce paallystysilmoitukset-nakymassa? (atom false))


(defn hae-paallystyskohteet [urakka-id sopimus-id]
  (k/post! :urakan-yllapitokohteet {:urakka-id  urakka-id
                                    :sopimus-id sopimus-id}))

(defn hae-paallystyskohdeosat [urakka-id sopimus-id paallystyskohde-id]
  (k/post! :urakan-yllapitokohdeosat {:urakka-id          urakka-id
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
  (k/post! :tallenna-yllapitokohdeosat {:urakka-id          urakka-id
                                        :sopimus-id         sopimus-id
                                        :paallystyskohde-id paallystyskohde-id
                                        :osat               osat}))

(def paallystyskohderivit
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               [valittu-sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @paallystys-tai-paikkauskohteet-nakymassa]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (hae-paallystyskohteet valittu-urakka-id valittu-sopimus-id))))

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

(defonce paallystystoteumat
         (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
                      [valittu-sopimus-id _] @u/valittu-sopimusnumero
                      nakymassa? @paallystysilmoitukset-nakymassa?]
                     {:nil-kun-haku-kaynnissa? true}
                     (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                       (hae-paallystystoteumat valittu-urakka-id valittu-sopimus-id))))

(defonce paallystysilmoitus-lomakedata (atom nil))          ; Vastaa rakenteeltaan päällystysilmoitus-taulun sisältöä

(defonce paallystyskohteet-kartalla
         (reaction (let [taso @karttataso-paallystyskohteet
                         kohderivit @paallystyskohderivit
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

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :valmis "Valmis"
    :aloitettu "Aloitettu"
    "Ei aloitettu"))


(def lomake-lukittu-muokkaukselta? (reaction (let [_ @lukko/nykyinen-lukko]
                                               (lukko/nykyinen-nakyma-lukittu?))))

(defn lomake-lukittu-huomautus
  [nykyinen-lukko]
  [:div.lomake-lukittu-huomautus
   (harja.ui.ikonit/livicon-info-sign) (str " Lomakkeen muokkaaminen on estetty, sillä toinen käyttäjä"
                                            (when (and (:etunimi nykyinen-lukko)
                                               (:sukunimi nykyinen-lukko))
                                      (str " (" (:etunimi nykyinen-lukko) " " (:sukunimi nykyinen-lukko) ")"))
                                            " muokkaa parhaillaan lomaketta. Yritä hetken kuluttua uudelleen.")])

(defn nayta-paatos [tila]
  (case tila
    :hyvaksytty [:span.ilmoitus-hyvaksytty (yhteiset-cljc/kuvaile-paatostyyppi tila)]
    :hylatty [:span.ilmoitus-hylatty (yhteiset-cljc/kuvaile-paatostyyppi tila)]
    ""))


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
