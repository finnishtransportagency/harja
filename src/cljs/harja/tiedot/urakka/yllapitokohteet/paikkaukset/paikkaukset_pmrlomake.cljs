(ns harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-pmrlomake
  (:require [clojure.data :refer [diff]]
            [tuck.core :as tuck]
            [clojure.string :as str]
            [harja.loki :refer [log]]
            [harja.ui.viesti :as viesti]
            [harja.tiedot.urakka.yllapitokohteet.paikkaukset.paikkaukset-paikkauskohteet :as t-paikkauskohteet]
            [harja.tiedot.urakka.urakka :as tila]))

(defn pmr-validoinnit [avain lomake]
  (avain
    (merge
      {:nimi [tila/ei-nil tila/ei-tyhja]
       :ulkoinen-id [tila/ei-nil tila/ei-tyhja tila/numero]
       :tie [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
       :aosa [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
       :losa [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
       :aet [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)]
       :let [tila/ei-nil tila/ei-tyhja tila/numero #(tila/maksimiarvo 90000 %)
             (tila/silloin-kun #(not (nil? (:aet lomake)))
                               (fn [arvo]
                                 ;; Validointi vaatii "nil" vastauksen, kun homma on pielessä ja kentän arvon, kun kaikki on ok
                                 (cond
                                   ;; Jos alkuosa ja loppuosa on sama
                                   ;; Ja alkuetäisyys on pienempi kuin loppuetäisyys)
                                   (and (= (:aosa lomake) (:losa lomake)) (< (:aet lomake) arvo))
                                   arvo
                                   ;; Alkuetäisyys on suurempi kuin loppuetäisyys
                                   (and (= (:aosa lomake) (:losa lomake)) (>= (:aet lomake) arvo))
                                   nil
                                   :else arvo)))]})))

(defn- validoi-pmr-lomake [lomake]
  (apply tila/luo-validius-tarkistukset
         [[:nimi] (pmr-validoinnit :nimi lomake)
          [:ulkoinen-id] (pmr-validoinnit :ulkoinen-id lomake)
          [:tie] (pmr-validoinnit :tie lomake)
          [:aosa] (pmr-validoinnit :aosa lomake)
          [:losa] (pmr-validoinnit :losa lomake)
          [:aet] (pmr-validoinnit :aet lomake)
          [:let] (pmr-validoinnit :let lomake)]))

(defrecord AvaaPMRLomake [lomake])
(defrecord PaivitaPMRLomake [lomake])
(defrecord TallennaPMRLomake [paikkauskohde])
(defrecord TallennaPMRLomakeOnnistui [paikkauskohde])
(defrecord TallennaPMRLomakeEpaonnistui [paikkauskohde])
(defrecord SuljePMRLomake [])

(extend-protocol tuck/Event
  AvaaPMRLomake
  (process-event [{lomake :lomake} app]
    (let [{:keys [validoi] :as validoinnit} (validoi-pmr-lomake lomake)
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
          {:keys [validoi] :as validoinnit} (validoi-pmr-lomake lomake)
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
        (viesti/nayta-toast! "Paikkauskohteen muokkaus epäonnistui" :varoitus viesti/viestin-nayttoaika-aareton)
        (-> app
            (assoc-in [:pmr-lomake :harja.tiedot.urakka.urakka/validi?] false)
            (update-in [:pmr-lomake :harja.tiedot.urakka.urakka/validius [:ulkoinen-id]]
                       #(merge %
                               {:validi? false
                                :virheteksti ulkoinen-id-virhe}))))))
  )

