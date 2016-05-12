(ns harja.tiedot.urakka.yllapitokohteet
  "Ylläpitokohteiden tiedot"
  (:require
    [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
    [harja.loki :refer [log tarkkaile!]]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn hae-yllapitokohteet [urakka-id sopimus-id]
  (k/post! :urakan-yllapitokohteet {:urakka-id  urakka-id
                                    :sopimus-id sopimus-id}))

(defn tallenna-yllapitokohteet [urakka-id sopimus-id kohteet]
  (k/post! :tallenna-yllapitokohteet {:urakka-id  urakka-id
                                        :sopimus-id sopimus-id
                                        :kohteet    kohteet}))

(defn tallenna-yllapitokohdeosat [urakka-id sopimus-id yllapitokohde-id osat]
  (k/post! :tallenna-yllapitokohdeosat {:urakka-id          urakka-id
                                        :sopimus-id         sopimus-id
                                        :yllapitokohde-id yllapitokohde-id
                                        :osat               osat}))

(defn kuvaile-kohteen-tila [tila]
  (case tila
    :valmis "Valmis"
    :aloitettu "Aloitettu"
    "Ei aloitettu"))


(defn paivita-yllapitokohde! [kohteet-atom id funktio & argumentit]
  (swap! kohteet-atom
         (fn [kohderivit]
           (into []
                 (map (fn [kohderivi]
                        (if (= id (:id kohderivi))
                          (apply funktio kohderivi argumentit)
                          kohderivi)))
                 kohderivit))))