(ns harja.tiedot.urakka.muutokset.lasketut-muutokset-tiedot
  "Urakan muutosten tiedot - lasketut muutokset."
  (:require
    [tuck.core :as tuck]

    [harja.tiedot.urakka :as u]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.ui.viesti :as viesti]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.tiedot.urakka.muutokset.yhteiset-tiedot :as t-yhteiset]))

;; Muutostyypit:
;; - Tehtävä- ja määrämuutokset
;; -


;; --- Tuck-eventit ja käsittelijät ---

;; -- Tehtävä- ja määrämuutokset -- ALKAA
(defrecord TallennaTehtavaMaaramuutokset [rivit])
(defrecord TallennaTehtavaMaaramuutoksetOnnistui [vastaus])
(defrecord TallennaTehtavaMaaramuutoksetEpaonnistui [vastaus])
(defrecord AvaaYksikkohintaModal [valittu-modal-tehtava tehtava_id])
(defrecord SuljeYksikkohintaModal [])
(defrecord MuokkaaYksikkohintaa [rivi hoitokausien-yksikkohinnat])
(defrecord TallennaYksikkohinta [rivi])
(defrecord TallennaYksikkohintaOnnistui [vastaus])
(defrecord TallennaYksikkohintaEpaonnistui [vastaus])
;; -- Tehtävä- ja määrämuutokset -- LOPPUU


(extend-protocol tuck/Event
  ;; -- Tehtävä- ja määrämuutokset -- ALKAA
  TallennaTehtavaMaaramuutokset
  (process-event [{:keys [rivit]}
                  {:keys [_valittu-rivi] :as app}]
    (let [parametrit {:rivit rivit
                      :urakka-id (-> @tila/yleiset :urakka :id)
                      :hoitokaudet @u/valitun-urakan-hoitokaudet
                      :valittu-hoitokausi (:valittu-hoitokausi app)}]
      ;; Kutsutaan gridin tallenna napista, ei modalista
      (tuck-apurit/post! app :tallenna-tehtava-maaramuutokset
        parametrit
        {:onnistui ->TallennaTehtavaMaaramuutoksetOnnistui
         :epaonnistui ->TallennaTehtavaMaaramuutoksetEpaonnistui})
      (-> app
        (assoc :haku-kaynnissa? true))))

  TallennaTehtavaMaaramuutoksetOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Tallennus onnistui" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (let [app (assoc app
                :haku-kaynnissa? false
                :tehtava-maaramuutokset vastaus)]

      ;; Laukaise lopuksi efekti, joka hakee urakan viimeisimmät muutostiedot koostenäkymään
      ;; Antaa viimeisimmän app-tilan eventille
      (tuck/fx app
        {:tuck.effect/type :laukaise-event
         :event #(t-yhteiset/->HaeUrakanMuutostiedot :lasketut-muutokset)})))

  TallennaTehtavaMaaramuutoksetEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Tehtävä- ja määrämuutosten tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))

  AvaaYksikkohintaModal
  (process-event [{:keys [valittu-modal-tehtava _tehtava_id]}
                  {:keys [_yksikkohinta-modal-auki?] :as app}]
    (assoc app
      :yksikkohinta-modal-auki? true
      :valittu-modal-tehtava valittu-modal-tehtava))


  SuljeYksikkohintaModal
  (process-event [_ {:keys [_yksikkohinta-modal-auki?] :as app}]
    (assoc app
      :valittu-modal-tehtava nil
      :yksikkohinta-modal-auki? false))

  MuokkaaYksikkohintaa
  (process-event [{:keys [rivi hoitokausien-yksikkohinnat]} app]
    (let [valittu-hoitokauden-alkuvuosi (->>
                                          hoitokausien-yksikkohinnat
                                          (filter #(= (:arvo %) (:yksikkohinta rivi)))
                                          first
                                          :hoitokauden-alkuvuosi)]
      ;; Kutsutaan kun modalista valitaan yksikköhinta (ei tallenneta vielä)
      (-> app
        ;; Päivitä yksikköhinta
        (update :valittu-modal-tehtava merge rivi)
        ;; Päivitä valittu yksikköhinnan hk
        (assoc-in [:valittu-modal-tehtava :yksikkohinnan_alkuvuosi] valittu-hoitokauden-alkuvuosi))))

  TallennaYksikkohinta
  (process-event [{:keys [rivi]}
                  {:keys [_valittu-rivi] :as app}]
    ;; Kutsutaan kun modalista tallennetaan valittu yksikköhinta
    (let [parametrit {:rivi rivi
                      :urakka-id (-> @tila/yleiset :urakka :id)
                      :hoitokaudet @u/valitun-urakan-hoitokaudet
                      :valittu-hoitokausi (:valittu-hoitokausi app)}]

      (tuck-apurit/post! app :tallenna-maaramuutos-yksikkohinta
        parametrit
        {:onnistui ->TallennaYksikkohintaOnnistui
         :epaonnistui ->TallennaYksikkohintaEpaonnistui})
      (-> app
        (assoc
          :haku-kaynnissa? true
          :yksikkohinta-modal-auki? false))))

  TallennaYksikkohintaOnnistui
  (process-event [{:keys [vastaus]} app]
    (viesti/nayta-toast! "Yksikköhinta tallennettu" :onnistui viesti/viestin-nayttoaika-lyhyt)
    (let [app (assoc app
                :haku-kaynnissa? false
                :tehtava-maaramuutokset vastaus)]

      ;; Laukaise lopuksi efekti, joka hakee urakan viimeisimmät muutostiedot koostenäkymään
      ;; Antaa viimeisimmän app-tilan eventille
      (tuck/fx app
        {:tuck.effect/type :laukaise-event
         :event #(t-yhteiset/->HaeUrakanMuutostiedot :lasketut-muutokset)})))


  TallennaYksikkohintaEpaonnistui
  (process-event [_ app]
    (viesti/nayta-toast! "Yksikköhinnan tallennus epäonnistui" :varoitus viesti/viestin-nayttoaika-keskipitka)
    (assoc app :haku-kaynnissa? false))
  ;; -- Tehtävä- ja määrämuutokset -- LOPPUU
  )
