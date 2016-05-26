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

(def alku (juxt :tr-alkuosa :tr-alkuetaisyys))
(def loppu (juxt :tr-loppuosa :tr-loppuetaisyys))

(defn kasittele-paivittyneet-kohdeosat
  "Käsittelee päivittyneen kohdeosien muokkaus-grid datan. Sekä uudet ja vanhat
  ovat mäppejä numerosta riviin. Oletetaan, että yksi avainten arvoista on muuttunut
  käyttäjän tekemän editointioperaation johdosta.
  Jos käyttäjä muokkasi alkuosaa, asetetaan mahdollisen edeltävän rivin loppuosa samaksi.
  Jos käyttäjä muokkasi loppuosaa, asetetaaan seuraavan rivin alkuosa samaksi."
  [vanhat uudet]
  (let [riveja (count uudet)
        [muokattu-vanha muokattu-uusi muokattu-key]
        (some #(let [vanha (get vanhat %)
                     uusi (get uudet %)]
                 (when-not (= vanha uusi)
                   [vanha uusi %]))
              (keys uudet))

        edellinen-key (when (> muokattu-key 1)
                        (dec muokattu-key))
        edellinen (when edellinen-key
                    (get uudet edellinen-key))

        seuraava-key (when (< muokattu-key riveja)
                       (inc muokattu-key))
        seuraava (when seuraava-key
                   (get uudet seuraava-key))

        alku-muutettu? (not= (alku muokattu-vanha) (alku muokattu-uusi))
        loppu-muutettu? (not= (loppu muokattu-vanha) (loppu muokattu-uusi))]

    (as-> uudet rivit
      (if alku-muutettu?
        (assoc rivit edellinen-key
               (merge edellinen
                      (zipmap [:tr-loppuosa :tr-loppuetaisyys]
                              (alku muokattu-uusi))))
        rivit)

      (if loppu-muutettu?
        (assoc rivit seuraava-key
               (merge seuraava
                      (zipmap [:tr-alkuosa :tr-alkuetaisyys]
                              (loppu muokattu-uusi))))
        rivit))))

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
