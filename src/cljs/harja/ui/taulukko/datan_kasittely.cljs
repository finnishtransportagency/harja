(ns harja.ui.taulukko.datan-kasittely
  (:require [harja.ui.taulukko.datan-kasittely-protokollat :as p]
            [harja.loki :refer [warn]]
            [reagent.core :as r]
            [clojure.walk :as walk]
            [cljs.spec.alpha :as s]
            [reagent.ratom :as ratom])
  (:require-macros [reagent.ratom :refer [reaction]]))

(s/def ::keyfn fn?)
(s/def ::comp fn?)

(s/def ::jarjestys (s/or :sort-by-with-comp (s/keys :req-un [::keyfn ::comp])
                         :mapit-avainten-mukaan (s/coll-of any? :kind vector?)))

(defrecord Jarjestys [jarjestys])

(defmulti jarjesta-data
          (fn [jarjestys data]
            (first (s/conform ::jarjestys jarjestys))))

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
      (do (warn "Yritetään järjestää ei map muotoinen data avainten mukaan. Ei järjestetä.")
          data))))

(defmethod jarjesta-data :default
  [_ data]
  (cond
    (or (record? data) (map? data)) (mapv val data)
    (sequential? data) (vec data)
    :else data))

(defn rajapinnan-data->grid-polut
  "Grid data sisältää vain sisäkkäisiä vektoreita.
   Rajapinnan-data voi sisältää mappeja tai vektoreita"
  ([grid-data rajapinnan-data rajapinnan-jarjestys] (rajapinnan-data->grid-polut grid-data rajapinnan-data rajapinnan-jarjestys []))
  ([grid-data rajapinnan-data rajapinnan-jarjestys polku]
   (let [[jarjestys & loput-jarjestykset] rajapinnan-jarjestys
         rajapinnan-data-jarjestetty (jarjesta-data jarjestys rajapinnan-data)]
     (cond
       (instance? Jarjestys grid-data) (assoc grid-data :jarjestys polku)
       (nil? grid-data) (->Jarjestys polku)
       :else (vec
               (map-indexed (fn [index gd]
                              (rajapinnan-data->grid-polut gd
                                                           (get rajapinnan-data-jarjestetty index)
                                                           loput-jarjestykset
                                                           (cond
                                                             (or (map? rajapinnan-data) (record? rajapinnan-data)) (conj polku (key (get rajapinnan-data-jarjestetty index)))
                                                             (sequential? rajapinnan-data) (conj polku index)
                                                             :else polku)))
                            grid-data))))))

(defrecord GridDatanKasittelija [grid pointterit data jarjestys rajapinta]
  ;; data avaimet :grid-polut :rajapinta :rajapinta->grid
  p/IGridDatanKasittely
  (-muokkaa-osan-data! [this id f]
    (let [polku (get-in @(:pointterit this) [id :polku-dataan])]
      (swap! (:data this)
             (fn [{grid-polut :grid-polut
                   :as kaikki-data}]
               (update-in kaikki-data (vec (cons :rajapinta (get-in grid-polut polku))) f)))))
  (-aseta-osan-data! [this id uusi-data]
    (let [polku (get-in @(:pointterit this) [id :polku-dataan])]
      (swap! (:data this)
             (fn [{grid-polut :grid-polut
                   :as kaikki-data}]
               (assoc-in kaikki-data (vec (cons :rajapinta (get-in grid-polut polku))) uusi-data)))))
  (-muokkaa-rajapinnan-data! [this rajapinta-polku f]
    (swap! (:data this)
           (fn [{grid-polut :grid-polut
                 rajapinta-data :rajapinta
                 rajapinta-grid-id :rajapinta-grid-id :as kaikki-data}]
             (let [rajapinnan-data (update-in rajapinta-data rajapinta-polku f)
                   rajapinnan-polku-gridissa (get-in @pointterit (get rajapinta-grid-id rajapinta-polku))
                   grid-polut (update-in grid-polut
                                        rajapinnan-polku-gridissa
                                        (fn [grid-polut]
                                          (rajapinnan-data->grid-polut grid-polut rajapinnan-data (get-in @(:jarjestys this) rajapinta-polku))))]
               (assoc kaikki-data :rajapinta rajapinnan-data
                                  :grid-polut grid-polut)))))
  (-aseta-rajapinnan-data! [this rajapinta-polku uusi-data]
    (swap! (:data this)
           (fn [{grid-polut :grid-polut
                 rajapinta-grid-id :rajapinta-grid-id :as kaikki-data}]
             (let [rajapinnan-polku-gridissa (get-in @pointterit (get rajapinta-grid-id rajapinta-polku))
                   grid-polut (update-in grid-polut
                                         rajapinnan-polku-gridissa
                                        (fn [grid-polut]
                                          (rajapinnan-data->grid-polut grid-polut uusi-data (get-in @(:jarjestys this) rajapinta-polku))))]
               (assoc kaikki-data :rajapinta uusi-data
                                  :grid-polut grid-polut)))))
  (-root [this]
    @(:grid this))
  (-aseta-root! [this annettu-grid]
    (swap! (:grid this) (fn [_] annettu-grid)))
  (-pointterit [this]
    @pointterit)
  (-aseta-pointteri! [this solun-id polku]
    (swap! (:pointterit this)
           (fn [kaikki-pointterit]
             (assoc kaikki-pointterit solun-id polku))))

  (-aseta-datan-jarjestys! [this nimi uusi-jarjestys]
    (swap! (:jarjestys this)
           (fn [kaikki-jarjestykset]
             (assoc kaikki-jarjestykset nimi uusi-jarjestys))))
  (-jarjesta-data! [this]
    ;; TODO
    this
    #_(swap! (:data this)
           (fn [{grid-polut :grid-polut
                 rajapinta-grid-id :rajapinta-grid-id :as kaikki-data}]
             (let [grid-polut (update-in grid-polut
                                         (get rajapinta-grid-id rajapinta-polku)
                                         (fn [grid-polut]
                                           (rajapinnan-data->grid-polut grid-polut uusi-data (get-in @(:jarjestys this) rajapinta-polku))))]
               (assoc kaikki-data :rajapinta uusi-data
                                  :grid-polut grid-polut)))))
  (-aseta-grid-polut! [this polut]
    (swap! (:data this)
           (fn [data]
             (assoc data :grid-polut polut))))
  (-aseta-grid-rajapinta! [this grid-rajapinta]
    (swap! (:data this)
           (fn [data]
             (assoc data :rajapinta-grid-id grid-rajapinta))))
  (-rajapinta [this]
    (:rajapinta this))
  (-osan-derefable [this id]
    (let [alkuperainen-polku (-> this :pointterit deref (get id) :polku-dataan)
          osan-polku (r/atom alkuperainen-polku)
          osan-polku-dataan (r/atom (-> this :data deref :grid-polut (get-in alkuperainen-polku)))
          osan-data (r/atom (-> this :data deref :rajapinta (get-in (:jarjestys @osan-polku-dataan))))]
      (add-watch (:pointterit this)
                 (keyword (str "pointterit-" id))
                 (fn [_ _ vanha uusi]
                   (when-not (= (:polku-dataan (get vanha id)) (:polku-dataan (get uusi id)))
                     (swap! osan-polku (fn [_] (:polku-dataan (get uusi id)))))))
      (add-watch (:data this)
                 (keyword (str "data-" id))
                 (fn [_ _ vanha uusi]
                   (let [polku @osan-polku
                         vanha-pointteri (-> vanha :grid-polut (get-in polku))
                         uusi-pointteri (-> uusi :grid-polut (get-in polku))
                         vanha-data (-> vanha :rajapinta (get-in vanha-pointteri))
                         uusi-data (-> uusi :rajapinta (get-in uusi-pointteri))]
                     (when-not (= vanha-data uusi-data)
                       (swap! osan-data (fn [_] uusi-data)))
                     (when-not (= vanha-pointteri uusi-pointteri)
                       (swap! osan-polku-dataan (fn [_] uusi-pointteri))))))
      ;; Reaction on tässä hyvä mm. sen takia, ettei solu voi swap! tai reset! sitä.
      ;; Datan pitäisi kulkea loopissa: solu -> datan käsittelijä -> reaction -> back again
      (reaction
        (let [_ @osan-data
              osan-polku-dataan @osan-polku-dataan
              polku @osan-polku]
          (-> this :data deref :rajapinta (get-in osan-polku-dataan)))))))

(defn datan-kasittelija [data-atom data-atom-polku datajarjestys rajapinta]
  (let [datakasittelija (->GridDatanKasittelija (r/cursor data-atom (conj data-atom-polku :grid))
                                                (r/cursor data-atom (conj data-atom-polku :pointterit))
                                                (r/cursor data-atom (conj data-atom-polku :data))
                                                (r/cursor data-atom (conj data-atom-polku :jarjestys))
                                                rajapinta)]
    (doseq [[nimi jarjestys] datajarjestys]
      (p/aseta-datan-jarjestys! datakasittelija nimi jarjestys))
    datakasittelija))
