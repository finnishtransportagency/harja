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

(defn j-m-j [jarjestys data]
  (let [jarjestys-map (zipmap jarjestys (range))
        data (merge (zipmap jarjestys (repeat nil))
                    data)]
    (if (or (record? data) (map? data))
      (into (sorted-map-by #(compare (if-let [n (jarjestys-map %1)] n 9999) (if-let [n (jarjestys-map %2)] n 9999)))
            data)
      (do (warn (str "Yritetään järjestää ei map muotoinen data avainten mukaan. Ei järjestetä."
                     " Järjestys: " jarjestys
                     " Data: " data))
          data))))

(def jarjestyksen-mukaan-jarejstys (memoize j-m-j))

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
  (seq (merge (jarjestyksen-mukaan-jarejstys jarjestys (reduce-kv (fn [m k _] (assoc m k nil)) {} data))
              data)))

(defmethod jarjesta-data :default
  [_ data]
  (cond
    (or (record? data) (map? data)) (mapv val data)
    (sequential? data) (vec data)
    :else data))

(defonce seurannan-valitila (atom {}))
(defonce seurannan-vanha-cache (atom {}))

(def ^:dynamic *da-write* false)
(def ^:dynamic *seuranta-muutos?* false)
(def ^:dynamic *kaytava-data-atom-hash* nil)

(def ^:mutable ^boolean ajetaan-seurannat #{})
(def ^:mutable ^array ajettavat-fns (array))

(defprotocol IKuuntelija
  (lisaa-kuuntelija! [this rajapinta kuuntelija])
  (poista-kuuntelija! [this kuuntelijan-nimi])
  (kuuntelijat-lisaaja! [this rajapinta kuuntelija-luonti]))

(defprotocol ISeuranta
  (lisaa-seuranta! [this seuranta])
  (poista-seuranta! [this seurannan-nimi tila])
  (seurannat-lisaaja! [this seurantojen-luonti]))

(defprotocol IAsettaja
  (lisaa-asettaja! [this rajapinta asettaja]))


;; TODO kuuntelijoiden tilan init samantapaiseksi kuin seurannan siivoa-tila. Eli muokataan annettua tilaa, eikä swapata valitilaa.
;; Pitäisikö laittaa siivoamis ja init funktiot molempiin (seurannat ja kuuntelijat)?
(deftype DatanKasittelija
  ;; Jos muutat argumentteja, tarkista -lookup metodi
  [data-atom nimi ^:mutable muuttuvien-seurantojen-polut ^:mutable on-dispose ^:mutable seurannat-lisaaja
   ^:mutable kuuntelija-lisaaja ^:mutable kuuntelijat ^:mutable asettajat ^:mutable seurannat]
  IKuuntelija
  (lisaa-kuuntelija! [this rajapinta [kuuntelun-nimi {:keys [polut haku luonti-init lisa-argumentit dynaaminen? kuuntelija-lisaajan-nimi kuuntelija-lisaajan-polut]}]]
    (let [kursorit (mapv (fn [polku]
                           (r/cursor data-atom polku))
                         polut)]
      (when luonti-init
        (if dynaaminen?
          (swap! seurannan-valitila (fn [valitilat]
                                      (update-in valitilat
                                                 [*kaytava-data-atom-hash* ::tila]
                                                 (fn [tila]
                                                   (apply luonti-init tila (map #(get-in tila %) kuuntelija-lisaajan-polut))))))
          (binding [*seuranta-muutos?* true]
            (swap! data-atom luonti-init))))
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
                    :kuuntelija-lisaajan-nimi kuuntelija-lisaajan-nimi}))))
  (poista-kuuntelija! [_ kuuntelijan-nimi]
    (let [{:keys [r]} (get kuuntelijat kuuntelijan-nimi)]
      (r/dispose! r)
      (set! kuuntelijat
            (dissoc kuuntelijat kuuntelijan-nimi))))
  (kuuntelijat-lisaaja! [this rajapinta [kuuntelija-lisaajan-nimi {:keys [luonti luonti-init haku] kuuntelija-lisaajan-polut :polut}]]
    (set! kuuntelija-lisaaja
          (assoc kuuntelija-lisaaja
                 kuuntelija-lisaajan-nimi
                 (fn [vanha uusi]
                   (let [vanhat-polut (apply luonti (map #(get-in vanha %) kuuntelija-lisaajan-polut))
                         uudet-polut (apply luonti (map #(get-in uusi %) kuuntelija-lisaajan-polut))

                         polut-muuttunut? (not= vanhat-polut uudet-polut)]
                     (assert (or (nil? uudet-polut)
                                 (empty? uudet-polut)
                                 (every? map? uudet-polut))
                             (str "Kuuntelun lisaaja: " kuuntelija-lisaajan-nimi " antamat polut eivät ole oikean muotoiset!\n"
                                  "Polkujen tulisi olla vektori mappeja. Saatiin:\n"
                                  uudet-polut))
                     (when g-debug/GRID_DEBUG
                       (when (some nil? (flatten (mapcat vals uudet-polut)))
                         (warn "Kuuntelijan luonnissa on uudessa polussa arvo nil:\n"
                               (str uudet-polut))))
                     (when polut-muuttunut?
                       (doseq [[kuuntelun-nimi {:keys [dynaaminen?] kuuntelijan-kuuntelija-lisaajan-nimi :kuuntelija-lisaajan-nimi}] kuuntelijat
                               :let [kuuntelu-poistettava? (and dynaaminen?
                                                                (nil? (some #(= (ffirst %) kuuntelun-nimi)
                                                                            uudet-polut))
                                                                (= kuuntelija-lisaajan-nimi kuuntelijan-kuuntelija-lisaajan-nimi))]]
                         (when kuuntelu-poistettava?
                           (poista-kuuntelija! this kuuntelun-nimi)))
                       (doseq [m uudet-polut
                               :let [[[kuuntelun-nimi polut]] (seq m)
                                     kuuntelu-luotu-jo? (some #(= (key %) kuuntelun-nimi)
                                                              kuuntelijat)
                                     lisa-argumentit (:args (meta polut))]]
                         (when-not kuuntelu-luotu-jo?
                           (lisaa-kuuntelija! this rajapinta [kuuntelun-nimi {:polut polut :haku haku :lisa-argumentit lisa-argumentit :luonti-init luonti-init :dynaaminen? true :kuuntelija-lisaajan-nimi kuuntelija-lisaajan-nimi :kuuntelija-lisaajan-polut kuuntelija-lisaajan-polut}])))))))))
  ISeuranta
  (lisaa-seuranta! [this [seurannan-nimi {:keys [polut aseta lisa-argumentit dynaaminen? siivoa-tila] :as seuranta}]]
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
                                         (if asetetaan-uusi-data?
                                           (let [arvot (mapv #(get-in uusi %) polut)]
                                             (apply aseta uusi (if lisa-argumentit
                                                                 (concat arvot lisa-argumentit)
                                                                 arvot)))
                                           uusi)))))))
  (poista-seuranta! [this seurannan-nimi tila]
    (let [{:keys [polut lisa-argumentit siivoa-tila]} (get seurannat seurannan-nimi)]
      (set! seurannat
            (dissoc seurannat seurannan-nimi))
      (if siivoa-tila
        (let [arvot (mapv #(get-in tila %) polut)]
          (apply siivoa-tila tila (if lisa-argumentit
                                    (concat arvot lisa-argumentit)
                                    arvot)))
        tila)))
  (seurannat-lisaaja! [this [seurannan-nimi {:keys [polut luonti siivoa-tila aseta] :as seuranta}]]
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
                     (if polut-muuttunut?
                       (let [poiston-jalkeinen-tila (reduce-kv (fn [tila seurannan-nimi {:keys [dynaaminen?]}]
                                                                 (let [seuranta-poistettava? (and dynaaminen?
                                                                                                  (nil? (some #(= (ffirst %) seurannan-nimi)
                                                                                                              uudet-polut)))]
                                                                   (if seuranta-poistettava?
                                                                     (poista-seuranta! this seurannan-nimi tila)
                                                                     tila)))
                                                               uusi
                                                               seurannat)]
                         (second (reduce (fn [[vanha uusi] m]
                                           (let [[[seurannan-nimi polut]] (seq m)
                                                 seuranta-luotu-jo? (some #(= (key %) seurannan-nimi)
                                                                          seurannat)
                                                 lisa-argumentit (:args (meta polut))]
                                             (if-not seuranta-luotu-jo?
                                               (do (lisaa-seuranta! this [seurannan-nimi {:polut polut :aseta aseta :lisa-argumentit lisa-argumentit :dynaaminen? true :siivoa-tila siivoa-tila}])
                                                   [uusi ((get-in seurannat [seurannan-nimi :seuranta-fn]) true vanha uusi)])
                                               [vanha uusi])))
                                         [vanha poiston-jalkeinen-tila]
                                         uudet-polut)))
                       uusi))))))
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
      ;; Derefataan data-atom ihan syystä. Jos data-atom on cursor, niin se evaluoituu laiskasti. Eli tämä dereffaus
      ;; ei välttämättä palauta vain arvoa vaan se saattaa triggeröidä cursorin arvon muutoksen, joka taasen triggeröi
      ;; seurannat (watch). Näin halutaan käyvän siltä varalta, että taulukon käyttäjä ei triggeröi vahingossa uusia
      ;; seurantoja dereffatessaan cursoria jossain seuranta funktiossa esim.
      @data-atom
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
                                       vanhat-seurannat seurannat
                                       uusi (reduce (fn [uusi [_ f]]
                                                      (f vanha uusi))
                                                    uusi
                                                    seurannat-lisaaja)
                                       kaydyt-tilat [(hash vanha) (hash uusi)]
                                       muuttunut? (not= (hash vanha) (hash uusi))]
                                  (let [kiertava-tila? (> (- (count kaydyt-tilat)
                                                             (count (distinct kaydyt-tilat)))
                                                          3)
                                        seurannat-muuttunut? (not= vanhat-seurannat seurannat)]
                                    (when (and kiertava-tila?
                                               muuttunut?)
                                      (error "Huomattiin kiertävä seuranta!\n"
                                             "SEURATTAVAT POLUT"
                                             (apply str (interpose " ->\n" muuttuvien-seurantojen-polut))
                                             "kaydyt tilat " (str kaydyt-tilat)
                                             "\nHypätään seurannasta pois."))
                                    (if (or (and muuttunut?
                                                 (not kiertava-tila?))
                                            seurannat-muuttunut?)
                                      (let [paivitetty-tila (loop [[seuranta & loput-seurannat] seurannat
                                                                   vanha vanha
                                                                   uusi uusi]
                                                              (let [[_ {:keys [seuranta-fn polut]}] seuranta]
                                                                (when-not (= (hash vanha) (hash uusi))
                                                                  (set! muuttuvien-seurantojen-polut
                                                                        (conj muuttuvien-seurantojen-polut polut)))
                                                                (if (nil? seuranta)
                                                                  uusi
                                                                  (let [tila (seuranta-fn false vanha uusi)]
                                                                    (recur loput-seurannat
                                                                           vanha
                                                                           (if (= tila vanha)
                                                                             uusi
                                                                             tila))))))]
                                        (recur uusi
                                               seurannat
                                               (reduce (fn [paivitetty-tila [_ f]]
                                                         (f uusi paivitetty-tila))
                                                       paivitetty-tila
                                                       seurannat-lisaaja)
                                               (conj kaydyt-tilat (hash paivitetty-tila))
                                               (not= (hash uusi) (hash paivitetty-tila))))
                                      uusi)))]
            (set! muuttuvien-seurantojen-polut [])
            (let [triggerit-kayty-lapi? (not= paivitetty-tila uusi)]
              (swap! seurannan-valitila assoc-in [data-atom-hash ::tila] paivitetty-tila)
              (binding [*kaytava-data-atom-hash* data-atom-hash]
                (doseq [[_ f] kuuntelija-lisaaja]
                  (f vanha-tila paivitetty-tila)))
              (when triggerit-kayty-lapi?
                (set! ajetaan-seurannat (conj ajetaan-seurannat data-atom-hash))
                (r/next-tick (fn []
                               (binding [*seuranta-muutos?* true]
                                 (when-let [seurannan-tila (get-in @seurannan-valitila [data-atom-hash ::tila])]
                                   (swap! seurannan-valitila #(update % data-atom-hash dissoc ::kaytavan-datan-hash))
                                   (swap! seurannan-vanha-cache dissoc data-atom-hash)
                                   (swap! data-atom (fn [entinen-tila]
                                                      seurannan-tila))
                                   (when (and (= 1 (count ajetaan-seurannat))
                                              ajettavat-fns)
                                     (let [fs ajettavat-fns]
                                       (dotimes [i (alength fs)]
                                         ((aget fs i)))))
                                   (set! ajettavat-fns (array))
                                   (set! ajetaan-seurannat (disj ajetaan-seurannat data-atom-hash)))))))))))))

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
  (-lookup [_ ksym else]
    ; ei voi käyttää vain agetia, koska advanced compilation
    (case ksym
      :data-atom data-atom
      :nimi nimi
      :muuttuvien-seurantojen-polut muuttuvien-seurantojen-polut
      :on-dispose on-dispose
      :seurannat-lisaaja seurannat-lisaaja
      :kuuntelija-lisaaja kuuntelija-lisaaja
      :kuuntelijat kuuntelijat
      :asettajat asettajat
      :seurannat seurannat
      else))
  IEquiv
  (-equiv [this other] (and (= (type this) (type other)) (identical? this other)))
  ratom/IDisposable
  (dispose! [this]
    (remove-watch nimi data-atom)
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

(defn datan-kasittelija [data-atom]
  {:pre [(satisfies? ratom/IReactiveAtom data-atom)]
   :post [(instance? DatanKasittelija %)]}
  (let [nimi (gensym "datan-kasittelija")
        dk (->DatanKasittelija data-atom nimi [] [] {} {} {} {} {})]
    (add-watch data-atom
               nimi
               (fn [_ _ _ uusi]
                 (reset! dk uusi)))
    dk))

(defn next-tick [f]
  (r/next-tick (fn []
                 (if-not (empty? ajetaan-seurannat)
                   (.push ajettavat-fns f)
                   (f)))))

(defn rajapinnan-kuuntelija [kasittelija rajapinnan-nimi]
  (get-in kasittelija [:kuuntelijat rajapinnan-nimi :r]))

(defn poista-seurannat! [kasittelija]
  (doseq [[_ {seurannan-lopetus :seurannan-lopetus!}] (get kasittelija :seurannat)]
    (seurannan-lopetus)))

(defn triggeroi-seuranta! [kasittelija seurannan-nimi]
  (binding [*muutetaan-seurattava-arvo?* true]
    ((get-in kasittelija [:seurannat seurannan-nimi :seuranta-fn]))))

(defn lopeta-tilan-kuuntelu! [kasittelija]
  (doseq [[_ {kuuntelija :r}] (:kuuntelijat kasittelija)]
    (r/dispose! kuuntelija)))

(defn aseta-rajapinnan-data! [kasittelija rajapinta & args]
  (apply (get-in kasittelija [:asettajat rajapinta]) args)
  (r/flush))
