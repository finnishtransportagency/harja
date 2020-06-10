(ns harja.tiedot.urakka.mhu-laskutus
  (:require
    [clojure.string :as string]
    [tuck.core :as tuck]
    [harja.loki :as loki]
    harja.ui.taulukko.taulukko
    [harja.ui.taulukko.protokollat :as p]
    [harja.ui.taulukko.osa :as osa]
    [harja.ui.taulukko.jana :as jana]
    [harja.tyokalut.tuck :as tuck-apurit]
    [harja.tiedot.urakka.urakka :as tila]
    [harja.pvm :as pvm]
    [harja.fmt :as fmt]
    [reagent.core :as r]
    [clojure.string :as str])
  (:require-macros [harja.ui.taulukko.tyokalut :refer [muodosta-taulukko]]
                   [harja.tyokalut.tuck :refer [varmista-kasittelyjen-jarjestys]]))

(defrecord LuoKulutaulukko [])
(defrecord KulujenSyotto [auki?])
(defrecord TallennaKulu [])
(defrecord PaivitaLomake [polut-ja-arvot optiot])
(defrecord AvaaLasku [lasku])
(defrecord NakymastaPoistuttiin [])
(defrecord PoistaKulu [id])
(defrecord PoistoOnnistui [tulos])
(defrecord AsetaHakuparametri [polku arvo])

(defrecord LiiteLisatty [liite])
(defrecord LiitteenPoistoOnnistui [tulos parametrit])

(defrecord LuoUusiAliurakoitsija [aliurakoitsija optiot])
(defrecord PaivitaAliurakoitsija [aliurakoitsija])

(defrecord HaeUrakanToimenpiteetJaTehtavaryhmat [urakka])
(defrecord HaeUrakanLaskut [hakuparametrit])
(defrecord HaeUrakanLaskutJaTiedot [hakuparametrit])
(defrecord OnkoLaskunNumeroKaytossa [laskun-numero])

(defrecord KutsuEpaonnistui [tulos parametrit])

(defrecord TarkistusOnnistui [tulos parametrit])
(defrecord MaksueraHakuOnnistui [tulos])
(defrecord TallennusOnnistui [tulos parametrit])
(defrecord ToimenpidehakuOnnistui [tulos])
(defrecord LaskuhakuOnnistui [tulos])
(defrecord AliurakoitsijahakuOnnistui [tulos])

(defrecord LataaLiite [id])
(defrecord PoistaLiite [id])

(defn parsi-summa [summa]
  (cond
    (not (string? summa)) summa
    (re-matches #"-?\d+(?:\.?,?\d+)?" (str summa))
    (-> summa
        str
        (string/replace "," ".")
        js/parseFloat)
    (not (or (str/blank? summa)
             (nil? summa))) summa
    :else 0))

(defn- osien-paivitys-fn
  [funktiot]
  (fn [osat]
    (mapv
      (fn [osa]
        (let [paivitys (partial (get funktiot (p/osan-id osa)))]
          (paivitys osa)))
      osat)))

(defn- merkitse-kentta-kosketuksi
  [{:keys [validoitava?]} args acc [polku arvo]]
  (let [paivitetty-lomake
        (apply
          (cond (and
                  (vector? polku)
                  (fn? arvo)) update-in
                (vector? polku) assoc-in
                (fn? arvo) update
                :else assoc)
          (into [acc polku arvo] (when (fn? arvo) args)))]
    (if (true? validoitava?)
      (vary-meta paivitetty-lomake (fn [lomake-meta]
                                     (update-in
                                       lomake-meta
                                       (conj [:validius]
                                             (if (keyword? polku)
                                               (vector polku)
                                               polku))
                                       (fn [meta-kentta]
                                         (assoc meta-kentta :tarkistettu? false
                                                            :koskettu? true)))))
      paivitetty-lomake)))

(defn lomakkeen-paivitys
  [lomake polut-ja-arvot {:keys [jalkiprosessointi-fn] :as optiot} & args]
  (let [jalkiprosessointi (or jalkiprosessointi-fn
                              identity)]
    (jalkiprosessointi
      (reduce (r/partial merkitse-kentta-kosketuksi optiot args)
              lomake
              (partition 2 polut-ja-arvot)))))

(defn kulu->lomake [lasku]
  (let [{suorittaja :suorittaja} lasku]
    (-> lasku
        (dissoc :suorittaja)
        (assoc :aliurakoitsija suorittaja)
        (update :kohdistukset (fn [kohdistukset]
                                (mapv #(assoc %
                                         :lisatyo?
                                         (if (= "lisatyo" (:maksueratyyppi %))
                                           true
                                           false))
                                      kohdistukset)))
        (with-meta (tila/kulun-validointi-meta lasku)))))

(defn- luo-paivitys-fn
  [& avain-arvot]
  (fn [osa]
    (apply
      (partial p/aseta-arvo osa)
      avain-arvot)))


(def kulutaulukon-paivitysfunktiot
  {"Pvm"          (luo-paivitys-fn
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
                    :class #{"col-xs-1"})})

(defn- hae-avaimella-fn [{:keys [verrattava haettava palautettava]}]
  (fn [kohde]
    (let [palautuksen-avain (or palautettava
                                haettava)]
      (when (= verrattava (if (or (vector? haettava)
                                  (seq? haettava))
                            (get-in kohde haettava)
                            (haettava kohde)))
        (palautuksen-avain kohde)))))

(defn- luo-taulukon-summarivi [e! toimenpiteet tehtavaryhmat maksuerat taulukko [tpi rivit]]
  (let [taulukko (if-not (= 1 (count rivit))
                   (p/lisaa-rivi! taulukko {:rivi             jana/->Rivi
                                            :pelkka-palautus? true
                                            :rivin-parametrit {:class #{"table-default"
                                                                        "table-default-strong"
                                                                        "table-default-odd"}}}
                                  [osa/->Teksti
                                   (keyword (gensym "lbl-erapv-"))
                                   (str (pvm/pvm (-> rivit first :erapaiva)))
                                   {:class #{"col-xs-2"}}]
                                  [osa/->Teksti
                                   (keyword (gensym "lbl-tpi-"))
                                   (str (some #(when (= tpi (:toimenpideinstanssi %)) (:toimenpide %)) toimenpiteet))
                                   {:class #{"col-xs-offset-1 col-xs-4"}}]
                                  [osa/->Teksti
                                   (keyword (gensym "lbl-sum-"))
                                   (fmt/euro (reduce #(+ %1 (:summa %2)) 0 rivit))
                                   {:class #{"col-xs-offset-4" "col-xs-1"}}])
                   taulukko)]
    (loop [taulukko taulukko
           parillinen? true
           [{:keys [erapaiva toimenpideinstanssi tehtavaryhma summa maksueratyyppi] :as rivi} & rivit] rivit]
      (if-not rivi
        taulukko
        (recur (p/lisaa-rivi! taulukko {:rivi             jana/->Rivi
                                        :pelkka-palautus? true
                                        :rivin-parametrit {:on-click #(e! (->AvaaLasku rivi))
                                                           :class    #{"table-default"
                                                                       "table-default-selectable"
                                                                       (str "table-default-"
                                                                            (if (true? parillinen?)
                                                                              "even"
                                                                              "odd"))}}}
                              [osa/->Teksti
                               (keyword (gensym "erapv-"))
                               (str
                                 #_(when (= 1 (count rivit))) (pvm/pvm erapaiva))
                               {:class #{"col-xs-1"}}]
                              [osa/->Teksti
                               (keyword (gensym "maksuera-"))
                               (str "HA" (some (hae-avaimella-fn {:verrattava   toimenpideinstanssi
                                                                  :haettava     [:toimenpideinstanssi :id]
                                                                  :palautettava :numero})
                                               maksuerat))
                               {:class #{"col-xs-2"}}]
                              [osa/->Teksti
                               (keyword (gensym "toimenpideinstanssi-"))
                               (str (some (hae-avaimella-fn {:verrattava   toimenpideinstanssi
                                                             :haettava     :toimenpideinstanssi
                                                             :palautettava :toimenpide})
                                          toimenpiteet))
                               {:class #{"col-xs-4"}}]
                              [osa/->Teksti
                               (keyword (gensym "tehtavaryhma-"))
                               (str (if (= maksueratyyppi "lisatyo")
                                      "Lisätyö"
                                      (some (hae-avaimella-fn {:verrattava   tehtavaryhma
                                                               :haettava     :id
                                                               :palautettava :tehtavaryhma})
                                            tehtavaryhmat)))
                               {:class #{"col-xs-4"}}]
                              [osa/->Teksti
                               (keyword (gensym "summa-"))
                               (fmt/euro summa)
                               {:class #{"col-xs-1"}}])
               (not parillinen?)
               rivit)))))

(defn- luo-kulutaulukko
  [{:keys [toimenpiteet tehtavaryhmat maksuerat]}]
  (let [e! (tuck/current-send-function)
        paivitysfunktiot kulutaulukon-paivitysfunktiot
        otsikot-rivi (fn [rivi]
                       (-> rivi
                           (p/aseta-arvo :id :otsikko-rivi
                                         :class #{"table-default" "table-default-header"})
                           (p/paivita-arvo :lapset
                                           (osien-paivitys-fn paivitysfunktiot))))
        ; pvm {
        ;     :summa 0
        ;     :rivit [
        ;         laskun nro {
        ;             :rivit [
        ;                  { :summa 0 } ]
        ;             :summa 0 } ] }
        ;
        ;

        valiotsikko-rivi (fn [lisaa-fn & solut]
                           (apply lisaa-fn (map (fn [solu]
                                                  (let [{:keys [otsikko arvo luokka]} solu]
                                                    [osa/->Teksti
                                                     (keyword (gensym "vo-rivi-"))
                                                     (str (or otsikko
                                                              "Otsikko") arvo)
                                                     (merge {} (when-not (nil? luokka) {:class luokka}))]))
                                                solut)))
        flattaus-fn (fn [kaikki nykyinen]
                      ;; Yhdistä perustiedot ja kohdistukset
                      (apply conj
                             kaikki
                             (map
                               #(merge nykyinen
                                       (select-keys nykyinen
                                                    [:kohdistukset])
                                       %)
                               (:kohdistukset nykyinen))))
        flattaa (fn [flatattavat]
                  (let [flatatut (reduce flattaus-fn
                                         []
                                         flatattavat)]
                    (sort-by :toimenpideinstanssi flatatut)))
        even? (r/atom true)
        luo-laskun-nro-otsikot (fn [rs [laskun-nro {summa :summa rivit :rivit}]]
                                 (reset! even? true)
                                 (let [flatatut (flattaa rivit)]
                                   (reduce
                                     (r/partial luo-taulukon-summarivi e! toimenpiteet tehtavaryhmat maksuerat)
                                     (if (= 0 laskun-nro)
                                       rs
                                       (valiotsikko-rivi
                                         (r/partial p/lisaa-rivi!
                                                    rs
                                                    {:rivi             jana/->Rivi
                                                     :pelkka-palautus? true
                                                     :rivin-parametrit {:class #{"table-default"
                                                                                 "table-default-header"
                                                                                 "table-default-thin"}}})
                                         {:otsikko "Koontilasku nro "
                                          :arvo    laskun-nro
                                          :luokka  #{"col-xs-4"}}
                                         {:otsikko ""
                                          :arvo    (fmt/euro summa)
                                          :luokka  #{"col-xs-offset-7" "col-xs-1"}}))
                                     (group-by :toimenpideinstanssi flatatut))))
        luo-paivamaara-otsikot (fn [koko [pvm {summa :summa rivit :rivit}]]
                                 ;; pvm tulee muodossa vvvv/kk
                                 (let [[vvvv kk] (map #(js/parseInt %)
                                                      (str/split pvm #"/"))]
                                   (reduce luo-laskun-nro-otsikot
                                           (valiotsikko-rivi
                                             (r/partial p/lisaa-rivi!
                                                        koko
                                                        {:rivi             jana/->Rivi
                                                         :pelkka-palautus? true
                                                         :rivin-parametrit {:class (cond-> #{"table-default"
                                                                                             "table-default-header"
                                                                                             "table-default-thin-strong"}
                                                                                           (not= (count (:rivit koko))
                                                                                              1)
                                                                                           (conj "top-margin-16px"))}})
                                             {:otsikko ""
                                              :arvo    (str (pvm/kk-fmt kk)
                                                            "kuu "
                                                            vvvv
                                                            " yhteensä")
                                              :luokka  #{"col-xs-4"}}
                                             {:otsikko ""
                                              :arvo    (fmt/euro summa)
                                              :luokka  #{"col-xs-offset-7" "col-xs-1"}})
                                           rivit)))
        jaottele-riveiksi-taulukkoon (fn [taulukko rivit]
                                       (let [taulukko-rivit (reduce luo-paivamaara-otsikot
                                                                    taulukko
                                                                    rivit)]
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
                                                 (let [uusi (if (nil? rivit)
                                                              taulukko
                                                              (jaottele-riveiksi-taulukkoon taulukko rivit))]
                                                   (->
                                                     tila/laskutus-kohdistetut-kulut
                                                     (swap! assoc-in [:taulukko] uusi)
                                                     :taulukko)))})))

(defn resetoi-kulut []
  tila/kulut-lomake-default)

(defn- resetoi-kulunakyma []
  tila/kulut-default)

(defn jarjesta-vuoden-ja-kuukauden-mukaan
  [pvm1 pvm2]
  (let [[vvvv1 kk1] (str/split pvm1 #"/")
        [vvvv2 kk2] (str/split pvm2 #"/")
        vvvv1 (js/parseInt vvvv1)
        vvvv2 (js/parseInt vvvv2)
        kk1 (js/parseInt kk1)
        kk2 (js/parseInt kk2)]
    (if (= vvvv1 vvvv2)
      (> kk1 kk2)
      (> vvvv1 vvvv2))))

(defn formatoi-tulos [uudet-rivit]
  (let [reduseri (fn [k [avain arvo]]
                   (update k avain
                           (fn [m]
                             (-> m
                                 (assoc :rivit arvo :summa
                                        (reduce #(+ %1 (:kokonaissumma %2)) 0 arvo))))))
        pvm-mukaan (reduce reduseri
                           {} (group-by #(pvm/kokovuosi-ja-kuukausi (:erapaiva %)) uudet-rivit))
        nro-mukaan (into {}
                         (sort-by
                           first
                           jarjesta-vuoden-ja-kuukauden-mukaan
                           (map (fn [[paivamaara rivit-ja-summa]]
                                  [paivamaara (assoc rivit-ja-summa :rivit (reduce reduseri {}
                                                                                   (group-by #(or (:laskun-numero %)
                                                                                                  0) (:rivit rivit-ja-summa))))])
                                pvm-mukaan)))]
    nro-mukaan))

(extend-protocol tuck/Event
  NakymastaPoistuttiin
  (process-event [_ app]
    (resetoi-kulunakyma))
  LiitteenPoistoOnnistui
  (process-event [{tulos :tulos {id :liite-id} :parametrit} app]
    (-> app
        (update-in
          [:lomake :liitteet]
          (fn [liitteet]
            (filter #(not (= id (:liite-id %))) liitteet)))
        (update-in [:parametrit :haetaan] dec)))
  PoistaLiite
  (process-event [{id :id} {:keys [lomake] :as app}]
    (if (nil? (:id lomake))
      (update-in app
                 [:lomake :liitteet]
                 (fn [liitteet]
                   (filter #(not (= id (:liite-id %))) liitteet)))
      (do
        (tuck-apurit/post! :poista-laskun-liite
                           {:urakka-id (-> @tila/tila :yleiset :urakka :id)
                            :lasku-id  (:id lomake)
                            :liite-id  id}
                           {:onnistui            ->LiitteenPoistoOnnistui
                            :onnistui-parametrit [{:liite-id id}]
                            :epaonnistui         ->KutsuEpaonnistui
                            :paasta-virhe-lapi?  true})
        (update-in app [:parametrit :haetaan] inc))))
  LataaLiite
  (process-event [{id :id} app]
    app)
  ;:kuvaus, :fileyard-hash, :urakka, :nimi,
  ;:id,:lahde,:tyyppi, :koko 65528
  LiiteLisatty
  (process-event [{{:keys [kuvaus nimi id tyyppi koko]} :liite} app]
    (update-in app
               [:lomake :liitteet]
               conj
               {:liite-id     id
                :liite-nimi   nimi
                :liite-tyyppi tyyppi
                :liite-koko   koko}))

  ;; SUCCESS

  TarkistusOnnistui
  (process-event [{tulos :tulos {:keys [ei-async-laskuria]} :parametrit} app]
    (->
      app
      (update-in [:parametrit :haetaan] (if ei-async-laskuria identity dec))
      (assoc-in [:lomake :tarkistukset :numerolla-tarkistettu-pvm] tulos)))
  MaksueraHakuOnnistui
  (process-event [{tulos :tulos} app]
    (->
      app
      (update-in [:parametrit :haetaan] dec)
      (assoc :maksuerat tulos)))
  TallennusOnnistui
  (process-event [{tulos :tulos {:keys [avain tilan-paivitys-fn]} :parametrit} app]
    (let [tilan-paivitys-fn (or tilan-paivitys-fn
                                #(identity %2))
          assoc-fn (cond
                     (nil? avain) (fn [app & _]
                                    app)
                     (vector? avain) assoc-in
                     :else assoc)]
      (-> app
          (update-in [:parametrit :haetaan] dec)
          (assoc-fn avain tulos)
          (tilan-paivitys-fn tulos))))
  AliurakoitsijahakuOnnistui
  (process-event [{tulos :tulos} app]
    (-> app
        (assoc :aliurakoitsijat tulos)
        (update-in [:parametrit :haetaan] dec)))
  LaskuhakuOnnistui
  (process-event [{tulos :tulos} {:keys [taulukko kulut toimenpiteet laskut] :as app}]
    (-> app
        (assoc :kulut tulos
               :taulukko (p/paivita-taulukko!
                           (luo-kulutaulukko app)
                           (formatoi-tulos tulos)))
        (update-in [:parametrit :haetaan] dec)))
  ToimenpidehakuOnnistui
  (process-event [{tulos :tulos} app]
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
                                                   (let [toimenpide-rivi? (nil? (:tehtavaryhma asia))
                                                         toimenpide-rivi-olemassa? (when toimenpide-rivi?
                                                                                     (some #(and (= (:toimenpideinstanssi %)
                                                                                                    (:toimenpideinstanssi asia))
                                                                                                 (= (:toimenpide-id %)
                                                                                                    (:toimenpide-id asia)))
                                                                                           (:toimenpiteet k)))]
                                                     (apply update k
                                                            (if toimenpide-rivi?
                                                              :toimenpiteet
                                                              :tehtavaryhmat)
                                                            (if (and toimenpide-rivi-olemassa?
                                                                     toimenpide-rivi?)
                                                              [identity]
                                                              [conj asia]))))
                                                 {:tehtavaryhmat []
                                                  :toimenpiteet  []}
                                                 (sort-by :jarjestys kasitelty))]
      (tuck-apurit/post! :kaikki-laskuerittelyt
                         {:urakka-id (-> @tila/tila :yleiset :urakka :id)}
                         {:onnistui           ->LaskuhakuOnnistui
                          :epaonnistui        ->KutsuEpaonnistui
                          :paasta-virhe-lapi? true})
      (assoc app
        :toimenpiteet toimenpiteet
        :tehtavaryhmat tehtavaryhmat)))

  ;; FAIL

  KutsuEpaonnistui
  (process-event [{{:keys [ei-async-laskuria]} :parametrit} app]
    (update-in app [:parametrit :haetaan] (if ei-async-laskuria identity dec)))

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
                          :paasta-virhe-lapi? true}))
    (update-in app [:parametrit :haetaan] + 2))
  HaeUrakanLaskut
  (process-event [{{:keys [id alkupvm loppupvm]} :hakuparametrit} app]
    (tuck-apurit/post! (if (and alkupvm loppupvm)
                         :laskuerittelyt
                         :kaikki-laskuerittelyt)
                       (cond-> {:urakka-id id}
                               (and alkupvm loppupvm) (assoc :alkupvm alkupvm
                                                             :loppupvm loppupvm))
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
    (update-in app [:parametrit :haetaan] inc))
  AvaaLasku
  (process-event [{lasku :lasku} app]
    (assoc app :syottomoodi true
               :lomake (kulu->lomake lasku)))

  ;; VIENNIT

  OnkoLaskunNumeroKaytossa
  (process-event [{laskun-numero :laskun-numero} app]
    (tuck-apurit/post! :tarkista-laskun-numeron-paivamaara
                       {:laskun-numero laskun-numero
                        :urakka        (-> @tila/yleiset :urakka :id)}
                       {:onnistui               ->TarkistusOnnistui
                        :onnistui-parametrit    [{:ei-async-laskuria true}]
                        :epaonnistui            ->KutsuEpaonnistui
                        :epaonnistui-parametrit [{:ei-async-laskuria true}]
                        :paasta-virhe-lapi?     true})
    app)
  PaivitaLomake
  (process-event [{polut-ja-arvot :polut-ja-arvot optiot :optiot} app]
    (update app :lomake lomakkeen-paivitys polut-ja-arvot optiot))
  LuoUusiAliurakoitsija
  (process-event [{aliurakoitsija :aliurakoitsija {:keys [sivuvaikutus-tuloksella-fn]} :optiot} app]
    (tuck-apurit/post! :tallenna-aliurakoitsija
                       aliurakoitsija
                       {:onnistui            ->TallennusOnnistui
                        :onnistui-parametrit [{:tilan-paivitys-fn (fn [app aliurakoitsija]
                                                                    (sivuvaikutus-tuloksella-fn aliurakoitsija)
                                                                    (-> app
                                                                        (update :aliurakoitsijat
                                                                                (fn [as]
                                                                                  (as-> as as
                                                                                        (conj as aliurakoitsija)
                                                                                        (sort-by :id as))))
                                                                        (update :lomake
                                                                                (fn [lomake]
                                                                                  (assoc lomake
                                                                                    :aliurakoitsija (:id aliurakoitsija)
                                                                                    :suorittaja-nimi (:nimi aliurakoitsija))))))}]
                        :epaonnistui         ->KutsuEpaonnistui
                        :paasta-virhe-lapi?  true})
    (update-in app [:parametrit :haetaan] inc))
  PaivitaAliurakoitsija
  (process-event [{aliurakoitsija :aliurakoitsija} app]
    (tuck-apurit/post! :paivita-aliurakoitsija
                       aliurakoitsija
                       {:onnistui            ->TallennusOnnistui
                        :onnistui-parametrit [{:tilan-paivitys-fn (fn [{:keys [aliurakoitsijat] :as app} paivitetty-aliurakoitsija]
                                                                    (update app :aliurakoitsijat
                                                                            (fn [as]
                                                                              (as-> as as
                                                                                    (filter
                                                                                      #(not
                                                                                         (= (:id %)
                                                                                            (:id paivitetty-aliurakoitsija)))
                                                                                      as)
                                                                                    (conj as paivitetty-aliurakoitsija)
                                                                                    (sort-by :id as)))))}]
                        :epaonnistui         ->KutsuEpaonnistui})
    (update-in app [:parametrit :haetaan] inc))
  TallennaKulu
  (process-event
    [_ {{:keys [kohdistukset koontilaskun-kuukausi liitteet
                laskun-numero lisatieto erapaiva id] :as lomake} :lomake :as app}]
    (let [urakka (-> @tila/yleiset :urakka :id)
          kokonaissumma (reduce #(+ %1 (if (true? (:poistettu %2))
                                         0
                                         (parsi-summa (:summa %2))))
                                0
                                kohdistukset)
          {validoi-fn :validoi} (meta lomake)
          validoitu-lomake (validoi-fn lomake)
          {validi? :validi?} (meta validoitu-lomake)
          tyyppi (or "laskutettava" "kiinteasti-hinnoiteltu")]
      ; { :toimenpideinstanssi :tehtavaryhma
      ;   :tehtava :maksueratyyppi
      ;   :suorittaja :suoritus_alku :suoritus_loppu
      ;   :muokkaaja
      (when (true? validi?)
        (tuck-apurit/post! :tallenna-lasku
                           {:urakka-id     urakka
                            :laskuerittely {:kohdistukset          kohdistukset
                                            :erapaiva              erapaiva
                                            :id                    (when-not (nil? id) id)
                                            :urakka                urakka
                                            :kokonaissumma         kokonaissumma
                                            :laskun-numero         laskun-numero
                                            :lisatieto             lisatieto
                                            :tyyppi                tyyppi
                                            :liitteet              liitteet
                                            :koontilaskun-kuukausi koontilaskun-kuukausi}}
                           {:onnistui            ->TallennusOnnistui
                            :onnistui-parametrit [{:tilan-paivitys-fn (fn [app {uusi-id :id :as tulos}]
                                                                        (as-> app a
                                                                              (update a :kulut (fn [kulut]
                                                                                                 (as-> kulut ks
                                                                                                       (filter (fn [{:keys [id] :as _kulu}]
                                                                                                                 (not= id uusi-id)) ks)
                                                                                                       (conj ks tulos))))
                                                                              (assoc a
                                                                                :taulukko (p/paivita-taulukko!
                                                                                            (luo-kulutaulukko a)
                                                                                            (formatoi-tulos (:kulut a)))
                                                                                :syottomoodi false)
                                                                              (update a :lomake resetoi-kulut)))}]
                            :epaonnistui         ->KutsuEpaonnistui}))
      (cond-> app
              true (assoc :lomake (assoc validoitu-lomake :paivita (inc (:paivita validoitu-lomake))))
              (true? validi?) (update-in [:parametrit :haetaan] inc))))
  PoistoOnnistui
  (process-event
    [{tulos :tulos} app]
    (as-> app a
          (update a :kulut (fn [kulut]
                             (as-> kulut ks
                                   (filter
                                     (fn [{:keys [id] :as _kulu}]
                                       (not= id (:id tulos)))
                                     ks))))
          (assoc a
            :taulukko (p/paivita-taulukko!
                        (luo-kulutaulukko a)
                        (formatoi-tulos (:kulut a)))
            :syottomoodi false)
          (update-in a [:parametrit :haetaan] dec)
          (update a :lomake resetoi-kulut)))
  PoistaKulu
  (process-event
    [{:keys [id]} app]
    (tuck-apurit/post! :poista-lasku
                       {:urakka-id (-> @tila/yleiset :urakka :id)
                        :id        id}
                       {:onnistui    ->PoistoOnnistui
                        :epaonnistui ->KutsuEpaonnistui})
    (update-in app [:parametrit :haetaan] inc))
  AsetaHakuparametri
  (process-event
    [{:keys [polku arvo]} app]
    (let [arvo (if (nil? arvo)
                 (-> @tila/yleiset :urakka polku)
                 arvo)]
      (assoc-in app [:parametrit (case polku
                                   :alkupvm :haun-alkupvm
                                   :loppupvm :haun-loppupvm)] arvo)))

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
