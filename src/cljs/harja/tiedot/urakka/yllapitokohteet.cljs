(ns harja.tiedot.urakka.yllapitokohteet
  "Ylläpitokohteiden tiedot"
  (:require
    [harja.ui.yleiset :refer [ajax-loader linkki livi-pudotusvalikko]]
    [harja.loki :refer [log tarkkaile!]]
    [cljs.core.async :refer [<!]]
    [harja.asiakas.kommunikaatio :as k]
    [harja.tiedot.urakka :as urakka])

  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go]]
                   [harja.atom :refer [reaction<!]]))

(defn hae-yllapitokohteet [urakka-id sopimus-id]
  (k/post! :urakan-yllapitokohteet {:urakka-id urakka-id
                                    :sopimus-id sopimus-id}))

(defn tallenna-yllapitokohteet! [urakka-id sopimus-id kohteet]
  (k/post! :tallenna-yllapitokohteet {:urakka-id urakka-id
                                      :sopimus-id sopimus-id
                                      :kohteet kohteet}))

(defn tallenna-yllapitokohdeosat! [urakka-id sopimus-id yllapitokohde-id osat]
  (k/post! :tallenna-yllapitokohdeosat {:urakka-id urakka-id
                                        :sopimus-id sopimus-id
                                        :yllapitokohde-id yllapitokohde-id
                                        :osat osat}))

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

(defn yha-kohde? [kohde]
  (some? (:yhaid kohde)))

(defn kasittele-paivittyneet-kohdeosat [kohteet]
  (let [uudet-kohteet
        ;; Kopioi kohteen N loppuosa kohtee N + 1 alkuosaksi
        ; FIXME Pitäisi tunnistaa kumpaa muokattiin, jotta kopiointi toimii myös toiseen suuntaan
        (into [] (map-indexed
                   (fn [index kohde]
                     (if (< index (- (count kohteet) 1))
                       (-> kohde
                           (assoc :tr-loppuosa (:tr-alkuosa (get kohteet (inc index))))
                           (assoc :tr-loppuetaisyys (:tr-alkuetaisyys (get kohteet (inc index)))))
                       kohde))
                   kohteet))]
    uudet-kohteet))

(defn lisaa-uusi-kohdeosa
  "Lisää uuden kohteen annetussa indeksissä olevan kohteen perään (alapuolelle)."
  [kohteet index]
  (let [uudet-kohteet (into [] (concat
                                 (take (inc index) kohteet)
                                 [{:nimi ""
                                   :tr-numero (:tr-numero (get kohteet index))
                                   :tr-alkuosa nil
                                   :tr-alkuetaisyys nil
                                   :tr-loppuosa (:tr-loppuosa (get kohteet index))
                                   :tr-loppuetaisyys (:tr-loppuetaisyys (get kohteet index))
                                   :toimenpide ""}]
                                 (drop (inc index) kohteet)))
        uudet-kohteet (assoc uudet-kohteet index (-> (get uudet-kohteet index)
                                                     (assoc :tr-loppuosa nil)
                                                     (assoc :tr-loppuetaisyys nil)))]
    uudet-kohteet))

(defn poista-kohdeosa
  "Poistaa valitun kohdeosan annetusta indeksistä. Pidentää edellistä kohdeosaa niin, että sen pituus täyttää
   poistetun kohdeosan jättämän alueen. Jos poistetaan ensimmäinen kohdeosa, pidennetään vastaavasti seuraava."
  [kohteet index]
  (let [uudet-kohteet (if (>= index 1)
                        (-> kohteet
                            (assoc (dec index)
                                   (-> (get kohteet (dec index))
                                       (assoc :tr-loppuosa (:tr-loppuosa (get kohteet index)))
                                       (assoc :tr-loppuetaisyys (:tr-loppuetaisyys (get kohteet index)))))
                            (assoc index nil))
                        ;; Poistetaan ensimmäinen kohdeosa
                        (-> kohteet
                            (assoc (inc index)
                                   (-> (get kohteet (inc index))
                                       (assoc :tr-alkuosa (:tr-alkuosa (get kohteet index)))
                                       (assoc :tr-alkuetaisyys (:tr-alkuetaisyys (get kohteet index)))))
                            (assoc index nil)))
        uudet-kohteet (remove nil? uudet-kohteet)]
    uudet-kohteet))