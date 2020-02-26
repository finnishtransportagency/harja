(ns harja.ui.taulukko.grid-pohjat
  (:require [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.impl.grid :as g]
            [harja.ui.napit :as napit]
            [clojure.set :as clj-set]
            [harja.ui.taulukko.impl.alue :as alue])
  (:require-macros [harja.ui.taulukko.grid :refer [defsolu]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defn grid-pohjasta [grid-pohja]
  (let [kopio (grid/kopio grid-pohja)]
    (g/muuta-id! kopio)))

(def grid-pohja-4 (grid/grid {:nimi ::root
                              :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
                              :koko (assoc-in konf/auto
                                              [:rivi :nimet]
                                              {::otsikko 0
                                               ::data 1
                                               ::yhteenveto 2})
                              :osat [(grid/rivi {:nimi ::otsikko
                                                 :koko (-> konf/livi-oletuskoko
                                                           (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                                          3 "1fr"})
                                                           (assoc-in [:sarake :oletus-leveys] "2fr"))
                                                 :osat (mapv (fn [_]
                                                               (solu/tyhja))
                                                             (range 4))
                                                 :luokat #{"salli-ylipiirtaminen"}}
                                                [{:sarakkeet [0 4] :rivit [0 1]}])
                                     (grid/taulukko {:nimi ::data
                                                     :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                                     :koko konf/auto
                                                     :luokat #{"salli-ylipiirtaminen"}}
                                                    [(grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 2]}]
                                                                 :koko (assoc-in konf/auto
                                                                                 [:rivi :nimet]
                                                                                 {::data-yhteenveto 0
                                                                                  ::data-sisalto 1})
                                                                 :luokat #{"salli-ylipiirtaminen"}
                                                                 :osat [(grid/rivi {:nimi ::data-yhteenveto
                                                                                    :koko {:seuraa {:seurattava ::otsikko
                                                                                                    :sarakkeet :sama
                                                                                                    :rivit :sama}}
                                                                                    :osat (mapv (fn [_]
                                                                                                  (solu/tyhja))
                                                                                                (range 4))
                                                                                    :luokat #{"salli-ylipiirtaminen"}}
                                                                                   [{:sarakkeet [0 4] :rivit [0 1]}])
                                                                        (grid/taulukko {:nimi ::data-sisalto
                                                                                        :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                                                                        :koko konf/auto
                                                                                        :luokat #{"piillotettu" "salli-ylipiirtaminen"}}
                                                                                       [(grid/rivi {:koko {:seuraa {:seurattava ::otsikko
                                                                                                                    :sarakkeet :sama
                                                                                                                    :rivit :sama}}
                                                                                                    :osat (mapv (fn [_]
                                                                                                                  (solu/tyhja))
                                                                                                                (range 4))
                                                                                                    :luokat #{"salli-ylipiirtaminen"}}
                                                                                                   [{:sarakkeet [0 4] :rivit [0 1]}])])]})])
                                     (grid/rivi {:nimi ::yhteenveto
                                                 :koko {:seuraa {:seurattava ::otsikko
                                                                 :sarakkeet :sama
                                                                 :rivit :sama}}
                                                 :osat (mapv (fn [_]
                                                               (solu/tyhja))
                                                             (range 4))}
                                                [{:sarakkeet [0 4] :rivit [0 1]}])]}))

(def grid-pohja-5 (let [grid-pohja (grid-pohjasta grid-pohja-4)]
                    (grid/lisaa-sarake! grid-pohja (solu/tyhja))
                    (grid/aseta-grid! (grid/get-in-grid grid-pohja [::otsikko])
                                      :koko
                                      (-> konf/livi-oletuskoko
                                          (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                         4 "1fr"})
                                          (assoc-in [:sarake :oletus-leveys] "2fr")))
                    (grid/aseta-grid! (grid/get-in-grid grid-pohja [::data 0 ::data-yhteenveto])
                                      :koko
                                      (-> konf/livi-oletuskoko
                                          (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                         4 "1fr"})
                                          (assoc-in [:sarake :oletus-leveys] "2fr")))
                    (grid/aseta-grid! (grid/get-in-grid grid-pohja [::data 0 ::data-sisalto])
                                      :koko
                                      (-> konf/livi-oletuskoko
                                          (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                         4 "1fr"})
                                          (assoc-in [:sarake :oletus-leveys] "2fr")))
                    grid-pohja))

(def grid-pohja-7 (let [grid-pohja (grid-pohjasta grid-pohja-5)]
                    (grid/lisaa-sarake! grid-pohja (solu/tyhja))
                    (grid/lisaa-sarake! grid-pohja (solu/tyhja))
                    #_(grid/aseta-grid! grid-pohja
                                        :koko
                                        (-> konf/livi-oletuskoko
                                            (assoc-in [:sarake :leveydet] {0 "3fr"
                                                                           1 "1fr"})
                                            (assoc-in [:sarake :oletus-leveys] "2fr")))
                    grid-pohja))

(defonce ^{:mutable true
           :doc "Hieman likaisen oloista, mutta tätä käytetään sen takia, että SyoteTaytaAlas komponentitn on-blur eventissä
 tarvitsee event arvoa, jotta nähdään, että onko nappia painettu. Halutaan
 kummiskin antaa nappia-painettu! funktiolle eventin sijasta se arvo, joka tulle
 käyttäytymisistä."}
         rivit-alla_ nil)

(defsolu SyoteTaytaAlas
         [nappi-nakyvilla? nappia-painettu! toiminnot kayttaytymiset parametrit fmt fmt-aktiivinen]
         {:pre [(boolean? nappi-nakyvilla?)]}
         (fn hankintasuunnitelmien-syotto [this]
           (let [on-blur (fn [event]
                           (let [klikattu-elementti (.-relatedTarget event)
                                 klikattu-nappia? (and (not (nil? klikattu-elementti))
                                                       (or (.getAttribute klikattu-elementti "data-kopioi-allaoleviin")
                                                           (= (str (.getAttribute klikattu-elementti "data-id"))
                                                              (str (grid/hae-osa (::tama-komponentti solu/*this*) :id)))))
                                 {osan-paikka :osan-paikka} (grid/solun-asia (::tama-komponentti solu/*this*) :tunniste-rajapinnan-dataan)
                                 rivin-paikka (first osan-paikka)
                                 rivit-alla (when klikattu-nappia?
                                              (grid/gridin-rivit (grid/osa-polusta (::tama-komponentti solu/*this*) [:.. :..])
                                                                 (fn [osa]
                                                                   (and (instance? alue/Rivi osa)
                                                                        (> (last (grid/osien-yhteinen-asia osa :index-polku))
                                                                           rivin-paikka)))))]
                             (when rivit-alla
                               (set! rivit-alla_ rivit-alla))
                             event))
                 input-osa (solu/syote {:toiminnot (into {}
                                                         (map (fn [[k f]]
                                                                [k (fn [x]
                                                                     (binding [solu/*this* (::tama-komponentti solu/*this*)]
                                                                       (if (= k :on-blur)
                                                                         (do (when rivit-alla_
                                                                               (nappia-painettu! rivit-alla_ x)
                                                                               (set! rivit-alla_ nil))
                                                                             (f x))
                                                                         (f x))))])
                                                              toiminnot))
                                        :kayttaytymiset (into {}
                                                              (map (fn [[k v]]
                                                                     [k (if (= k :on-blur)
                                                                          (conj (or v []) {:oma {:f on-blur}})
                                                                          v)])
                                                                   kayttaytymiset))
                                        :parametrit parametrit
                                        :fmt fmt
                                        :fmt-aktiivinen fmt-aktiivinen})
                 ala-tee-mitaan (fn [&_])]
             (fn [this]
               [:div.kustannus-syotto {:tab-index -1
                                       :data-id (str (grid/hae-osa this :id))}
                [napit/yleinen-ensisijainen "Kopioi allaoleviin" ala-tee-mitaan
                 {:luokka (str "kopioi-nappi button-primary-default "
                               (when-not (:nappi-nakyvilla? this)
                                 "piillotettu"))
                  :data-attributes {:data-kopioi-allaoleviin true}
                  :tabindex 0}]
                [grid/piirra (assoc input-osa ::tama-komponentti this
                                    :parametrit (:parametrit this)
                                    :harja.ui.taulukko.impl.grid/osan-derefable (grid/solun-asia this :osan-derefable))]]))))

(defn tee-osa [{:keys [tyyppi id luokat parametrit fmt fmt-aktiivinen nimi aukaise-fn auki-alussa? toiminnot kayttaytymiset
                       nappi-nakyvilla? nappia-painettu!]}]
  (case tyyppi
    :teksti (solu/teksti {:parametrit (merge {:id id :class luokat} parametrit) :fmt fmt :nimi nimi})
    :laajenna (solu/laajenna {:aukaise-fn aukaise-fn
                              :auki-alussa? auki-alussa?
                              :parametrit (merge {:id id :class luokat} parametrit)
                              :fmt fmt
                              :nimi nimi})
    :syote (solu/syote {:toiminnot toiminnot :kayttaytymiset kayttaytymiset :parametrit (merge {:id id :class luokat} parametrit)
                        :fmt fmt :fmt-aktiivinen fmt-aktiivinen :nimi nimi})
    :syote-tayta-alas (syote-tayta-alas nappi-nakyvilla? (or nappia-painettu! identity) toiminnot kayttaytymiset
                                        (merge {:id id :class luokat} parametrit) fmt fmt-aktiivinen)
    :tyhja (solu/tyhja)
    (solu/tyhja)))

(defn otsikkorivi-pohja [otsikkorivi sarakkeiden-maara]
  (let [vakioleveys 1
        suhteelliset-leveydet-yhteensa (reduce (fn [summa {:keys [leveys] :or {leveys vakioleveys}}]
                                                 (+ summa leveys))
                                               0
                                               otsikkorivi)
        leveydet (into {}
                       (map-indexed (fn [index {:keys [leveys] :or {leveys vakioleveys}}]
                                      [index (str (* (/ leveys suhteelliset-leveydet-yhteensa) 100) "%")])
                                    otsikkorivi))]
    (grid/rivi {:nimi ::otsikko
                :koko (assoc-in konf/livi-oletuskoko [:sarake :leveydet] leveydet)
                :osat (mapv tee-osa otsikkorivi)
                :luokat #{"salli-ylipiirtaminen"}}
               [{:sarakkeet [0 sarakkeiden-maara] :rivit [0 1]}])))

(defn data-pohja [{:keys [nimi tyyppi osat luokat] :as body} sarakkeiden-maara]
  (case tyyppi
    :taulukko (let [osioiden-maara (count osat)
                    osien-nimet (mapv :nimi osat)
                    osien-koot (into {}
                                     (keep (fn [[nimi index]]
                                             (when nimi
                                               [nimi index]))
                                           (zipmap osien-nimet
                                                   (range))))]
                (grid/grid {:alueet [{:sarakkeet [0 1] :rivit [0 osioiden-maara]}]
                            :nimi nimi
                            :koko (if-not (empty? osien-koot)
                                    (assoc-in konf/auto
                                              [:rivi :nimet]
                                              osien-koot)
                                    konf/auto)
                            :luokat (clj-set/union #{"salli-ylipiirtaminen"} luokat)
                            :osat (vec
                                    (loop [[osio-conf & osio-confit] osat
                                           osiot []]
                                      (if (nil? osio-conf)
                                        osiot
                                        (let [osio (data-pohja osio-conf sarakkeiden-maara)]
                                          (recur osio-confit
                                                 (conj osiot
                                                       osio))))))}))
    :rivi (grid/rivi {:nimi nimi
                      :koko {:seuraa {:seurattava ::otsikko
                                      :sarakkeet :sama
                                      :rivit :sama}}
                      :osat (mapv tee-osa osat)
                      :luokat (clj-set/union #{"salli-ylipiirtaminen"} luokat)}
                     [{:sarakkeet [0 sarakkeiden-maara] :rivit [0 1]}])))

(defn footer-pohja [footer sarakkeiden-maara]
  (grid/rivi {:nimi ::yhteenveto
              :koko {:seuraa {:seurattava ::otsikko
                              :sarakkeet :sama
                              :rivit :sama}}
              :osat (mapv tee-osa footer)}
             [{:sarakkeet [0 sarakkeiden-maara] :rivit [0 1]}]))

(defn uusi-taulukko [{:keys [taulukon-id root-asetukset root-asetus! header body footer]}]
  {:pre [(vector? header)
         (every? map? header)

         (vector? body)
         #_(every? vector? body)
         #_(every? map? (mapcat identity body))

         (or (nil? footer)
             (and (vector? footer)
                  (every? map? footer)))]}
  (let [sarakkeiden-maara (count header)
        otsikko-ja-data [(otsikkorivi-pohja header sarakkeiden-maara)
                         (grid/taulukko {:nimi ::data
                                         :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                         :koko konf/auto
                                         :luokat #{"salli-ylipiirtaminen"}}
                                        (mapv #(data-pohja % sarakkeiden-maara) body))]
        g (grid/grid {:nimi ::root
                      :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
                      :koko (update konf/auto :rivi (fn [rivi-koko]
                                                      (-> rivi-koko
                                                          (assoc :nimet
                                                                 {::otsikko 0
                                                                  ::data 1
                                                                  ::yhteenveto 2})
                                                          (assoc :korkeudet
                                                                 {0 "40px"
                                                                  2 "40px"}))))
                      :dom-id taulukon-id
                      :osat (if footer
                              (conj otsikko-ja-data
                                    (footer-pohja footer sarakkeiden-maara))
                              otsikko-ja-data)})
        g (grid/aseta-root-fn g root-asetukset)]
    (root-asetus! g)
    g))