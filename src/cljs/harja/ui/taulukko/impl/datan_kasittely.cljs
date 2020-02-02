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

(defn rajapinnan-kuuntelijat [data-atom rajapinta kuvaus]
  (reduce (fn [kuuntelijat [rajapinnan-nimi {:keys [polut haku]}]]
            (let [kursorit (mapv (fn [polku]
                                   (r/cursor data-atom polku))
                                 polut)]
              (assoc kuuntelijat
                     rajapinnan-nimi
                     (reaction (let [rajapinnan-data (apply haku (mapv deref kursorit))]
                                 (when-not (s/valid? (get rajapinta rajapinnan-nimi) rajapinnan-data)
                                   (warn "Rajapinnan " (str rajapinnan-nimi) " data:\n" (with-out-str (cljs.pprint/pprint rajapinnan-data)) " ei vastaa spekkiin. " (str (get rajapinta rajapinnan-nimi))
                                         (str (s/explain (get rajapinta rajapinnan-nimi) rajapinnan-data))))
                                 rajapinnan-data)))))
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
                                                     (apply f tila args))))])
             kuvaus)))

(defonce kaikki-seurannat (atom #{}))
(defonce kaydyt-seurannat (atom [#{}]))
(defonce seurannan-valitila (atom nil))


(defn aseta-seuranta! [data-atom seurannat]
  (println (str "ASETA SEURANTA! " (keys seurannat)))
  (doseq [[seurannan-nimi {init :init}] seurannat]
    (when (fn? init)
      (swap! data-atom
             (fn [tila]
               (println "SUORITETAAN INIT: " seurannan-nimi)
               (merge tila (init tila))))))
  (let [kaikki-taman-seurannat (into #{} (keys seurannat))
        _ (swap! kaikki-seurannat (fn [seurannat] (clojure.set/union seurannat kaikki-taman-seurannat)))
        seuranta-fn! (fn [{:keys [aseta polut]} seurannan-nimi uusi-data]
                       (println "SUORITETAAN SEURANTA: " seurannan-nimi)
                       (swap! seurannan-valitila
                              (fn [tila]
                                (let [seurattava-data (or tila uusi-data)]
                                  (println "foo")
                                  (merge tila
                                         (apply aseta seurattava-data (doall (map #(get-in seurattava-data %) polut))))))))]


    (into {}
          (doall (map (fn [[seurannan-nimi {:keys [polut] :as seuranta}]]
                        (add-watch data-atom
                                   seurannan-nimi
                                   (fn [_ _ vanha uusi]
                                     (println (str "SEURANTA: " seurannan-nimi))
                                     (js/console.log (str "-->- KAIKKI SEURANNAT: " @kaikki-seurannat))
                                     (js/console.log (str "->- KAYDYT SEURANNAT: " @kaydyt-seurannat))

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

                                           seurattava-data-muuttunut? (not= vanha-data uusi-data)
                                           asetetaan-uusi-data? (and seurattava-data-muuttunut?
                                                                     *muutetaan-seurattava-arvo?*)]
                                       (js/console.log (str "<->SEURANTA " seurannan-nimi " AJAMINEN, \n"
                                                            "MUUTETAAN?: " asetetaan-uusi-data? "\n"
                                                            "seurattava-data-muuttunut?: " seurattava-data-muuttunut? "\n"
                                                            "*muutetaan-seurattava-arvo?*: " *muutetaan-seurattava-arvo?* "\n"
                                                            "MUUTTUNUT DATA: "))
                                       (when asetetaan-uusi-data?
                                         (seuranta-fn! seuranta seurannan-nimi uusi)))
                                     (when (= @kaikki-seurannat (last @kaydyt-seurannat))
                                       (js/console.log (str "KAIKKI SEURANNAT KÄYTY"))
                                       (js/console.log (str "-> KAIKKI SEURANNAT: " @kaikki-seurannat))
                                       (js/console.log (str "-> KAYDYT SEURANNAT: " @kaydyt-seurannat))
                                       (when-let [sv @seurannan-valitila]
                                         (reset! seurannan-valitila nil)
                                         (println "->SWAPATAAN DATA ATOM")
                                         (js/console.log (str "DATA " (get-in sv [:gridit :suunnittellut-hankinnat :yhteenveto :data])))
                                         (let [data-atom-sisalto (swap! data-atom merge sv)]
                                           (when (= 1 (count @kaydyt-seurannat))
                                             ;; Lopullinen muutos data-atomiin pitää tehdä joskus muuloin kuin
                                             ;; add-watchin sisällä, kosksa add-watchin alun perin laukaisema muutos
                                             ;; muutoin lopussa overridaa tuloksen cursoreille.
                                             (r/next-tick (fn []
                                                            (reset! data-atom data-atom-sisalto)))))
                                         (println "<- DATA ATOM SWAPATTU"))
                                       (swap! kaydyt-seurannat #(vec (butlast %))))
                                     (when (instance? reagent.ratom/RCursor data-atom)
                                       (set! (.-reaction data-atom) nil))
                                     (println "Done!")))
                        [seurannan-nimi {:seurannan-lopetus! (fn []
                                                               (remove-watch data-atom seurannan-nimi))
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
