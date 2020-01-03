(ns harja.tiedot.urakka.mhu-laskutus
  (:require [tuck.core :as tuck]
            [harja.loki :as loki]
            [harja.ui.taulukko.protokollat :as p]
            [harja.ui.taulukko.osa :as osa]
            [harja.ui.taulukko.jana :as jana]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.tiedot.urakka.urakka :as tila]
            [harja.pvm :as pvm]
            [reagent.core :as r])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]
                   [harja.tyokalut.tuck :refer [varmista-kasittelyjen-jarjestys]]))

(defrecord LuoKulutaulukko [])
(defrecord KulujenSyotto [auki?])
(defrecord TallennaKulu [])
(defrecord PaivitaLomake [polut-ja-arvot])
(defrecord AvaaLasku [lasku])

(defrecord LiiteLisatty [liite])

(defrecord LuoUusiAliurakoitsija [aliurakoitsija])

(defrecord HaeUrakanToimenpiteetJaTehtavaryhmat [urakka])
(defrecord HaeUrakanLaskut [hakuparametrit])
(defrecord HaeAliurakoitsijat [])
(defrecord HaeUrakanLaskutJaTiedot [hakuparametrit])

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

(defn- osien-paivitys-fn
  [funktiot]
  (fn [osat]
    (mapv
      (fn [osa]
        (let [paivitys (partial (get funktiot (p/osan-id osa)))]
          (paivitys osa)))
      osat)))

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

(defn- luo-paivitys-fn
  [& avain-arvot]
  (fn [osa]
    (apply
      (partial p/aseta-arvo osa)
      avain-arvot)))

(defn- luo-kulutaulukko
  [{:keys [toimenpiteet tehtavaryhmat]}]
  (loki/log "taulukon luonti")
  (let [paivitysfunktiot {"Pvm"          (luo-paivitys-fn
                                           :id :pvm
                                           :arvo "Pvm"
                                           :class #{"col-xs-1"})
                          "Maksuerä"     (luo-paivitys-fn
                                           :id :maksuera
                                           :arvo "Maksuerä"
                                           :class #{"col-xs-2"})
                          "Toimenpide"   (luo-paivitys-fn
                                           :id :toimenpide
                                           :arvo "Toimenpide"
                                           :class #{"col-xs-4"})
                          "Tehtäväryhmä" (luo-paivitys-fn
                                           :id :tehtavaryhma
                                           :arvo "Tehtäväryhmä"
                                           :class #{"col-xs-4"})
                          "Määrä"        (luo-paivitys-fn
                                           :id :maara
                                           :arvo "Määrä"
                                           :class #{"col-xs-1"})}
        otsikot-rivi (fn [rivi]
                       (-> rivi
                           (p/aseta-arvo :id :otsikko-rivi
                                         :class #{"table-default" "table-default-header"})
                           (p/paivita-arvo :lapset
                                           (osien-paivitys-fn paivitysfunktiot))))
        kulut-rivi (fn [rivi]
                     (-> rivi
                         (p/aseta-arvo :id :kulut-rivi
                                       :class #{"table-default-even"})))
        ; pvm {
        ;     :summa 0
        ;     :rivit [
        ;         laskun nro {
        ;             :rivit [
        ;                  { :summa 0 } ]
        ;             :summa 0 } ] }
        ;
        ;
        redusoija (fn []
                    true)
        valiotsikko-rivi (fn [lisaa-fn & solut]
                           (apply lisaa-fn (map (fn [solu]
                                                  (let [{:keys [otsikko arvo]} solu]
                                                    (loki/log "Rivi" otsikko arvo)
                                                    [osa/->Teksti
                                                     (keyword (gensym "vo-rivi-"))
                                                     (str (or otsikko
                                                              "Otsikko") arvo)]))
                                                solut)))
        flattaus-fn (fn [perustiedot kaikki nykyinen]
                      (apply conj kaikki (map #(merge perustiedot %) (:kohdistukset nykyinen))))
        flattaa (fn [flatattavat]
                  (let [flatatut (reduce (r/partial flattaus-fn (dissoc (first flatattavat) :kohdistukset)) [] flatattavat)]
                    (group-by :toimenpideinstanssi flatatut)))
        luo-taulukon-summarivi (fn [toimenpiteet tehtavaryhmat taulukko [tpi rivit]]
                                 (loki/log "RR" tpi rivit (count rivit))
                                 (reduce
                                   (fn [kaikki {:keys [erapaiva summa toimenpideinstanssi tehtavaryhma] :as rivi}]
                                     (loki/log "RR/Rivi" rivi)
                                     (p/lisaa-rivi! kaikki {:rivi             jana/->Rivi
                                                            :pelkka-palautus? true}
                                                    [osa/->Teksti
                                                     (keyword (gensym "erap"))
                                                     (str (when (= 1 (count rivit)) (pvm/pvm erapaiva)))
                                                     {:class #{"col-xs-1"}}]
                                                    [osa/->Teksti
                                                     (keyword (gensym "maksuera"))
                                                     (str (when (= 1 (count rivit)) (pvm/pvm erapaiva)))
                                                     {:class #{"col-xs-2"}}]
                                                    [osa/->Teksti
                                                     (keyword (gensym "toimenpideinstanssi"))
                                                     (str (some #(when (= toimenpideinstanssi (:toimenpideinstanssi %)) (:toimenpide %)) toimenpiteet))
                                                     {:class #{"col-xs-4"}}]
                                                    [osa/->Teksti
                                                     (keyword (gensym "tehtavaryhma"))
                                                     (str (some #(when (= tehtavaryhma (:id %)) (:tehtavaryhma %)) tehtavaryhmat))
                                                     {:class #{"col-xs-4"}}]
                                                    [osa/->Teksti
                                                     (keyword (gensym "summa"))
                                                     (str summa)
                                                     {:class #{"col-xs-1"}}]))
                                   (if-not (= 1 (count rivit))
                                     (p/lisaa-rivi! taulukko {:rivi             jana/->Rivi
                                                              :pelkka-palautus? true}
                                                    [osa/->Teksti
                                                     (keyword (gensym "erap"))
                                                     (str (pvm/pvm (:erapaiva (first rivit))))
                                                     {:class #{"col-xs-2"}}]
                                                    [osa/->Teksti
                                                     (keyword (gensym "erap"))
                                                     (str (some #(when (= tpi (:toimenpideinstanssi %)) (:toimenpide %)) toimenpiteet))
                                                     {:class #{"col-xs-offset-1 col-xs-4"}}]
                                                    [osa/->Teksti
                                                     (keyword (gensym "erap"))
                                                     (str "Yhteensä ")
                                                     {:class #{"col-xs-offset-3 col-xs-1"}}]
                                                    [osa/->Teksti
                                                     (keyword (gensym "erap"))
                                                     (str (reduce #(+ %1 (:summa %2)) 0 rivit))
                                                     {:class #{"col-xs-1"}}])
                                     taulukko)
                                   rivit)
                                 #_(loop [rivit kakki]
                                     (if (empty? ks)
                                       rivit
                                       (let [{:keys [summa tehtavaryhma toimenpideinstanssi]} (first ks)]
                                         (recur (rest ks)
                                                )))))
        luo-laskun-nro-otsikot (fn [rs [laskun-nro {summa :summa rivit :rivit}]]
                                 (loki/log (flattaa rivit))
                                 (reduce
                                   (r/partial luo-taulukon-summarivi toimenpiteet tehtavaryhmat)
                                   (if (= 0 laskun-nro)
                                     rs
                                     (valiotsikko-rivi
                                       (r/partial p/lisaa-rivi! rs {:rivi             jana/->Rivi
                                                                    :pelkka-palautus? true
                                                                    :rivin-parametrit {:class #{"table-default" "table-default-header" "table-default-thin"}}})
                                       {:otsikko "Koontilasku nro " :arvo laskun-nro}
                                       {:otsikko "Yhteensä " :arvo summa}))
                                   (flattaa rivit)))
        luo-paivamaara-otsikot (fn [koko [pvm {summa :summa rivit :rivit}]]
                                 (reduce luo-laskun-nro-otsikot
                                         (valiotsikko-rivi
                                           (r/partial p/lisaa-rivi! koko {:rivi             jana/->Rivi
                                                                          :pelkka-palautus? true
                                                                          :rivin-parametrit {:class #{"table-default" "table-default-header" "table-default-thin"}}})
                                           {:otsikko "Paivamaara " :arvo pvm}
                                           {:otsikko "Yhteensä " :arvo summa})
                                         rivit))
        jaottele-riveiksi-taulukkoon (fn [taulukko rivit {toimenpiteet :toimenpiteet tehtavaryhmat :tehtavaryhmat}]
                                       (let [taulukko-rivit (reduce luo-paivamaara-otsikot
                                                                    taulukko rivit)]
                                         (loki/log "RIVIT" rivit)
                                         (p/paivita-taulukko! taulukko-rivit)))]
    (muodosta-taulukko :kohdistetut-kulut-taulukko
                       {:otsikot     {:janan-tyyppi jana/Rivi
                                      :osat         [osa/Teksti osa/Teksti osa/Teksti osa/Teksti osa/Teksti]}
                        :valiotsikot {:janan-tyyppi jana/Rivi
                                      :osat         [osa/Teksti osa/Teksti]}
                        :kulut       {:janan-tyyppi jana/Rivi
                                      :osat         [osa/Teksti osa/Teksti osa/Teksti osa/Teksti osa/Teksti]}}
                       ["Pvm" "Maksuerä" "Toimenpide" "Tehtäväryhmä" "Määrä"]
                       [:otsikot otsikot-rivi]
                       {:class                 #{}
                        :taulukon-paivitys-fn! (fn [taulukko rivit]
                                                 (loki/log "UUSI" taulukko "UUSI" rivit)
                                                 (let [uusi (if (nil? rivit)
                                                              taulukko
                                                              (jaottele-riveiksi-taulukkoon taulukko rivit @tila/laskutus-kohdistetut-kulut))]
                                                   (->
                                                     tila/laskutus-kohdistetut-kulut
                                                     (swap! assoc-in [:taulukko] uusi)
                                                     :taulukko)))})))

(defn formatoi-tulos [uudet-rivit]
  (let [reduseri (fn [k [avain arvo]]
                   (update k avain
                           (fn [m]
                             (-> m
                                 (assoc :rivit arvo :summa
                                        (reduce #(+ %1 (:kokonaissumma %2)) 0 arvo))))))
        pvm-mukaan (reduce reduseri
                           {} (group-by #(pvm/kuukausi-ja-vuosi (:erapaiva %)) uudet-rivit))
        nro-mukaan (into {} (map (fn [[paivamaara rivit-ja-summa]]
                                   [paivamaara (assoc rivit-ja-summa :rivit (reduce reduseri {}
                                                                                    (group-by #(or (:laskun-numero %)
                                                                                                   0) (:rivit rivit-ja-summa))))])
                                 pvm-mukaan))]
    (loki/log "paivita" uudet-rivit nro-mukaan)
    nro-mukaan))

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
            :liite-koko koko))

  ;; SUCCESS

  TallennusOnnistui
  (process-event [{tulos :tulos {:keys [avain tuloksen-tallennus-fn]} :parametrit} app]
    (loki/log avain tulos)
    (let [assoc-fn
          (if (vector? avain)
            assoc-in
            assoc)]
      (-> app
          (assoc-fn avain tulos)
          (assoc :taulukko (p/paivita-taulukko! (luo-kulutaulukko app) (formatoi-tulos tulos)))
          (assoc :syottomoodi false))))
  AliurakoitsijahakuOnnistui
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :aliurakoitsijat tulos)
        (update-in [:meta :haetaan] dec)))
  LaskuhakuOnnistui
  (process-event [{tulos :tulos} {:keys [taulukko kulut toimenpiteet laskut] :as app}]
    (loki/log "laskut haettu")
    (let
      [;e! (tuck/current-send-function)
       ;u-k-lkm (count kulut)
       #_(reduce
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
          (assoc :laskut tulos
                 :taulukko (p/paivita-taulukko! (luo-kulutaulukko app) (formatoi-tulos tulos))
                 )
          (update-in [:meta :haetaan] dec))))
  ToimenpidehakuOnnistui
  (process-event [{tulos :tulos} app]
    (loki/log "Tulo!!!s  " tulos)
    (let [kasitelty (set
                      (flatten
                        (mapv
                          (fn [{:keys [tehtavaryhma-id tehtavaryhma-nimi toimenpide jarjestys toimenpide-id toimenpideinstanssi]}]
                            (vector
                              {:toimenpideinstanssi toimenpideinstanssi :toimenpide-id toimenpide-id :toimenpide toimenpide :jarjestys jarjestys}
                              {:tehtavaryhma tehtavaryhma-nimi :id tehtavaryhma-id :toimenpide toimenpide-id :toimenpideinstanssi toimenpideinstanssi :jarjestys jarjestys}))
                          tulos)))
          {:keys [tehtavaryhmat toimenpiteet]} (reduce
                                                 (fn [k asia]
                                                   (update k
                                                           (if (nil? (:tehtavaryhma asia))
                                                             :toimenpiteet
                                                             :tehtavaryhmat)
                                                           conj asia))
                                                 {:tehtavaryhmat []
                                                  :toimenpiteet  []}
                                                 (sort-by :jarjestys kasitelty))]
      (assoc app
        :toimenpiteet toimenpiteet
        :tehtavaryhmat tehtavaryhmat)))

  ;; FAIL

  KutsuEpaonnistui
  (process-event [{:keys [tulos]} app]
    (loki/log "tai ulos!!!!!! " tulos)
    app)

  ;; HAUT

  HaeUrakanLaskutJaTiedot
  (process-event [{:keys [hakuparametrit]} app]
    (varmista-kasittelyjen-jarjestys
      (tuck-apurit/post! :tehtavaryhmat-ja-toimenpiteet
                         {:urakka-id (:id hakuparametrit)}
                         {:onnistui           ->ToimenpidehakuOnnistui
                          :epaonnistui        ->KutsuEpaonnistui
                          :paasta-virhe-lapi? true})
      (tuck-apurit/post! :kaikki-laskuerittelyt
                         {:urakka-id (:id hakuparametrit)}
                         {:onnistui           ->LaskuhakuOnnistui
                          :epaonnistui        ->KutsuEpaonnistui
                          :paasta-virhe-lapi? true}))
    (update-in app [:meta :haetaan] inc))
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
  HaeUrakanToimenpiteetJaTehtavaryhmat
  (process-event
    [{:keys [urakka]} app]
    (tuck-apurit/post! :tehtavaryhmat-ja-toimenpiteet
                       {:urakka-id urakka}
                       {:onnistui           ->ToimenpidehakuOnnistui
                        :epaonnistui        ->KutsuEpaonnistui
                        :paasta-virhe-lapi? true})
    app)
  AvaaLasku
  (process-event [{lasku :lasku} app]
    (loki/log "avaan laskun" lasku app)
    (assoc app :syottomoodi true
               :lomake (lasku->lomake lasku)))

  ;; VIENNIT

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
          ;taulukko
          #_(reduce (fn [koko t]
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
          kokonaissumma (reduce #(+ %1 (:summa %2)) 0 kohdistukset)
          ;paivita-laskut
          #_(fn [tulos laskut]
              (apply conj laskut (reduce
                                   (r/partial
                                     (fn [perustiedot kaikki k]
                                       (loki/log "MOIIII" perustiedot kaikki k)
                                       (conj kaikki (merge perustiedot k)))
                                     (dissoc tulos :kohdistukset))
                                   []
                                   (:kohdistukset tulos))))]
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
                                          :laskun-numero (js/parseFloat laskun-numero)
                                          :lisatieto     lisatieto
                                          :tyyppi        "laskutettava"}} ;TODO fix
                         {:onnistui            ->TallennusOnnistui
                          :onnistui-parametrit [{:avain :laskut
                                                 ;:tuloksen-tallennus-fn paivita-laskut
                                                 }]
                          :epaonnistui         ->KutsuEpaonnistui
                          ;:paasta-virhe-lapi?  true
                          })
      (assoc app :taulukko taulukko))
    app)

  ;; FORMITOIMINNOT

  KulujenSyotto
  (process-event
    [{:keys [auki?]} app]
    (assoc app :syottomoodi auki?))
  LuoKulutaulukko
  (process-event
    [_ app]
    (assoc app :taulukko (luo-kulutaulukko app))))