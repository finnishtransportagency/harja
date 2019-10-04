(ns harja.tiedot.urakka.mhu-laskutus
  (:require [tuck.core :as tuck]
            [harja.loki :as loki]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.jana :as jana]))

(defrecord LuoKulutaulukko [taulukko])
(defrecord KulujenSyotto [auki?])
(defrecord TallennaKulu [kulu])

(extend-protocol tuck/Event
  TallennaKulu
  (process-event
    [{{:keys [tehtavat koontilaskun-kuukausi koontilaskun-era]} :kulu} {:keys [taulukko] :as app}]
    (loki/log app taulukko)
    (let [uudet-kulut (update app :kulut (fn [m]
                                           (apply
                                             conj
                                             m
                                             (map #(assoc % :koontilaskun-kuukausi koontilaskun-kuukausi
                                                            :koontilaskun-era koontilaskun-era) tehtavat))))
          u-k-lkm (count (:kulut uudet-kulut))
          taulukko (reduce #(let [{:keys [tehtava maara tehtavaryhma]} %2]
                              (p/lisaa-rivi!
                                %1
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
                                 maara]))
                           taulukko
                           tehtavat)
          #_(assoc uudet-kulut :taulukko (p/aseta-arvo taulukko :lapset
                                                       (apply
                                                         conj
                                                         (:rivit taulukko)
                                                         (mapv
                                                           (fn [{:keys [tehtava maara tehtavaryhma]}]
                                                             (harja.ui.taulukko.jana/->Rivi
                                                               (keyword (str "kulu-" u-k-lkm))
                                                               [(osa/->Teksti (keyword (str "kk-hv-" u-k-lkm))
                                                                              koontilaskun-kuukausi
                                                                              {:class #{"osa" "osa-teksti"}})
                                                                (osa/->Teksti (keyword (str "era-" u-k-lkm))
                                                                              koontilaskun-era
                                                                              {:class #{"osa" "osa-teksti"}})
                                                                (osa/->Teksti (keyword (str "toimenpide-" u-k-lkm))
                                                                              tehtava
                                                                              {:class #{"osa" "osa-teksti"}})
                                                                (osa/->Teksti (keyword (str "tehtavaryhma-" u-k-lkm))
                                                                              tehtavaryhma
                                                                              {:class #{"osa" "osa-teksti"}})
                                                                (osa/->Teksti (keyword (str "maara-" u-k-lkm))
                                                                              maara
                                                                              {:class #{"osa" "osa-teksti"}})]
                                                               #{"jana" "janan-rivi" "table-default" "table-default-even"}))
                                                           tehtavat))))]
      taulukko))

  KulujenSyotto
  (process-event
    [{:keys [auki?]} app]
    (assoc app :syottomoodi auki?))
  LuoKulutaulukko
  (process-event
    [{:keys [taulukko]} app]
    (loki/log "Ta" taulukko)
    (assoc app :taulukko taulukko)))