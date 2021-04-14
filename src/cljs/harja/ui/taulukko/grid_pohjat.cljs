(ns harja.ui.taulukko.grid-pohjat
  (:require [harja.ui.taulukko.grid :as grid]
            [harja.ui.taulukko.grid-oletusarvoja :as konf]
            [harja.ui.taulukko.impl.solu :as solu]
            [harja.ui.taulukko.impl.grid :as g]
            [harja.ui.napit :as napit]
            [reagent.core :as r]
            [clojure.set :as clj-set]
            [harja.ui.taulukko.impl.alue :as alue])
  (:require-macros [harja.ui.taulukko.grid :refer [defsolu]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(defonce ^{:mutable true
           :doc "Hieman likaisen oloista, mutta tätä käytetään sen takia, että SyoteTaytaAlas komponentitn on-blur eventissä
 tarvitsee event arvoa, jotta nähdään, että onko nappia painettu. Halutaan
 kummiskin antaa nappia-painettu! funktiolle eventin sijasta se arvo, joka tulle
 käyttäytymisistä."}
         rivit-alla_ nil)

(defonce ^{:mutable true} on-blur-arvo_ nil)

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
                                 #_#_{osan-paikka :osan-paikka} (grid/solun-asia (::tama-komponentti solu/*this*) :tunniste-rajapinnan-dataan)
                                 #_#_rivin-paikka (first osan-paikka)
                                 #_#_rivit-alla (when klikattu-nappia?
                                              (grid/gridin-rivit (grid/osa-polusta (::tama-komponentti solu/*this*) [:.. :..])
                                                                 (fn [osa]
                                                                   (and (instance? alue/Rivi osa)
                                                                        (> (last (grid/osien-yhteinen-asia osa :index-polku))
                                                                           rivin-paikka)))))]
                             (when-not klikattu-nappia?
                               (grid/paivita-osa! (::tama-komponentti solu/*this*)
                                                  (fn [solu]
                                                    (assoc solu :nappi-nakyvilla? false))))
                             #_(when rivit-alla
                               (set! rivit-alla_ rivit-alla))
                             event))
                 input-osa (solu/syote {:toiminnot (into {}
                                                         (map (fn [[k f]]
                                                                [k (fn [x]
                                                                     (binding [solu/*this* (::tama-komponentti solu/*this*)]
                                                                       (if (= k :on-blur)
                                                                         (do #_(when rivit-alla_
                                                                                 (nappia-painettu! rivit-alla_ x)
                                                                                 (set! rivit-alla_ nil))
                                                                           (set! on-blur-arvo_ x)
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
                 klikattu-fn! (fn [_]
                                  (let [{osan-paikka :osan-paikka} (grid/solun-asia solu/*this* :tunniste-rajapinnan-dataan)
                                        rivin-paikka (first osan-paikka)
                                        rivit-alla (grid/gridin-rivit (grid/osa-polusta solu/*this* [:.. :..])
                                                                      (fn [osa]
                                                                        (and (instance? alue/Rivi osa)
                                                                             (> (last (grid/osien-yhteinen-asia osa :index-polku))
                                                                                rivin-paikka))))]
                                    (when rivit-alla
                                      (nappia-painettu! rivit-alla on-blur-arvo_)
                                      (set! on-blur-arvo_ nil))
                                    (grid/paivita-osa! solu/*this*
                                                       (fn [solu]
                                                         (assoc solu :nappi-nakyvilla? false)))))]
             (fn [this]
               [:div.kustannus-syotto {:tab-index -1
                                       :data-id (str (grid/hae-osa this :id))}
                [napit/yleinen-ensisijainen "Kopioi allaoleviin"
                 (r/partial (fn [event]
                              (binding [solu/*this* this]
                                (klikattu-fn! event))))
                 {:luokka (str "kopioi-nappi button-primary-default "
                               (when-not (:nappi-nakyvilla? this)
                                 "piillotettu"))
                  :data-attributes {:data-kopioi-allaoleviin true
                                    :data-cy "kopioi-allaoleviin"}
                  :tabindex 0}]
                [grid/piirra (assoc input-osa ::tama-komponentti this
                                    :parametrit (:parametrit this)
                                    :harja.ui.taulukko.impl.grid/osan-derefable (grid/solun-asia this :osan-derefable))]]))))

(defn tee-osa [{:keys [tyyppi id luokat parametrit fmt fmt-aktiivinen nimi aukaise-fn auki-alussa? toiminnot kayttaytymiset
                       nappi-nakyvilla? nappia-painettu! ikoni linkki sisalto constructor vaihtoehdot rivin-haku] :as asetukset}]
  (case tyyppi
    :teksti (solu/teksti {:parametrit (merge {:id id :class luokat} parametrit) :fmt fmt :nimi nimi})
    :laajenna (solu/laajenna {:aukaise-fn aukaise-fn
                              :auki-alussa? auki-alussa?
                              :parametrit (merge {:id id :class luokat :ikoni ikoni} parametrit)
                              :fmt fmt
                              :nimi nimi})
    :syote (solu/syote {:toiminnot toiminnot :kayttaytymiset kayttaytymiset :parametrit (merge {:id id :class luokat} parametrit)
                        :fmt fmt :fmt-aktiivinen fmt-aktiivinen :nimi nimi})
    :syote-tayta-alas (syote-tayta-alas nappi-nakyvilla? (or nappia-painettu! identity) toiminnot kayttaytymiset
                                        (merge {:id id :class luokat} parametrit) fmt fmt-aktiivinen)
    :linkki (solu/linkki {:parametrit (merge {:id id :class luokat} parametrit) :linkki linkki :fmt fmt :nimi nimi})
    :nappi (solu/nappi {:toiminnot toiminnot :kayttaytymiset kayttaytymiset :parametrit (merge {:id id :class luokat} parametrit)
                        :sisalto sisalto :fmt fmt :nimi nimi})
    :ikoni (solu/ikoni {:parametrit (merge {:id id :class luokat} parametrit) :fmt fmt :nimi nimi})
    :pudotusvalikko (solu/pudotusvalikko (select-keys asetukset #{:valinta :format-fn :valitse-fn :class :disabled? :itemit-komponentteja? :naytettava-arvo
                                                                  :on-focus :title :li-luokka-fn :ryhmittely :nayta-ryhmat :ryhman-otsikko :data-cy :vayla-tyyli?})
                                         vaihtoehdot
                                         rivin-haku)
    :tyhja (solu/tyhja luokat)
    :oma (constructor asetukset)
    (solu/tyhja)))

(defn otsikkorivi-pohja [otsikkorivi header-korkeus header-luokat sarakkeiden-maara]
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
                :koko (-> konf/livi-oletuskoko
                          (assoc-in [:sarake :leveydet] leveydet)
                          (update-in [:rivi :korkeudet 0] (fn [korkeudet]
                                                            (if header-korkeus
                                                              header-korkeus
                                                              korkeudet))))
                :osat (mapv tee-osa otsikkorivi)
                :luokat (clj-set/union #{"salli-ylipiirtaminen"} header-luokat)
                :data-cy "otsikko-rivi"}
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

(defn uusi-taulukko [{:keys [taulukon-id root-luokat root-asetukset root-asetus! header body footer header-korkeus header-luokat body-luokat]}]
  {:pre [(vector? header)
         (every? map? header)

         (vector? body)
         #_(every? vector? body)
         #_(every? map? (mapcat identity body))

         (or (nil? footer)
             (and (vector? footer)
                  (every? map? footer)))]}
  (let [sarakkeiden-maara (count header)
        otsikko-ja-data [(otsikkorivi-pohja header header-korkeus header-luokat sarakkeiden-maara)
                         (grid/grid {:nimi ::data
                                     :alueet [{:sarakkeet [0 1] :rivit [0 1]}]
                                     :koko konf/auto
                                     :luokat (clj-set/union #{"salli-ylipiirtaminen"} body-luokat)
                                     :osat (mapv #(data-pohja % sarakkeiden-maara) body)})]
        g (grid/grid {:nimi ::root
                      :luokat root-luokat
                      :alueet [{:sarakkeet [0 1] :rivit [0 3]}]
                      :koko (update konf/auto :rivi (fn [rivi-koko]
                                                      (-> rivi-koko
                                                          (assoc :nimet
                                                                 {::otsikko 0
                                                                  ::data 1
                                                                  ::yhteenveto 2})
                                                          (assoc :korkeudet
                                                                 {0 (or header-korkeus "40px")
                                                                  2 "40px"}))))
                      :dom-id taulukon-id
                      :osat (if footer
                              (conj otsikko-ja-data
                                    (footer-pohja footer sarakkeiden-maara))
                              otsikko-ja-data)})
        g (grid/aseta-root-fn! g root-asetukset)]
    (root-asetus! g)
    (println "petar GRID " (pr-str (keys g)))
    g))