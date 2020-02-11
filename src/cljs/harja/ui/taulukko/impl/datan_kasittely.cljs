(ns harja.ui.taulukko.impl.datan-kasittely
  (:require [harja.loki :refer [warn error]]
            [reagent.core :as r]
            [cljs.core.async :as async]
            [cljs.spec.alpha :as s]
            [clojure.set :as clj-set]
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
                (and (seq? x) (map-entry? (first x))))
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

(defn rajapinnan-kuuntelijat [data-atom rajapinta kuvaus]
  (reduce (fn [kuuntelijat [rajapinnan-nimi {:keys [polut haku]}]]
            (let [kursorit (mapv (fn [polku]
                                   (r/cursor data-atom polku))
                                 polut)]
              (assoc kuuntelijat
                     rajapinnan-nimi
                     (reaction (let [rajapinnan-data (apply haku (mapv deref kursorit))]
                                 (when (nil? (get rajapinta rajapinnan-nimi))
                                   (warn "Rajapinnan kuuntelijalle " (str rajapinnan-nimi) " ei ole määritetty rajapinnan skeemaa!\n"
                                         "Rajapinta:\n" (with-out-str (pp/pprint rajapinta))))
                                 (when-not (s/valid? (get rajapinta rajapinnan-nimi) rajapinnan-data)
                                   (warn "Rajapinnan " (str rajapinnan-nimi) " data:\n" (with-out-str (pp/pprint rajapinnan-data)) " ei vastaa spekkiin. " (str (get rajapinta rajapinnan-nimi))
                                         (str (s/explain (get rajapinta rajapinnan-nimi) rajapinnan-data))))
                                 rajapinnan-data)))))
          {}
          kuvaus))
#_(defn rajapinnan-kuuntelijat [data-atom rajapinta kuvaus]
  (reduce (fn [kuuntelijat [rajapinnan-nimi {:keys [polut haku dynamic-fns]}]]
            (let [kursorit (mapv (fn [polku]
                                   (r/cursor data-atom polku))
                                 polut)
                  haku-tulos (reaction (let [rajapinnan-data (apply haku (mapv deref kursorit))]
                                         (when-not (s/valid? (get rajapinta rajapinnan-nimi) rajapinnan-data)
                                           (warn "Rajapinnan " (str rajapinnan-nimi) " data:\n" (with-out-str (pp/pprint rajapinnan-data)) " ei vastaa spekkiin. " (str (get rajapinta rajapinnan-nimi))
                                                 (str (s/explain (get rajapinta rajapinnan-nimi) rajapinnan-data))))
                                         rajapinnan-data))]
              (assoc kuuntelijat
                     rajapinnan-nimi
                     (if dynamic-fns
                       (loop [[f & fs] dynamic-fns
                              tulos @haku-tulos]
                         (if (nil? f)
                           (mapv #(r/atom %) tulos)
                           (recur fs (f tulos))))
                       haku-tulos))))
          {}
          kuvaus))

(defn rajapinnan-asettajat [data-atom rajapinta kuvaus]
  (into {}
        (map (fn [[rajapinnan-nimi f]]
               [rajapinnan-nimi (fn [& args]
                                  (when-not (s/valid? (get rajapinta rajapinnan-nimi) args)
                                    (warn "Rajapinnalle " rajapinnan-nimi " annettu data ei vastaa spekkiin. "
                                          (s/conform (get rajapinta rajapinnan-nimi) args)))
                                  (swap! data-atom (fn [tila]
                                                     (try (apply f tila args)
                                                          (catch :default e
                                                            (error (str "RAJAPINNAN ASTTAJA " rajapinnan-nimi " KAATUI VIRHEESEEN " (.-name e) "\n"
                                                                        "ANNETUT ARGUMENTIT:\n" (with-out-str (pp/pprint args))))
                                                            (error e)
                                                            tila)))))])
             kuvaus)))

(defonce kaikki-seurannat (atom nil))
(defonce kaydyt-seurannat (atom nil))
(defonce seurannan-valitila (atom nil))
(defonce seurannan-tila (atom {}))

(defn paivita-datanseuranta-atomit [data-atom kaikki-taman-seurannat]
  (let [datan-kasittelytunniste (get @data-atom ::tunniste)]
    (swap! kaikki-seurannat (fn [kaikki-seurannat]
                              (update kaikki-seurannat
                                      datan-kasittelytunniste
                                      (fn [seurannat]
                                        (clj-set/union seurannat kaikki-taman-seurannat)))))))

(defn init-datanseuranta-atomit [data-atom]
  (let [datan-kasittelytunniste (gensym "datan-kasittely")]
    (swap! data-atom (fn [tila]
                       (assoc tila ::tunniste datan-kasittelytunniste)))
    (swap! kaydyt-seurannat (fn [tila]
                              (assoc tila datan-kasittelytunniste [#{}])))
    (swap! seurannan-valitila (fn [tila]
                                (assoc tila datan-kasittelytunniste nil)))))

(defn aseta-seuranta! [data-atom seurannat]
  (println "AJETAAN INIT ARVOT")
  (doseq [[seurannan-nimi {init :init}] seurannat]
    (when (fn? init)
      (swap! data-atom
             (fn [tila]
               (merge tila (init tila))))))
  (when-not (get @data-atom ::tunniste)
    (init-datanseuranta-atomit data-atom))
  (paivita-datanseuranta-atomit data-atom (into #{} (keys seurannat)))
  (let [datan-kasittelytunniste (get @data-atom ::tunniste)
        seuranta-fn! (fn [{:keys [aseta polut]} seurannan-nimi uusi-data]
                       (swap! seurannan-valitila
                              (fn [kaikki-tilat]
                                (update kaikki-tilat
                                        datan-kasittelytunniste
                                        (fn [tila]
                                          (let [seurattava-data (or tila uusi-data)]
                                            (merge-recur
                                                        tila
                                                        (apply aseta seurattava-data (doall (map #(get-in seurattava-data %) polut))))))))))]


    (into {}
          (doall (map (fn [[seurannan-nimi {:keys [polut] :as seuranta}]]
                        (add-watch data-atom
                                   seurannan-nimi
                                   (fn [_ _ vanha uusi]
                                     (let [reaction (.-reaction data-atom)]
                                       ;; cursorin dereffaaminen ei pelkästään palauta sen nykyistä arvoa, vaan
                                       ;; se saattaa myös asettaa uuden arvon riippuen sen käyttämän ratomin tilasta.
                                       ;; Koska tässä swappaillaan add-watchin sisällä, on ratomilla aina se vanha arvo,
                                       ;; jonka deref cursorille yrittää myös asettaa. Tätä ei kuitenkaan haluta, joten
                                       ;; käytetään tämmöistä haksia, että cursorin dereffaaminen ei aiheuta tilan muutosta.
                                       (when (instance? reagent.ratom/RCursor data-atom)
                                         (set! (.-reaction data-atom)
                                               (reify
                                                 IDeref
                                                 (-deref [_]
                                                   uusi))))

                                       (swap! kaydyt-seurannat (fn [ks]
                                                                 (update ks
                                                                         datan-kasittelytunniste
                                                                         (fn [ks]
                                                                           (vec
                                                                             (loop [[kierros & loput] (reverse ks)
                                                                                    i (dec (count ks))
                                                                                    kierrokset ks
                                                                                    lisatty? false]
                                                                               (cond
                                                                                 lisatty? kierrokset
                                                                                 (nil? kierros) (conj kierrokset #{seurannan-nimi})
                                                                                 :else (let [kierros-sisaltaa-seurannan? (contains? kierros seurannan-nimi)]
                                                                                         (recur loput
                                                                                                (dec i)
                                                                                                (if kierros-sisaltaa-seurannan?
                                                                                                  kierrokset
                                                                                                  (vec (map-indexed (fn [index kierros]
                                                                                                                      (if (= index i)
                                                                                                                        (conj kierros seurannan-nimi)
                                                                                                                        kierros))
                                                                                                                    kierrokset)))
                                                                                                (not kierros-sisaltaa-seurannan?))))))))))
                                       (let [vanha-data (doall (map #(get-in vanha %) polut))
                                             uusi-data (doall (map #(get-in uusi %) polut))

                                             seurattava-data-muuttunut? (not= vanha-data uusi-data)
                                             asetetaan-uusi-data? (and seurattava-data-muuttunut?
                                                                       *muutetaan-seurattava-arvo?*)]
                                         (when asetetaan-uusi-data?
                                           (seuranta-fn! seuranta seurannan-nimi uusi)))
                                       (when (= (get @kaikki-seurannat datan-kasittelytunniste) (last (get @kaydyt-seurannat datan-kasittelytunniste)))
                                         (if-let [sv (get @seurannan-valitila datan-kasittelytunniste)]
                                           (do
                                             (swap! seurannan-valitila #(assoc % datan-kasittelytunniste nil))
                                             (swap! data-atom (fn [da]
                                                                (merge-recur da sv))))
                                           ;; Halutaan lukea vain state eikä laukaista _set-state:a
                                           (let [data-atom-sisalto (.-state data-atom)]
                                             (swap! seurannan-tila (fn [kaikki-seurannat]
                                                                     (update kaikki-seurannat
                                                                             datan-kasittelytunniste
                                                                             (fn [vanha-seurannan-tila]
                                                                               (merge-recur vanha-seurannan-tila data-atom-sisalto)))))
                                             ;; Lopullinen muutos data-atomiin pitää tehdä joskus muuloin kuin
                                             ;; add-watchin sisällä, kosksa add-watchin alun perin laukaisema muutos
                                             ;; muutoin lopussa overridaa tuloksen cursoreille.

                                             ;; Pitää käyttää seurannan-tila atomia, koska next-tick ei välttämättä laukea ennen kuin
                                             ;; data-atom:ia on muutettu useammin kuin kerran
                                             (r/next-tick (fn []
                                                            (reset! data-atom (get @seurannan-tila datan-kasittelytunniste))))))
                                         (swap! kaydyt-seurannat #(update % datan-kasittelytunniste (fn [ks] (vec (butlast ks))))))
                                       (when (instance? reagent.ratom/RCursor data-atom)
                                         (set! (.-reaction data-atom) reaction)))))
                        [seurannan-nimi {:seurannan-lopetus! (fn []
                                                               (remove-watch data-atom seurannan-nimi))
                                         :seuranta-trigger! (fn []
                                                              (seuranta-fn! seuranta seurannan-nimi @data-atom))}])
                      seurannat)))))

(defn rajapinnan-kuuntelija [kasittelija rajapinnan-nimi]
  (get-in kasittelija [:kuuntelijat rajapinnan-nimi]))

(defn poista-seurannat! [kasittelija]
  (doseq [[_ {seurannan-lopetus :seurannan-lopetus!}] (get kasittelija :seurannat)]
    (seurannan-lopetus)))

(defn triggeroi-seuranta! [kasittelija seurannan-nimi]
  (binding [*muutetaan-seurattava-arvo?* true]
    ((get-in kasittelija [:seurannat seurannan-nimi :seuranta-trigger!]))))

(defn lopeta-tilan-kuuntelu! [kasittelija]
  (doseq [[_ kuuntelija] (:kuuntelijat kasittelija)]
    (r/dispose! kuuntelija)))

(defn aseta-rajapinnan-data! [kasittelija rajapinta & args]
  (apply (get-in kasittelija [:asettajat rajapinta]) args)
  (r/flush))
