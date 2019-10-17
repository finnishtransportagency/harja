(ns harja.tiedot.urakka.mhu-laskutus
  (:require [tuck.core :as tuck]
            [harja.loki :as loki]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.jana :as jana]
            [harja.tyokalut.tuck :as tuck-apurit]))

(defrecord LuoKulutaulukko [taulukko])
(defrecord KulujenSyotto [auki?])
(defrecord TallennaKulu [kulu])
(defrecord HaeKustannussuunnitelma [urakka])
(defrecord KutsuOnnistui [tulos])
(defrecord KutsuEpaonnistui [tulos])

(extend-protocol tuck/Event
  KutsuOnnistui
  (process-event [{{:keys [kiinteahintaiset-tyot johto-ja-hallintokorvaukset kustannusarvioidut-tyot] :as tulos} :tulos} app]
    (loki/log "Tulos  " tulos)
    (let [toimenpiteet (concat (keys (group-by :toimenpide kiinteahintaiset-tyot)) (keys (group-by :toimenpide-avain kustannusarvioidut-tyot)))
          tehtavaryhmat [:johto-ja-hallintokorvaus :erilliskustannukset]]
      (assoc app :kustannussuunnitelma tulos
                 :toimenpiteet toimenpiteet
                 :tehtavaryhmat tehtavaryhmat
                :kiinteahintaiset-tyot (group-by :toimenpide kiinteahintaiset-tyot)
                :johto-ja-hallintokorvaukset johto-ja-hallintokorvaukset
                :kustannusarvioidut-tyot (group-by :toimenpide-avain kustannusarvioidut-tyot))))
  KutsuEpaonnistui
  (process-event [{:keys [tulos]} app]
    (loki/log "tai ulos " tulos)
    app)
  HaeKustannussuunnitelma
  (process-event
    [{:keys [urakka]} app]
    (tuck-apurit/post! :budjetoidut-tyot
                       {:urakka-id urakka}
                       {:onnistui           ->KutsuOnnistui
                        :epaonnistui        ->KutsuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)
  TallennaKulu
  (process-event
    [{{:keys [tehtavat koontilaskun-kuukausi koontilaskun-era]} :kulu} {:keys [taulukko] :as app}]
    (let [uudet-kulut (update app :kulut (fn [m]
                                           (apply
                                             conj
                                             m
                                             (map #(assoc % :koontilaskun-kuukausi koontilaskun-kuukausi
                                                            :koontilaskun-era koontilaskun-era) tehtavat))))
          u-k-lkm (count (:kulut uudet-kulut))
          taulukko (reduce (fn [koko t]
                             (let [{:keys [tehtava maara tehtavaryhma]} t]
                               (p/lisaa-rivi!
                                 koko
                                 {:avain :kulut :rivi jana/->Rivi}
                                 [osa/->Teksti
                                  (keyword (str "kk-hv-" u-k-lkm))
                                  koontilaskun-kuukausi]
                                 [osa/->Teksti
                                  (keyword (str "era-" u-k-lkm))
                                  koontilaskun-era]
                                 [osa/->Teksti
                                  (keyword (str "toimenpide-" u-k-lkm))
                                  tehtava]
                                 [osa/->Teksti
                                  (keyword (str "tehtavaryhma-" u-k-lkm))
                                  tehtavaryhma]
                                 [osa/->Teksti
                                  (keyword (str "maara-" u-k-lkm))
                                  maara])))
                           taulukko
                           tehtavat)]
      (assoc app :taulukko taulukko)))

  KulujenSyotto
  (process-event
    [{:keys [auki?]} app]
    (assoc app :syottomoodi auki?))
  LuoKulutaulukko
  (process-event
    [{:keys [taulukko]} app]
    (loki/log "Ta" taulukko)
    (assoc app :taulukko taulukko)))