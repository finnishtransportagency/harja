(ns harja.tiedot.sillat
  "Sillat karttatason vaatimat tiedot. Sillat on jaettu geometrisesti hoidon alueurakoiden alueille."
  (:require [reagent.core :refer [atom] :as r]
            [harja.asiakas.kommunikaatio :as k]
            [harja.loki :refer [log]]
            [harja.tiedot.navigaatio :as nav]
            [harja.atom :refer-macros [reaction<! reaction-writable]]
            [harja.geo :as geo]
            [harja.pvm :as pvm])
  (:require-macros [cljs.core.async.macros :refer [go]]
                   [reagent.ratom :refer [reaction]]))

(def karttataso-sillat (atom false))
(def jarjestys (atom :nimi))
(def listaus (atom :kaikki))

(defn- on-tarkastettu-hoitokautena?
  [silta]
  (let [[hoitokausi-alkupvm hoitokausi-loppupvm] (pvm/paivamaaran-hoitokausi (pvm/nyt))]
    (true? (pvm/valissa? (:tarkastusaika silta) hoitokausi-alkupvm hoitokausi-loppupvm))))

(defn on-poistettu?
  "Silta ei ole enää urakan vastuulla. Se on lakkautettu, purettu tai siirretty kunnan vastuulle. Silta ei ole kokonaan poistettu Harjasta, koska siihen on tehty siltatarkastuksia."
  [silta]
  (or (some? (:loppupvm silta))
      (some? (:lakkautuspvm silta))
      (:kunnan-vastuulla silta)))

(defn- varita-silta [silta]
  ;; Värittää sillan vihreäksi mikäli se on tarkastettu tämän hoitokauden aikana

  (-> silta
      (assoc-in [:alue :fill] true)
      (assoc-in [:alue :color] (if (on-tarkastettu-hoitokautena? silta)
                                 "palegreen"
                                 (if (on-poistettu? silta)
                                   "gainsboro"
                                   "crimson")))))

(defn- hae-urakan-siltalistaus [urakka listaus]
  (k/post! :hae-urakan-sillat
           {:urakka-id (:id urakka)
            :listaus listaus}))

(defonce paivita-kartta! (atom false))

(def haetut-sillat
  (reaction<! [paalla? @karttataso-sillat
               urakka @nav/valittu-urakka
               listaus @listaus
               _ @paivita-kartta!]
              {:nil-kun-haku-kaynnissa? true}
              (when (and paalla? urakka)
                (log "Siltataso päällä, haetaan sillat urakalle: "
                     (:nimi urakka) " (id: " (:id urakka) ")")
                (go (into []
                          (comp (map #(assoc % :type :silta))
                                (map varita-silta))
                          (<! (hae-urakan-siltalistaus urakka listaus)))))))

(defn- skaalaa-sillat-zoom-tason-mukaan [koko sillat]
  ;; PENDING: Ei ole optimaalista, että sillat ovat "point", jotka
  ;; piirretään tietyllä radiuksella... ikoni olisi hyvä saada.
  (let [sillan-koko (* 0.003 koko)]
    (when sillat
      (into []
            (comp (map #(assoc-in % [:alue :radius] sillan-koko))
                  (map #(assoc % :tyyppi-kartalla :silta)))
            sillat))))

(def sillat-kartalla
  (reaction-writable
   (skaalaa-sillat-zoom-tason-mukaan
    @nav/kartan-nakyvan-alueen-koko @haetut-sillat)))


(defn paivita-silta! [id funktio & args]
  (swap! paivita-kartta! not)
  (swap! sillat-kartalla (fn [sillat]
                  (mapv (fn [silta]
                          (if (= id (:id silta))
                            (apply funktio silta args)
                            silta)) sillat))))
