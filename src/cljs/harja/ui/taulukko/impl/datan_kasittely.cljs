(ns harja.ui.taulukko.impl.datan-kasittely
  (:require [harja.loki :refer [warn]]
            [reagent.core :as r]
            [cljs.core.async :as async]
            [cljs.spec.alpha :as s])
  (:require-macros [reagent.ratom :refer [reaction]]
                   [cljs.core.async.macros :refer [go go-loop]]))

(s/def ::keyfn fn?)
(s/def ::comp fn?)

(s/def ::jarjestys (s/or :sort-by-with-comp (s/keys :req-un [::keyfn ::comp])
                         :mapit-avainten-mukaan (s/coll-of any? :kind vector?)))

(def ^:dynamic *muutetaan-seurattava-arvo?* true)
(def ^:dynamic *suoritettava-seuranta* nil)
(def ^:dynamic *watch-kutsuttu-vanhalle-datalle?* false)
(def ^:dynamic *lisataan-seuranta?* false)

(defmulti jarjesta-data
          (fn [jarjestys data]
            (let [c (s/conform ::jarjestys jarjestys)]
              (when-not (= ::s/invalid c)
                (first c)))))

(defmethod jarjesta-data :sort-by-with-comp
  [{:keys [keyfn comp]} data]
  (let [jarjestetty (sort-by keyfn comp data)]
    (vec jarjestetty)))

(defmethod jarjesta-data :mapit-avainten-mukaan
  [jarjestys data]
  (let [jarjestys-map (zipmap jarjestys (range))]
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

(defn rajapinnan-kuuntelijat [data-atom seurannan-tila rajapinta kuvaus lisataan-seuranta?]
  (let [tila data-atom #_(reaction (let [tila @data-atom
                             seurannat @seurannan-tila]
                         (println "RAJAPINTA: " (keys rajapinta))
                         (println "SEURANNAT NIL?: " (nil? seurannat))
                         #_(println "SEURANNAT: " (str seurannat))
                         (if #_@lisataan-seuranta? (nil? seurannat)
                           (do (reset! lisataan-seuranta? false)
                               tila)
                           (binding [*lisataan-seuranta?* true]
                             (reset! lisataan-seuranta? true)
                             (reset! seurannan-tila nil)
                             (swap! data-atom (fn [tila]
                                                (with-meta (merge tila seurannat)
                                                           {:lisataan-seuranta? true})))
                             #_(merge tila seurannat)))))]
    (reduce (fn [kuuntelijat [rajapinnan-nimi {:keys [polut haku]}]]
              (let [kursorit (mapv (fn [polku]
                                     (r/cursor tila polku))
                                   polut)]
                (assoc kuuntelijat
                       rajapinnan-nimi
                       (reaction (let [rajapinnan-data (apply haku (mapv deref kursorit))]
                                   (when-not (s/valid? (get rajapinta rajapinnan-nimi) rajapinnan-data)
                                     (warn "Rajapinnan " (str rajapinnan-nimi) " data:\n" (with-out-str (cljs.pprint/pprint rajapinnan-data)) " ei vastaa spekkiin. " (str (get rajapinta rajapinnan-nimi))
                                           (str (s/explain (get rajapinta rajapinnan-nimi) rajapinnan-data))))
                                   rajapinnan-data)))))
            {}
            kuvaus)))

(defn rajapinnan-asettajat [data-atom rajapinta kuvaus]
  (into {}
        (map (fn [[rajapinnan-nimi f]]
               [rajapinnan-nimi (fn [& args]
                                  (when-not (s/valid? (get rajapinta rajapinnan-nimi) args)
                                    (warn "Rajapinnalle " rajapinnan-nimi " annettu data ei vastaa spekkiin. "
                                          (s/conform (get rajapinta rajapinnan-nimi) args)))
                                  (swap! data-atom (fn [tila]
                                                     (apply f tila args))))])
             kuvaus)))

(defonce kaikki-seurannat (atom #{}))
(defonce kaydyt-seurannat (atom [#{}]))
(defonce seurannan-valitila (atom nil))


(defn aseta-seuranta! [data-atom seurannan-tila seurannat lisataan-seuranta?]
  (println (str "ASETA SEURANTA! " (keys seurannat)))
  (doseq [[seurannan-nimi {init :init}] seurannat]
    (when (fn? init)
      (swap! data-atom
             (fn [tila]
               (println "SUORITETAAN INIT: " seurannan-nimi)
               (merge tila (init tila))))))
  (let [kaikki-taman-seurannat (into #{} (map key seurannat))
        _ (swap! kaikki-seurannat (fn [seurannat] (clojure.set/union seurannat kaikki-taman-seurannat)))
        seurannat-kanava (async/chan 10)
        vanha-cache (atom (zipmap kaikki-taman-seurannat
                                  (repeat (count kaikki-taman-seurannat) ::ei-asetettu)))
        seuranta-fn! (fn [{:keys [aseta polut]} seurannan-nimi uusi-data]
                       (binding [*suoritettava-seuranta* seurannan-nimi]
                         (println "SUORITETAAN SEURANTA: " seurannan-nimi)
                         (swap! seurannan-valitila
                                (fn [tila]
                                  (let [seurattava-data (or tila uusi-data)]
                                    (println "foo")
                                    (merge tila
                                           (apply aseta seurattava-data (doall (map #(get-in seurattava-data %) polut)))))))))
        #_#_kasittelija-kanava (go-loop [[seurannan-nimi vanha uusi] (async/<! seurannat-kanava)]
                             (js/console.log (str "ALETAAN KÄSITTELEMÄÄN LOOPISSA"))
                             (js/console.log (str "-->- KAIKKI SEURANNAT: " @kaikki-seurannat))
                             (js/console.log (str "->- KAYDYT SEURANNAT: " @kaydyt-seurannat))
                             (try (let [{:keys [polut] :as seuranta} (get seurannat seurannan-nimi)
                                        vanha-data (doall (map #(get-in vanha %) polut))
                                        uusi-data (doall (map #(get-in uusi %) polut))

                                        vanha-data-muuttunut? (not= [vanha-data uusi-data] (get @vanha-cache seurannan-nimi))
                                        #_#__ (js/console.log (str "VANHA CACHE"))
                                        #_#__ (cljs.pprint/pprint @vanha-cache)
                                        #_#__ (js/console.log (str "VANHA"))
                                        #_#__ (cljs.pprint/pprint [vanha-data uusi-data])
                                        #_#__ (reset! vanha-cache [vanha-data uusi-data])

                                        seurattava-data-muuttunut? (not= vanha-data uusi-data)
                                        asetetaan-uusi-data? (and seurattava-data-muuttunut?
                                                                  *muutetaan-seurattava-arvo?*
                                                                  #_(not= *suoritettava-seuranta* seurannan-nimi)
                                                                  vanha-data-muuttunut?
                                                                  #_(not (true? (:lisataan-seuranta? (meta uusi))))
                                                                  #_(not *lisataan-seuranta?*)
                                                                  #_(or (false? @lisataan-seuranta?)
                                                                        #_(and *lisataan-seuranta?*
                                                                               seurattava-arvo?)))]
                                    (js/console.log (str "<->SEURANTA " seurannan-nimi " AJAMINEN, \n"
                                                         "MUUTETAAN?: " (and seurattava-data-muuttunut?
                                                                             *muutetaan-seurattava-arvo?*
                                                                             vanha-data-muuttunut?) "\n"
                                                         "seurattava-data-muuttunut?: " seurattava-data-muuttunut? "\n"
                                                         "*muutetaan-seurattava-arvo?*: " *muutetaan-seurattava-arvo?* "\n"
                                                         "(not= *suoritettava-seuranta* seurannan-nimi): " (not= *suoritettava-seuranta* seurannan-nimi) "\n"
                                                         "vanha-data-muuttunut?: " vanha-data-muuttunut? "\n"
                                                         "(not *lisataan-seuranta?*): " (not *lisataan-seuranta?*) "\n"
                                                         "MUUTTUNUT DATA: "))
                                    (when asetetaan-uusi-data?
                                      (swap! vanha-cache (fn [wanha]
                                                           (assoc wanha seurannan-nimi [vanha-data uusi-data])))
                                      #_(cljs.pprint/pprint (drop-last (clojure.data/diff vanha-data uusi-data)))
                                      #_(cljs.pprint/pprint (update (select-keys uusi #{:gridit :domain}) :gridit (fn [gridit]
                                                                                                                    (into {}
                                                                                                                          (mapv (fn [[k v]]
                                                                                                                                  [k (dissoc v :grid)])
                                                                                                                                gridit)))))
                                      (seuranta-fn! seuranta seurannan-nimi uusi))
                                    (swap! kaydyt-seurannat (fn [ks]
                                                              (vec
                                                                (loop [[kierros & loput] ks
                                                                       i 0
                                                                       kierrokset ks
                                                                       lisatty? false]
                                                                  (cond
                                                                    lisatty? kierrokset
                                                                    (nil? kierros) (conj kierrokset #{seurannan-nimi})
                                                                    :else (let [kierros-sisaltaa-seurannan? (contains? kierros seurannan-nimi)]
                                                                            (recur loput
                                                                                   (inc i)
                                                                                   (if kierros-sisaltaa-seurannan?
                                                                                     kierrokset
                                                                                     (vec (map-indexed (fn [index kierros]
                                                                                                         (if (= index i)
                                                                                                           (conj kierros seurannan-nimi)
                                                                                                           kierros))
                                                                                                       kierrokset)))
                                                                                   (not kierros-sisaltaa-seurannan?))))))))
                                    (when (= @kaikki-seurannat (first @kaydyt-seurannat))
                                      (js/console.log (str "KAIKKI SEURANNAT KÄYTY"))
                                      (js/console.log (str "-> KAIKKI SEURANNAT: " @kaikki-seurannat))
                                      (js/console.log (str "-> KAYDYT SEURANNAT: " @kaydyt-seurannat))
                                      (swap! kaydyt-seurannat #(vec (rest %)))
                                      (r/next-tick (fn [] (swap! data-atom merge @seurannan-valitila)))
                                      (reset! seurannan-valitila nil)
                                      #_(reset! seurannan-tila @seurannan-valitila)))
                                  (catch :default er
                                    (warn (str "VIRHE GO LOOPISSA KUUNTELUSSA: " er))))
                             (recur (async/<! seurannat-kanava)))]


    (into {}
          (doall (map (fn [[seurannan-nimi {:keys [init polut seurattava-arvo?] :as seuranta}]]
                        #_(when (fn? init)
                          (swap! data-atom
                                 (fn [tila]
                                   (println "SUORITETAAN INIT: " seurannan-nimi)
                                   (merge tila (init tila)))))
                        (add-watch data-atom
                                   seurannan-nimi
                                   (fn [_ _ vanha uusi]
                                     (println (str "SEURANTA: " seurannan-nimi))
                                     (js/console.log (str "-->- KAIKKI SEURANNAT: " @kaikki-seurannat))
                                     (js/console.log (str "->- KAYDYT SEURANNAT: " @kaydyt-seurannat))
                                     (js/console.log (str "*watch-kutsuttu-vanhalle-datalle?*: " *watch-kutsuttu-vanhalle-datalle?*))
                                     (set! (.-reaction data-atom)
                                           (reify
                                             IDeref
                                             (-deref [this]
                                               uusi)))

                                     (binding [*watch-kutsuttu-vanhalle-datalle?* true]
                                       (println "DEREF ->")
                                       @data-atom
                                       (println "<- DEREF"))

                                     (swap! kaydyt-seurannat (fn [ks]
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
                                                                                    (not kierros-sisaltaa-seurannan?))))))))
                                     (let [vanha-data (doall (map #(get-in vanha %) polut))
                                           uusi-data (doall (map #(get-in uusi %) polut))

                                           vanha-data-muuttunut? (not= [vanha-data uusi-data] (get @vanha-cache seurannan-nimi))
                                           #_#__ (js/console.log (str "VANHA CACHE"))
                                           #_#__ (cljs.pprint/pprint @vanha-cache)
                                           #_#__ (js/console.log (str "VANHA"))
                                           #_#__ (cljs.pprint/pprint [vanha-data uusi-data])
                                           #_#__ (reset! vanha-cache [vanha-data uusi-data])

                                           seurattava-data-muuttunut? (not= vanha-data uusi-data)
                                           asetetaan-uusi-data? (and seurattava-data-muuttunut?
                                                                     *muutetaan-seurattava-arvo?*
                                                                     #_(not= *suoritettava-seuranta* seurannan-nimi)
                                                                     vanha-data-muuttunut?
                                                                     #_(not (true? (:lisataan-seuranta? (meta uusi))))
                                                                     ;(not *lisataan-seuranta?*)
                                                                     #_(or (false? @lisataan-seuranta?)
                                                                           #_(and *lisataan-seuranta?*
                                                                                  seurattava-arvo?)))]
                                       (js/console.log (str "<->SEURANTA " seurannan-nimi " AJAMINEN, \n"
                                                            "MUUTETAAN?: " asetetaan-uusi-data? "\n"
                                                            "seurattava-data-muuttunut?: " seurattava-data-muuttunut? "\n"
                                                            "*muutetaan-seurattava-arvo?*: " *muutetaan-seurattava-arvo?* "\n"
                                                            "(not= *suoritettava-seuranta* seurannan-nimi): " (not= *suoritettava-seuranta* seurannan-nimi) "\n"
                                                            "vanha-data-muuttunut?: " vanha-data-muuttunut? "\n"
                                                            "(not *lisataan-seuranta?*): " (not *lisataan-seuranta?*) "\n"
                                                            "MUUTTUNUT DATA: "))
                                       (when asetetaan-uusi-data?
                                         #_(swap! vanha-cache (fn [wanha]
                                                                (assoc wanha seurannan-nimi [vanha-data uusi-data])))
                                         #_(cljs.pprint/pprint (drop-last (clojure.data/diff vanha-data uusi-data)))
                                         #_(cljs.pprint/pprint (update (select-keys uusi #{:gridit :domain}) :gridit (fn [gridit]
                                                                                                                       (into {}
                                                                                                                             (mapv (fn [[k v]]
                                                                                                                                     [k (dissoc v :grid)])
                                                                                                                                   gridit)))))

                                         (seuranta-fn! seuranta seurannan-nimi uusi)))
                                     (when (= @kaikki-seurannat (last @kaydyt-seurannat))
                                       (js/console.log (str "KAIKKI SEURANNAT KÄYTY"))
                                       (js/console.log (str "-> KAIKKI SEURANNAT: " @kaikki-seurannat))
                                       (js/console.log (str "-> KAYDYT SEURANNAT: " @kaydyt-seurannat))
                                       (when-let [sv @seurannan-valitila]
                                         (reset! seurannan-valitila nil)
                                         ;(println (str ":gridit :suunnittellut-hankinnat" (get-in sv [:gridit :suunnittellut-hankinnat])))
                                         (binding [*lisataan-seuranta?* true
                                                   *watch-kutsuttu-vanhalle-datalle?* true]
                                           (println "->SWAPATAAN DATA ATOM")
                                           (js/console.log (str "DATA " (get-in sv [:gridit :suunnittellut-hankinnat :yhteenveto :data])))
                                           (let [data-atom-sisalto (swap! data-atom merge sv)]
                                             (when (= 1 (count @kaydyt-seurannat))
                                               (r/next-tick (fn []
                                                              (reset! data-atom data-atom-sisalto)))))
                                           (println "<- DATA ATOM SWAPATTU")))
                                       (swap! kaydyt-seurannat #(vec (butlast %))))
                                     (set! (.-reaction data-atom) nil)
                                     (println "Done!")))
                        [seurannan-nimi {:seurannan-lopetus! (fn []
                                                               (remove-watch data-atom seurannan-nimi)
                                                               #_(async/close! kasittelija-kanava)
                                                               (async/close! seurannat-kanava))
                                         :seuranta-trigger! (fn []
                                                              (println "SEURANTA TRIGGERÖITY")
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
