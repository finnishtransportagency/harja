(ns harja.ui.taulukko.datan-kasittely
  (:require [harja.loki :refer [warn]]
            [reagent.core :as r]
            [cljs.spec.alpha :as s])
  (:require-macros [reagent.ratom :refer [reaction]]))

(s/def ::keyfn fn?)
(s/def ::comp fn?)

(s/def ::jarjestys (s/or :sort-by-with-comp (s/keys :req-un [::keyfn ::comp])
                         :mapit-avainten-mukaan (s/coll-of any? :kind vector?)))

(def ^:dynamic *muutetaan-seurattava-arvo?* true)
(def ^:dynamic *suoritettava-seuranta* nil)
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

(defn rajapinnan-kuuntelijat [data-atom seurannan-tila rajapinta kuvaus]
  (let [tila (reaction (let [tila @data-atom
                             seurannat @seurannan-tila]
                         (println "SEURANNAT NIL?: " (nil? seurannat))
                         (if (nil? seurannat)
                           tila
                           (binding [*lisataan-seuranta?* true]
                             (reset! seurannan-tila nil)
                             (swap! data-atom merge seurannat)
                             (merge tila seurannat)))))]
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

(defn aseta-seuranta! [data-atom seurannan-tila seurannat]
  (into {}
        (map (fn [[seurannan-nimi {:keys [init polut seurattava-arvo?] :as seuranta}]]
               (when (fn? init)
                 (swap! data-atom
                        (fn [tila]
                          (merge tila (init tila)))))
               (let [seuranta-fn! (fn [{:keys [aseta polut]} uusi-data]
                                    (binding [*suoritettava-seuranta* seurannan-nimi]
                                      (println "SUORITETAAN SEURANTA: " seurannan-nimi)
                                      (swap! seurannan-tila
                                             (fn [tila]
                                               (let [seurattava-data (or tila uusi-data @data-atom)]
                                                 (merge tila
                                                        (apply aseta seurattava-data (map #(get-in seurattava-data %) polut))))))))]
                 (add-watch data-atom
                            seurannan-nimi
                            (fn [_ data-atom vanha uusi]
                              (let [vanha-data (map #(get-in vanha %) polut)
                                    uusi-data (map #(get-in uusi %) polut)
                                    seurattava-data-muuttunut? (not= vanha-data uusi-data)
                                    asetetaan-uusi-data? (and seurattava-data-muuttunut?
                                                              *muutetaan-seurattava-arvo?*
                                                              (not *suoritettava-seuranta*)
                                                              #_(or (false? *lisataan-seuranta?*)
                                                                  (and *lisataan-seuranta?*
                                                                       seurattava-arvo?)))]
                                (when asetetaan-uusi-data?
                                  #_(cljs.pprint/pprint (update (select-keys uusi #{:gridit :domain}) :gridit (fn [gridit]
                                                                                                              (into {}
                                                                                                                    (mapv (fn [[k v]]
                                                                                                                           [k (dissoc v :grid)])
                                                                                                                         gridit)))))
                                  (seuranta-fn! seuranta uusi)))))
                 [seurannan-nimi {:seurannan-lopetus! (fn []
                                                        (remove-watch data-atom seurannan-nimi))
                                  :seuranta-trigger! (fn []
                                                       (seuranta-fn! seuranta nil))}]))
             seurannat)))

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

(defn datan-kasittelija [data-atom rajapinta haku-kuvaus asetus-kuvaus seurannat]
  (let [seurannan-tila (r/atom nil)
        seurannat (aseta-seuranta! data-atom seurannan-tila seurannat)
        kuuntelijat (rajapinnan-kuuntelijat data-atom seurannan-tila rajapinta haku-kuvaus)
        asettajat (rajapinnan-asettajat data-atom rajapinta asetus-kuvaus)]
    {:kuuntelijat kuuntelijat
     :asettajat asettajat
     :seurannat seurannat}))
