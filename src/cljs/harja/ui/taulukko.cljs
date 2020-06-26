(ns harja.ui.taulukko
  (:require [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.grid-debug :as g-debug]
            [harja.loki :refer [log warn error]]
            [harja.fmt :as fmt]
            [clojure.string :as clj-str]
            [clojure.set :as clj-set]
            [cljs.spec.alpha :as s])
  (:require-macros [harja.ui.taulukko.grid :refer [jarjesta-data triggeroi-seurannat]]))

(defn summa-formatointi [teksti]
  (if (nil? teksti)
    "0,00"
    (let [teksti (clj-str/replace (str teksti) "," ".")]
      (if (or (= "" teksti) (js/isNaN teksti))
        "0,00"
        (fmt/desimaaliluku teksti 2 true)))))

(defn summa-formatointi-aktiivinen [teksti]
  (let [teksti-ilman-pilkkua (clj-str/replace (str teksti) "," ".")]
    (cond
      (or (nil? teksti) (= "" teksti)) ""
      (re-matches #".*\.0*$" teksti-ilman-pilkkua) (apply str (fmt/desimaaliluku teksti-ilman-pilkkua nil true)
                                                          (drop 1 (re-find #".*(\.|,)(0*)" teksti)))
      :else (fmt/desimaaliluku teksti-ilman-pilkkua nil true))))

(defn poista-tyhjat [arvo]
  (clj-str/replace arvo #"\s" ""))

(defn paivita-raidat! [g vaihdetun-osan-nimet]
  (let [paivita-luokat (fn [luokat odd?]
                         (if odd?
                           (-> luokat
                               (conj "table-default-odd")
                               (disj "table-default-even"))
                           (-> luokat
                               (conj "table-default-even")
                               (disj "table-default-odd"))))]
    (loop [[rivi & loput-rivit] (grid/nakyvat-rivit g)
           index 0]
      (if rivi
        (let [rivin-nimi (grid/hae-osa rivi :nimi)]
          (grid/paivita-grid! rivi
                              :parametrit
                              (fn [parametrit]
                                (update parametrit :class (fn [luokat]
                                                            (if (contains? vaihdetun-osan-nimet rivin-nimi)
                                                              (paivita-luokat luokat (not (odd? index)))
                                                              (paivita-luokat luokat (odd? index)))))))
          (recur loput-rivit
                 (if (contains? vaihdetun-osan-nimet rivin-nimi)
                   index
                   (inc index))))))))

(defn paivita-solun-arvo! [{:keys [paivitettava-asia arvo solu ajettavat-jarejestykset triggeroi-seuranta?]
                            :or {ajettavat-jarejestykset false triggeroi-seuranta? false}}]
  (jarjesta-data ajettavat-jarejestykset
    (triggeroi-seurannat triggeroi-seuranta?
      (grid/aseta-rajapinnan-data!
        (grid/osien-yhteinen-asia solu :datan-kasittelija)
        paivitettava-asia
        arvo
        (grid/solun-asia solu :tunniste-rajapinnan-dataan)))))

(defn tayta-alla-olevat-rivit! [asettajan-nimi rivit-alla arvo]
  (when (and arvo (not (empty? rivit-alla)))
    (doseq [rivi rivit-alla
            :let [a-sarakkeen-solu (grid/get-in-grid rivi [1])]]
      (paivita-solun-arvo! {:paivitettava-asia asettajan-nimi
                            :arvo arvo
                            :solu a-sarakkeen-solu
                            :ajettavat-jarejestykset false
                            :triggeroi-seuranta? false}))))

(defmulti predef
          (fn [optio _]
            optio))

(defmethod predef :numero
  [_ {:keys [desimaalien-maara positiivinen-arvo?]
      :or {desimaalien-maara 2
           positiivinen-arvo? true}}]
  (let [kaytos (if positiivinen-arvo?
                 :positiivinen-numero
                 :numero)]
    {:fmt summa-formatointi
     :fmt-aktiivinen summa-formatointi-aktiivinen
     :kayttaytymiset {:on-change [{kaytos {:desimaalien-maara desimaalien-maara}}
                                  {:eventin-arvo {:f poista-tyhjat}}]
                      :on-blur [kaytos
                                {:eventin-arvo {:f poista-tyhjat}}]}}))

(defmethod predef :input
  [_ _]
  {:toiminnot {:on-change (fn [arvo]
                            (when arvo
                              (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                    :arvo arvo
                                                    :solu solu/*this*})))
               :on-blur (fn [arvo]
                          (when arvo
                            (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                  :arvo arvo
                                                  :solu solu/*this*
                                                  :ajettavat-jarejestykset :deep
                                                  :triggeroi-seuranta? true})))
               :on-key-down (fn [event]
                              (when (= "Enter" (.. event -key))
                                (.. event -target blur)))}
   :parametrit {:size 2
                :class #{"input-default"}}})

(defmethod predef :syote-tayta-alas
  [_ conf]
  {:nappia-painettu! (fn [rivit-alla arvo]
                                (let [grid (grid/root (first rivit-alla))]
                                  (tayta-alla-olevat-rivit! :aseta-arvo! rivit-alla arvo)
                                  (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                        :arvo arvo
                                                        :solu solu/*this*
                                                        :ajettavat-jarejestykset :deep
                                                        :triggeroi-seuranta? true})
                                  (grid/jarjesta-grid-data! grid)))
   :on-focus (fn [_]
               (grid/paivita-osa! solu/*this*
                                  (fn [solu]
                                    (assoc solu :nappi-nakyvilla? true))))})

#_(defn predefs [{:keys [conf predefs solu] :as args}]
  (let [solu-args (reduce (predef optio (get args
                                             (keyword (str (name optio) "-conf"))))
                          {}
                          predefs)]))

{:conf {:ratom tila-atom
        :dom-id dom-id
        :grid-polku grid-polku
        :data-polku data-polku}
 :vaihto-osat {:yhteenveto-checkboxilla {:body {:conf {:static? true}
                                                :osat [(yhteenvetorivi (fn [this _]
                                                                         (grid/vaihda-kentta-takaisin! this)))
                                                       (vec
                                                         (cons (vayla-checkbox (fn [this event]
                                                                                 (.preventDefault event)
                                                                                 (let [disable-rivit? (not (grid/solun-arvo this))]
                                                                                   (e! (tuck-apurit/->MuutaTila disable-rivit?-polku disable-rivit?))))
                                                                               "Disabloi rivit")
                                                               (repeatedly 4 (fn [] (solu/tyhja)))))]}}}
 :datavaikutukset-taulukkoon {:rahavaraukset-disablerivit {:polut [[:data-disable]]
                                                           :toiminto! (fn [g _ data-disable]
                                                                        (doseq [rivikontti (-> g (grid/get-in-grid [::data]) (grid/hae-grid :lapset))
                                                                                :let [yhteenvedon-ensimmainen-osa (-> rivikontti (grid/get-in-grid [::data-yhteenveto 0]))
                                                                                      laajenna-osa (if (grid/rivi? yhteenvedon-ensimmainen-osa)
                                                                                                     (grid/get-in-grid yhteenvedon-ensimmainen-osa [0])
                                                                                                     yhteenvedon-ensimmainen-osa)
                                                                                      rivikontin-sisaltaman-datan-nimi (grid/solun-arvo laajenna-osa)
                                                                                      disable-rivit? (get data-disable rivikontin-sisaltaman-datan-nimi)]]
                                                                          (solujen-disable! (-> rivikontti (grid/get-in-grid [::data-sisalto]) (grid/hae-grid :lapset))
                                                                                            disable-rivit?)))}}
 :taulukko {:header {:conf {:nimi ::otsikko
                            :koko (-> konf/livi-oletuskoko
                                      (assoc-in [:sarake :leveydet] {0 "9fr"
                                                                     4 "1fr"})
                                      (assoc-in [:sarake :oletus-leveys] "3fr"))
                            :luokat #{"salli-ylipiirtaminen"}}
                     :osat (conj (mapv (fn [nimi]
                                         (solu/otsikko {:jarjesta-fn! jarjesta-fn!
                                                        :parametrit {:class #{"table-default" "table-default-header"}}
                                                        :nimi nimi}))
                                       [:rivi :a :b :c])
                                 (solu/tyhja #{"table-default" "table-default-header"}))}
            :body {:conf {:nimi ::data}
                   :osat {:conf {:sarakkeiden-nimet [:rivin-nimi :a :b :c :poista]}
                          :header {:conf {:nimi ::data-yhteenveto
                                          :vaihdettava-kentta :yhteenveto-checkboxilla}
                                   :osat (yhteenvetorivi (fn [this auki?]
                                                           (grid/vaihda-kentta! this :yhteenveto-checkboxilla)))}
                          :body {:conf {:nimi ::data-sisalto
                                        :static? true
                                        :luokat #{"piillotettu" "salli-ylipiirtaminen"}}
                                 :osat (mapv (fn [index]
                                               {:conf {}
                                                :osat [(solu/tyhja)
                                                       {:conf {:nimi :a-kentta}
                                                        :predefs #{:numero :input :syote-tayta-alas}
                                                        :numero-input-conf {:desimaalien-maara 2}}
                                                       (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                     :fmt summa-formatointi})
                                                       {:predefs #{:numero}
                                                        :riippuu-toisesta {:riippuvuudet [[:a-kentta]]
                                                                           :kasittely-fn (fn [a-arvo]
                                                                                           (* 10 a-arvo))}}
                                                       (solu/tyhja)]})
                                             (range 3))}}}
            :footer {:conf {:nimi ::yhteenveto}
                     :osat (vec
                             (concat [(solu/tyhja #{"table-default" "table-default-sum"})]
                                     (map (fn [sarake]
                                            {:predefs #{:numero}
                                             :riippuu-toisesta {:riippuvuudet [[:/ ::data ^:sarake sarake]]
                                                                :kasittely-fn (fn [sarakkeen-arvot]
                                                                                (reduce + 0 sarakkeen-arvot))}})
                                          [:a :b :c])
                                     [(solu/tyhja #{"table-default" "table-default-sum"})]))}}}

(defn poista-nil
  [m osio]
  {:pre [(map? m)]
   :post [(map? m)]}
  (reduce-kv (fn [m k v]
               (cond
                 (and (nil? k) (= :key osio)) m
                 (and (nil? v) (= :val osio)) m
                 :else (assoc m k v)))
             {}
             m))

(defn- taulukon-koko-conf [taulukko]
  (let [taulukon-osiot (select-keys taulukko #{:header :body :footer})
        jarjestys {:header 0 :body 1 :footer 2}
        osioiden-index (poista-nil :key
                                   (loop [[[osion-nimi _] & loput] (sort-by key
                                                                            (comparator (fn [a b]
                                                                                          (< (jarjestys a) (jarjestys b))))
                                                                            taulukon-osiot)
                                          i 0
                                          nimet {}]
                                     (if (nil? osion-nimi)
                                       nimet
                                       (recur loput
                                              (inc i)
                                              (assoc nimet osion-nimi i)))))]
    {:rivi-n (count taulukon-osiot)
     :nimet-osioihin (poista-nil :val
                                 (clj-set/rename-keys osioiden-index
                                                      {:header (get-in taulukon-osiot [:header :conf :nimi])
                                                       :body (get-in taulukon-osiot [:body :conf :nimi])
                                                       :footer (get-in taulukon-osiot [:footer :conf :nimi])}))
     :header-index (:header osioiden-index)
     :body-index (:body osioiden-index)
     :footer-index (:footer osioiden-index)}))

(defn vaihda-osa-takaisin! [])

(defn vaihda-osa! [osa vaihdettavan-osan-polku]
  (when g-debug/GRID_DEBUG
    (when (and (nil? osa)
               (nil? solu/*this*))
      (error (str "Yritettiin vaihtaa osaa, mutta annettu solu on nil ja harja.ui.taulukko.impl.solu/*this* on myös nil!\n\n"
                  "Korjaa virhe antamalla vaihda-osa! funktiolle osa, josta root voidaan hakea tai toteuta tämän funktion triggeröimä"
                  " solu siten, että harja.ui.taulukko.impl.solu/*this* sisältää tämän solun."))))
  (let [root-grid (if (nil? osa)
                    (grid/root solu/*this*)
                    (grid/root osa))
        vaihdettava-osa (grid/get-in-grid root-grid vaihdettavan-osan-polku)
        vaihdettavan-osan-id (grid/hae-osa vaihdettava-osa :id)
        vaihto-osien-mappaus (get root-grid ::vaihto-osien-mappaus)
        vaihto-osan-tunniste (get-in @vaihto-osien-mappaus [:mappaus vaihdettavan-osan-id])]
    (grid/vaihda-osa!
      vaihdettava-osa
      (constantly (get-in @vaihto-osien-mappaus [:vaihto-osat vaihto-osan-tunniste])))))

(def ^:dynamic *vaihto-osien-mappaus* nil)
(def ^:dynamic *otsikon-nimi* nil)

(declare muodosta-grid-rivi muodosta-grid-staattinen-taulukko muodosta-grid-dynaaminen-taulukko #_muodosta-grid-solu)

(defn vaihdettavan-osan-ilmoitus! [osan-maaritelma osan-id]
  (when-let [vaihdettavan-osan-nimi (get-in osan-maaritelma [:conf :vaihdettava-osa])]
    (when g-debug/GRID_DEBUG
      (when (nil? *vaihto-osien-mappaus*)
        (warn "*vaihto-osien-mappaus* on nil vaikka siihen yritetään asettaa vaihdettava-osa!")))
    (swap! *vaihto-osien-mappaus*
           (fn [mappaukset]
             (update mappaukset :mappaus assoc osan-id vaihdettavan-osan-nimi)))))

(defn- muodosta-grid-osa
  [osan-maaritelma]
  (let [staattinen-taulukko? (not (empty? (select-keys osan-maaritelma #{:header :body :footer})))
        dynaaminen-taulukko? (get osan-maaritelma :toistettava-osa)
        rivi? (contains? osan-maaritelma :osat)
        solu? (grid/solu? osan-maaritelma)
        solu-conf? (contains? osan-maaritelma :solu)

        muodostettu-osa (cond
                          staattinen-taulukko? (muodosta-grid-staattinen-taulukko osan-maaritelma)
                          dynaaminen-taulukko? (muodosta-grid-dynaaminen-taulukko osan-maaritelma)
                          rivi? (muodosta-grid-rivi osan-maaritelma)
                          solu? osan-maaritelma
                          solu-conf? (:solu osan-maaritelma) #_(muodosta-grid-solu osa))]
    (vaihdettavan-osan-ilmoitus! osan-maaritelma (grid/hae-osa muodostettu-osa :id))
    muodostettu-osa))

#_(defn- muodosta-grid-solu [solumaaritelma]
  (let [{:keys [solu]}]))

(defn- muodosta-grid-rivi [rivimaaritelma]
  (let [{:keys [nimi koko luokat]} (get rivimaaritelma :conf)]
    (grid/rivi {:nimi (or nimi (str (gensym)))
                :koko (or koko
                          (and *otsikon-nimi*
                               {:seurattava *otsikon-nimi*
                                :sarakkeet :sama
                                :rivit :sama})
                          konf/livi-oletuskoko)
                :osat (mapv muodosta-grid-osa (:osat rivimaaritelma))
                :luokat (clj-set/union #{"salli-ylipiirtaminen"} luokat)}
               [{:sarakkeet [0 (count (:osat rivimaaritelma))] :rivit [0 1]}])))

(defn- muodosta-grid-dynaaminen-taulukko [taulukkomaaritelma]
  (let [{:keys [nimi luokat]} (:conf taulukkomaaritelma)]
    (grid/dynamic-grid {:nimi (or nimi ::data)
                        :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                        :osatunnisteet #(map key %)
                        :koko konf/auto
                        :luokat (clj-set/union #{"salli-ylipiirtaminen"} luokat)
                        :osien-maara-muuttui! (fn [g _]
                                                (paivita-raidat! g)
                                                #_(paivita-raidat! (grid/osa-polusta (grid/root g) [::data])))
                        :toistettavan-osan-data identity
                        :toistettava-osa (fn [rivien-data]
                                           (mapv (fn [rivin-data]
                                                   (muodosta-grid-osa (:toistettava-osa taulukkomaaritelma)))
                                                 rivien-data))})))

(defn- muodosta-grid-staattinen-taulukko
  ([taulukkomaaritelma] (muodosta-grid-staattinen-taulukko taulukkomaaritelma nil))
  ([taulukkomaaritelma root-conf]
   (let [{:keys [rivi-n nimet-osioihin header-index body-index footer-index]} (taulukon-koko-conf taulukkomaaritelma)
         {:keys [nimi koko]} (get taulukkomaaritelma :conf)
         header-osa (when header-index
                      (muodosta-grid-osa (:header taulukkomaaritelma)))
         headerosa-koko-maarityksella (first
                                        (grid/hae-kaikki-osat header-osa
                                                              (every-pred grid/rivi?
                                                                          #(boolean (grid/hae-osa % :nimi))
                                                                          #(not (nil? (get (grid/hae-grid % :koko) :seurattava))))))]
     (binding [*otsikon-nimi* (if headerosa-koko-maarityksella
                                (grid/hae-osa headerosa-koko-maarityksella :nimi)
                                *otsikon-nimi*)]
       (grid/grid (merge {:nimi nimi
                          :alueet [{:sarakkeet [0 1] :rivit [0 rivi-n]}]
                          :koko (or koko
                                    (cond-> konf/auto
                                            nimet-osioihin (assoc-in [:rivi :nimet]
                                                                     nimet-osioihin)
                                            header-index (assoc-in [:rivi :korkeudet header-index] "40px")
                                            footer-index (assoc-in [:rivi :korkeudet footer-index] "40px")))
                          :osat (vec
                                  (cond-> []
                                          header-osa (conj header-osa)
                                          body-index (concat (map muodosta-grid-osa (:body taulukkomaaritelma)))
                                          footer-index (concat [(muodosta-grid-osa (:footer taulukkomaaritelma))])))}
                         root-conf)))))
  (grid/grid {:nimi ::root
              :dom-id dom-id
              :root-fn (fn [] (get-in @tila [:grid]))
              :paivita-root! (fn [f]
                               (swap! tila
                                      (fn [tila]
                                        (update-in tila [:grid] f))))
              :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
              :koko (-> konf/auto
                        (assoc-in [:rivi :nimet]
                                  {::otsikko 0
                                   ::data 1
                                   ::yhteenveto 2})
                        (assoc-in [:rivi :korkeudet] {0 "40px"
                                                      2 "40px"}))
              :osat [(grid/rivi {:nimi ::otsikko
                                 :koko (-> konf/livi-oletuskoko
                                           (assoc-in [:sarake :leveydet] {0 "9fr"
                                                                          4 "1fr"})
                                           (assoc-in [:sarake :oletus-leveys] "3fr"))
                                 :osat (conj (mapv (fn [nimi]
                                                     (solu/otsikko {:jarjesta-fn! jarjesta-fn!
                                                                    :parametrit {:class #{"table-default" "table-default-header"}}
                                                                    :nimi nimi}))
                                                   [:rivi :a :b :c])
                                             (solu/tyhja #{"table-default" "table-default-header"}))
                                 :luokat #{"salli-ylipiirtaminen"}}
                                [{:sarakkeet [0 5] :rivit [0 1]}])
                     (grid/dynamic-grid {:nimi ::data
                                         :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                         :osatunnisteet #(map key %)
                                         :koko konf/auto
                                         :luokat #{"salli-ylipiirtaminen"}
                                         :osien-maara-muuttui! (fn [g _] (paivita-raidat! (grid/osa-polusta (grid/root g) [::data])))
                                         :toistettavan-osan-data (fn [rivit]
                                                                   rivit)
                                         :toistettava-osa (fn [rivit-ryhmiteltyna]
                                                            (mapv (fn [[rivi rivien-arvot]]
                                                                    (with-meta
                                                                      (grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                                                                                  :nimi ::datarivi
                                                                                  :koko (-> konf/auto
                                                                                            (assoc-in [:rivi :nimet]
                                                                                                      {::data-yhteenveto 0
                                                                                                       ::data-sisalto 1}))
                                                                                  :luokat #{"salli-ylipiirtaminen"}
                                                                                  :osat [(with-meta (grid/rivi {:nimi ::data-yhteenveto
                                                                                                                :koko {:seuraa {:seurattava ::otsikko
                                                                                                                                :sarakkeet :sama
                                                                                                                                :rivit :sama}}
                                                                                                                :osat [(solu/laajenna {:aukaise-fn
                                                                                                                                       (fn [this auki?]
                                                                                                                                         (laajenna-solua-klikattu this rivi auki? [::data]))
                                                                                                                                       :auki-alussa? false
                                                                                                                                       :parametrit {:class #{"table-default" "lihavoitu"}}})
                                                                                                                       (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                     :fmt summa-formatointi})
                                                                                                                       (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                     :fmt summa-formatointi})
                                                                                                                       (solu/teksti {:parametrit {:class #{"table-default" "harmaa-teksti"}}
                                                                                                                                     :fmt summa-formatointi})
                                                                                                                       (solu/ikoni {:nimi "poista"
                                                                                                                                    :toiminnot {:on-click (fn [_]
                                                                                                                                                            (e! (->PoistaRivi (grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan))))}})]
                                                                                                                :luokat #{"salli-ylipiirtaminen"}}
                                                                                                               [{:sarakkeet [0 5] :rivit [0 1]}])
                                                                                                    {:key (str rivi "-yhteenveto")})
                                                                                         (with-meta
                                                                                           (grid/taulukko {:nimi ::data-sisalto
                                                                                                           :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
                                                                                                           :koko konf/auto
                                                                                                           :luokat #{"piillotettu" "salli-ylipiirtaminen"}}
                                                                                                          (mapv
                                                                                                            (fn [index]
                                                                                                              (with-meta
                                                                                                                (grid/rivi {:koko {:seuraa {:seurattava ::otsikko
                                                                                                                                            :sarakkeet :sama
                                                                                                                                            :rivit :sama}}
                                                                                                                            :osat [(with-meta
                                                                                                                                     (solu/tyhja)
                                                                                                                                     {:key (str rivi "-" index "-otsikko")})
                                                                                                                                   (with-meta
                                                                                                                                     (g-pohjat/->SyoteTaytaAlas (gensym "a")
                                                                                                                                                                false
                                                                                                                                                                (fn [rivit-alla arvo]
                                                                                                                                                                  (let [grid (grid/root (first rivit-alla))]
                                                                                                                                                                    (tayta-alla-olevat-rivit! :aseta-arvo! rivit-alla arvo)
                                                                                                                                                                    (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                          :arvo arvo
                                                                                                                                                                                          :solu solu/*this*
                                                                                                                                                                                          :ajettavat-jarejestykset :deep
                                                                                                                                                                                          :triggeroi-seuranta? true})
                                                                                                                                                                    (grid/jarjesta-grid-data! grid
                                                                                                                                                                                              (keyword (str "data-" rivi)))))
                                                                                                                                                                {:on-change (fn [arvo]
                                                                                                                                                                              (when arvo
                                                                                                                                                                                (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                                      :arvo arvo
                                                                                                                                                                                                      :solu solu/*this*
                                                                                                                                                                                                      :ajettavat-jarejestykset false})))
                                                                                                                                                                 :on-focus (fn [_]
                                                                                                                                                                             (grid/paivita-osa! solu/*this*
                                                                                                                                                                                                (fn [solu]
                                                                                                                                                                                                  (assoc solu :nappi-nakyvilla? true))))
                                                                                                                                                                 :on-blur (fn [arvo]
                                                                                                                                                                            (when arvo
                                                                                                                                                                              (paivita-solun-arvo! {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                                    :arvo arvo
                                                                                                                                                                                                    :solu solu/*this*
                                                                                                                                                                                                    :ajettavat-jarejestykset :deep
                                                                                                                                                                                                    :triggeroi-seuranta? true})))
                                                                                                                                                                 :on-key-down (fn [event]
                                                                                                                                                                                (when (= "Enter" (.. event -key))
                                                                                                                                                                                  (.. event -target blur)))}
                                                                                                                                                                {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                             {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                                 :on-blur [:positiivinen-numero
                                                                                                                                                                           {:eventin-arvo {:f poista-tyhjat}}]}
                                                                                                                                                                {:size 2
                                                                                                                                                                 :class #{"input-default"}}
                                                                                                                                                                summa-formatointi
                                                                                                                                                                summa-formatointi-aktiivinen)
                                                                                                                                     {:key (str rivi "-" index "-maara")})
                                                                                                                                   (with-meta
                                                                                                                                     (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                   :fmt summa-formatointi})
                                                                                                                                     {:key (str rivi "-" index "-yhteensa")})
                                                                                                                                   (with-meta
                                                                                                                                     (solu/teksti {:parametrit {:class #{"table-default"}}})
                                                                                                                                     {:key (str rivi "-" index "-indeksikorjattu")})
                                                                                                                                   (solu/tyhja)]
                                                                                                                            :luokat #{"salli-ylipiirtaminen"}}
                                                                                                                           [{:sarakkeet [0 5] :rivit [0 1]}])
                                                                                                                {:key (str rivi "-" index)}))
                                                                                                            (range 3)))
                                                                                           {:key (str rivi "-data-sisalto")})]})
                                                                      {:key rivi}))
                                                                  rivit-ryhmiteltyna))})
                     (grid/rivi {:nimi ::yhteenveto
                                 :koko {:seuraa {:seurattava ::otsikko
                                                 :sarakkeet :sama
                                                 :rivit :sama}}
                                 :osat (conj (vec (repeatedly 2 (fn []
                                                                  (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}}))))
                                             (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum"}}
                                                           :fmt summa-formatointi})
                                             (solu/teksti {:parametrit {:class #{"table-default" "table-default-sum" "harmaa-teksti"}}
                                                           :fmt summa-formatointi})
                                             (solu/tyhja #{"table-default" "table-default-sum"}))}
                                [{:sarakkeet [0 5] :rivit [0 1]}])]}))

(defn- muodosta-grid [{:keys [ratom dom-id grid-polku]} taulukko]
  (let [root-conf {:dom-id dom-id
                   :root-fn (fn [] (get-in @ratom grid-polku))
                   :paivita-root! (fn [f]
                                    (swap! ratom
                                           (fn [tila]
                                             (update-in tila grid-polku f))))}]
    (muodosta-grid-staattinen-taulukko (update-in taulukko [:conf :nimi] (fn [nimi] (if nimi nimi ::root)))
                                       root-conf)))

(s/def ::nimi (s/or :keyword keyword?
                    :string string?))

(s/def ::data-polku vector?)
(s/def ::grid-polku vector?)
(s/def ::dom-id string?)
(s/def ::ratom #(implements? reagent.ratom/IReactiveAtom %))
(s/def ::tee-taulukko-conf (s/keys :req-un [::ratom ::dom-id ::grid-polku ::data-polku]))

(defn tee-taulukko!
  [maaritelma]
  {:pre [(s/conform ::tee-taulukko-conf (:conf maaritelma))]}
  (let [{:keys [conf datavaikutukset-taulukkoon taulukko]} maaritelma
        vaihto-osien-mappaus (atom {:vaihto-osat (:vaihto-osat maaritelma)
                                    :mappaus {}})
        grid (assoc (binding [*vaihto-osien-mappaus* vaihto-osien-mappaus]
                            (muodosta-grid conf taulukko))
                    ::vaihto-osien-mappaus vaihto-osien-mappaus)
        rajapinta (muodosta-rajapinta)
        datakasittely-ratom (muodosta-datakasittelija-ratom)
        datakasittely-taulukko (muodosta-datakasittelija-taulukko)
        gridin-tapahtumat (muodosta-grid-tapahtumat)]
    {:ratom tila-atom
     :dom-id dom-id
     :grid-polku grid-polku
     :data-polku data-polku}
    (swap! (:ratom conf) assoc-in (:grid-polku conf) grid)
    (grid/rajapinta-grid-yhdistaminen! grid
                                       rajapinta
                                       datakasittely-ratom
                                       datakasittely-taulukko)
    (grid/grid-tapahtumat grid
                          ratom
                          gridin-tapahtumat)))