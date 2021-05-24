(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake
  (:require [clojure.data :refer [diff]]
            [tuck.core :as tuck]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.urakka :as tila]))

(defrecord AvaaPMRLomake [lomake])
(defrecord PaivitaPMRLomake [lomake])
(defrecord TallennaPMRLomake [paikkauskohde])
(defrecord TallennaPMRLomakeOnnistui [paikkauskohde])
(defrecord TallennaPMRLomakeEpaonnistui [paikkauskohde])
(defrecord SuljePMRLomake [])

(extend-protocol tuck/Event
  AvaaPMRLomake
  (process-event [{lomake :lomake} app]
    (let [{:keys [validoi] :as validoinnit} (t-paikkauskohteet/validoi-lomake lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)]
      (-> app
          (assoc :pmr-lomake lomake)
          (assoc-in [:pmr-lomake ::tila/validius] validius)
          (assoc-in [:pmr-lomake ::tila/validi?] validi?))))

  SuljePMRLomake
  (process-event [_ app]
    (dissoc app :pmr-lomake))

  PaivitaPMRLomake
  (process-event [{lomake :lomake} app]
    (let [lomake (t-paikkauskohteet/laske-paikkauskohteen-pituus lomake [:pmr-lomake])
          {:keys [validoi] :as validoinnit} (t-paikkauskohteet/validoi-lomake lomake)
          {:keys [validi? validius]} (validoi validoinnit lomake)]
      (-> app
          (assoc :pmr-lomake lomake)
          (assoc-in [:pmr-lomake ::tila/validius] validius)
          (assoc-in [:pmr-lomake ::tila/validi?] validi?))))

  TallennaPMRLomake
  (process-event [{paikkauskohde :paikkauskohde} app]
    (do
      (t-paikkauskohteet/tallenna-paikkauskohde (t-paikkauskohteet/siivoa-ennen-lahetysta paikkauskohde)
                                                ->TallennaPMRLomakeOnnistui
                                                ->TallennaPMRLomakeEpaonnistui)
      app))

  TallennaPMRLomakeOnnistui
  (process-event [{_ :paikkauskohde} app]
    (let [_ (t-paikkauskohteet/hae-paikkauskohteet (-> @tila/yleiset :urakka :id) app)]
      (viesti/nayta-toast! "Muutokset tallennettu")
      (dissoc app :pmr-lomake)))

  TallennaPMRLomakeEpaonnistui
  (process-event [{paikkauskohde :paikkauskohde} app]
    (let [;; Otetaan virhe talteen
          virhe (get-in paikkauskohde [:response :virhe])
          ulkoinen-id-virhe (when (str/includes? virhe "ulkoinen-id")
                              "Tarkista numero. Mahdollinen duplikaatti.")]
      (do
        (js/console.log "Paikkauskohteen tallennus epäonnistui" (pr-str paikkauskohde))
        (viesti/nayta-toast! "Paikkauskohteen muokkaus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
        (-> app
            (assoc-in [:pmr-lomake :harja.tiedot.urakka.urakka/validi?] false)
            (update-in [:pmr-lomake :harja.tiedot.urakka.urakka/validius [:ulkoinen-id]]
                       #(merge %
                               {:validi? false
                                :virheteksti ulkoinen-id-virhe}))))))
  )

