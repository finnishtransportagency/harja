(ns harja.tiedot.urakka.mhu-laskutus
  (:require [tuck.core :as tuck]
            [harja.loki :as loki]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.jana :as jana]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm]))

(defrecord LuoKulutaulukko [taulukko])
(defrecord KulujenSyotto [auki?])
(defrecord TallennaKulu [])
(defrecord PaivitaLomake [polut-ja-arvot])
(defrecord AvaaLasku [lasku])

(defrecord LiiteLisatty [liite])

(defrecord LuoUusiAliurakoitsija [aliurakoitsija])

(defrecord HaeUrakanToimenpiteet [urakka])
(defrecord HaeUrakanLaskut [hakuparametrit])
(defrecord HaeAliurakoitsijat [])

(defrecord KutsuEpaonnistui [tulos])

(defrecord TallennusOnnistui [tulos parametrit])
(defrecord ToimenpidehakuOnnistui [tulos])
(defrecord LaskuhakuOnnistui [tulos])
(defrecord AliurakoitsijahakuOnnistui [tulos])

(defrecord LataaLiite [id])
(defrecord PoistaLiite [id])

(def instanssi->toimenpide {52 :talvihoito
                            53 :liikenneympariston-hoito
                            55 :paallystepaikkaukset
                            56 :mhu-ylläpito
                            57 :mhu-korvausinvestointi})

(defn lomakkeen-paivitys
  [lomake polut-ja-arvot & args]
  (reduce (fn [acc [polku arvo]]
            (apply
              (if (vector? polku)
                (if (fn? arvo) update-in assoc-in)
                (if (fn? arvo) update assoc))
              (into [acc polku arvo] (when (fn? arvo) args)))) lomake
          (partition 2 polut-ja-arvot)))

(defn lasku->lomake [{:keys [kohdistukset] :as lasku}]
  (let [{aliurakoitsija :suorittaja-id} (first kohdistukset)]
    (-> lasku
        (update :kohdistukset (fn [ks] (mapv (fn [kohdistukset]
                                               (->
                                                 kohdistukset
                                                 (dissoc :suorittaja-id :suorittaja-nimi))) ks)))
        (assoc :aliurakoitsija aliurakoitsija))))

(extend-protocol tuck/Event
  PoistaLiite
  (process-event [{id :id} app]
    (update app :lomake dissoc :liite-id :liite-nimi :liite-tyyppi :liite-oid :liite-koko))
  LataaLiite
  (process-event [{id :id} app]
    app)
  ;:kuvaus, :fileyard-hash, :urakka, :nimi,
  ;:id,:lahde,:tyyppi, :koko 65528
  LiiteLisatty
  (process-event [{{:keys [kuvaus nimi id tyyppi koko]} :liite} app]
    (update app
            :lomake
            assoc
            :liite-id id
            :liite-nimi nimi
            :liite-tyyppi tyyppi
            :liite-koko koko
            ))
  AvaaLasku
  (process-event [{lasku :lasku} app]
    (loki/log "avaan laskun" lasku app)
    (assoc app :syottomoodi true
               :lomake (lasku->lomake lasku)))
  TallennusOnnistui
  (process-event [{tulos :tulos {:keys [avain]} :parametrit} app]
    (loki/log avain tulos)
    (let [assoc-fn
          (if (vector? avain)
            assoc-in
            assoc)]
      (assoc-fn app avain tulos)))
  PaivitaLomake
  (process-event [{polut-ja-arvot :polut-ja-arvot} app]
    (apply loki/log "päivitetään" polut-ja-arvot)
    (update app :lomake lomakkeen-paivitys polut-ja-arvot))
  LuoUusiAliurakoitsija
  (process-event [{aliurakoitsija :aliurakoitsija} app]
    (loki/log "!" aliurakoitsija)
    (tuck-apurit/post! :tallenna-aliurakoitsija
                       aliurakoitsija
                       {:onnistui            ->TallennusOnnistui
                        :onnistui-parametrit [{:avain :aliurakoitsijat}]
                        :epaonnistui         ->KutsuEpaonnistui
                        :paasta-virhe-lapi?  true})
    app)
  HaeAliurakoitsijat
  (process-event [_ app]
    (tuck-apurit/get! :aliurakoitsijat
                      {:onnistui           ->AliurakoitsijahakuOnnistui
                       :epaonnistui        ->KutsuEpaonnistui
                       :paasta-virhe-lapi? true})
    (update-in app [:meta :haetaan] inc))
  HaeUrakanLaskut
  (process-event [{:keys [hakuparametrit]} app]
    (tuck-apurit/post! :kaikki-laskuerittelyt
                       {:urakka-id (:id hakuparametrit)}
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
  (process-event [{tulos :tulos} {:keys [taulukko kulut toimenpiteet] :as app}]
    (loki/log "laskut haettu")
    (let
      [e! (tuck/current-send-function)
       u-k-lkm (count kulut)
       paivitetty-taulukko (reduce
                             (fn [koko {:keys [summa viite erapaiva kohdistukset] :as l}]
                               (loki/log "kohdistukset" l)
                               (loop
                                 [taul koko
                                  kohd kohdistukset]
                                 (loki/log "KOHD" kohd)
                                 (if (nil? (first kohd))
                                   taul
                                   (recur
                                     (let [{:keys [tehtava tehtavaryhma toimenpideinstanssi summa]} (first kohd)]
                                       (p/lisaa-rivi! koko
                                                      {:avain            :kulut
                                                       :rivi             jana/->Rivi
                                                       :alkuun?          true
                                                       :rivin-parametrit {:on-click #(e! (->AvaaLasku l))}}
                                                      [osa/->Teksti
                                                       (keyword (str "kk-hv-" u-k-lkm))
                                                       (or (pvm/kuukausi-ja-vuosi erapaiva)
                                                           "Eräpäivä") #_(:koontilaskun-kuukausi (first kohd))]
                                                      [osa/->Teksti
                                                       (keyword (str "era-" u-k-lkm))
                                                       (or (pvm/kuukausi-ja-vuosi erapaiva)
                                                           "Eräpäivä") #_(:koontilaskun-pvm (first kohd))]
                                                      [osa/->Teksti
                                                       (keyword (str "toimenpide-" u-k-lkm))
                                                       (some #(when (= (:toimenpideinstanssi %) toimenpideinstanssi) (:tpi-nimi %)) toimenpiteet) #_(:tehtava (first kohd))]
                                                      [osa/->Teksti
                                                       (keyword (str "tehtavaryhma-" u-k-lkm))
                                                       (or tehtavaryhma
                                                           "Tehtäväryhmä") #_(:tehtavaryhma (first kohd))]
                                                      [osa/->Teksti
                                                       (keyword (str "maara-" u-k-lkm))
                                                       summa]))
                                     (rest kohd)))))
                             taulukko
                             tulos)]
      (loki/log "LASKUT" tulos)
      (-> app
          (assoc :laskut tulos :taulukko paivitetty-taulukko)
          (update-in [:meta :haetaan] dec))))
  ToimenpidehakuOnnistui
  (process-event [{tulos :tulos} app]
    (loki/log "Tulo!!!s  " tulos)
    (let [kasitelty (set
                      (flatten
                        (mapv
                          (fn [{:keys [t3_id t3_nimi t3_koodi tpi_id tpi_nimi t2_id t2_nimi]}]
                            (vector
                              {:koodi t2_id :nimi t2_nimi :toimenpideinstanssi tpi_id :tpi-nimi tpi_nimi}
                              {:tehtavaryhma t3_nimi :koodi t3_koodi :id t3_id :toimenpideinstanssi tpi_id}))
                          tulos)))
          {:keys [tehtavaryhmat toimenpiteet]} (reduce
                                                 (fn [k asia]
                                                   (update k
                                                           (if (nil? (:tehtavaryhma asia))
                                                             :toimenpiteet
                                                             :tehtavaryhmat)
                                                           conj asia))
                                                 {:tehtavaryhmat [{:tehtavaryhma :johto-ja-hallintokorvaus} {:tehtavaryhma :erilliskustannukset}]
                                                  :toimenpiteet  []}
                                                 kasitelty)]
      (assoc app
        :toimenpiteet toimenpiteet
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
    [_ {taulukko :taulukko {:keys [kohdistukset koontilaskun-kuukausi
                                   laskun-numero lisatieto viite erapaiva]} :lomake :as app}]
    (let [urakka (-> @tila/yleiset :urakka :id)
          uudet-kulut (update app :kulut (fn [m]
                                           (apply
                                             conj
                                             m
                                             (map #(assoc % :koontilaskun-kuukausi koontilaskun-kuukausi
                                                            :erapaiva erapaiva) kohdistukset))))
          u-k-lkm (count (:kulut uudet-kulut))
          taulukko (reduce (fn [koko t]
                             (let [{:keys [tehtava maara tehtavaryhma]} t]
                               (p/lisaa-rivi!
                                 koko
                                 {:avain :kulut :rivi jana/->Rivi}
                                 [osa/->Teksti
                                  (keyword (str "pvm-" u-k-lkm))
                                  erapaiva]
                                 [osa/->Teksti
                                  (keyword (str "maksuera-" u-k-lkm))
                                  koontilaskun-kuukausi]
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
                           kohdistukset)
          kokonaissumma (reduce #(+ %1 (:summa %2)) 0 kohdistukset)]
      ; { :toimenpideinstanssi :tehtavaryhma
      ;   :tehtava :maksueratyyppi
      ;   :suorittaja :suoritus_alku :suoritus_loppu
      ;   :muokkaaja
      (tuck-apurit/post! :tallenna-lasku
                         {:urakka-id     urakka
                          :laskuerittely {:kohdistukset  kohdistukset
                                          :viite         viite
                                          :erapaiva      erapaiva
                                          :urakka        urakka
                                          :kokonaissumma kokonaissumma
                                          :laskun-numero laskun-numero
                                          :lisatieto     lisatieto
                                          :tyyppi        "laskutettava"}} ;TODO fix
                         {:onnistui            ->TallennusOnnistui
                          :onnistui-parametrit [{:avain :laskut}]
                          :epaonnistui         ->KutsuEpaonnistui
                          :paasta-virhe-lapi?  true})
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