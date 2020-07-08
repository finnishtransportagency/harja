(ns harja.tiedot.urakka.toteumat.maarien-toteuma-lomake
  (:require [tuck.core :as tuck]
            [harja.domain.toteuma :as t]
            [harja.ui.lomake :as ui-lomake]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.loki :as loki]))

(defrecord HaeTehtavat [parametrit])
(defrecord HakuOnnistui [tulos parametrit])
(defrecord HakuEpaonnistui [tulos parametrit])
(defrecord LahetaLomake [lomake])
(defrecord LisaaToteuma [lomake])
(defrecord LomakkeenLahetysOnnistui [tulos])
(defrecord LomakkeenLahetysEpaonnistui [tulos])
(defrecord PaivitaLomake [lomake])
(defrecord TyhjennaLomake [lomake])

(def oletuslomake {})

(def uusi-toteuma {})

(defn- hae-tehtavat
  ([toimenpide]
   (hae-tehtavat toimenpide :maaramitattava))
  ([toimenpide tyyppi]
   (let [tehtavaryhma (when toimenpide
                        (:otsikko toimenpide))
         rajapinta (case tyyppi
                     :akillinen-hoitotyo :akillisten-hoitotoiden-toimenpiteiden-tehtavat
                     :maarien-toteutumien-toimenpiteiden-tehtavat)
         toimenpide-re-string (when toimenpide
                                (cond
                                  (re-find #"TALVIHOITO" tehtavaryhma) "Talvihoito"
                                  (re-find #"LIIKENNEYMPÄRISTÖN HOITO" tehtavaryhma) "Liikenneympäristön hoito"
                                  (re-find #"SORATEIDEN HOITO" tehtavaryhma) "Sorateiden hoito"
                                  :else ""))
         parametrit {:polku    :tehtavat
                     :filtteri (when (= tyyppi :akillinen-hoitotyo)
                                 #(re-find (re-pattern (str "(" toimenpide-re-string "|rahavaraus)")) (:tehtava %)))}]
     (when-not (and
                 toimenpide
                 (= tyyppi :lisatyo))
       (tuck-apurit/post! rajapinta
                          {:tehtavaryhma tehtavaryhma
                           :urakka-id    (-> @tila/yleiset :urakka :id)}
                          {:onnistui               ->HakuOnnistui
                           :onnistui-parametrit    [parametrit]
                           :epaonnistui            ->HakuEpaonnistui
                           :epaonnistui-parametrit []
                           :paasta-virhe-lapi?     true})))))

(extend-protocol
  tuck/Event
  HaeTehtavat
  (process-event [{{:keys [toimenpide]} :parametrit} app]
    (hae-tehtavat toimenpide)
    app)
  HakuOnnistui
  (process-event [{tulos :tulos {:keys [polku filtteri]} :parametrit} app]
    (assoc app polku (if filtteri
                       (filter filtteri tulos)
                       tulos)))
  HakuEpaonnistui
  (process-event [{tulos :tulos {:keys []} :parametrit} app] ;:TODO: Tee
    app)
  LahetaLomake
  (process-event [{lomake :lomake} app]
    (let [{loppupvm   ::t/pvm
           tyyppi     ::t/tyyppi
           toimenpide ::t/toimenpide
           toteumat   ::t/toteumat} lomake
          urakka-id (-> @tila/yleiset :urakka :id)]
      (loki/log toteumat)
      (tuck-apurit/post! :tallenna-toteuma
                         {:urakka-id  urakka-id
                          :toimenpide toimenpide
                          :tyyppi     tyyppi
                          :loppupvm   loppupvm
                          :toteumat   (mapv #(into {}       ; siivotaan namespacet lähetettävästä
                                                   (map
                                                     (fn [[k v]]
                                                       [(-> k name keyword) v])
                                                     %))
                                            toteumat)}
                         {:onnistui    ->LomakkeenLahetysOnnistui
                          :epaonnistui ->LomakkeenLahetysEpaonnistui}))
    app)
  LisaaToteuma
  (process-event [{lomake :lomake} app]
    (let [lomake (update lomake ::t/toteumat conj uusi-toteuma)]
      (assoc app :lomake lomake)))
  LomakkeenLahetysEpaonnistui                               ;TODO: tee
  (process-event [_ app]
    app)
  LomakkeenLahetysOnnistui                                  ;TODO: tee
  (process-event [_ app]
    app)
  PaivitaLomake
  (process-event [{{useampi?          ::t/useampi-toteuma
                    tyyppi            ::t/tyyppi
                    sijainti          ::t/sijainti
                    ei-sijaintia      ::t/ei-sijaintia
                    toimenpide        ::t/toimenpide
                    viimeksi-muokattu ::ui-lomake/viimeksi-muokattu-kentta
                    :as               lomake} :lomake} app]
    ; sivuvaikutusten triggeröintiin
    (case viimeksi-muokattu
      ::t/toimenpide (hae-tehtavat toimenpide tyyppi)
      ::t/tyyppi (hae-tehtavat toimenpide tyyppi)
      :default)
    ; :TODO: tää pitää korjata sijaintien osalta jos on yksi toteuma
    (let [useampi-aiempi? (get-in app [:lomake ::t/useampi-toteuma])
          paivitettava-toteumat-vektoriin #{::t/lisatieto ::t/maara ::t/tehtava ::t/sijainti}
          app (assoc app :lomake lomake)
          uusi-app (update app :lomake (fn [l]
                                         (cond-> l
                                                 (and (true? useampi?)
                                                      (= tyyppi :maaramitattava)
                                                      (not= useampi? useampi-aiempi?))
                                                 (update ::t/toteumat conj uusi-toteuma)

                                                 (and (not (true? useampi?))
                                                      (= tyyppi :maaramitattava)
                                                      (not= useampi? useampi-aiempi?))
                                                 (update ::t/toteumat #(conj [] (first %)))

                                                 (and (not (true? useampi?))
                                                      (some #(do
                                                               (loki/log % viimeksi-muokattu (= viimeksi-muokattu %))
                                                               (= viimeksi-muokattu %))
                                                            paivitettava-toteumat-vektoriin))
                                                 (assoc-in [::t/toteumat 0 viimeksi-muokattu] (viimeksi-muokattu lomake)))))]
      #_(-> app
            (update :lomake (if (and
                                  (= tyyppi :maaramitattava)
                                  (not= useampi? useampi-aiempi?))
                              (fn [lomake]
                                (if (true? useampi?)
                                  (update lomake ::t/toteumat conj uusi-toteuma)
                                  (update lomake ::t/toteumat #(conj [] (first %)))))
                              identity)))
      (loki/log "ual" uusi-app lomake)
      uusi-app))
  TyhjennaLomake
  (process-event [{lomake :lomake} app]
    (assoc app :syottomoodi false)))