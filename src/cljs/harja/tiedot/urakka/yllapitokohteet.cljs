(ns harja.tiedot.urakka.yllapitokohteet
  "Ylläpitokohteiden tiedot"
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

(defn hae-yllapitokohteet [urakka-id sopimus-id]
  (k/post! :urakan-yllapitokohteet {:urakka-id  urakka-id
                                    :sopimus-id sopimus-id}))

(defn hae-yllapitokohdeosat [urakka-id sopimus-id yllapitokohde-id]
  (k/post! :urakan-yllapitokohdeosat {:urakka-id          urakka-id
                                      :sopimus-id         sopimus-id
                                      :yllapitokohde-id   yllapitokohde-id}))

(defn tallenna-yllapitokohteet [urakka-id sopimus-id kohteet]
  (k/post! :tallenna-yllapitokohteet {:urakka-id  urakka-id
                                        :sopimus-id sopimus-id
                                        :kohteet    kohteet}))

(defn tallenna-yllapitokohdeosat [urakka-id sopimus-id paallystyskohde-id osat]
  (k/post! :tallenna-yllapitokohdeosat {:urakka-id          urakka-id
                                        :sopimus-id         sopimus-id
                                        :paallystyskohde-id paallystyskohde-id
                                        :osat               osat}))

(def yllapitokohteet
  (reaction<! [valittu-urakka-id (:id @nav/valittu-urakka)
               [valittu-sopimus-id _] @u/valittu-sopimusnumero
               nakymassa? @paallystys-tai-paikkauskohteet-nakymassa]
              {:nil-kun-haku-kaynnissa? true}
              (when (and valittu-urakka-id valittu-sopimus-id nakymassa?)
                (hae-yllapitokohteet valittu-urakka-id valittu-sopimus-id))))

(defonce yllapitokohteet-kartalla
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

(defn paivita-yllapitokohde! [id funktio & argumentit]
  (swap! yllapitokohteet
         (fn [kohderivit]
           (into []
                 (map (fn [kohderivi]
                        (if (= id (:id kohderivi))
                          (apply funktio kohderivi argumentit)
                          kohderivi)))
                 kohderivit))))

(defonce karttataso-yllapitokohteet (atom false))

(defn kuvaile-yllapitokohteen-tila [tila]
  (case tila
    :valmis "Valmis"
    :aloitettu "Aloitettu"
    "Ei aloitettu"))