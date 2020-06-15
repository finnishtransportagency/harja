(ns harja.views.urakka.suunnittelu.foo
  (:require [reagent.core :as r :refer [atom]]
            [tuck.core :as tuck]
            [harja.ui.komponentti :as komp]
            [harja.tyokalut.tuck :as tuck-apurit]
            [harja.ui.taulukko.grid-osan-vaihtaminen :as gov]
            [harja.ui.taulukko.grid-pohjat :as g-pohjat]
            [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.protokollat :as p]
            [clojure.string :as clj-str]
            [harja.fmt :as fmt])
  (:require-macros [harja.ui.taulukko.grid :refer [jarjesta-data triggeroi-seurannat]]))

(defonce ^:mutable e! nil)

(defrecord JarjestaData [otsikko])

(extend-protocol tuck/Event
  JarjestaData
  (process-event [{:keys [otsikko]} {g :grid :as app}]
    (println "otsikko: " otsikko)
    (when-not (nil? otsikko)
      (grid/jarjesta-grid-data! g
                                :datarivit
                                0
                                (fn [datarivit]
                                  (println "------ DATARIVIT -----")
                                  (println (if (= :rivi otsikko)
                                             (sort-by key datarivit)
                                             (sort-by (fn [[_ v]]
                                                        (reduce #(+ %1 (:a %2))
                                                                0
                                                                v))
                                                      datarivit)))
                                  (if (= :rivi otsikko)
                                    (sort-by key datarivit)
                                    (sort-by (fn [[_ v]]
                                               (reduce #(+ %1 (:a %2))
                                                       0
                                                       v))
                                             datarivit)))))
    app))

(defonce alkudata {:data [{:rivi :foo :a 1 :b 2 :c 3}
                          {:rivi :foo :a 2 :b 3 :c 4}
                          {:rivi :foo :a 3 :b 4 :c 5}
                          {:rivi :bar :a 10 :b 20 :c 30}
                          {:rivi :bar :a 20 :b 30 :c 40}
                          {:rivi :bar :a 30 :b 40 :c 50}
                          {:rivi :baz :a 400 :b 200 :c 300}
                          {:rivi :baz :a 200 :b 300 :c 400}
                          {:rivi :baz :a 300 :b 400 :c 500}]
                   :gridin-otsikot ["RIVIN NIMI" "A" "B" "C"]
                   :suodattimet {:naytettavat-rivit #{:foo :bar :baz}}})

(def tila (atom alkudata))

(defn summa-formatointi [teksti]
  (if (or (nil? teksti) (= "" teksti) (js/isNaN teksti))
    "0,00"
    (let [teksti (clj-str/replace (str teksti) "," ".")]
      (fmt/desimaaliluku teksti 2 true))))

(defn summa-formatointi-uusi [teksti]
  (if (nil? teksti)
    ""
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

(defn paivita-raidat! [g]
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
                                                            (if (= ::valinta rivin-nimi)
                                                              (paivita-luokat luokat (not (odd? index)))
                                                              (paivita-luokat luokat (odd? index)))))))
          (recur loput-rivit
                 (if (= ::valinta rivin-nimi)
                   index
                   (inc index))))))))

(defn laajenna-solua-klikattu
  ([solu auki? dom-id polku-dataan] (laajenna-solua-klikattu solu auki? dom-id polku-dataan nil))
  ([solu auki? dom-id polku-dataan
    {:keys [aukeamis-polku sulkemis-polku]
     :or {aukeamis-polku [:.. :.. 1]
          sulkemis-polku [:.. :.. 1]}}]
   (if auki?
     (do (grid/nayta! (grid/osa-polusta solu aukeamis-polku))
         (paivita-raidat! (grid/osa-polusta (grid/root solu) polku-dataan)))
     (do (grid/piillota! (grid/osa-polusta solu sulkemis-polku))
         (paivita-raidat! (grid/osa-polusta (grid/root solu) polku-dataan))))))

(defn paivita-solun-arvo [{:keys [paivitettava-asia arvo solu ajettavat-jarejestykset triggeroi-seuranta?]
                           :or {ajettavat-jarejestykset false triggeroi-seuranta? false}}
                          & args]
  (println "PÄIVITETTÄVÄ ASIA: " paivitettava-asia)
  (jarjesta-data ajettavat-jarejestykset
                 (triggeroi-seurannat triggeroi-seuranta?
                                      (case paivitettava-asia
                                        :aseta-arvo! (apply grid/aseta-rajapinnan-data!
                                                            (grid/osien-yhteinen-asia solu :datan-kasittelija)
                                                            :aseta-arvo!
                                                            arvo
                                                            (grid/solun-asia solu :tunniste-rajapinnan-dataan)
                                                            args)
                                        (println (str "YRITETTIIN " paivitettava-asia))))))

(defn tayta-alas-napin-toiminto [e! asettajan-nimi maara-solun-index rivit-alla arvo]
  (when (and arvo (not (empty? rivit-alla)))
    (doseq [rivi rivit-alla
            :let [maara-solu (grid/get-in-grid rivi [maara-solun-index])
                  piillotettu? (grid/piillotettu? rivi)]]
      (when-not piillotettu?
        (paivita-solun-arvo {:paivitettava-asia asettajan-nimi
                             :arvo arvo
                             :solu maara-solu
                             :ajettavat-jarejestykset #{:mapit}}
                            true)))
    #_(when (= asettajan-nimi :aseta-rahavaraukset!)
      (e! (t/->TallennaKustannusarvoitu tallennettava-asia
                                        (vec (keep (fn [rivi]
                                                     (let [maara-solu (grid/get-in-grid rivi [1])
                                                           piillotettu? (grid/piillotettu? rivi)]
                                                       (when-not piillotettu?
                                                         (grid/solun-asia maara-solu :tunniste-rajapinnan-dataan))))
                                                   rivit-alla))))
      (e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))

(defn jarjesta-fn! []
  (println "jarjesta-fn!")
  (println "THIS SOLU: " solu/*this*)
  (e! (->JarjestaData (grid/hae-osa solu/*this* :nimi))))

(defn foo* [_ _]
  (komp/luo
    (komp/piirretty (fn [_]
                      (let [dom-id "foo"
                            g (grid/grid {:nimi ::root
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
                                                                       (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                                      3 "1fr"})
                                                                       (assoc-in [:sarake :oletus-leveys] "2fr"))
                                                             :osat (mapv (fn [nimi] (solu/otsikko {:jarjesta-fn! jarjesta-fn!
                                                                                                   :parametrit {:class #{"table-default" "table-default-header"}}
                                                                                                   :nimi nimi})
                                                                           #_(solu/teksti {:parametrit {:class #{"table-default" "table-default-header"}}}))
                                                                         [:rivi :a :b :c])
                                                             :luokat #{"salli-ylipiirtaminen"}}
                                                            [{:sarakkeet [0 4] :rivit [0 1]}])
                                                 (grid/dynamic-grid {:nimi ::data
                                                                     :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                                                     :koko konf/auto
                                                                     :luokat #{"salli-ylipiirtaminen"}
                                                                     :osien-maara-muuttui! (fn [g _] (paivita-raidat! (grid/osa-polusta (grid/root g) [::data])))
                                                                     :toistettavan-osan-data (fn [rivit]
                                                                                               (println "##########")
                                                                                               (println "#  RIVIT #")
                                                                                               (println "##########")
                                                                                               (println rivit)
                                                                                               rivit
                                                                                               #_(let [rivit (:data alkudata)]
                                                                                                 (group-by :rivi rivit)))
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
                                                                                                                                                                     (laajenna-solua-klikattu this auki? dom-id [::data] #_{:sulkemis-polku [:.. :.. :.. 1]}))
                                                                                                                                                                   :auki-alussa? false
                                                                                                                                                                   :parametrit {:class #{"table-default" "lihavoitu"}}})
                                                                                                                                                   (solu/syote {:toiminnot {:on-change (fn [arvo]
                                                                                                                                                                                         (paivita-solun-arvo {:paivitettava-asia :aseta-yhteenveto!
                                                                                                                                                                                                              :arvo arvo
                                                                                                                                                                                                              :solu solu/*this*
                                                                                                                                                                                                              :ajettavat-jarejestykset #{:mapit}}))
                                                                                                                                                                            :on-blur (fn [arvo]
                                                                                                                                                                                       (when arvo
                                                                                                                                                                                         (paivita-solun-arvo {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                                              :arvo arvo
                                                                                                                                                                                                              :solu solu/*this*
                                                                                                                                                                                                              :ajettavat-jarejestykset true
                                                                                                                                                                                                              :triggeroi-seuranta? true}
                                                                                                                                                                                                             true)
                                                                                                                                                                                         (let [vanhempiosa (grid/osa-polusta solu/*this* [:.. :..])
                                                                                                                                                                                               tallennettavien-arvojen-osat (if (= ::datarivi (grid/hae-osa vanhempiosa :nimi))
                                                                                                                                                                                                                              (grid/hae-grid (grid/osa-polusta vanhempiosa [1]) :lapset)
                                                                                                                                                                                                                              (grid/hae-grid (grid/osa-polusta vanhempiosa [:.. 1]) :lapset))
                                                                                                                                                                                               tunnisteet (mapv #(grid/solun-asia (get (grid/hae-grid % :lapset) 1)
                                                                                                                                                                                                                                  :tunniste-rajapinnan-dataan)
                                                                                                                                                                                                                tallennettavien-arvojen-osat)]
                                                                                                                                                                                           #_(e! (t/->TallennaKustannusarvoitu (tyyppi->tallennettava-asia tyyppi) tunnisteet))
                                                                                                                                                                                           #_(e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta)))))
                                                                                                                                                                            :on-key-down (fn [event]
                                                                                                                                                                                           (when (= "Enter" (.. event -key))
                                                                                                                                                                                             (.. event -target blur)))}
                                                                                                                                                                :kayttaytymiset {:on-change [{:positiivinen-numero {:desimaalien-maara 2}}
                                                                                                                                                                                             {:eventin-arvo {:f poista-tyhjat}}]
                                                                                                                                                                                 :on-blur [:positiivinen-numero
                                                                                                                                                                                           :eventin-arvo]}
                                                                                                                                                                :parametrit {:size 2
                                                                                                                                                                             :class #{"input-default"}}
                                                                                                                                                                :fmt summa-formatointi
                                                                                                                                                                :fmt-aktiivinen summa-formatointi-aktiivinen})
                                                                                                                                                   (solu/teksti {:parametrit {:class #{"table-default"}}
                                                                                                                                                                 :fmt summa-formatointi})
                                                                                                                                                   (solu/teksti {:parametrit {:class #{"table-default" "harmaa-teksti"}}
                                                                                                                                                                 :fmt summa-formatointi})]
                                                                                                                                            :luokat #{"salli-ylipiirtaminen"}}
                                                                                                                                           [{:sarakkeet [0 4] :rivit [0 1]}])
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
                                                                                                                                                                                            (partial tayta-alas-napin-toiminto
                                                                                                                                                                                                     :aseta-arvo!
                                                                                                                                                                                                     1)
                                                                                                                                                                                            {:on-change (fn [arvo]
                                                                                                                                                                                                          (when arvo
                                                                                                                                                                                                            (paivita-solun-arvo {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                                                                 :arvo arvo
                                                                                                                                                                                                                                 :solu solu/*this*
                                                                                                                                                                                                                                 :ajettavat-jarejestykset false #_#{:mapit}}
                                                                                                                                                                                                                                false)))
                                                                                                                                                                                             :on-focus (fn [_]
                                                                                                                                                                                                         (grid/paivita-osa! solu/*this*
                                                                                                                                                                                                                            (fn [solu]
                                                                                                                                                                                                                              (assoc solu :nappi-nakyvilla? true))))
                                                                                                                                                                                             :on-blur (fn [arvo]
                                                                                                                                                                                                        (when arvo
                                                                                                                                                                                                          (paivita-solun-arvo {:paivitettava-asia :aseta-arvo!
                                                                                                                                                                                                                               :arvo arvo
                                                                                                                                                                                                                               :solu solu/*this*
                                                                                                                                                                                                                               :ajettavat-jarejestykset true
                                                                                                                                                                                                                               :triggeroi-seuranta? true}
                                                                                                                                                                                                                              true)
                                                                                                                                                                                                          (println "(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan) " (grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan))
                                                                                                                                                                                                          #_(e! (t/->TallennaKustannusarvoitu (tyyppi->tallennettava-asia tyyppi) [(grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)]))
                                                                                                                                                                                                          #_(e! (t/->TallennaJaPaivitaTavoiteSekaKattohinta))))
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
                                                                                                                                                                 {:key (str rivi "-" index "-indeksikorjattu")})]
                                                                                                                                                        :luokat #{"salli-ylipiirtaminen"}}
                                                                                                                                                       [{:sarakkeet [0 4] :rivit [0 1]}])
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
                                                                                       :fmt summa-formatointi}))}
                                                            [{:sarakkeet [0 4] :rivit [0 1]}])]})

                            rajapinta {:otsikot any?
                                       :yhteensarivit any?
                                       :datarivit any?
                                       :footer any?

                                       :aseta-arvo! any?
                                       :aseta-yhteenveto! any?}]
                        (e! (tuck-apurit/->MuutaTila [:grid] g))
                        (grid/rajapinta-grid-yhdistaminen! g
                                                           rajapinta
                                                           (grid/datan-kasittelija tila
                                                                                   rajapinta
                                                                                   {:otsikot {:polut [[:gridin-otsikot]]
                                                                                              :haku identity}
                                                                                    :yhteensarivit {:polut [[:data-yhteensa]]
                                                                                                    :haku identity}
                                                                                    :datarivit {:polut [[:data]]
                                                                                                :luonti-init (fn [tila data]
                                                                                                               (update tila
                                                                                                                       :data
                                                                                                                       (fn [data]
                                                                                                                         (vec (map-indexed (fn [index m]
                                                                                                                                             (assoc m ::index index))
                                                                                                                                           data)))))
                                                                                                :haku (fn [data]
                                                                                                        (group-by :rivi data))}
                                                                                    :footer {:polut [[:data-yhteensa]]
                                                                                             :haku (fn [data-yhteensa]
                                                                                                     (reduce-kv (fn [m _ {:keys [a b c]}]
                                                                                                                  (-> m
                                                                                                                      (update :a + a)
                                                                                                                      (update :b + b)
                                                                                                                      (update :c + c)))
                                                                                                                {:rivin-otsikko "Yhteensä"}
                                                                                                                data-yhteensa))}

                                                                                    :data-yhteenveto {:polut [[:data-yhteensa]]
                                                                                                      :luonti (fn [data-yhteensa]
                                                                                                                (vec
                                                                                                                  (map (fn [[rivin-otsikko _]]
                                                                                                                         ;; Luonnissa, luotavan nimi on tärkeä, sillä sitä vasten tarkistetaan olemassa olo
                                                                                                                         {(keyword (str "data-yhteenveto-" rivin-otsikko)) ^{:args [rivin-otsikko]} [[:data-yhteensa rivin-otsikko]]})
                                                                                                                       data-yhteensa)))
                                                                                                      :haku (fn [& args]
                                                                                                              (assoc (first args) :rivin-otsikko (second args)))}
                                                                                    :data-sisalto {:polut [[:data]]
                                                                                                   :luonti (fn [data]
                                                                                                             (mapv (fn [[rivin-otsikko _]]
                                                                                                                     {(keyword (str "data-" rivin-otsikko)) ^{:args [rivin-otsikko]} [[:data]]})
                                                                                                                   (group-by :rivi data)))
                                                                                                   :haku (fn [data rivin-otsikko]
                                                                                                           (filterv #(= (:rivi %) rivin-otsikko) data))
                                                                                                   :identiteetti {1 (fn [arvo]
                                                                                                                      (::index arvo))
                                                                                                                  2 (fn [arvo]
                                                                                                                      (key arvo))}}}

                                                                                   {:aseta-arvo! (fn [tila arvo {:keys [rivi-index arvon-avain]}]
                                                                                                   (println "arvo: " arvo)
                                                                                                   (let [arvo (try (js/Number arvo)
                                                                                                                   (catch :default _
                                                                                                                     arvo))
                                                                                                         vektorin-index (loop [[rivi & loput-rivit] (:data tila)
                                                                                                                               i 0]
                                                                                                                          (let [tama-rivi-index (::index rivi)]
                                                                                                                            (if (= tama-rivi-index rivi-index)
                                                                                                                              i
                                                                                                                              (recur loput-rivit
                                                                                                                                     (inc i)))))]
                                                                                                     (println "kastattu arvo: " arvo " tyyppi: " (type arvo))
                                                                                                     (println "vektorin-index: " vektorin-index)
                                                                                                     (assoc-in tila [:data vektorin-index arvon-avain] arvo)))
                                                                                    :aseta-yhteenveto! (fn [tila arvo iden]
                                                                                                         (println "arvo: " arvo)
                                                                                                         (println "iden: " iden)
                                                                                                         tila)}
                                                                                   {:yhteenveto-seuranta {:polut [[:data]]
                                                                                                          :init (fn [tila]
                                                                                                                  (assoc tila :data-yhteensa
                                                                                                                         (reduce (fn [m {:keys [rivi] :as data-map}]
                                                                                                                                   (update m rivi #(merge-with + % (dissoc data-map :rivi))))
                                                                                                                                 {}
                                                                                                                                 (:data tila))))
                                                                                                          :aseta (fn [tila data]
                                                                                                                   (println "----> YHTEENSÄ")
                                                                                                                   (assoc tila :data-yhteensa
                                                                                                                          (reduce (fn [m {:keys [rivi] :as data-map}]
                                                                                                                                    (update m rivi #(merge-with + % (dissoc data-map :rivi))))
                                                                                                                                  {}
                                                                                                                                  data)))}})
                                                           {[::otsikko] {:rajapinta :otsikot
                                                                         :solun-polun-pituus 1
                                                                         :datan-kasittely identity}
                                                            [::yhteenveto] {:rajapinta :footer
                                                                            :solun-polun-pituus 1
                                                                            :jarjestys [[:rivin-otsikko :a :b :c]]
                                                                            :datan-kasittely (fn [yhteensa]
                                                                                               (mapv (fn [[_ nimi]]
                                                                                                       nimi)
                                                                                                     yhteensa))}
                                                            [::data] {:rajapinta :datarivit
                                                                      :solun-polun-pituus 0
                                                                      :jarjestys [{:keyfn key
                                                                                   :comp (fn [a b]
                                                                                           (compare a b))}]
                                                                      :datan-kasittely identity
                                                                      :luonti (fn [data-ryhmiteltyna-nimen-perusteella]
                                                                                (println "data-ryhmiteltyna-nimen-perusteella: " data-ryhmiteltyna-nimen-perusteella)
                                                                                (map-indexed (fn [index [rivin-otsikko _]]
                                                                                               {[:. index ::data-yhteenveto] {:rajapinta (keyword (str "data-yhteenveto-" rivin-otsikko))
                                                                                                                              :solun-polun-pituus 1
                                                                                                                              :jarjestys [^{:nimi :mapit} [:rivin-otsikko :a :b :c]]
                                                                                                                              :datan-kasittely (fn [yhteenveto]
                                                                                                                                                 (mapv (fn [[_ v]]
                                                                                                                                                         v)
                                                                                                                                                       yhteenveto))
                                                                                                                              :tunnisteen-kasittely (fn [osat _]
                                                                                                                                                      (mapv (fn [osa]
                                                                                                                                                              (when (instance? solu/Syote osa)
                                                                                                                                                                {:osa :maara
                                                                                                                                                                 :rivin-otsikko rivin-otsikko}))
                                                                                                                                                            (grid/hae-grid osat :lapset)))}
                                                                                                [:. index ::data-sisalto] {:rajapinta (keyword (str "data-" rivin-otsikko))
                                                                                                                           :solun-polun-pituus 2
                                                                                                                           :jarjestys [{:keyfn :a
                                                                                                                                        :comp (fn [a1 a2]
                                                                                                                                                (println "[a1 a2] " [a1 a2])
                                                                                                                                                (let [muuta-numeroksi (fn [x]
                                                                                                                                                                        (try (js/Number x)
                                                                                                                                                                             (catch :default _
                                                                                                                                                                               x)))]
                                                                                                                                                  (compare (muuta-numeroksi a1) (muuta-numeroksi a2))))}
                                                                                                                                       ^{:nimi :mapit} [:rivi :a :b :c]]
                                                                                                                           :datan-kasittely (fn [data]
                                                                                                                                              (mapv (fn [rivi]
                                                                                                                                                      (mapv (fn [[_ v]]
                                                                                                                                                              v)
                                                                                                                                                            rivi))
                                                                                                                                                    data))
                                                                                                                           :tunnisteen-kasittely (fn [_ data]
                                                                                                                                                   (vec
                                                                                                                                                     (map-indexed (fn [i rivi]
                                                                                                                                                                    (let [rivi-index (some #(when (= ::index (first %))
                                                                                                                                                                                              (second %))
                                                                                                                                                                                           rivi)]
                                                                                                                                                                      (vec
                                                                                                                                                                        (keep-indexed (fn [j [k _]]
                                                                                                                                                                                        (when-not (= k ::index)
                                                                                                                                                                                          {:rivi-index rivi-index
                                                                                                                                                                                           :arvon-avain k
                                                                                                                                                                                           :osan-paikka [i j]}))
                                                                                                                                                                                      rivi))))
                                                                                                                                                                  data)))}})
                                                                                             data-ryhmiteltyna-nimen-perusteella))}}))))
    (fn [e*! {:keys [grid] :as app}]
      (set! e! e*!)
      [:div
       (if grid
         [grid/piirra grid]
         [:span "Odotellaan..."])])))

(defn foo []
  [tuck/tuck tila foo*])