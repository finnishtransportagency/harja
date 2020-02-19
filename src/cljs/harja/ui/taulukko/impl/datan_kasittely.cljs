(ns harja.ui.taulukko.impl.datan-kasittely
  (:require [harja.loki :refer [warn error]]
            [reagent.core :as r]
            [reagent.ratom :as ratom]
            [harja.ui.grid-debug :as g-debug]
            [clojure.set :as clj-set]
            [cljs.spec.alpha :as s]
            [cljs.pprint :as pp])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(s/def ::keyfn fn?)
(s/def ::comp fn?)

(s/def ::jarjestys (s/or :sort-by fn?
                         :sort-by-with-comp (s/keys :req-un [::keyfn ::comp])
                         :mapit-avainten-mukaan (s/coll-of any? :kind vector?)))

(def ^:dynamic *muutetaan-seurattava-arvo?* true)

(defmulti jarjesta-data
          (fn [jarjestys data]
            (let [c (s/conform ::jarjestys jarjestys)]
              (when-not (= ::s/invalid c)
                (first c)))))

(defmethod jarjesta-data :sort-by
  [f data]
  (vec (sort-by f data)))

(defmethod jarjesta-data :sort-by-with-comp
  [{:keys [keyfn comp]} data]
  (let [jarjestetty (sort-by keyfn comp data)]
    (vec jarjestetty)))

(defmethod jarjesta-data :mapit-avainten-mukaan
  [jarjestys data]
  (let [jarjestys-map (zipmap jarjestys (range))
        data (merge (zipmap jarjestys (repeat nil))
                    data)]
    (if (or (record? data) (map? data))
      (sort-by key
               #(compare (jarjestys-map %1) (jarjestys-map %2))
               data)
      (do (warn (str "Yritetään järjestää ei map muotoinen data avainten mukaan. Ei järjestetä."
                     " Järjestys: " jarjestys
                     " Data: " data))
          data))))

(defmethod jarjesta-data :default
  [_ data]
  (cond
    (or (record? data) (map? data)) (mapv val data)
    (sequential? data) (vec data)
    :else data))


(defn merge-recur [& ms]
  (if (every? (fn [x]
                (and (seqable? x) (map-entry? (first x))))
              ms)
    (try (apply merge-with
                (fn [& xs]
                  (apply merge-recur xs))
                ms)
         (catch :default e
           (println "ERROR ")
           (println (str ms))
           (println (every? associative? ms))
           (throw e)))
    (last ms)))



(defonce seurannan-valitila (atom {}))
(defonce seurannan-vanha-cache (atom {}))

(def ^:dynamic *da-write* false)
(def ^:dynamic *seuranta-muutos?* false)

(defprotocol IKuuntelija
  (lisaa-kuuntelija! [this rajapinta kuuntelija])
  (kuuntelijat-lisaaja! [this rajapinta kuuntelija-luonti]))

(defprotocol ISeuranta
  (lisaa-seuranta! [this seuranta])
  (seurannat-lisaaja! [this seurantojen-luonti]))

(defprotocol IAsettaja
  (lisaa-asettaja! [this rajapinta asettaja]))

(deftype DatanKasittelija [data-atom nimi ^:mutable muuttuvien-seurantojen-polut ^:mutable on-dispose ^:mutable seurannat-lisaaja
                           ^:mutable kuuntelija-lisaaja ^:mutable kuuntelijat ^:mutable asettajat ^:mutable seurannat]
  IKuuntelija
  (lisaa-kuuntelija! [this rajapinta [kuuntelun-nimi {:keys [polut haku lisa-argumentit dynaaminen? vanhemman-kuuntelijan-nimi]}]]
    (let [kursorit (mapv (fn [polku]
                           (r/cursor data-atom polku))
                         polut)]
      (println "Lisätään kuuntelija: " kuuntelun-nimi)
      (set! kuuntelijat
            (assoc kuuntelijat
                   kuuntelun-nimi
                   {:r (reaction (let [kursorien-arvot (mapv deref kursorit)
                                       rajapinnan-data (apply haku (if lisa-argumentit
                                                                     (concat kursorien-arvot lisa-argumentit)
                                                                     kursorien-arvot))]
                                   ;; TODO rajapinta dynaamisille
                                   (when-not dynaaminen?
                                     (when (nil? (get rajapinta kuuntelun-nimi))
                                       (warn "Rajapinnan kuuntelijalle " (str kuuntelun-nimi) " ei ole määritetty rajapinnan skeemaa!\n"
                                             "Rajapinta:\n" (with-out-str (pp/pprint rajapinta))))
                                     (when-not (s/valid? (get rajapinta kuuntelun-nimi) rajapinnan-data)
                                       (warn "Rajapinnan " (str kuuntelun-nimi) " data:\n" (with-out-str (pp/pprint rajapinnan-data)) " ei vastaa spekkiin. " (str (get rajapinta kuuntelun-nimi))
                                             (str (s/explain (get rajapinta kuuntelun-nimi) rajapinnan-data)))))
                                   (if (satisfies? IWithMeta rajapinnan-data)
                                     {:data rajapinnan-data
                                      :meta (meta rajapinnan-data)}
                                     {:data rajapinnan-data})))
                    :dynaaminen? dynaaminen?
                    :vanhemman-kuuntelijan-nimi vanhemman-kuuntelijan-nimi}))))
  (kuuntelijat-lisaaja! [this rajapinta [vanhemman-kuuntelijan-nimi {:keys [polut luonti haku]}]]
    (set! kuuntelija-lisaaja
          (assoc kuuntelija-lisaaja
                 vanhemman-kuuntelijan-nimi
                 (fn [vanha uusi]
                   (let [vanhat-polut (apply luonti (map #(get-in vanha %) polut))
                         uudet-polut (apply luonti (map #(get-in uusi %) polut))

                         polut-muuttunut? (not= vanhat-polut uudet-polut)]
                     (assert (or (nil? uudet-polut)
                                 (empty? uudet-polut)
                                 (every? map? uudet-polut))
                             (str "Kuuntelun lisaaja: " vanhemman-kuuntelijan-nimi " antamat polut eivät ole oikean muotoiset!\n"
                                  "Polkujen tulisi olla vektori mappeja. Saatiin:\n"
                                  uudet-polut))
                     (when g-debug/GRID_DEBUG
                       (when (some nil? (flatten (mapcat vals uudet-polut)))
                         (warn "Kuuntelijan luonnissa on uudessa polussa arvo nil:\n"
                               (str uudet-polut))))
                     (when polut-muuttunut?
                       (doseq [[kuuntelun-nimi {:keys [r dynaaminen?] kuuntelijan-vanhemman-kuuntelijan-nimi :vanhemman-kuuntelijan-nimi}] kuuntelijat
                               :let [kuuntelu-poistettava? (and dynaaminen?
                                                                (nil? (some #(= (ffirst %) kuuntelun-nimi)
                                                                            uudet-polut))
                                                                (= vanhemman-kuuntelijan-nimi kuuntelijan-vanhemman-kuuntelijan-nimi))]]
                         (when kuuntelu-poistettava?
                           (println "poistetaan kuuntelija: " kuuntelun-nimi)
                           (r/dispose! r)
                           (set! kuuntelijat
                                 (dissoc kuuntelijat kuuntelun-nimi))))
                       (doseq [m uudet-polut
                               :let [[[kuuntelun-nimi polut]] (seq m)
                                     kuuntelu-luotu-jo? (some #(= (key %) kuuntelun-nimi)
                                                              kuuntelijat)
                                     lisa-argumentit (:args (meta polut))]]
                         (when-not kuuntelu-luotu-jo?
                           (lisaa-kuuntelija! this rajapinta [kuuntelun-nimi {:polut polut :haku haku :lisa-argumentit lisa-argumentit :dynaaminen? true :vanhemman-kuuntelijan-nimi vanhemman-kuuntelijan-nimi}])))))))))
  ISeuranta
  (lisaa-seuranta! [this [seurannan-nimi {:keys [polut aseta lisa-argumentit dynaaminen?] :as seuranta}]]
    (println "Lisätään seuranta: " seurannan-nimi)
    (set! seurannat
          (assoc seurannat
                 seurannan-nimi
                (assoc seuranta
                       :seuranta-fn (fn [seuranta-lisatty? vanha uusi]
                                      (let [vanha-data (doall (map #(get-in vanha %) polut))
                                            uusi-data (doall (map #(get-in uusi %) polut))

                                            seurattava-data-muuttunut? (not= vanha-data uusi-data)
                                            asetetaan-uusi-data? (and (or seurattava-data-muuttunut?
                                                                          seuranta-lisatty?)
                                                                      *muutetaan-seurattava-arvo?*)]
                                        ;(println "-> AJETAAN seuranta " seurannan-nimi)
                                        (if asetetaan-uusi-data?
                                          (let [arvot (mapv #(get-in uusi %) polut)]
                                            (apply aseta uusi (if lisa-argumentit
                                                                (concat arvot lisa-argumentit)
                                                                arvot)))
                                          uusi)))))))
  (seurannat-lisaaja! [this [seurannan-nimi {:keys [polut luonti aseta] :as seuranta}]]
    (set! seurannat-lisaaja
          (assoc seurannat-lisaaja
                 seurannan-nimi
                (fn [vanha uusi]
                  (let [vanhat-polut (apply luonti (map #(get-in vanha %) polut))
                        uudet-polut (apply luonti (map #(get-in uusi %) polut))

                        polut-muuttunut? (not= vanhat-polut uudet-polut)]
                    (assert (or (nil? uudet-polut)
                                (empty? uudet-polut)
                                (every? map? uudet-polut))
                            (str "Seurannan lisaaja: " seurannan-nimi " antamat polut eivät ole oikean muotoiset!\n"
                                 "Polkujen tulisi olla vektori mappeja. Saatiin:\n"
                                 uudet-polut))
                    (when g-debug/GRID_DEBUG
                      (when (some nil? (flatten (mapcat vals uudet-polut)))
                        (warn "Seurannan " seurannan-nimi " luonnissa on uudessa polussa arvo nil:\n"
                              (str uudet-polut))))
                    (when polut-muuttunut?
                      (doseq [[seurannan-nimi {:keys [dynaaminen?]}] seurannat
                              :let [seuranta-poistettava? (and dynaaminen?
                                                               (nil? (some #(= (ffirst %) seurannan-nimi)
                                                                           uudet-polut)))]]
                        (when seuranta-poistettava?
                          (println "Poisteteaan seuranta: " seurannan-nimi)
                          (set! seurannat
                                (dissoc seurannat seurannan-nimi))))
                      (doseq [m uudet-polut
                              :let [[[seurannan-nimi polut]] (seq m)
                                    seuranta-luotu-jo? (some #(= (key %) seurannan-nimi)
                                                             seurannat)
                                    lisa-argumentit (:args (meta polut))]]
                        (when-not seuranta-luotu-jo?
                          (lisaa-seuranta! this [seurannan-nimi {:polut polut :aseta aseta :lisa-argumentit lisa-argumentit :dynaaminen? true}])))
                      (into #{} (map ffirst uudet-polut))))))))
  IAsettaja
  (lisaa-asettaja! [this rajapinta [rajapinnan-nimi f]]
    (set! asettajat
          (assoc asettajat
                 rajapinnan-nimi
                 (fn [& args]
                   (if-let [spec (get rajapinta rajapinnan-nimi)]
                     (when-not (s/valid? spec args)
                       (warn (str "Rajapinnalle " rajapinnan-nimi " annettu data ei vastaa spekkiin. ")
                             (s/conform (get rajapinta rajapinnan-nimi) args)))
                     (warn (str "Asettajalle " rajapinnan-nimi " ei ole määritetty spekkiä rajapinnassa.")))
                   (swap! data-atom (fn [tila]
                                      (try (apply f tila args)
                                           (catch :default e
                                             (error (str "RAJAPINNAN ASTTAJA " rajapinnan-nimi " KAATUI VIRHEESEEN " (.-name e) "\n"
                                                         "ANNETUT ARGUMENTIT:\n" (with-out-str (pp/pprint args))))
                                             (error e)
                                             tila))))))))

  IReset
  (-reset! [this uusi]
    (when-not *seuranta-muutos?*
      (let [uusi-hash (hash uusi)
            data-atom-hash (str (hash data-atom))
            valitilan-hash (get-in @seurannan-valitila [data-atom-hash ::kaytavan-datan-hash])
            edellisen-kieroksen-tila (get-in @seurannan-valitila [data-atom-hash ::tila])
            uusi (cond
                   ;; Ensimmäinen kierros
                   (nil? valitilan-hash) (do (swap! seurannan-vanha-cache assoc data-atom-hash edellisen-kieroksen-tila)
                                             (swap! seurannan-valitila assoc-in [data-atom-hash ::kaytavan-datan-hash] uusi-hash)
                                             uusi)
                   ;; Mahdollista, että tulee uusi tila, ennen next tickiä
                   (not= uusi-hash valitilan-hash) (do (swap! seurannan-valitila assoc data-atom-hash
                                                              {::tila (get @seurannan-vanha-cache data-atom-hash)
                                                               ::kaytavan-datan-hash uusi-hash})
                                                       uusi)
                   ;; Käsitellään vielä saman muutoksen triggereitä
                   (= uusi-hash valitilan-hash) (get-in @seurannan-valitila [data-atom-hash ::tila]))
            vanha-tila (get @seurannan-vanha-cache data-atom-hash)]
        (when-not (= vanha-tila uusi)
          (let [paivitetty-tila (loop [vanha vanha-tila
                                       uusi uusi
                                       seurannat-lisatty (mapv (fn [[_ f]]
                                                                 (f vanha-tila uusi))
                                                               seurannat-lisaaja)
                                       kaydyt-tilat [(hash vanha) (hash uusi)]
                                       muuttunut? (not= (hash vanha) (hash uusi))]
                                  (let [kiertava-tila? (> (- (count kaydyt-tilat)
                                                             (count (distinct kaydyt-tilat)))
                                                          3)
                                        seurannat-lisatty (apply clj-set/union seurannat-lisatty)]
                                    (when (and kiertava-tila?
                                               muuttunut?)
                                      (error "Huomattiin kiertävä seuranta!\n"
                                             "SEURATTAVAT POLUT"
                                             (apply str (interpose " ->\n" muuttuvien-seurantojen-polut))
                                             "kaydyt tilat " (str kaydyt-tilat)
                                             "\nHypätään seurannasta pois."))
                                    (if (or (and muuttunut?
                                                 (not kiertava-tila?))
                                            (not (empty? seurannat-lisatty)))
                                      (let [paivitetty-tila (loop [[seuranta & loput-seurannat] seurannat
                                                                   lisatty? (contains? seurannat-lisatty (first seuranta))
                                                                   vanha vanha
                                                                   uusi uusi]
                                                              (let [[_ {:keys [seuranta-fn polut]}] seuranta]
                                                                (when-not (= (hash vanha) (hash uusi))
                                                                  (set! muuttuvien-seurantojen-polut
                                                                        (conj muuttuvien-seurantojen-polut polut)))
                                                                (if (nil? seuranta)
                                                                  uusi
                                                                  (let [tila (seuranta-fn lisatty? vanha uusi)]
                                                                    (recur loput-seurannat
                                                                           (contains? seurannat-lisatty (ffirst loput-seurannat))
                                                                           vanha
                                                                           (if (= tila vanha)
                                                                             uusi
                                                                             tila))))))]
                                        (recur uusi
                                               paivitetty-tila
                                               (mapv (fn [[_ f]]
                                                       (f uusi paivitetty-tila))
                                                     seurannat-lisaaja)
                                               (conj kaydyt-tilat (hash paivitetty-tila))
                                               (not= (hash uusi) (hash paivitetty-tila))))
                                      uusi)))]
            (set! muuttuvien-seurantojen-polut [])
            (let [triggerit-kayty-lapi? (not= paivitetty-tila uusi)]
              (swap! seurannan-valitila assoc-in [data-atom-hash ::tila] paivitetty-tila)
              (doseq [[_ f] kuuntelija-lisaaja]
                (f vanha-tila paivitetty-tila))
              (when triggerit-kayty-lapi?
                (r/next-tick (fn []
                               (binding [*seuranta-muutos?* true]
                                 (when-let [seurannan-tila (get-in @seurannan-valitila [data-atom-hash ::tila])]
                                   (swap! seurannan-valitila dissoc data-atom-hash)
                                   (swap! seurannan-vanha-cache dissoc data-atom-hash)
                                   (swap! data-atom (fn [entinen-tila]
                                                      seurannan-tila)))))))))))))

  IPrintWithWriter
  (-pr-writer [a writer opts]
    (-write writer (str "#<DatanKasittelija "))
    (if *da-write*
      " - recur -"
      (binding [*da-write* true]
        (pr-writer {:seurannat-lisaaja seurannat-lisaaja :kuuntelija-lisaaja kuuntelija-lisaaja
                    :kuuntelijat kuuntelijat :asettajat asettajat :seurannat seurannat}
                   writer
                   opts)))
    (-write writer ">"))

  IHash
  (-hash [_] (hash nimi))
  ILookup
  (-lookup [this k] (-lookup this k nil))
  (-lookup [this ksym else]
     (if-let [loytyi (and (keyword? ksym) (aget this (name ksym)))]
       loytyi
       else))
  IEquiv
  (-equiv [this other] (and (= (type this) (type other)) (identical? this other)))
  ratom/IDisposable
  (dispose! [this]
    (println "DISPOSATAAN DATAN KASITTELIJÄ")
    (remove-watch nimi data-atom)
    #_(set! tila nil)
    (set! muuttuvien-seurantojen-polut nil)
    (set! seurannat-lisaaja nil)
    (set! kuuntelija-lisaaja nil)
    (doseq [[_ {r :r}] kuuntelijat]
      (r/dispose! r))
    (set! kuuntelijat nil)
    (set! asettajat nil)
    (set! seurannat nil)
    (when on-dispose
      (dotimes [f on-dispose]
        (f this))))

  (add-on-dispose! [this f]
    (set! on-dispose (conj on-dispose f))))

(defonce ^:dynamic *FOOO* nil)

(defn datan-kasittelija [data-atom]
  {:pre [(satisfies? ratom/IReactiveAtom data-atom)]
   :post [(instance? DatanKasittelija %)]}
  (let [nimi (or *FOOO* (gensym "datan-kasittelija"))
        dk (->DatanKasittelija data-atom nimi [] [] {} {} {} {} {})]
    (add-watch data-atom
               nimi
               (fn [_ _ _ uusi]
                 (reset! dk uusi)))
    dk))


(defn rajapinnan-kuuntelija [kasittelija rajapinnan-nimi]
  (get-in kasittelija [:kuuntelijat rajapinnan-nimi :r]))

(defn poista-seurannat! [kasittelija]
  (doseq [[_ {seurannan-lopetus :seurannan-lopetus!}] (get kasittelija :seurannat)]
    (seurannan-lopetus)))

(defn triggeroi-seuranta! [kasittelija seurannan-nimi]
  (binding [*muutetaan-seurattava-arvo?* true]
    ((get-in kasittelija [:seurannat seurannan-nimi :seuranta-trigger!]))))

(defn lopeta-tilan-kuuntelu! [kasittelija]
  (doseq [[_ {kuuntelija :r}] (:kuuntelijat kasittelija)]
    (r/dispose! kuuntelija)))

(defn aseta-rajapinnan-data! [kasittelija rajapinta & args]
  (apply (get-in kasittelija [:asettajat rajapinta]) args)
  (r/flush))
