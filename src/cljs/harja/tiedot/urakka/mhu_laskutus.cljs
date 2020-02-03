(ns harja.tiedot.urakka.mhu-laskutus
  (:require
    [clojure.string :as string]
    [tuck.core :as tuck]
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

(defrecord MaksueraHakuOnnistui [tulos])
(defrecord TallennusOnnistui [tulos parametrit])
(defrecord ToimenpidehakuOnnistui [tulos])
(defrecord LaskuhakuOnnistui [tulos])
(defrecord AliurakoitsijahakuOnnistui [tulos])

(defrecord LataaLiite [id])
(defrecord PoistaLiite [id])

(defn validoi-lomake [lomake & kentat-ja-validaatiot]
  (let [kentat-ja-validaatiot (partition 2 kentat-ja-validaatiot)
        validaatiot (:validius (meta lomake))
        validointi-reduseri (r/partial
                              (fn [lomake kaikki [kentta validointi-fn]]
                                (let [assoc-fn (if (vector? kentta) assoc-in assoc)
                                      get-fn (if (vector? kentta) get-in get)]
                                  (assoc-fn kaikki kentta (validointi-fn (get-fn lomake kentta)))))
                              lomake)
        lasketut-validaatiot (reduce validointi-reduseri
                                     validaatiot
                                     kentat-ja-validaatiot)]
    (vary-meta lomake assoc :validius lasketut-validaatiot)))

(defn lomake-validi? [lomake])

(defn parsi-summa [summa]
  (loki/log "PARSI " summa)
  (if (re-matches #"\d+(?:\.?,?\d+)?" (str summa))
    (-> summa
        str
        (string/replace "," ".")
        js/parseFloat)
    0))

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
              (into [acc polku arvo] (when (fn? arvo) args))))
          lomake
          (partition 2 polut-ja-arvot)))

(defn lasku->lomake [{:keys [kohdistukset] :as lasku}]
  (let [{aliurakoitsija :suorittaja} lasku]
    (-> lasku
        (dissoc :suorittaja)
        #_(update :kohdistukset (fn [ks] (mapv (fn [kohdistukset]
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
  [{:keys [toimenpiteet tehtavaryhmat maksuerat]}]
  (loki/log "taulukon luonti")
  (let [e! (tuck/current-send-function)
        paivitysfunktiot {"Pvm"          (luo-paivitys-fn
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
                                                  (let [{:keys [otsikko arvo luokka]} solu]
                                                    [osa/->Teksti
                                                     (keyword (gensym "vo-rivi-"))
                                                     (str (or otsikko
                                                              "Otsikko") arvo)
                                                     (merge {} (when-not (nil? luokka) {:class luokka}))]))
                                                solut)))
        flattaus-fn (fn [perustiedot kaikki nykyinen]
                      (apply conj kaikki (map #(merge perustiedot (select-keys nykyinen [:kohdistukset]) %) (:kohdistukset nykyinen))))
        flattaa (fn [flatattavat]
                  (let [flatatut (reduce (r/partial flattaus-fn (dissoc (first flatattavat) :kohdistukset)) [] flatattavat)]
                    (sort-by :toimenpideinstanssi flatatut)))
        even? (r/atom true)
        hae-avaimella-fn (fn [verrattava haettava palautettava]
                           (fn [kohde]
                             (let [palautuksen-avain (or palautettava
                                                         haettava)]
                               (when (= verrattava (if (or (vector? haettava)
                                                           (seq? haettava))
                                                     (get-in kohde haettava)
                                                     (haettava kohde)))
                                 (palautuksen-avain kohde)))))
        luo-taulukon-summarivi (fn [toimenpiteet tehtavaryhmat taulukko [tpi rivit]]
                                 (let [taulukko (if-not (= 1 (count rivit))
                                                  (do
                                                    (swap! even? not)
                                                    (p/lisaa-rivi! taulukko {:rivi             jana/->Rivi
                                                                             :pelkka-palautus? true
                                                                             :rivin-parametrit {:class #{"table-default" "table-default-strong"
                                                                                                         (str "table-default-"
                                                                                                              (if (true? @even?)
                                                                                                                "even"
                                                                                                                "odd"))}}}
                                                                   [osa/->Teksti
                                                                    (keyword (gensym "lbl-erapv-"))
                                                                    (str (pvm/pvm (-> rivit first :erapaiva)))
                                                                    {:class #{"col-xs-2"}}]
                                                                   [osa/->Teksti
                                                                    (keyword (gensym "lbl-tpi-"))
                                                                    (str (some #(when (= tpi (:toimenpideinstanssi %)) (:toimenpide %)) toimenpiteet))
                                                                    {:class #{"col-xs-offset-1 col-xs-4"}}]
                                                                   [osa/->Teksti
                                                                    (keyword (gensym "lbl-yht-"))
                                                                    (str "Yhteensä ")
                                                                    {:class #{"col-xs-offset-3 col-xs-1"}}]
                                                                   [osa/->Teksti
                                                                    (keyword (gensym "lbl-sum-"))
                                                                    (str (reduce #(+ %1 (:summa %2)) 0 rivit))
                                                                    {:class #{"col-xs-1"}}]))
                                                  taulukko)]
                                   (reduce (fn [taulukko {:keys [erapaiva toimenpideinstanssi tehtavaryhma summa] :as rivi}]
                                             (swap! even? not)
                                             (p/lisaa-rivi! taulukko {:rivi             jana/->Rivi
                                                                      :pelkka-palautus? true
                                                                      :rivin-parametrit {:on-click #(e! (->AvaaLasku rivi))
                                                                                         :class    #{"table-default"
                                                                                                     "table-default-selectable"
                                                                                                     (str "table-default-"
                                                                                                          (if (true? @even?)
                                                                                                            "even"
                                                                                                            "odd"))}}}
                                                            [osa/->Teksti
                                                             (keyword (gensym "erapv-"))
                                                             (str
                                                               (when (= 1 (count rivit)) (pvm/pvm erapaiva)))
                                                             {:class #{"col-xs-1"}}]
                                                            [osa/->Teksti
                                                             (keyword (gensym "maksuera-"))
                                                             (str "HA" (some (hae-avaimella-fn toimenpideinstanssi [:toimenpideinstanssi :id] :numero) maksuerat))
                                                             {:class #{"col-xs-2"}}]
                                                            [osa/->Teksti
                                                             (keyword (gensym "toimenpideinstanssi-"))
                                                             (str (some (hae-avaimella-fn toimenpideinstanssi :toimenpideinstanssi :toimenpide) toimenpiteet))
                                                             {:class #{"col-xs-4"}}]
                                                            [osa/->Teksti
                                                             (keyword (gensym "tehtavaryhma-"))
                                                             (str (some (hae-avaimella-fn tehtavaryhma :id :tehtavaryhma) tehtavaryhmat))
                                                             {:class #{"col-xs-4"}}]
                                                            [osa/->Teksti
                                                             (keyword (gensym "summa-"))
                                                             (str summa)
                                                             {:class #{"col-xs-1"}}]))
                                           taulukko
                                           rivit)))

        luo-laskun-nro-otsikot (fn [rs [laskun-nro {summa :summa rivit :rivit}]]
                                 (loki/log (flattaa rivit))
                                 (reset! even? true)
                                 (let [flatatut (flattaa rivit)]
                                   (reduce
                                     (r/partial luo-taulukon-summarivi toimenpiteet tehtavaryhmat)
                                     (if (= 0 laskun-nro)
                                       rs
                                       (valiotsikko-rivi
                                         (r/partial p/lisaa-rivi! rs {:rivi             jana/->Rivi
                                                                      :pelkka-palautus? true
                                                                      :rivin-parametrit {:class #{"table-default" "table-default-header" "table-default-thin"}}})
                                         {:otsikko "Koontilasku nro " :arvo laskun-nro :luokka #{"col-xs-4"}}
                                         {:otsikko "Yhteensä " :arvo summa :luokka #{"col-xs-offset-6" "col-xs-2"}}))
                                     (group-by :toimenpideinstanssi flatatut))))
        luo-paivamaara-otsikot (fn [koko [pvm {summa :summa rivit :rivit}]]
                                 (reduce luo-laskun-nro-otsikot
                                         (valiotsikko-rivi
                                           (r/partial p/lisaa-rivi! koko {:rivi             jana/->Rivi
                                                                          :pelkka-palautus? true
                                                                          :rivin-parametrit {:class #{"table-default" "table-default-header" "table-default-thin-strong"}}})
                                           {:otsikko "Paivamaara " :arvo pvm :luokka #{"col-xs-4"}}
                                           {:otsikko "Yhteensä " :arvo summa :luokka #{"col-xs-offset-6" "col-xs-2"}})
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

(defn resetoi-kulut []
  tila/kulut-lomake-default)

(defn formatoi-tulos [uudet-rivit]
  (loki/log "uudet" uudet-rivit)
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

  MaksueraHakuOnnistui
  (process-event [{tulos :tulos} app]
    (assoc app :maksuerat tulos))
  TallennusOnnistui
  (process-event [{tulos :tulos {:keys [avain tilan-paivitys-fn]} :parametrit} app]
    (loki/log avain tulos)
    (let [tilan-paivitys-fn (or tilan-paivitys-fn
                                #(identity %2))
          assoc-fn (cond
                     (nil? avain) (fn [app & _] app)
                     (vector? avain) assoc-in
                     :else assoc)]
      (-> app
          (assoc-fn avain tulos)
          (tilan-paivitys-fn tulos))))
  AliurakoitsijahakuOnnistui
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :aliurakoitsijat tulos)
        (update-in [:parametrit :haetaan] dec)))
  LaskuhakuOnnistui
  (process-event [{tulos :tulos} {:keys [taulukko kulut toimenpiteet laskut] :as app}]
    (loki/log "laskut haettu")
    (loki/log "LASKUT" tulos)
    (-> app
        (assoc :kulut tulos
               :taulukko (p/paivita-taulukko!
                           (luo-kulutaulukko app)
                           (formatoi-tulos tulos)))
        (update-in [:parametrit :haetaan] dec)))
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
      (tuck-apurit/post! :hae-urakan-maksuerat
                         (:id hakuparametrit)
                         {:onnistui           ->MaksueraHakuOnnistui
                          :epaonnistui        ->KutsuEpaonnistui
                          :paasta-virhe-lapi? true})
      (tuck-apurit/post! :kaikki-laskuerittelyt
                         {:urakka-id (:id hakuparametrit)}
                         {:onnistui           ->LaskuhakuOnnistui
                          :epaonnistui        ->KutsuEpaonnistui
                          :paasta-virhe-lapi? true}))
    (update-in app [:parametrit :haetaan] inc))
  HaeAliurakoitsijat
  (process-event [_ app]
    (tuck-apurit/get! :aliurakoitsijat
                      {:onnistui           ->AliurakoitsijahakuOnnistui
                       :epaonnistui        ->KutsuEpaonnistui
                       :paasta-virhe-lapi? true})
    (update-in app [:parametrit :haetaan] inc))
  HaeUrakanLaskut
  (process-event [{:keys [hakuparametrit]} app]
    (tuck-apurit/post! :kaikki-laskuerittelyt
                       {:urakka-id (:id hakuparametrit)}
                       {:onnistui           ->LaskuhakuOnnistui
                        :epaonnistui        ->KutsuEpaonnistui
                        :paasta-virhe-lapi? true})
    (update-in app [:parametrit :haetaan] inc))
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
                        :onnistui-parametrit [{:avain             :aliurakoitsijat
                                               :tilan-paivitys-fn (fn [{:keys [aliurakoitsijat] :as app} _]
                                                                    (let [alik-id (some #(when (= (:nimi aliurakoitsija) (:nimi %)) (:id %)) aliurakoitsijat)]
                                                                      (loki/log "alik tulos" alik-id)
                                                                      (update app :lomake (fn [lomake]
                                                                                            (assoc lomake :aliurakoitsija alik-id
                                                                                                          :suorittaja-nimi (:nimi aliurakoitsija))))))}]
                        :epaonnistui         ->KutsuEpaonnistui
                        :paasta-virhe-lapi?  true})
    app)
  TallennaKulu
  (process-event
    [_ {aliurakoitsijat :aliurakoitsijat {:keys [kohdistukset koontilaskun-kuukausi aliurakoitsija
                                                                    laskun-numero lisatieto viite erapaiva] :as lomake} :lomake :as app}]
    (let [urakka (-> @tila/yleiset :urakka :id)
          kokonaissumma (reduce #(+ %1 (:summa %2)) 0 kohdistukset)
          {validoi-fn :validoi} (meta lomake)
          validoitu-lomake (validoi-fn lomake)
          {validi? :validi?} (meta validoitu-lomake)]
      ; { :toimenpideinstanssi :tehtavaryhma
      ;   :tehtava :maksueratyyppi
      ;   :suorittaja :suoritus_alku :suoritus_loppu
      ;   :muokkaaja
      (loki/log "validi?" validi? validoitu-lomake)
      (when (true? validi?)
        (tuck-apurit/post! :tallenna-lasku
                           {:urakka-id     urakka
                            :laskuerittely {:kohdistukset    kohdistukset
                                            :viite           viite
                                            :erapaiva        erapaiva
                                            :urakka          urakka
                                            :suorittaja-nimi (some #(when (= aliurakoitsija (:id %)) (:nimi %)) aliurakoitsijat)
                                            :kokonaissumma   kokonaissumma
                                            :laskun-numero   (js/parseFloat laskun-numero)
                                            :lisatieto       lisatieto
                                            :tyyppi          "laskutettava"}} ;TODO fix
                           {:onnistui            ->TallennusOnnistui
                            :onnistui-parametrit [{:tilan-paivitys-fn (fn [app tulos]
                                                                        (loki/log "app" app (:kulut app))
                                                                        (as-> app a
                                                                              (update a :kulut (fn [kulut]
                                                                                                 (conj
                                                                                                   (filter
                                                                                                     #(not= (:id tulos) (:id %))
                                                                                                     kulut)
                                                                                                   tulos)))
                                                                              (assoc a :taulukko (p/paivita-taulukko!
                                                                                                   (luo-kulutaulukko a)
                                                                                                   (formatoi-tulos (:kulut a)))
                                                                                       :syottomoodi false)
                                                                              (update a :lomake resetoi-kulut)))}]
                            :epaonnistui         ->KutsuEpaonnistui}))
      (assoc app :lomake validoitu-lomake)))

  ;; FORMITOIMINNOT

  KulujenSyotto
  (process-event
    [{:keys [auki?]} app]
    (-> app
        (update :lomake resetoi-kulut)
        (assoc :syottomoodi auki?)))
  LuoKulutaulukko
  (process-event
    [_ app]
    (assoc app :taulukko (luo-kulutaulukko app))))