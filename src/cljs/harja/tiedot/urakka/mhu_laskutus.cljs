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
(defrecord PaivitaLomake [polut-ja-arvot])

(defrecord TallennaLasku [])

(defrecord HaeUrakanToimenpiteet [urakka])
(defrecord HaeUrakanLaskut [hakuparametrit])
(defrecord HaeAliurakoitsijat [])

(defrecord KutsuEpaonnistui [tulos])

(defrecord ToimenpidehakuOnnistui [tulos])
(defrecord LaskuhakuOnnistui [tulos])
(defrecord AliurakoitsijahakuOnnistui [tulos])

(def instanssi->toimenpide {52 :talvihoito
                            53 :liikenneympariston-hoito
                            55 :paallystepaikkaukset
                            56 :mhu-ylläpito
                            57 :mhu-korvausinvestointi})

(defn lomakkeen-paivitys
  [lomake polut-ja-arvot]
  (reduce (fn [acc [polku arvo]]
            (apply
              (if (vector? polku)
               (if (fn? arvo) update-in assoc-in)
               (if (fn? arvo) update assoc))
              [acc polku arvo])) lomake
           (partition 2 polut-ja-arvot)))

(extend-protocol tuck/Event
  TallennaLasku
  (process-event [_ app]
    app)
  PaivitaLomake
  (process-event [{polut-ja-arvot :polut-ja-arvot} app]
    (apply loki/log "päivitetään" polut-ja-arvot)
    (update app :lomake lomakkeen-paivitys polut-ja-arvot))
  HaeAliurakoitsijat
  (process-event [_ app]
    (tuck-apurit/get! :aliurakoitsijat
                       {:onnistui           ->AliurakoitsijahakuOnnistui
                        :epaonnistui        ->KutsuEpaonnistui
                        :paasta-virhe-lapi? true})
    (update-in app [:meta :haetaan] inc))
  HaeUrakanLaskut
  (process-event [{:keys [hakuparametrit]} app]
    (tuck-apurit/post! :laskuerittelyt
                       {:urakka-id (:id hakuparametrit)
                        :alkupvm   (:alkupvm hakuparametrit)
                        :loppupvm  (:loppupvm hakuparametrit)}
                       {:onnistui           ->LaskuhakuOnnistui
                        :epaonnistui        ->KutsuEpaonnistui
                        :paasta-virhe-lapi? true})
    (update-in app [:meta :haetaan] inc))
  AliurakoitsijahakuOnnistui
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :aliurakoitsijat tulos)
        (update-in [:meta :haetaan] dec)))
  LaskuhakuOnnistui
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :laskut tulos)
        (update-in [:meta :haetaan] dec)))
  ToimenpidehakuOnnistui
  (process-event [{tulos :tulos} app]
    (loki/log "Tulo!!!s  " tulos)
    (let [kasitelty (set (flatten (mapv (fn [{:keys [t2_koodi t2_nimi t3_id t3_nimi t3_koodi t3_emo]}] (vector {:toimenpide t2_nimi :koodi t2_koodi :id t3_emo}
                                                                                                               {:tehtavaryhma t3_nimi :koodi t3_koodi :id t3_id :emo t3_emo})) tulos)))
          toimenpiteet (filterv #(not (nil? (:toimenpide %))) kasitelty)
          tehtavaryhmat (into [{:tehtavaryhma :johto-ja-hallintokorvaus} {:tehtavaryhma :erilliskustannukset}] (filterv #(not (nil? (:tehtavaryhma %))) kasitelty))]
      (assoc app :toimenpiteet toimenpiteet
                 :tehtavaryhmat tehtavaryhmat)))
  KutsuEpaonnistui
  (process-event [{:keys [tulos]} app]
    (loki/log "tai ulos " tulos)
    app)
  HaeUrakanToimenpiteet
  (process-event
    [{:keys [urakka]} app]
    (tuck-apurit/post! :urakan-toimenpiteet
                       urakka
                       {:onnistui           ->ToimenpidehakuOnnistui
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